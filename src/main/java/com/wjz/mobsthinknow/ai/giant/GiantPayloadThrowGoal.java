package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 巨人的两条独立手部流水线。
 *
 * <p>每只手都先锁定候选 UUID，再独立经历会合、接取、持握、瞄准、投掷和恢复。
 * 一侧载荷死亡或掉队只会清空该侧；另一侧无需等待第二只手装满便可发动攻击。两次真正
 * 离手仍保留最少 10 tick 的视觉间隔，避免两个实体在同一帧重叠飞出。</p>
 */
public final class GiantPayloadThrowGoal extends Goal {
	private static final double PICKUP_DISTANCE_SQUARED = 5.0 * 5.0;
	private static final double MAXIMUM_THROW_DISTANCE_SQUARED = 34.0 * 34.0;
	private static final int PICKUP_ATTACH_TICK = 4;
	private static final int PICKUP_ANIMATION_TICKS = 8;
	private static final int HOLD_BEFORE_AIM_TICKS = 5;
	private static final int AIM_TICKS = 12;
	private static final int THROW_RELEASE_TICK = 4;
	private static final int THROW_ANIMATION_TICKS = 8;
	private static final int MINIMUM_RELEASE_STAGGER_TICKS = 10;

	private final Giant giant;
	private final Set<UUID> completedPayloads = new HashSet<>();
	private final EnumMap<GiantHand, Integer> phaseTicks = new EnumMap<>(GiantHand.class);
	private final EnumMap<GiantHand, Integer> cooldownDurations = new EnumMap<>(GiantHand.class);
	private final EnumSet<GiantHand> releasedHands = EnumSet.noneOf(GiantHand.class);
	private @Nullable LivingEntity target;
	private int repathCooldown;
	private int goalTicks;
	private int nextThrowStartTick;

	public GiantPayloadThrowGoal(final Giant giant) {
		this.giant = giant;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		for (GiantHand hand : GiantHand.values()) {
			this.phaseTicks.put(hand, 0);
			this.cooldownDurations.put(hand, GiantHandPhase.COOLDOWN.nominalDurationTicks());
		}
	}

	@Override
	public boolean canUse() {
		if (!enabled() || !this.giant.isAlive() || !validTarget(this.giant.getTarget())) {
			return false;
		}
		this.target = this.giant.getTarget();
		return this.hasActiveHandWork() || !GiantPassengerLayout.payloads(this.giant).isEmpty()
			|| this.hasAvailableAssignedPayload(true);
	}

	@Override
	public boolean canContinueToUse() {
		return enabled() && this.giant.isAlive() && validTarget(this.target)
			&& (this.hasActiveHandWork()
				|| !GiantPassengerLayout.payloads(this.giant).isEmpty()
				|| this.hasAvailableAssignedPayload(false));
	}

	@Override
	public void start() {
		GiantTacticsState.reconcile(this.giant);
		this.repathCooldown = 0;
		this.goalTicks = 0;
		this.nextThrowStartTick = 0;
		this.releasedHands.clear();
		for (GiantHand hand : GiantHand.values()) {
			this.phaseTicks.put(hand, 0);
			this.cooldownDurations.put(hand, 10 + this.giant.getRandom().nextInt(7));
			if (GiantTacticsState.payloadForHand(this.giant, hand) != null
				&& GiantTacticsState.handPhase(this.giant, hand) == GiantHandPhase.EMPTY) {
				this.transition(hand, GiantHandPhase.HOLDING);
			}
		}
		this.giant.setAggressive(true);
	}

	@Override
	public void stop() {
		boolean invalidated = !validTarget(this.target) || !enabled();
		if (invalidated) {
			this.releaseHeldPayloadsSafely();
		} else {
			// 普通 Goal 抢占不丢载荷；未接到掌心的预约则释放，避免永久占槽。
			for (GiantHand hand : GiantHand.values()) {
				if (GiantTacticsState.payloadForHand(this.giant, hand) != null) {
					this.transition(hand, GiantHandPhase.HOLDING);
				} else if (GiantTacticsState.handPhase(this.giant, hand) != GiantHandPhase.COOLDOWN) {
					GiantTacticsState.resetHand(this.giant, hand);
				}
			}
		}
		this.giant.getNavigation().stop();
		this.giant.setAggressive(this.giant.getTarget() != null);
		this.target = null;
		this.releasedHands.clear();
	}

	@Override
	public void tick() {
		LivingEntity currentTarget = this.target;
		if (!validTarget(currentTarget)) {
			return;
		}
		this.goalTicks++;
		this.giant.getLookControl().setLookAt(currentTarget, 55.0F, 45.0F);
		this.claimOpenHands();
		for (GiantHand hand : GiantHand.values()) {
			this.tickHand(hand, currentTarget);
		}
		this.faceHeldPayloads(currentTarget);
		this.updateMovement(currentTarget);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public int heldPayloadCount() {
		return GiantPassengerLayout.payloads(this.giant).size();
	}

	private void claimOpenHands() {
		for (GiantHand hand : GiantHand.values()) {
			if (GiantTacticsState.handPhase(this.giant, hand) != GiantHandPhase.EMPTY
				|| GiantTacticsState.hasPayloadReservation(this.giant, hand)
				|| (hand == GiantHand.RIGHT && this.rightHandReservedForBoarding())) {
				continue;
			}
			Mob candidate = this.nextAssignedPayload(false);
			if (candidate == null) {
				continue;
			}
			GiantTacticsState.assignPayload(this.giant, hand, candidate);
			this.transition(hand, GiantHandPhase.RENDEZVOUS);
		}
	}

	private void tickHand(final GiantHand hand, final LivingEntity currentTarget) {
		GiantHandPhase phase = GiantTacticsState.handPhase(this.giant, hand);
		this.phaseTicks.compute(hand, (ignored, ticks) -> ticks == null ? 1 : ticks + 1);
		switch (phase) {
			case EMPTY -> {
				// claimOpenHands 会在本 tick 提交新预约；下一 tick 再驱动其会合。
			}
			case RENDEZVOUS -> this.tickRendezvous(hand, currentTarget);
			case PICKUP -> this.tickPickup(hand, currentTarget);
			case HOLDING -> this.tickHolding(hand, currentTarget);
			case AIMING -> this.tickAiming(hand, currentTarget);
			case THROWING -> this.tickThrowing(hand, currentTarget);
			case COOLDOWN -> this.tickCooldown(hand);
		}
	}

	private void tickRendezvous(final GiantHand hand, final LivingEntity currentTarget) {
		Mob candidate = candidateMob(hand);
		if (!validCandidate(candidate)) {
			this.abandon(hand, candidate);
			return;
		}
		candidate.setTarget(currentTarget);
		if (this.giant.distanceToSqr(candidate) <= 10.0 * 10.0) {
			candidate.getNavigation().stop();
			candidate.getLookControl().setLookAt(this.giant, 65.0F, 50.0F);
		}
		if (this.giant.distanceToSqr(candidate) <= PICKUP_DISTANCE_SQUARED) {
			this.giant.getNavigation().stop();
			this.giant.swing(interactionHand(hand));
			this.transition(hand, GiantHandPhase.PICKUP);
		}
	}

	private void tickPickup(final GiantHand hand, final LivingEntity currentTarget) {
		Mob candidate = candidateMob(hand);
		if (!validCandidate(candidate)) {
			this.abandon(hand, candidate);
			return;
		}
		candidate.setTarget(currentTarget);
		candidate.getNavigation().stop();
		candidate.getLookControl().setLookAt(this.giant, 65.0F, 50.0F);
		if (candidate.getVehicle() == this.giant) {
			this.giant.positionRider(candidate);
			if (this.ticksInPhase(hand) >= PICKUP_ANIMATION_TICKS) {
				this.transition(hand, GiantHandPhase.HOLDING);
			}
			return;
		}
		if (this.giant.distanceToSqr(candidate) > PICKUP_DISTANCE_SQUARED * 1.35) {
			this.transition(hand, GiantHandPhase.RENDEZVOUS);
			return;
		}
		if (this.ticksInPhase(hand) >= PICKUP_ATTACH_TICK) {
			this.attachPickup(hand, candidate);
		}
	}

	private void tickHolding(final GiantHand hand, final LivingEntity currentTarget) {
		Entity payload = GiantTacticsState.payloadForHand(this.giant, hand);
		if (payload == null) {
			GiantTacticsState.resetHand(this.giant, hand);
			return;
		}
		if (this.ticksInPhase(hand) < HOLD_BEFORE_AIM_TICKS) {
			return;
		}
		if (this.canAimAt(currentTarget)) {
			this.transition(hand, GiantHandPhase.AIMING);
		}
	}

	private void tickAiming(final GiantHand hand, final LivingEntity currentTarget) {
		if (GiantTacticsState.payloadForHand(this.giant, hand) == null) {
			GiantTacticsState.resetHand(this.giant, hand);
			return;
		}
		if (!this.canAimAt(currentTarget)) {
			this.transition(hand, GiantHandPhase.HOLDING);
			return;
		}
		if (this.ticksInPhase(hand) >= AIM_TICKS && this.goalTicks >= this.nextThrowStartTick) {
			this.releasedHands.remove(hand);
			this.transition(hand, GiantHandPhase.THROWING);
			this.nextThrowStartTick = this.goalTicks + MINIMUM_RELEASE_STAGGER_TICKS;
		}
	}

	private void tickThrowing(final GiantHand hand, final LivingEntity currentTarget) {
		if (this.ticksInPhase(hand) >= THROW_RELEASE_TICK && !this.releasedHands.contains(hand)) {
			Entity payload = GiantTacticsState.payloadForHand(this.giant, hand);
			if (payload != null) {
				this.throwPayload(hand, payload, currentTarget);
			}
			this.releasedHands.add(hand);
		}
		if (this.ticksInPhase(hand) >= THROW_ANIMATION_TICKS) {
			this.releasedHands.remove(hand);
			this.cooldownDurations.put(hand, 10 + this.giant.getRandom().nextInt(7));
			this.transition(hand, GiantHandPhase.COOLDOWN);
		}
	}

	private void tickCooldown(final GiantHand hand) {
		if (this.ticksInPhase(hand) >= this.cooldownDurations.getOrDefault(hand, 12)) {
			GiantTacticsState.resetHand(this.giant, hand);
			this.phaseTicks.put(hand, 0);
		}
	}

	private void attachPickup(final GiantHand hand, final Mob candidate) {
		if (candidate.isPassenger() || candidate.getVehicle() != null
			|| !candidate.startRiding(this.giant, true, true)) {
			this.abandon(hand, candidate);
			return;
		}
		candidate.getNavigation().stop();
		candidate.setDeltaMovement(Vec3.ZERO);
		if (candidate instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		this.giant.positionRider(candidate);
		this.giant.swing(interactionHand(hand));
		this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.9F, hand == GiantHand.RIGHT ? 0.76F : 0.82F);
		if (this.giant.level() instanceof ServerLevel level) {
			Vec3 position = candidate.position();
			level.sendParticles(ParticleTypes.POOF, position.x, position.y + 0.5, position.z, 8, 0.35, 0.30, 0.35, 0.02);
		}
		SmartGiantMetrics.payloadPickedUp();
	}

	private void throwPayload(final GiantHand hand, final Entity entity, final LivingEntity currentTarget) {
		Vec3 origin = GiantPassengerLayout.handPosition(this.giant, hand);
		entity.stopRiding();
		entity.snapTo(origin.x, origin.y, origin.z, this.giant.getYRot(), 0.0F);
		if (entity instanceof Mob mob) {
			mob.setTarget(currentTarget);
			mob.getNavigation().stop();
			mob.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
		}
		entity.setDeltaMovement(GiantThrowMath.launchVelocity(
			origin,
			currentTarget.getEyePosition().subtract(0.0, 0.7, 0.0),
			currentTarget.getDeltaMovement()
		));
		entity.setOnGround(false);
		entity.fallDistance = 0.0F;
		this.completedPayloads.add(entity.getUUID());
		GiantTacticsState.clearPayload(this.giant, hand);
		if (entity instanceof Creeper creeper) {
			creeper.setSwellDir(1);
			creeper.ignite();
			SmartGiantMetrics.creeperThrown();
		} else if (entity instanceof Zombie) {
			SmartGiantMetrics.zombieThrown();
		}
		this.giant.swing(interactionHand(hand));
		this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.25F, hand == GiantHand.RIGHT ? 0.60F : 0.66F);
		if (this.giant.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 14, 0.45, 0.35, 0.45, 0.06);
		}
	}

	private void updateMovement(final LivingEntity currentTarget) {
		Mob rendezvous = null;
		for (GiantHand hand : GiantHand.values()) {
			GiantHandPhase phase = GiantTacticsState.handPhase(this.giant, hand);
			if (phase == GiantHandPhase.RENDEZVOUS || phase == GiantHandPhase.PICKUP) {
				Mob candidate = candidateMob(hand);
				if (validCandidate(candidate)
					&& (rendezvous == null || this.giant.distanceToSqr(candidate) < this.giant.distanceToSqr(rendezvous))) {
					rendezvous = candidate;
				}
			}
		}
		if (rendezvous != null && this.giant.distanceToSqr(rendezvous) > PICKUP_DISTANCE_SQUARED) {
			if (--this.repathCooldown <= 0 || this.giant.getNavigation().isDone()) {
				this.repathCooldown = 6;
				this.giant.getNavigation().moveTo(rendezvous, 0.92);
			}
			return;
		}

		boolean hasHeld = GiantPassengerLayout.payloads(this.giant).size() > 0;
		if (hasHeld && !this.canAimAt(currentTarget)) {
			if (--this.repathCooldown <= 0 || this.giant.getNavigation().isDone()) {
				this.repathCooldown = 7;
				this.giant.getNavigation().moveTo(currentTarget, 0.88);
			}
			return;
		}
		this.giant.getNavigation().stop();
	}

	private void faceHeldPayloads(final LivingEntity currentTarget) {
		for (GiantPassengerLayout.LivingPayload payload : GiantPassengerLayout.payloads(this.giant)) {
			// 原版实体 tick 也会 positionRider；这里再按同步阶段对齐一次，保证离手帧与掌心轨迹完全连续。
			this.giant.positionRider(payload.entity());
			if (payload.entity() instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(currentTarget);
				mob.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
			}
			if (payload.entity() instanceof Creeper creeper) {
				creeper.setSwellDir(-1);
			}
		}
	}

	private boolean hasActiveHandWork() {
		for (GiantHand hand : GiantHand.values()) {
			if (GiantTacticsState.handPhase(this.giant, hand) != GiantHandPhase.EMPTY
				|| GiantTacticsState.hasPayloadReservation(this.giant, hand)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasAvailableAssignedPayload(final boolean activeOnly) {
		if (!(this.giant.level() instanceof ServerLevel level)) {
			return false;
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		List<Mob> candidates = activeOnly
			? coordinator.activeGiantPayloadsFor(this.giant)
			: coordinator.assignedGiantPayloadsFor(this.giant);
		for (Mob candidate : candidates) {
			if (validCandidate(candidate) && !this.isClaimed(candidate)) {
				return true;
			}
		}
		return false;
	}

	private @Nullable Mob nextAssignedPayload(final boolean activeOnly) {
		if (!(this.giant.level() instanceof ServerLevel level)) {
			return null;
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		List<Mob> candidates = activeOnly
			? coordinator.activeGiantPayloadsFor(this.giant)
			: coordinator.assignedGiantPayloadsFor(this.giant);
		for (Mob candidate : candidates) {
			if (validCandidate(candidate) && !this.isClaimed(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean rightHandReservedForBoarding() {
		if (GiantTacticsState.boardingPhase(this.giant) != GiantBoardingPhase.NONE
			|| GiantTacticsState.boardingRider(this.giant) != null) {
			return true;
		}
		if (!(this.giant.level() instanceof ServerLevel level)) {
			return false;
		}
		var rider = ZombieSquadCoordinator.forLevel(level).assignedGiantHeadRiderFor(this.giant);
		return rider != null && GiantPassengerLayout.headRider(this.giant) != rider;
	}

	private boolean isClaimed(final Mob candidate) {
		for (GiantHand hand : GiantHand.values()) {
			Entity claimed = GiantTacticsState.payloadCandidate(this.giant, hand);
			if (claimed == candidate || (claimed != null && claimed.getUUID().equals(candidate.getUUID()))) {
				return true;
			}
		}
		return false;
	}

	private @Nullable Mob candidateMob(final GiantHand hand) {
		Entity candidate = GiantTacticsState.payloadCandidate(this.giant, hand);
		return candidate instanceof Mob mob ? mob : null;
	}

	private boolean validCandidate(final @Nullable Mob candidate) {
		return candidate != null
			&& candidate.isAlive()
			&& !this.completedPayloads.contains(candidate.getUUID())
			&& !candidate.isVehicle()
			&& (candidate.getVehicle() == null || candidate.getVehicle() == this.giant)
			&& GiantPassengerLayout.isPayload(candidate);
	}

	private boolean canAimAt(final LivingEntity currentTarget) {
		return this.giant.distanceToSqr(currentTarget) <= MAXIMUM_THROW_DISTANCE_SQUARED
			&& this.giant.getSensing().hasLineOfSight(currentTarget);
	}

	private void abandon(final GiantHand hand, final @Nullable Mob candidate) {
		if (candidate != null) {
			this.completedPayloads.add(candidate.getUUID());
		}
		GiantTacticsState.resetHand(this.giant, hand);
		this.phaseTicks.put(hand, 0);
	}

	private void releaseHeldPayloadsSafely() {
		for (GiantHand hand : GiantHand.values()) {
			Entity entity = GiantTacticsState.payloadForHand(this.giant, hand);
			if (entity != null) {
				Vec3 release = GiantPassengerLayout.handPosition(this.giant, hand);
				entity.stopRiding();
				entity.snapTo(release.x, this.giant.getY() + 0.25, release.z, entity.getYRot(), entity.getXRot());
				if (entity instanceof Creeper creeper && !creeper.isIgnited()) {
					creeper.setSwellDir(-1);
				}
			}
			GiantTacticsState.resetHand(this.giant, hand);
			this.phaseTicks.put(hand, 0);
		}
	}

	private void transition(final GiantHand hand, final GiantHandPhase phase) {
		GiantTacticsState.transitionHand(this.giant, hand, phase);
		this.phaseTicks.put(hand, 0);
	}

	private int ticksInPhase(final GiantHand hand) {
		return this.phaseTicks.getOrDefault(hand, 0);
	}

	private static InteractionHand interactionHand(final GiantHand hand) {
		return hand == GiantHand.RIGHT ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
	}

	private static boolean validTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.giantZombieAiEnabled && config.giantZombiePayloadThrowing;
	}
}

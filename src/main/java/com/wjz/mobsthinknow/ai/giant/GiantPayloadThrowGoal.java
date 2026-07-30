package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * 巨人的双手载荷状态机。候选实体只来自小队协调器的一次性预约，不在每个 tick 扫描附近实体；
 * 右手、左手最多各装一名苦力怕或普通僵尸，举起后按 10～16 tick 间隔依次抛向目标。
 */
public final class GiantPayloadThrowGoal extends Goal {
	private static final double PICKUP_DISTANCE_SQUARED = 5.0 * 5.0;
	private static final double MAXIMUM_THROW_DISTANCE_SQUARED = 34.0 * 34.0;
	private static final int ASSEMBLY_TIMEOUT_TICKS = 90;
	private static final int MINIMUM_AIM_TICKS = 12;
	private final Giant giant;
	private final Set<UUID> completedPayloads = new HashSet<>();
	private @Nullable LivingEntity target;
	private int assemblyTicks;
	private int repathCooldown;
	private int aimTicks;
	private int throwCooldown;
	private boolean throwing;
	private boolean finished;

	public GiantPayloadThrowGoal(final Giant giant) {
		this.giant = giant;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!enabled() || !this.giant.isAlive() || !validTarget(this.giant.getTarget())) {
			return false;
		}
		this.target = this.giant.getTarget();
		if (!GiantPassengerLayout.payloads(this.giant).isEmpty()) {
			return true;
		}
		return this.hasAvailableAssignedPayload(true);
	}

	@Override
	public boolean canContinueToUse() {
		return enabled() && this.giant.isAlive() && validTarget(this.target) && !this.finished
			&& (!GiantPassengerLayout.payloads(this.giant).isEmpty() || this.hasAvailableAssignedPayload(false));
	}

	@Override
	public void start() {
		this.assemblyTicks = 0;
		this.repathCooldown = 0;
		this.aimTicks = 0;
		this.throwCooldown = 0;
		this.throwing = GiantPassengerLayout.payloads(this.giant).size() >= GiantPassengerLayout.MAXIMUM_PAYLOADS;
		this.finished = false;
		this.giant.setAggressive(true);
	}

	@Override
	public void stop() {
		if (!validTarget(this.target) || !enabled()) {
			this.releaseHeldPayloadsSafely();
		}
		this.giant.getNavigation().stop();
		this.giant.setAggressive(this.giant.getTarget() != null);
		this.target = null;
		this.finished = false;
	}

	@Override
	public void tick() {
		LivingEntity currentTarget = this.target;
		if (!validTarget(currentTarget)) {
			return;
		}
		this.giant.getLookControl().setLookAt(currentTarget, 55.0F, 45.0F);
		this.faceHeldPayloads(currentTarget);

		if (!this.throwing) {
			this.tickAssembly(currentTarget);
			return;
		}
		this.tickThrowing(currentTarget);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public int heldPayloadCount() {
		return GiantPassengerLayout.payloads(this.giant).size();
	}

	private void tickAssembly(final LivingEntity currentTarget) {
		this.assemblyTicks++;
		if (this.heldPayloadCount() >= GiantPassengerLayout.MAXIMUM_PAYLOADS
			|| this.assemblyTicks >= ASSEMBLY_TIMEOUT_TICKS
			|| !this.hasAvailableAssignedPayload(false)) {
			this.throwing = this.heldPayloadCount() > 0;
			this.aimTicks = 0;
			if (!this.throwing) {
				this.finished = true;
			}
			return;
		}

		Mob candidate = this.nextAssignedPayload();
		if (candidate == null) {
			return;
		}
		candidate.setTarget(currentTarget);
		if (this.giant.distanceToSqr(candidate) <= 10.0 * 10.0) {
			candidate.getNavigation().stop();
			candidate.getLookControl().setLookAt(this.giant, 65.0F, 50.0F);
		}
		if (this.giant.distanceToSqr(candidate) > PICKUP_DISTANCE_SQUARED) {
			if (--this.repathCooldown <= 0 || this.giant.getNavigation().isDone()) {
				this.repathCooldown = 6;
				this.giant.getNavigation().moveTo(candidate, 0.92);
			}
			return;
		}
		this.pickUp(candidate);
	}

	private void tickThrowing(final LivingEntity currentTarget) {
		List<GiantPassengerLayout.LivingPayload> held = GiantPassengerLayout.payloads(this.giant);
		if (held.isEmpty()) {
			this.finished = true;
			return;
		}
		this.aimTicks++;
		double distance = this.giant.distanceToSqr(currentTarget);
		if (distance > MAXIMUM_THROW_DISTANCE_SQUARED || !this.giant.getSensing().hasLineOfSight(currentTarget)) {
			if (--this.repathCooldown <= 0 || this.giant.getNavigation().isDone()) {
				this.repathCooldown = 7;
				this.giant.getNavigation().moveTo(currentTarget, 0.88);
			}
			return;
		}
		this.giant.getNavigation().stop();
		if (this.aimTicks < MINIMUM_AIM_TICKS) {
			return;
		}
		if (this.throwCooldown > 0) {
			this.throwCooldown--;
			return;
		}
		// 始终先抛列表末端（左手），剩余右手载荷的挂点不会因列表压缩而跳边。
		this.throwPayload(held.getLast(), currentTarget);
		this.throwCooldown = 10 + this.giant.getRandom().nextInt(7);
	}

	private void pickUp(final Mob candidate) {
		if (!GiantPassengerLayout.hasFreeHand(this.giant) || candidate.isPassenger()
			|| !candidate.startRiding(this.giant, true, true)) {
			this.completedPayloads.add(candidate.getUUID());
			return;
		}
		candidate.getNavigation().stop();
		candidate.setDeltaMovement(Vec3.ZERO);
		if (candidate instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		this.giant.swing(GiantPassengerLayout.payloads(this.giant).size() == 1
			? net.minecraft.world.InteractionHand.MAIN_HAND
			: net.minecraft.world.InteractionHand.OFF_HAND);
		this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 0.78F);
		if (this.giant.level() instanceof ServerLevel level) {
			Vec3 position = candidate.position();
			level.sendParticles(ParticleTypes.POOF, position.x, position.y + 0.5, position.z, 8, 0.35, 0.30, 0.35, 0.02);
		}
		SmartGiantMetrics.payloadPickedUp();
		if (this.heldPayloadCount() >= GiantPassengerLayout.MAXIMUM_PAYLOADS) {
			this.throwing = true;
			this.aimTicks = 0;
		}
	}

	private void throwPayload(
		final GiantPassengerLayout.LivingPayload payload,
		final LivingEntity currentTarget
	) {
		Entity entity = payload.entity();
		Vec3 origin = GiantPassengerLayout.handPosition(this.giant, payload.handIndex());
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
		if (entity instanceof Creeper creeper) {
			creeper.setSwellDir(1);
			creeper.ignite();
			SmartGiantMetrics.creeperThrown();
		} else if (entity instanceof Zombie) {
			SmartGiantMetrics.zombieThrown();
		}
		this.giant.swing(payload.handIndex() == 0
			? net.minecraft.world.InteractionHand.MAIN_HAND
			: net.minecraft.world.InteractionHand.OFF_HAND);
		this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.25F, 0.62F);
		if (this.giant.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 14, 0.45, 0.35, 0.45, 0.06);
		}
	}

	private void faceHeldPayloads(final LivingEntity currentTarget) {
		for (GiantPassengerLayout.LivingPayload payload : GiantPassengerLayout.payloads(this.giant)) {
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

	private boolean hasAvailableAssignedPayload(final boolean activeOnly) {
		return this.nextAssignedPayload(activeOnly) != null;
	}

	private @Nullable Mob nextAssignedPayload() {
		return this.nextAssignedPayload(false);
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
			if (candidate.isAlive()
				&& !this.completedPayloads.contains(candidate.getUUID())
				&& !candidate.isVehicle()
				&& (candidate.getVehicle() == null || candidate.getVehicle() == this.giant)
				&& GiantPassengerLayout.isPayload(candidate)) {
				if (candidate.getVehicle() != this.giant) {
					return candidate;
				}
			}
		}
		return null;
	}

	private void releaseHeldPayloadsSafely() {
		for (GiantPassengerLayout.LivingPayload payload : GiantPassengerLayout.payloads(this.giant)) {
			Entity entity = payload.entity();
			entity.stopRiding();
			entity.snapTo(this.giant.getX(), this.giant.getY() + 0.25, this.giant.getZ(), entity.getYRot(), entity.getXRot());
			if (entity instanceof Creeper creeper && !creeper.isIgnited()) {
				creeper.setSwellDir(-1);
			}
		}
	}

	private static boolean validTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.giantZombieAiEnabled && config.giantZombiePayloadThrowing;
	}
}

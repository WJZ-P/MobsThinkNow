package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 巨人专用的带命中帧近身格斗状态机。
 *
 * <p>普通 {@code MeleeAttackGoal} 在冷却结束时直接调用一次攻击，没有足够的信息让十二格高的模型
 * 展示可读前摇，也不能正确结算横扫和砸地范围。本 Goal 把“接敌”和“动作播放”分开：远处仍交给
 * 原版导航，进入七格战圈后选择横扫、拍击、踩踏、正蹬、抓取或双拳砸地。所有动作在命中前
 * 留出锁向窗口；抓取仍只在接触帧造成一次直接伤害，随后把真实乘客抛出。</p>
 */
public final class GiantMeleeCombatGoal extends Goal {
	private static final double QUERY_RADIUS = 7.60;
	private static final double MAXIMUM_VERTICAL_START_DIFFERENCE = 5.0;
	private static final int MINIMUM_RECOVERY_TICKS = 8;
	private static final int MAXIMUM_RECOVERY_TICKS = 15;
	private static final int INTERRUPTED_RECOVERY_TICKS = 20;
	private static final double MAXIMUM_TRACKING_RADIANS = Math.toRadians(14.0);
	private static final double ROOT_MOTION_SUPPORT_PROBE = 0.62;
	private static final double GRAB_THROW_HORIZONTAL_SPEED = 1.28;
	private static final double GRAB_THROW_VERTICAL_SPEED = 0.46;
	private static final float MINIMUM_GRAB_INTERRUPT_DAMAGE = 6.0F;
	private static final float GRAB_INTERRUPT_MAX_HEALTH_FRACTION = 0.05F;

	private final Giant giant;
	private final double speedModifier;
	private @Nullable LivingEntity target;
	private GiantMeleeAction action = GiantMeleeAction.NONE;
	private GiantMeleeAction previousAction = GiantMeleeAction.NONE;
	private Vec3 attackForward = new Vec3(0.0, 0.0, 1.0);
	private int actionTicks;
	private int recoveryTicks;
	private int selectionCooldown;
	private int repathCooldown;
	private boolean impactApplied;
	private boolean grappleReleased;
	private float observedHealth;

	public GiantMeleeCombatGoal(final Giant giant, final double speedModifier) {
		this.giant = giant;
		this.speedModifier = speedModifier;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!enabled()) {
			return false;
		}
		LivingEntity current = this.giant.getTarget();
		if (!this.validTarget(current)) {
			return false;
		}
		this.target = current;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return enabled() && this.giant.isAlive() && this.validTarget(this.giant.getTarget());
	}

	@Override
	public void start() {
		this.target = this.giant.getTarget();
		this.action = GiantMeleeAction.NONE;
		this.actionTicks = 0;
		this.recoveryTicks = 0;
		this.selectionCooldown = 0;
		this.repathCooldown = 0;
		this.impactApplied = false;
		this.grappleReleased = false;
		this.observedHealth = this.giant.getHealth();
		this.releaseGrappledTarget(false);
		GiantTacticsState.resetMelee(this.giant);
		this.giant.setAggressive(true);
	}

	@Override
	public void stop() {
		this.releaseGrappledTarget(false);
		this.giant.getNavigation().stop();
		this.giant.setAggressive(this.giant.getTarget() != null);
		this.target = null;
		this.action = GiantMeleeAction.NONE;
		this.actionTicks = 0;
		this.recoveryTicks = 0;
		this.selectionCooldown = 0;
		this.impactApplied = false;
		this.grappleReleased = false;
		this.observedHealth = this.giant.getHealth();
		GiantTacticsState.resetMelee(this.giant);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		LivingEntity current = this.giant.getTarget();
		if (!this.validTarget(current)) {
			return;
		}
		this.target = current;
		this.giant.getLookControl().setLookAt(current, 45.0F, 35.0F);

		if (this.action.isActive()) {
			this.tickAction(current);
			return;
		}

		if (this.recoveryTicks > 0) {
			this.recoveryTicks--;
			this.followDuringRecovery(current);
			return;
		}
		if (this.selectionCooldown > 0) {
			this.selectionCooldown--;
			this.follow(current);
			return;
		}

		double horizontalDistance = horizontalDistance(this.giant.position(), current.position());
		double verticalDifference = Math.abs(current.getY() - this.giant.getY());
		if (horizontalDistance <= GiantMeleePlanner.MAXIMUM_ACTION_DISTANCE
			&& verticalDifference <= MAXIMUM_VERTICAL_START_DIFFERENCE) {
			List<LivingEntity> nearby = this.nearbyEnemies();
			Vec3 intendedForward = horizontalDirection(
				this.giant.position(),
				current.position(),
				this.attackForward
			);
			GiantMeleeAction selected = GiantMeleePlanner.choose(
				new GiantMeleePlanner.Context(
					horizontalDistance,
					this.countVisibleFrontEnemies(nearby, intendedForward),
					this.handAvailable(GiantHand.RIGHT),
					this.handAvailable(GiantHand.LEFT),
					GiantIntelligence.get(this.giant),
					current.isBlocking(),
					this.canGrabTarget(current),
					this.previousAction
				),
				this.giant.getRandom().nextDouble(),
				this.giant.getRandom().nextDouble()
			);
			if (selected.isActive()) {
				this.beginAction(selected, current);
				return;
			}
			// 墙后、垂直边界或双手忙碌但尚未进入踩踏距离时，不应每 tick 重扫实体索引。
			this.selectionCooldown = 4;
		}
		this.follow(current);
	}

	private int countVisibleFrontEnemies(final List<LivingEntity> nearby, final Vec3 forward) {
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		int count = 0;
		for (LivingEntity entity : nearby) {
			Vec3 relative = entity.position().subtract(this.giant.position());
			if (GiantMeleeGeometry.contains(
				GiantMeleeAction.SWEEP_RIGHT,
				relative.dot(forward),
				relative.dot(side),
				relative.y
			) && this.giant.hasLineOfSight(entity)) {
				count++;
			}
		}
		return count;
	}

	private void tickAction(final LivingEntity current) {
		this.giant.getNavigation().stop();
		this.actionTicks++;
		if (this.grabWasInterrupted()) {
			this.interruptGrab();
			return;
		}
		if (GiantMeleeMotion.tracksTarget(this.action, this.actionTicks)) {
			Vec3 desired = horizontalDirection(this.giant.position(), current.position(), this.attackForward);
			this.attackForward = GiantMeleeMotion.turnToward(
				this.attackForward,
				desired,
				MAXIMUM_TRACKING_RADIANS
			);
		}
		this.faceAttackDirection();
		this.applyRootMotion();
		if (!this.impactApplied && this.actionTicks >= this.action.impactTick()) {
			this.impactApplied = true;
			this.performImpact();
		}
		LivingEntity grappled = GiantTacticsState.grappledTarget(this.giant);
		if (grappled != null && grappled.getVehicle() == this.giant) {
			this.giant.positionRider(grappled);
		}
		if (this.action.hasReleaseTick()
			&& !this.grappleReleased
			&& this.actionTicks >= this.action.releaseTick()) {
			this.grappleReleased = true;
			this.releaseGrappledTarget(true);
		}
		if (this.actionTicks < this.action.durationTicks()) {
			return;
		}

		this.previousAction = this.action;
		this.action = GiantMeleeAction.NONE;
		this.actionTicks = 0;
		this.impactApplied = false;
		this.grappleReleased = false;
		this.recoveryTicks = Mth.nextInt(
			this.giant.getRandom(),
			MINIMUM_RECOVERY_TICKS,
			MAXIMUM_RECOVERY_TICKS
		);
		GiantTacticsState.resetMelee(this.giant);
	}

	private void beginAction(final GiantMeleeAction selected, final LivingEntity current) {
		this.action = selected;
		this.actionTicks = 0;
		this.impactApplied = false;
		this.grappleReleased = false;
		this.observedHealth = this.giant.getHealth();
		this.attackForward = bodyForward(this.giant);
		this.faceAttackDirection();
		this.giant.getNavigation().stop();
		GiantTacticsState.transitionMelee(this.giant, selected);
		SmartGiantMetrics.meleeActionStarted();
		switch (selected.family()) {
			case GROUND_SMASH -> this.giant.playSound(SoundEvents.RAVAGER_ROAR, 1.45F, 0.62F);
			case STOMP -> this.giant.playSound(SoundEvents.ZOMBIE_AMBIENT, 0.90F, 0.58F);
			case SWEEP, SLAP -> this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.65F, 0.72F);
			case KICK -> this.giant.playSound(SoundEvents.RAVAGER_STEP, 0.90F, 0.70F);
			case GRAB -> this.giant.playSound(SoundEvents.IRON_GOLEM_STEP, 0.85F, 0.62F);
			case NONE -> {
			}
		}
	}

	private void performImpact() {
		if (!(this.giant.level() instanceof ServerLevel level)) {
			return;
		}
		List<LivingEntity> victims = new ArrayList<>();
		Vec3 side = new Vec3(-this.attackForward.z, 0.0, this.attackForward.x);
		for (LivingEntity entity : this.nearbyEnemies()) {
			Vec3 relative = entity.position().subtract(this.giant.position());
			double forwardDistance = relative.dot(this.attackForward);
			double sideDistance = relative.dot(side);
			if (GiantMeleeGeometry.contains(this.action, forwardDistance, sideDistance, relative.y)
				&& this.giant.hasLineOfSight(entity)) {
				victims.add(entity);
			}
		}
		victims.sort(Comparator.comparingDouble(this.giant::distanceToSqr));
		if (this.action.family() == GiantMeleeAction.Family.GRAB) {
			victims.removeIf(victim -> victim != this.target);
		}
		if ((this.action.family() == GiantMeleeAction.Family.SLAP
			|| this.action.family() == GiantMeleeAction.Family.KICK
			|| this.action.family() == GiantMeleeAction.Family.GRAB) && victims.size() > 1) {
			victims.subList(1, victims.size()).clear();
		}

		float damage = (float)(
			this.giant.getAttributeValue(Attributes.ATTACK_DAMAGE) * this.action.damageMultiplier()
		);
		int hits = 0;
		for (LivingEntity victim : victims) {
			boolean blockingKick = this.action.family() == GiantMeleeAction.Family.KICK && victim.isBlocking();
			ItemStack blockingItem = blockingKick ? victim.getItemBlockingWith().copy() : ItemStack.EMPTY;
			boolean damaged = victim.hurtServer(level, this.giant.damageSources().mobAttack(this.giant), damage);
			if (!damaged && !blockingKick) {
				continue;
			}
			hits++;
			if (blockingKick) {
				victim.stopUsingItem();
				if (victim instanceof Player player && !blockingItem.isEmpty()) {
					player.getCooldowns().addCooldown(blockingItem, 30);
				}
				this.giant.playSound(SoundEvents.SHIELD_BREAK.value(), 1.10F, 0.72F);
			}
			if (this.action.family() == GiantMeleeAction.Family.GRAB && this.attachGrappledTarget(victim)) {
				continue;
			}
			victim.knockback(
				this.action.knockback(),
				this.giant.getX() - victim.getX(),
				this.giant.getZ() - victim.getZ()
			);
			Vec3 movement = victim.getDeltaMovement();
			victim.setDeltaMovement(
				movement.x,
				Math.max(movement.y, this.action.verticalLaunch()),
				movement.z
			);
		}
		SmartGiantMetrics.meleeImpact(hits);
		this.playImpactFeedback(level);
	}

	private void playImpactFeedback(final ServerLevel level) {
		Vec3 origin = switch (this.action.family()) {
			case GROUND_SMASH -> this.giant.position().add(this.attackForward.scale(3.25));
			case SLAP, SWEEP -> this.giant.position().add(this.attackForward.scale(3.0)).add(0.0, 2.0, 0.0);
			case KICK -> this.giant.position().add(this.attackForward.scale(2.75)).add(0.0, 1.10, 0.0);
			case GRAB -> this.giant.position().add(this.attackForward.scale(2.60)).add(0.0, 3.10, 0.0);
			case STOMP -> {
				double side = this.action == GiantMeleeAction.STOMP_RIGHT ? -1.05 : 1.05;
				yield this.giant.position().add(
					new Vec3(-this.attackForward.z, 0.0, this.attackForward.x).scale(side)
				);
			}
			case NONE -> this.giant.position();
		};
		switch (this.action.family()) {
			case SWEEP -> {
				this.giant.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.80F, 0.62F);
				level.sendParticles(
					ParticleTypes.SWEEP_ATTACK,
					origin.x,
					origin.y,
					origin.z,
					7,
					2.4,
					0.65,
					2.4,
					0.0
				);
			}
			case SLAP -> {
				this.giant.playSound(SoundEvents.RAVAGER_ATTACK, 1.35F, 0.72F);
				level.sendParticles(ParticleTypes.CRIT, origin.x, origin.y, origin.z, 18, 0.65, 0.70, 0.65, 0.18);
			}
			case STOMP -> {
				this.giant.playSound(SoundEvents.RAVAGER_STEP, 1.65F, 0.58F);
				this.spawnGroundDebris(level, origin, 34, 1.20);
			}
			case GROUND_SMASH -> {
				this.giant.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.25F, 0.58F);
				level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y + 0.35, origin.z, 3, 1.0, 0.25, 1.0, 0.04);
				this.spawnGroundDebris(level, origin, 72, 2.45);
			}
			case KICK -> {
				this.giant.playSound(SoundEvents.RAVAGER_ATTACK, 1.45F, 0.68F);
				level.sendParticles(ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 20, 0.75, 0.45, 0.75, 0.08);
			}
			case GRAB -> {
				this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.05F, 0.58F);
				level.sendParticles(ParticleTypes.POOF, origin.x, origin.y, origin.z, 12, 0.48, 0.60, 0.48, 0.03);
			}
			case NONE -> {
			}
		}
	}

	private void spawnGroundDebris(
		final ServerLevel level,
		final Vec3 origin,
		final int count,
		final double spread
	) {
		BlockPos groundPos = BlockPos.containing(origin.x, this.giant.getY() - 0.20, origin.z);
		BlockParticleOption debris = new BlockParticleOption(
			ParticleTypes.BLOCK,
			level.getBlockState(groundPos.below())
		);
		level.sendParticles(
			debris,
			origin.x,
			this.giant.getY() + 0.25,
			origin.z,
			count,
			spread,
			0.35,
			spread,
			0.18
		);
	}

	private List<LivingEntity> nearbyEnemies() {
		AABB query = this.giant.getBoundingBox().inflate(QUERY_RADIUS, 4.75, QUERY_RADIUS);
		return this.giant.level().getEntitiesOfClass(
			LivingEntity.class,
			query,
			this::isAttackableEnemy
		);
	}

	private boolean isAttackableEnemy(final LivingEntity entity) {
		boolean grappled = GiantTacticsState.isGrappledTarget(this.giant, entity);
		if (entity == this.giant
			|| !entity.isAlive()
			|| (!grappled && entity.getVehicle() == this.giant)
			|| (!grappled && this.giant.hasPassenger(entity))
			|| this.giant.isAlliedTo(entity)
			|| !this.giant.canAttack(entity)) {
			return false;
		}
		LivingEntity primary = this.giant.getTarget();
		boolean intendedOpponent = entity == primary
			|| entity instanceof Player
			|| entity instanceof AbstractVillager
			|| entity instanceof IronGolem
			|| this.giant.getLastHurtByMob() == entity
			|| (entity instanceof Mob mob && mob.getTarget() == this.giant);
		if (!intendedOpponent) {
			return false;
		}
		MobsThinkNowConfig config = ConfigManager.get();
		return !(config.squadIgnoreFriendlyFire
			&& entity instanceof Mob mob
			&& ZombieSquadCoordinator.areSquadmates(this.giant, mob));
	}

	private boolean handAvailable(final GiantHand hand) {
		if (GiantTacticsState.isHandReservedByGrapple(this.giant, hand)
			|| GiantTacticsState.hasPayloadReservation(this.giant, hand)
			|| GiantTacticsState.handPhase(this.giant, hand) != GiantHandPhase.EMPTY) {
			return false;
		}
		return hand != GiantHand.RIGHT || GiantTacticsState.boardingPhase(this.giant) == GiantBoardingPhase.NONE;
	}

	private void followDuringRecovery(final LivingEntity current) {
		if (horizontalDistance(this.giant.position(), current.position()) > 4.8) {
			this.follow(current);
		} else {
			this.giant.getNavigation().stop();
		}
	}

	private boolean canGrabTarget(final LivingEntity entity) {
		return entity != this.giant
			&& !entity.isPassenger()
			&& !entity.isVehicle()
			&& entity.getBbWidth() <= 2.50F
			&& entity.getBbHeight() <= 4.50F;
	}

	private boolean attachGrappledTarget(final LivingEntity victim) {
		GiantHand hand = this.action.actionHand();
		if (hand == null || !this.canGrabTarget(victim) || !this.handAvailable(hand)) {
			return false;
		}
		GiantTacticsState.beginGrapple(this.giant, hand, victim);
		if (!victim.startRiding(this.giant, true, true)) {
			GiantTacticsState.clearGrapple(this.giant);
			return false;
		}
		victim.setDeltaMovement(Vec3.ZERO);
		victim.fallDistance = 0.0F;
		this.giant.positionRider(victim);
		SmartGiantMetrics.grabbedTarget();
		return true;
	}

	private void releaseGrappledTarget(final boolean thrown) {
		LivingEntity grabbed = GiantTacticsState.grappledTarget(this.giant);
		if (grabbed == null) {
			GiantTacticsState.clearGrapple(this.giant);
			return;
		}
		Vec3 releasePosition = grabbed.position();
		AABB heldBounds = grabbed.getBoundingBox();
		if (grabbed.getVehicle() == this.giant) {
			grabbed.stopRiding();
		}
		GiantHand releaseHand = GiantTacticsState.grappleHand(this.giant);
		Vec3 safeRelease = GiantGrappleRelease.find(
			this.giant,
			grabbed,
			releasePosition,
			heldBounds,
			this.attackForward,
			releaseHand
		);
		if (safeRelease.distanceToSqr(releasePosition) > 1.0E-6) {
			SmartGiantMetrics.grappleReleaseRelocated();
		}
		grabbed.snapTo(
			safeRelease.x,
			safeRelease.y,
			safeRelease.z,
			grabbed.getYRot(),
			grabbed.getXRot()
		);
		releasePosition = safeRelease;
		Vec3 velocity = thrown
			? this.attackForward.scale(GRAB_THROW_HORIZONTAL_SPEED).add(0.0, GRAB_THROW_VERTICAL_SPEED, 0.0)
			: this.attackForward.scale(0.18).add(0.0, 0.12, 0.0);
		grabbed.setDeltaMovement(velocity);
		grabbed.setOnGround(false);
		grabbed.fallDistance = 0.0F;
		GiantTacticsState.clearGrapple(this.giant);
		if (!thrown) {
			return;
		}
		SmartGiantMetrics.grabThrowCompleted();
		this.giant.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.35F, 0.52F);
		if (this.giant.level() instanceof ServerLevel level) {
			level.sendParticles(
				ParticleTypes.CLOUD,
				releasePosition.x,
				releasePosition.y + 0.65,
				releasePosition.z,
				18,
				0.55,
				0.45,
				0.55,
				0.08
			);
		}
	}

	private boolean grabWasInterrupted() {
		float currentHealth = this.giant.getHealth();
		float damage = Math.max(0.0F, this.observedHealth - currentHealth);
		this.observedHealth = currentHealth;
		float threshold = Math.max(
			MINIMUM_GRAB_INTERRUPT_DAMAGE,
			this.giant.getMaxHealth() * GRAB_INTERRUPT_MAX_HEALTH_FRACTION
		);
		return this.action.family() == GiantMeleeAction.Family.GRAB
			&& !this.grappleReleased
			&& damage >= threshold;
	}

	private void interruptGrab() {
		this.releaseGrappledTarget(false);
		this.previousAction = this.action;
		this.action = GiantMeleeAction.NONE;
		this.actionTicks = 0;
		this.impactApplied = false;
		this.grappleReleased = false;
		this.recoveryTicks = INTERRUPTED_RECOVERY_TICKS;
		GiantTacticsState.resetMelee(this.giant);
		SmartGiantMetrics.meleeInterrupted();
		this.giant.playSound(SoundEvents.IRON_GOLEM_HURT, 1.20F, 0.72F);
		if (this.giant.level() instanceof ServerLevel level) {
			Vec3 center = this.giant.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 18, 0.90, 1.35, 0.90, 0.04);
		}
	}

	private void applyRootMotion() {
		double distance = GiantMeleeMotion.forwardStep(this.action, this.actionTicks);
		if (distance <= 0.0 || !this.giant.onGround()) {
			return;
		}
		Vec3 movement = this.attackForward.scale(distance);
		AABB destination = this.giant.getBoundingBox().move(movement);
		if (!this.giant.level().noCollision(this.giant, destination)
			|| this.giant.level().noCollision(
				this.giant,
				destination.move(0.0, -ROOT_MOTION_SUPPORT_PROBE, 0.0)
			)) {
			return;
		}
		this.giant.move(MoverType.SELF, movement);
	}

	private void faceAttackDirection() {
		float yaw = (float)(Math.atan2(-this.attackForward.x, this.attackForward.z) * Mth.RAD_TO_DEG);
		this.giant.setYRot(yaw);
		this.giant.setYBodyRot(yaw);
	}

	private void follow(final LivingEntity current) {
		if (--this.repathCooldown <= 0 || this.giant.getNavigation().isDone()) {
			this.repathCooldown = 6;
			this.giant.getNavigation().moveTo(current, this.speedModifier);
		}
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.giantZombieAiEnabled && config.giantZombieMeleeActions;
	}

	private boolean validTarget(final @Nullable LivingEntity entity) {
		return entity != null
			&& entity.isAlive()
			&& !entity.isSpectator()
			&& this.isAttackableEnemy(entity);
	}

	private static double horizontalDistance(final Vec3 first, final Vec3 second) {
		double x = second.x - first.x;
		double z = second.z - first.z;
		return Math.sqrt(x * x + z * z);
	}

	private static Vec3 horizontalDirection(final Vec3 from, final Vec3 to, final Vec3 fallback) {
		Vec3 delta = to.subtract(from).multiply(1.0, 0.0, 1.0);
		return delta.lengthSqr() > 1.0E-6 ? delta.normalize() : fallback;
	}

	private static Vec3 bodyForward(final Giant giant) {
		double yaw = giant.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	/** 仅供 GameTest 观察生产状态机，不提供任何写入口。 */
	GiantMeleeAction currentAction() {
		return this.action;
	}

	/** 仅供 GameTest 锁定前摇与唯一命中帧。 */
	int currentActionTicks() {
		return this.actionTicks;
	}

	/** 仅供 GameTest 验证锁向窗口，不暴露写入口。 */
	Vec3 currentAttackForward() {
		return this.attackForward;
	}
}

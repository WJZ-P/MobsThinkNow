package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 蜘蛛的混编机动坐骑 Goal。苦力怕继续交给专用投送状态机；本 Goal 负责把骷髅或僵尸
 * 以真实跳跃动作接到背上，并按双方较高智力提供快速换点能力。
 */
public final class SpiderSquadCarrierGoal extends Goal {
	private static final double BOARDING_TRIGGER_DISTANCE_SQUARED = 2.8 * 2.8;
	private static final double BOARDING_CATCH_DISTANCE_SQUARED = 3.2 * 3.2;
	private static final int MINIMUM_BOARDING_TICKS = 3;
	private static final int MAXIMUM_BOARDING_TICKS = 9;
	private static final int BOARDING_RETRY_TICKS = 8;
	private final Spider spider;
	private @Nullable Mob passenger;
	private @Nullable LivingEntity target;
	private int boardingTicks;
	private int repathCooldown;
	private int attackCooldown;
	private long nextBoardingAt;
	private boolean boarding;
	private double carrierSpeedSample = Double.NaN;

	public SpiderSquadCarrierGoal(final Spider spider) {
		this.spider = spider;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!enabled() || !this.spider.isAlive() || !(this.spider.level() instanceof ServerLevel level)) {
			return false;
		}
		Mob mounted = this.mountedSupportedPassenger();
		if (this.spider.getFirstPassenger() != null && mounted == null) {
			return false;
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		Mob assigned = mounted == null
			? coordinator.activeTransportPartnerFor(this.spider)
			: coordinator.assignedTransportPartnerFor(this.spider);
		if (assigned instanceof Creeper || assigned == null || (mounted != null && assigned != mounted)) {
			return false;
		}
		LivingEntity selectedTarget = preferredTarget(this.spider.getTarget(), assigned.getTarget());
		if (!isValidTarget(selectedTarget) || !this.isAvailable(assigned, mounted)) {
			return false;
		}
		this.passenger = assigned;
		this.target = selectedTarget;
		this.ensureCarrierSpeedSample();
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		Mob current = this.passenger;
		if (!enabled() || current == null || !current.isAlive() || !isValidTarget(this.target)
			|| !(this.spider.level() instanceof ServerLevel level)) {
			return false;
		}
		Mob assigned = ZombieSquadCoordinator.forLevel(level).assignedTransportPartnerFor(this.spider);
		if (assigned != current) {
			return false;
		}
		if (current.getVehicle() == this.spider) {
			return true;
		}
		double maximumSeparation = ConfigManager.get().coordinationRadius * 2.0;
		return !current.isPassenger() && this.spider.distanceToSqr(current) <= maximumSeparation * maximumSeparation;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
		this.attackCooldown = 0;
		this.spider.setAggressive(true);
		Mob current = this.passenger;
		if (current != null && current.getVehicle() != this.spider
			&& this.spider.distanceToSqr(current) <= BOARDING_TRIGGER_DISTANCE_SQUARED) {
			this.beginBoarding(current);
		}
	}

	@Override
	public void stop() {
		Mob current = this.passenger;
		boolean assignmentStillValid = false;
		if (current != null && this.spider.level() instanceof ServerLevel level) {
			assignmentStillValid = ZombieSquadCoordinator.forLevel(level)
				.assignedTransportPartnerFor(this.spider) == current;
		}
		if (!enabled() || !assignmentStillValid || !isValidTarget(this.target) || current == null || !current.isAlive()) {
			if (current != null && current.getVehicle() == this.spider) {
				current.stopRiding();
			}
			transportAccess(this.spider).mobsthinknow$clearSquadPassenger();
			this.passenger = null;
			this.target = null;
			this.carrierSpeedSample = Double.NaN;
		}
		this.resetBoarding();
		this.spider.getNavigation().stop();
		this.spider.setAggressive(false);
	}

	@Override
	public void tick() {
		Mob current = this.passenger;
		LivingEntity currentTarget = this.target;
		if (current == null || !current.isAlive() || !isValidTarget(currentTarget)) {
			return;
		}
		this.spider.setTarget(currentTarget);
		current.setTarget(currentTarget);
		if (current.getVehicle() != this.spider) {
			this.tickAssembly(current);
			return;
		}
		this.tickDelivery(current, currentTarget);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public boolean isCarryingSquadmate() {
		return this.passenger != null && this.passenger.getVehicle() == this.spider;
	}

	public double carrierSpeedMaximum() {
		this.ensureCarrierSpeedSample();
		return SpiderCombatMath.randomizedCarrierMaximum(
			ConfigManager.get().spiderCreeperCarrierSpeed,
			this.carrierSpeedSample
		);
	}

	private void tickAssembly(final Mob current) {
		long now = this.spider.level().getGameTime();
		if (!this.boarding) {
			if (now >= this.nextBoardingAt
				&& this.spider.distanceToSqr(current) <= BOARDING_TRIGGER_DISTANCE_SQUARED) {
				this.beginBoarding(current);
				return;
			}
			this.spider.getLookControl().setLookAt(current, 55.0F, 45.0F);
			if (--this.repathCooldown <= 0 || this.spider.getNavigation().isDone()) {
				this.repathCooldown = 5;
				this.spider.getNavigation().moveTo(current, 1.22);
			}
			return;
		}

		this.boardingTicks++;
		this.spider.getNavigation().stop();
		current.getNavigation().stop();
		this.spider.getLookControl().setLookAt(current, 60.0F, 45.0F);
		current.getLookControl().setLookAt(this.spider, 70.0F, 55.0F);
		this.steerBoarding(current);
		if (this.boardingTicks >= MINIMUM_BOARDING_TICKS
			&& this.spider.distanceToSqr(current) <= BOARDING_CATCH_DISTANCE_SQUARED) {
			this.completeBoarding(current);
		} else if (this.boardingTicks >= MAXIMUM_BOARDING_TICKS) {
			this.resetBoarding();
			this.nextBoardingAt = now + BOARDING_RETRY_TICKS;
		}
	}

	private void beginBoarding(final Mob current) {
		this.boarding = true;
		this.boardingTicks = 0;
		this.spider.getNavigation().stop();
		current.getNavigation().stop();
		current.lookAt(EntityAnchorArgument.Anchor.EYES, this.spider.getEyePosition());
		current.setDeltaMovement(SpiderCombatMath.boardingLeapVelocity(current.position(), this.spider.position()));
		current.setOnGround(false);
		current.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.35F, 0.96F);
		if (this.spider.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, current.getX(), current.getY() + 0.1, current.getZ(), 4, 0.18, 0.04, 0.18, 0.01);
		}
	}

	private void steerBoarding(final Mob current) {
		Vec3 offset = this.spider.position().subtract(current.position()).multiply(1.0, 0.0, 1.0);
		if (offset.lengthSqr() < 1.0E-7) {
			return;
		}
		Vec3 desired = offset.normalize().scale(Mth.clamp(offset.length() * 0.13, 0.20, 0.34));
		Vec3 movement = current.getDeltaMovement();
		current.setDeltaMovement(
			Mth.lerp(0.30, movement.x, desired.x),
			movement.y,
			Mth.lerp(0.30, movement.z, desired.z)
		);
	}

	private void completeBoarding(final Mob current) {
		if (!current.startRiding(this.spider, true, true)) {
			this.resetBoarding();
			this.nextBoardingAt = this.spider.level().getGameTime() + BOARDING_RETRY_TICKS;
			return;
		}
		transportAccess(this.spider).mobsthinknow$markSquadPassenger(current.getId());
		this.resetBoarding();
		this.spider.getNavigation().stop();
		current.getNavigation().stop();
		if (isValidTarget(this.target)) {
			current.lookAt(EntityAnchorArgument.Anchor.EYES, this.target.getEyePosition());
		}
		this.spider.playSound(SoundEvents.SPIDER_STEP, 0.9F, 0.78F);
		if (this.spider.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.POOF, this.spider.getX(), this.spider.getY() + this.spider.getBbHeight(), this.spider.getZ(), 8, 0.35, 0.25, 0.35, 0.02);
		}
	}

	private void tickDelivery(final Mob current, final LivingEntity currentTarget) {
		this.spider.getLookControl().setLookAt(currentTarget, 55.0F, 45.0F);
		current.getLookControl().setLookAt(currentTarget, 70.0F, 60.0F);
		int intelligence = Math.max(SpiderIntelligence.get(this.spider), intelligenceOf(current));
		if (--this.repathCooldown <= 0 || this.spider.getNavigation().isDone()) {
			this.repathCooldown = SpiderCombatMath.repathTicks(intelligence);
			Vec3 destination = SpiderCombatMath.carrierDestination(
				currentTarget.position(),
				currentTarget.getDeltaMovement(),
				intelligence
			);
			double speed = SpiderCombatMath.carrierSpeed(
				this.carrierSpeedMaximum(),
				intelligence,
				this.spider.level().getDifficulty().getId()
			);
			if (!this.spider.getNavigation().moveTo(destination.x, destination.y, destination.z, speed)) {
				this.spider.getNavigation().moveTo(currentTarget, speed);
			}
		}
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}
		double reach = this.spider.getBbWidth() + currentTarget.getBbWidth() + 1.0;
		if (this.attackCooldown <= 0
			&& this.spider.distanceToSqr(currentTarget) <= reach * reach
			&& this.spider.getSensing().hasLineOfSight(currentTarget)
			&& this.spider.level() instanceof ServerLevel level
			&& this.spider.doHurtTarget(level, currentTarget)) {
			this.attackCooldown = 20;
		}
	}

	private boolean isAvailable(final Mob candidate, final @Nullable Mob mounted) {
		return candidate != this.spider
			&& isSupportedPassenger(candidate)
			&& (candidate == mounted || (!candidate.isPassenger() && !candidate.isVehicle()));
	}

	private @Nullable Mob mountedSupportedPassenger() {
		Entity first = this.spider.getFirstPassenger();
		return first instanceof Mob mob && isSupportedPassenger(mob) && !(mob instanceof Creeper) ? mob : null;
	}

	private void ensureCarrierSpeedSample() {
		if (!Double.isFinite(this.carrierSpeedSample)) {
			this.carrierSpeedSample = this.spider.getRandom().nextDouble();
		}
	}

	private void resetBoarding() {
		this.boarding = false;
		this.boardingTicks = 0;
	}

	public static boolean isSupportedPassenger(final Entity entity) {
		return entity.getType() == EntityType.ZOMBIE
			|| entity.getType() == EntityType.SKELETON
			|| entity.getType() == EntityType.CREEPER;
	}

	private static int intelligenceOf(final Mob mob) {
		if (mob instanceof Zombie zombie) {
			return ZombieIntelligence.get(zombie);
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			return SkeletonIntelligence.get(skeleton);
		}
		return mob instanceof Creeper creeper ? CreeperIntelligence.get(creeper) : 1;
	}

	private static @Nullable LivingEntity preferredTarget(
		final @Nullable LivingEntity spiderTarget,
		final @Nullable LivingEntity passengerTarget
	) {
		return isValidTarget(spiderTarget) ? spiderTarget : isValidTarget(passengerTarget) ? passengerTarget : null;
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.packSurrounding && config.spiderAiEnabled;
	}

	private static SpiderSquadTransportAccess transportAccess(final Spider spider) {
		return (SpiderSquadTransportAccess)spider;
	}
}

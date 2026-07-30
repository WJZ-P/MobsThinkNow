package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.creeper.CreeperCombatMath;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 蜘蛛—苦力怕协同状态机：限频局部搜索、双向会合、真实骑乘、快速投送、近身起爆。
 * 每只蜘蛛最多保留一个苦力怕预约，预约带 60 tick 租约；这避免同一苦力怕被多只蜘蛛争抢，也避免全局 N² 配对。
 */
public final class SpiderCreeperCarrierGoal extends Goal {
	private static final double BOARDING_LEAP_TRIGGER_DISTANCE_SQUARED = 2.8 * 2.8;
	private static final double BOARDING_CATCH_DISTANCE_SQUARED = 3.2 * 3.2;
	private static final int MINIMUM_BOARDING_LEAP_TICKS = 3;
	private static final int MAXIMUM_BOARDING_LEAP_TICKS = 9;
	private static final int BOARDING_RETRY_TICKS = 6;
	private static final int RESERVATION_TICKS = 60;
	private static final int ASSEMBLY_TIMEOUT_TICKS = 100;
	private static final int MAXIMUM_CANDIDATE_CHECKS_PER_SEARCH = 32;

	private final Spider spider;
	private final UUID spiderId;
	private @Nullable Creeper creeper;
	private @Nullable LivingEntity target;
	private Phase phase = Phase.IDLE;
	private long nextSearchTick;
	private long assemblyDeadlineTick = Long.MIN_VALUE;
	private int repathCooldown;
	private int boardingLeapTicks;
	private long nextBoardingLeapTick;
	private boolean boardingLeapActive;
	private boolean fuseCommitted;
	private boolean deliveryPounceUsed;
	private boolean boardingFailed;
	private double carrierSpeedRandomSample = Double.NaN;

	public SpiderCreeperCarrierGoal(final Spider spider) {
		this.spider = spider;
		this.spiderId = spider.getUUID();
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!transportEnabled() || this.spider.getType() != EntityType.SPIDER || !this.spider.isAlive()) {
			return false;
		}

		ZombieSquadCoordinator coordinator = this.spider.level() instanceof net.minecraft.server.level.ServerLevel level
			? ZombieSquadCoordinator.forLevel(level)
			: null;
		boolean belongsToSquad = coordinator != null && coordinator.viewFor(this.spider) != null;
		Mob squadAssignment = coordinator == null ? null : coordinator.assignedTransportPartnerFor(this.spider);
		Creeper mounted = mountedCreeper();
		if (mounted != null) {
			if (belongsToSquad && squadAssignment != mounted) {
				return false;
			}
			this.creeper = mounted;
			this.target = preferredTarget(this.spider.getTarget(), mounted.getTarget());
			return isValidTarget(this.target);
		}
		if (this.spider.isVehicle()) {
			return false;
		}

		long now = this.spider.level().getGameTime();
		if (belongsToSquad) {
			Mob activeAssignment = coordinator.activeTransportPartnerFor(this.spider);
			return activeAssignment instanceof Creeper assigned && this.reserveAssignedCreeper(assigned, now);
		}
		if (this.creeper != null && now > this.assemblyDeadlineTick) {
			reservation(this.creeper).mobsthinknow$releaseSpiderReservation(this.spiderId);
			this.creeper = null;
			this.target = null;
			this.resetBoardingLeap();
			this.carrierSpeedRandomSample = Double.NaN;
		}
		if (this.creeper != null
			&& this.creeper.isAlive()
			&& reservation(this.creeper).mobsthinknow$isReservedForSpider(this.spiderId, now)
			&& isValidTarget(this.target)) {
			return true;
		}
		this.creeper = null;
		this.target = null;
		this.resetBoardingLeap();
		this.carrierSpeedRandomSample = Double.NaN;
		if (now < this.nextSearchTick) {
			return false;
		}
		this.nextSearchTick = now + 10L + this.spider.getRandom().nextInt(11);
		return this.findAndReserveCreeper(now);
	}

	@Override
	public boolean canContinueToUse() {
		if (!transportEnabled() || !this.spider.isAlive() || !isValidTarget(this.target)) {
			return false;
		}
		Creeper current = this.creeper;
		if (current == null || !current.isAlive() || this.boardingFailed) {
			return false;
		}
		if (current.getVehicle() == this.spider) {
			return this.squadAssignmentStillAllows(current);
		}
		long now = this.spider.level().getGameTime();
		double maximumSeparation = ConfigManager.get().spiderCreeperSearchRadius * 1.75;
		return !current.isPassenger()
			&& this.squadAssignmentStillAllows(current)
			&& now <= this.assemblyDeadlineTick
			&& reservation(current).mobsthinknow$isReservedForSpider(this.spiderId, now)
			&& this.spider.distanceToSqr(current) <= maximumSeparation * maximumSeparation;
	}

	private boolean reserveAssignedCreeper(final Creeper assigned, final long now) {
		if (this.creeper != null && this.creeper != assigned) {
			reservation(this.creeper).mobsthinknow$releaseSpiderReservation(this.spiderId);
		}
		LivingEntity selectedTarget = preferredTarget(this.spider.getTarget(), assigned.getTarget());
		if (!isValidTarget(selectedTarget)
			|| !compatibleTargets(this.spider.getTarget(), assigned.getTarget())
			|| !this.isAvailable(assigned, now)) {
			return false;
		}
		CreeperTransportAccess access = reservation(assigned);
		if (!access.mobsthinknow$tryReserveForSpider(this.spiderId, now, now + RESERVATION_TICKS)) {
			return false;
		}
		this.creeper = assigned;
		this.target = selectedTarget;
		this.assemblyDeadlineTick = now + ASSEMBLY_TIMEOUT_TICKS;
		this.resetBoardingLeap();
		this.nextBoardingLeapTick = now;
		this.carrierSpeedRandomSample = this.spider.getRandom().nextDouble();
		assigned.setSwellDir(-1);
		this.spider.setTarget(selectedTarget);
		assigned.setTarget(selectedTarget);
		return true;
	}

	private boolean squadAssignmentStillAllows(final Creeper current) {
		if (!(this.spider.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return true;
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		return coordinator.viewFor(this.spider) == null
			|| coordinator.assignedTransportPartnerFor(this.spider) == current;
	}

	@Override
	public void start() {
		this.repathCooldown = 0;
		this.boardingFailed = false;
		this.phase = mountedCreeper() != null ? Phase.DELIVERING : Phase.ASSEMBLING;
		this.spider.setAggressive(true);
		this.ensureCarrierSpeedSample();
		if (this.phase == Phase.ASSEMBLING) {
			if (this.creeper != null) {
				this.creeper.setSwellDir(-1);
				this.advanceBoarding(this.creeper);
			}
		} else if (this.creeper != null && isValidTarget(this.target)) {
			this.aimPayloadAtTarget(this.creeper, this.target, true);
		}
	}

	@Override
	public void stop() {
		this.spider.setAggressive(false);
		Creeper current = this.creeper;
		boolean abandon = !transportEnabled()
			|| !this.spider.isAlive()
			|| current == null
			|| !current.isAlive()
			|| !isValidTarget(this.target)
			|| this.boardingFailed
			|| (current != null && !this.squadAssignmentStillAllows(current))
			|| this.assemblyLinkIsInvalid(current);
		if (abandon) {
			this.abandonTransport();
		}
	}

	@Override
	public void tick() {
		Creeper current = this.creeper;
		LivingEntity currentTarget = this.target;
		if (current == null || !current.isAlive() || !isValidTarget(currentTarget)) {
			return;
		}
		this.spider.setTarget(currentTarget);
		current.setTarget(currentTarget);
		this.spider.getLookControl().setLookAt(currentTarget, 50.0F, 45.0F);

		if (current.getVehicle() != this.spider) {
			this.phase = Phase.ASSEMBLING;
			this.tickAssembly(current);
			return;
		}

		this.aimPayloadAtTarget(current, currentTarget, false);
		this.phase = this.fuseCommitted ? Phase.FINAL_CHARGE : Phase.DELIVERING;
		this.tickDelivery(current, currentTarget);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public Phase phase() {
		return this.phase;
	}

	public boolean isCarryingCreeper() {
		return this.creeper != null && this.creeper.getVehicle() == this.spider;
	}

	public boolean isFuseCommitted() {
		return this.fuseCommitted;
	}

	public boolean isBoardingLeapActive() {
		return this.boardingLeapActive;
	}

	public double carrierSpeedMaximum() {
		this.ensureCarrierSpeedSample();
		return SpiderCombatMath.randomizedCarrierMaximum(
			ConfigManager.get().spiderCreeperCarrierSpeed,
			this.carrierSpeedRandomSample
		);
	}

	private boolean findAndReserveCreeper(final long now) {
		MobsThinkNowConfig config = ConfigManager.get();
		double radius = config.spiderCreeperSearchRadius;
		AABB searchBox = this.spider.getBoundingBox().inflate(radius, Math.min(5.0, radius), radius);
		List<Creeper> nearby = this.spider.level().getEntitiesOfClass(
			Creeper.class,
			searchBox,
			candidate -> candidate.getType() == EntityType.CREEPER && candidate.isAlive()
		);
		SmartSpiderMetrics.carrierSearch();
		LivingEntity spiderTarget = validOrNull(this.spider.getTarget());
		Creeper selected = null;
		double selectedScore = Double.POSITIVE_INFINITY;
		int checks = 0;
		for (Creeper candidate : nearby) {
			if (checks++ >= MAXIMUM_CANDIDATE_CHECKS_PER_SEARCH) {
				break;
			}
			SmartSpiderMetrics.carrierCandidateChecked();
			if (!this.isAvailable(candidate, now)
				|| !compatibleTargets(spiderTarget, candidate.getTarget())) {
				continue;
			}
			double score = this.candidateScore(candidate);
			if (score < selectedScore) {
				selected = candidate;
				selectedScore = score;
			}
		}
		if (selected == null) {
			return false;
		}

		LivingEntity selectedTarget = preferredTarget(spiderTarget, selected.getTarget());
		if (!isValidTarget(selectedTarget)) {
			return false;
		}
		CreeperTransportAccess access = reservation(selected);
		if (!access.mobsthinknow$tryReserveForSpider(this.spiderId, now, now + RESERVATION_TICKS)) {
			return false;
		}
		this.creeper = selected;
		this.target = selectedTarget;
		this.assemblyDeadlineTick = now + ASSEMBLY_TIMEOUT_TICKS;
		this.resetBoardingLeap();
		this.nextBoardingLeapTick = now;
		this.carrierSpeedRandomSample = this.spider.getRandom().nextDouble();
		selected.setSwellDir(-1);
		this.spider.setTarget(selectedTarget);
		selected.setTarget(selectedTarget);
		return true;
	}

	private boolean isAvailable(final Creeper candidate, final long now) {
		if (candidate.isPassenger()
			|| candidate.isVehicle()
			|| candidate.isIgnited()
			|| candidate.getSwelling(1.0F) >= 0.20F) {
			return false;
		}
		CreeperTransportAccess access = reservation(candidate);
		return !access.mobsthinknow$isReservedForAnySpider(now)
			|| access.mobsthinknow$isReservedForSpider(this.spiderId, now);
	}

	private double candidateScore(final Creeper candidate) {
		// 距离占主导；同距离时略微偏好更聪明、投送后更会预判的苦力怕。
		return this.spider.distanceToSqr(candidate) - CreeperIntelligence.get(candidate) * 0.30;
	}

	private void tickAssembly(final Creeper current) {
		long now = this.spider.level().getGameTime();
		if (!reservation(current).mobsthinknow$tryReserveForSpider(
			this.spiderId,
			now,
			now + RESERVATION_TICKS
		)) {
			this.boardingFailed = true;
			return;
		}
		current.setSwellDir(-1);
		if (this.advanceBoarding(current)) {
			return;
		}
		if (--this.repathCooldown > 0) {
			return;
		}
		this.repathCooldown = 5;
		double assemblySpeed = Math.min(1.30, ConfigManager.get().spiderCreeperCarrierSpeed);
		this.spider.getNavigation().moveTo(current, assemblySpeed);
		current.getNavigation().moveTo(this.spider, 1.15);
	}

	private boolean advanceBoarding(final Creeper current) {
		if (current.getVehicle() == this.spider) {
			return true;
		}
		long now = this.spider.level().getGameTime();
		if (!this.boardingLeapActive) {
			if (now < this.nextBoardingLeapTick
				|| this.spider.distanceToSqr(current) > BOARDING_LEAP_TRIGGER_DISTANCE_SQUARED) {
				return false;
			}
			this.beginBoardingLeap(current);
			return true;
		}

		this.boardingLeapTicks++;
		this.spider.getNavigation().stop();
		current.getNavigation().stop();
		this.spider.getLookControl().setLookAt(current, 60.0F, 45.0F);
		current.getLookControl().setLookAt(this.spider, 70.0F, 55.0F);
		this.steerBoardingLeap(current);
		if (this.boardingLeapTicks >= MINIMUM_BOARDING_LEAP_TICKS
			&& this.spider.distanceToSqr(current) <= BOARDING_CATCH_DISTANCE_SQUARED) {
			return this.completeBoarding(current);
		}
		if (this.boardingLeapTicks >= MAXIMUM_BOARDING_LEAP_TICKS) {
			this.resetBoardingLeap();
			this.nextBoardingLeapTick = now + BOARDING_RETRY_TICKS;
			return false;
		}
		return true;
	}

	private void beginBoardingLeap(final Creeper current) {
		this.boardingLeapActive = true;
		this.boardingLeapTicks = 0;
		this.spider.getNavigation().stop();
		current.getNavigation().stop();
		this.spider.getLookControl().setLookAt(current, 60.0F, 45.0F);
		current.lookAt(EntityAnchorArgument.Anchor.EYES, this.spider.getEyePosition());
		current.setDeltaMovement(SpiderCombatMath.boardingLeapVelocity(current.position(), this.spider.position()));
		current.setOnGround(false);
		current.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.35F, 0.92F);
		if (this.spider.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			serverLevel.sendParticles(
				ParticleTypes.CLOUD,
				current.getX(),
				current.getY() + 0.1,
				current.getZ(),
				4,
				0.18,
				0.04,
				0.18,
				0.01
			);
		}
	}

	private void steerBoardingLeap(final Creeper current) {
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

	private boolean completeBoarding(final Creeper current) {
		if (!current.startRiding(this.spider, true, true)) {
			this.boardingFailed = true;
			return false;
		}
		this.resetBoardingLeap();
		reservation(current).mobsthinknow$releaseSpiderReservation(this.spiderId);
		this.spider.getNavigation().stop();
		current.getNavigation().stop();
		this.phase = Phase.DELIVERING;
		this.assemblyDeadlineTick = Long.MIN_VALUE;
		this.fuseCommitted = current.getSwellDir() > 0;
		this.deliveryPounceUsed = false;
		if (isValidTarget(this.target)) {
			this.aimPayloadAtTarget(current, this.target, true);
		}
		this.spider.playSound(SoundEvents.SPIDER_STEP, 0.9F, 0.72F);
		if (this.spider.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			serverLevel.sendParticles(
				ParticleTypes.POOF,
				this.spider.getX(),
				this.spider.getY() + this.spider.getBbHeight(),
				this.spider.getZ(),
				8,
				0.35,
				0.25,
				0.35,
				0.02
			);
		}
		SmartSpiderMetrics.creeperMounted();
		return true;
	}

	private void tickDelivery(final Creeper current, final LivingEntity currentTarget) {
		MobsThinkNowConfig config = ConfigManager.get();
		int combinedIntelligence = Math.max(SpiderIntelligence.get(this.spider), CreeperIntelligence.get(current));
		double startDistance = CreeperCombatMath.fuseStartDistance(
			config.creeperMaximumFuseStartDistance,
			CreeperIntelligence.get(current),
			current.isPowered(),
			this.spider.level().getDifficulty().getId()
		);
		double distanceSquared = this.spider.distanceToSqr(currentTarget);
		float fuseProgress = current.getSwelling(1.0F);
		if (!current.isIgnited()
			&& this.fuseCommitted
			&& fuseProgress < 0.55F
			&& distanceSquared > (startDistance + 6.0) * (startDistance + 6.0)) {
			current.setSwellDir(-1);
			this.fuseCommitted = false;
			this.phase = Phase.DELIVERING;
			this.deliveryPounceUsed = false;
		}
		if (!this.fuseCommitted && distanceSquared <= startDistance * startDistance) {
			this.fuseCommitted = true;
			this.phase = Phase.FINAL_CHARGE;
			current.setSwellDir(1);
			SmartSpiderMetrics.deliveryFuseStarted();
		} else if (this.fuseCommitted || current.isIgnited()) {
			this.fuseCommitted = true;
			this.phase = Phase.FINAL_CHARGE;
			current.setSwellDir(1);
		}

		if (--this.repathCooldown <= 0 || this.spider.getNavigation().isDone()) {
			this.repathCooldown = SpiderCombatMath.repathTicks(combinedIntelligence);
			Vec3 destination = SpiderCombatMath.carrierDestination(
				currentTarget.position(),
				currentTarget.getDeltaMovement(),
				combinedIntelligence
			);
			double speed = SpiderCombatMath.carrierSpeed(
				this.carrierSpeedMaximum(),
				combinedIntelligence,
				this.spider.level().getDifficulty().getId()
			);
			if (!this.spider.getNavigation().moveTo(destination.x, destination.y, destination.z, speed)) {
				this.spider.getNavigation().moveTo(currentTarget, speed);
			}
		}

		if (this.fuseCommitted
			&& !this.deliveryPounceUsed
			&& this.spider.onGround()
			&& distanceSquared >= 7.0
			&& distanceSquared <= 36.0) {
			Vec3 velocity = SpiderCombatMath.pounceVelocity(
				this.spider.position(),
				this.spider.getDeltaMovement(),
				currentTarget.position(),
				currentTarget.getDeltaMovement(),
				combinedIntelligence,
				this.spider.level().getDifficulty().getId()
			);
			this.spider.getNavigation().stop();
			this.spider.setDeltaMovement(velocity);
			this.deliveryPounceUsed = true;
			SmartSpiderMetrics.pounceStarted();
		}
	}

	private void abandonTransport() {
		Creeper current = this.creeper;
		if (current != null) {
			reservation(current).mobsthinknow$releaseSpiderReservation(this.spiderId);
			if (!current.isIgnited()) {
				current.setSwellDir(-1);
			}
			if (current.getVehicle() == this.spider) {
				current.stopRiding();
			}
		}
		this.spider.getNavigation().stop();
		this.creeper = null;
		this.target = null;
		this.phase = Phase.IDLE;
		this.fuseCommitted = false;
		this.deliveryPounceUsed = false;
		this.boardingFailed = false;
		this.resetBoardingLeap();
		this.nextBoardingLeapTick = 0L;
		this.carrierSpeedRandomSample = Double.NaN;
		this.assemblyDeadlineTick = Long.MIN_VALUE;
	}

	private void aimPayloadAtTarget(
		final Creeper current,
		final LivingEntity currentTarget,
		final boolean snapImmediately
	) {
		current.getLookControl().setLookAt(currentTarget, 70.0F, 60.0F);
		if (snapImmediately) {
			current.lookAt(EntityAnchorArgument.Anchor.EYES, currentTarget.getEyePosition());
		}
	}

	private void ensureCarrierSpeedSample() {
		if (!Double.isFinite(this.carrierSpeedRandomSample)) {
			this.carrierSpeedRandomSample = this.spider.getRandom().nextDouble();
		}
	}

	private void resetBoardingLeap() {
		this.boardingLeapActive = false;
		this.boardingLeapTicks = 0;
	}

	private boolean assemblyLinkIsInvalid(final @Nullable Creeper current) {
		if (current == null || current.getVehicle() == this.spider) {
			return false;
		}
		long now = this.spider.level().getGameTime();
		double maximumSeparation = ConfigManager.get().spiderCreeperSearchRadius * 1.75;
		return current.isPassenger()
			|| now > this.assemblyDeadlineTick
			|| !reservation(current).mobsthinknow$isReservedForSpider(this.spiderId, now)
			|| this.spider.distanceToSqr(current) > maximumSeparation * maximumSeparation;
	}

	private @Nullable Creeper mountedCreeper() {
		Entity firstPassenger = this.spider.getFirstPassenger();
		return firstPassenger instanceof Creeper mounted && mounted.getType() == EntityType.CREEPER
			? mounted
			: null;
	}

	private static boolean compatibleTargets(
		final @Nullable LivingEntity first,
		final @Nullable LivingEntity second
	) {
		LivingEntity validFirst = validOrNull(first);
		LivingEntity validSecond = validOrNull(second);
		return validFirst != null || validSecond != null
			? validFirst == null || validSecond == null || validFirst.getUUID().equals(validSecond.getUUID())
			: false;
	}

	private static @Nullable LivingEntity preferredTarget(
		final @Nullable LivingEntity spiderTarget,
		final @Nullable LivingEntity creeperTarget
	) {
		LivingEntity validSpiderTarget = validOrNull(spiderTarget);
		return validSpiderTarget != null ? validSpiderTarget : validOrNull(creeperTarget);
	}

	private static @Nullable LivingEntity validOrNull(final @Nullable LivingEntity target) {
		return isValidTarget(target) ? target : null;
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static CreeperTransportAccess reservation(final Creeper creeper) {
		return (CreeperTransportAccess)creeper;
	}

	public static boolean isTransportControlled(final Creeper creeper) {
		return creeper.getVehicle() instanceof Spider
			|| creeper.getVehicle() instanceof EnderMan
			|| ((CreeperTransportAccess)creeper).mobsthinknow$isReservedForAnyCarrier(creeper.level().getGameTime());
	}

	private static boolean transportEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.spiderAiEnabled && config.spiderCreeperCoordination;
	}

	public enum Phase {
		IDLE,
		ASSEMBLING,
		DELIVERING,
		FINAL_CHARGE
	}
}

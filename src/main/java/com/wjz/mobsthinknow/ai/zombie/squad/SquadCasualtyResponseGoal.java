package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.spider.SmartSpiderMetrics;
import com.wjz.mobsthinknow.ai.spider.SpiderCombatMath;
import com.wjz.mobsthinknow.ai.spider.SpiderSquadTransportAccess;
import com.wjz.mobsthinknow.ai.utility.EscapePathing;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyLanguage;
import com.wjz.mobsthinknow.ai.zombie.ZombieRetreatMemory;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 执行协调器的限时伤员撤离：伤员正向奔跑，护卫横移到威胁与伤员之间并主动举盾/反击；没有盾卫时，
 * 高智力蜘蛛可以接取伤员并背离威胁。
 *
 * <p>真正的配对只在协调器每个决策周期做一次 O(K) 扫描。本 Goal 每 tick 只读取自己的 O(1) 快照；
 * 火灾求生、空袭和骷髅贴脸紧急脱离仍可用更高活动优先级抢占。</p>
 */
public final class SquadCasualtyResponseGoal extends Goal {
	private static final double DESTINATION_REACHED_SQUARED = 1.35 * 1.35;
	private static final int ATTACK_COOLDOWN_TICKS = 20;
	private static final int SHIELD_RECOVERY_TICKS = 5;
	private static final double BOARDING_TRIGGER_DISTANCE_SQUARED = 2.7 * 2.7;
	private static final double BOARDING_CATCH_DISTANCE_SQUARED = 3.2 * 3.2;
	private static final int MINIMUM_BOARDING_TICKS = 3;
	private static final int MAXIMUM_BOARDING_TICKS = 10;
	private static final int BOARDING_RETRY_TICKS = 8;

	private final PathfinderMob mob;
	private final double evacuationSpeed;
	private final double escortSpeed;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.CASUALTY_EXTRACTION);
	private @Nullable SquadCasualtyDirective directive;
	private int attackCooldown;
	private long shieldResumeAt;
	private boolean boarding;
	private int boardingTicks;
	private long nextBoardingAt;

	public SquadCasualtyResponseGoal(
		final PathfinderMob mob,
		final double evacuationSpeed,
		final double escortSpeed
	) {
		this.mob = mob;
		this.evacuationSpeed = evacuationSpeed;
		this.escortSpeed = escortSpeed;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		this.directive = this.readDirective();
		return this.directive != null
			&& this.activityLease.canAcquire(this.mob, this.mob.level().getGameTime());
	}

	@Override
	public boolean canContinueToUse() {
		this.directive = this.readDirective();
		return this.directive != null
			&& this.activityLease.owns(this.mob, this.mob.level().getGameTime());
	}

	@Override
	public void start() {
		long now = this.mob.level().getGameTime();
		if (!this.activityLease.acquire(this.mob, now)) {
			return;
		}
		this.attackCooldown = 0;
		this.shieldResumeAt = now;
		this.resetBoarding();
		this.nextBoardingAt = now;
		this.mob.getNavigation().stop();
		this.mob.stopUsingItem();
		SquadCasualtyDirective current = this.directive;
		if (current != null && current.role() == SquadCasualtyDirective.Role.EVACUEE) {
			if (this.mob instanceof Zombie zombie) {
				// The squad response supersedes the per-zombie damage snapshot. Consuming it
				// here prevents a recovered evacuee from immediately starting a stale second retreat.
				ZombieRetreatMemory.discard(zombie);
				ZombieBodyLanguage.startPersistent(zombie, ZombieBodyAction.RETREAT);
			}
			this.playCallout(false);
		} else if (current != null && current.role() == SquadCasualtyDirective.Role.CARRIER) {
			this.mob.setAggressive(false);
			this.playCallout(true);
		} else {
			this.mob.setAggressive(true);
			if (this.mob instanceof Zombie zombie) {
				ZombieBodyLanguage.play(
					zombie,
					ZombieArmory.hasShield(zombie) ? ZombieBodyAction.SHIELD_TAP : ZombieBodyAction.WAR_CRY
				);
			}
			this.playCallout(true);
		}
		SmartZombieMetrics.casualtyGoalStarted();
		this.tick();
	}

	@Override
	public void tick() {
		long now = this.mob.level().getGameTime();
		if (!this.activityLease.renew(this.mob, now)) {
			return;
		}
		SquadCasualtyDirective current = this.directive;
		LivingEntity threat = this.mob.getTarget();
		if (current == null || threat == null || !threat.isAlive()) {
			return;
		}

		this.attackCooldown = Math.max(0, this.attackCooldown - 1);
		if (current.role() == SquadCasualtyDirective.Role.CARRIER && this.mob instanceof Spider spider) {
			this.tickCarrier(spider, current, threat);
			return;
		}
		if (current.role() == SquadCasualtyDirective.Role.EVACUEE
			&& this.tickEvacueeBoarding(current, threat, now)) {
			return;
		}
		Vec3 destination = current.destination();
		double speed = current.role() == SquadCasualtyDirective.Role.EVACUEE
			? this.evacuationSpeed
			: this.escortSpeed;
		boolean reached = this.mob.position().distanceToSqr(destination) <= DESTINATION_REACHED_SQUARED;
		if (reached) {
			this.mob.getNavigation().stop();
		} else if (this.mob.getNavigation().isDone() || Math.floorMod(this.mob.tickCount, 6) == 0) {
			this.mob.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
		}

		if (current.role() == SquadCasualtyDirective.Role.EVACUEE) {
			EscapePathing.faceCurrentPathOrDestination(this.mob, destination);
			return;
		}

		this.faceThreat(threat);
		this.tickEscortCombat(threat, now);
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		if (this.mob instanceof Zombie zombie) {
			ZombieBodyLanguage.stopPersistent(zombie, ZombieBodyAction.RETREAT);
			if (zombie.isUsingItem() && zombie.getUsedItemHand() == InteractionHand.OFF_HAND) {
				zombie.stopUsingItem();
			}
		}
		this.mob.setAggressive(this.mob.getTarget() != null);
		this.directive = null;
		this.attackCooldown = 0;
		this.shieldResumeAt = 0L;
		this.resetBoarding();
		this.nextBoardingAt = 0L;
		this.activityLease.release(this.mob);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tickEscortCombat(final LivingEntity threat, final long now) {
		boolean meleeRange = this.mob.isWithinMeleeAttackRange(threat)
			&& this.mob.getSensing().hasLineOfSight(threat);
		if (this.mob instanceof Zombie zombie && ZombieArmory.hasShield(zombie)) {
			if (meleeRange && this.attackCooldown == 0 && this.mob.level() instanceof ServerLevel level) {
				zombie.stopUsingItem();
				this.performEscortAttack(level, threat);
				this.shieldResumeAt = now + SHIELD_RECOVERY_TICKS;
			} else if (now >= this.shieldResumeAt
				&& !ZombieArmory.isShieldDisabled(zombie)
				&& !zombie.isUsingItem()) {
				zombie.startUsingItem(InteractionHand.OFF_HAND);
			}
			return;
		}
		if (meleeRange && this.attackCooldown == 0 && this.mob.level() instanceof ServerLevel level) {
			this.performEscortAttack(level, threat);
		}
	}

	private void performEscortAttack(final ServerLevel level, final LivingEntity threat) {
		this.mob.swing(InteractionHand.MAIN_HAND);
		if (this.mob.doHurtTarget(level, threat)) {
			SmartZombieMetrics.casualtyEscortHit();
		}
		this.attackCooldown = ATTACK_COOLDOWN_TICKS;
	}

	/** 蜘蛛先与指定伤员会合；接到乘员后只执行撤离导航，不在运输途中回头贴脸。 */
	private void tickCarrier(
		final Spider spider,
		final SquadCasualtyDirective current,
		final LivingEntity threat
	) {
		Mob casualty = this.mobById(current.casualtyId());
		if (casualty == null || !casualty.isAlive()) {
			spider.getNavigation().stop();
			return;
		}
		spider.setAggressive(false);
		if (casualty.getVehicle() != spider) {
			spider.getLookControl().setLookAt(casualty, 65.0F, 50.0F);
			if (spider.distanceToSqr(casualty) <= BOARDING_TRIGGER_DISTANCE_SQUARED) {
				spider.getNavigation().stop();
			} else if (spider.getNavigation().isDone() || Math.floorMod(spider.tickCount, 5) == 0) {
				spider.getNavigation().moveTo(casualty, Math.min(this.evacuationSpeed, 1.30));
			}
			return;
		}

		casualty.getLookControl().setLookAt(threat, 55.0F, 45.0F);
		Vec3 destination = current.destination();
		if (spider.position().distanceToSqr(destination) <= DESTINATION_REACHED_SQUARED) {
			spider.getNavigation().stop();
		} else if (spider.getNavigation().isDone() || Math.floorMod(spider.tickCount, 4) == 0) {
			spider.getNavigation().moveTo(destination.x, destination.y, destination.z, this.evacuationSpeed);
		}
		EscapePathing.faceCurrentPathOrDestination(spider, destination);
	}

	/** 伤员执行与普通机动乘员一致的可见起跳，而不是在两实体接触瞬间硬切到蛛背。 */
	private boolean tickEvacueeBoarding(
		final SquadCasualtyDirective current,
		final LivingEntity threat,
		final long now
	) {
		if (!ConfigManager.get().squadSpiderCasualtyTransport) {
			return false;
		}
		Mob escort = this.mobById(current.escortId());
		if (!(escort instanceof Spider spider) || !spider.isAlive()) {
			return false;
		}
		SquadCasualtyDirective carrierDirective = this.readDirectiveFor(spider);
		if (carrierDirective == null || carrierDirective.role() != SquadCasualtyDirective.Role.CARRIER) {
			return false;
		}
		if (this.mob.getVehicle() == spider) {
			this.mob.getNavigation().stop();
			this.mob.getLookControl().setLookAt(threat, 55.0F, 45.0F);
			return true;
		}
		if (this.mob.isPassenger() || this.mob.isVehicle()) {
			return false;
		}

		if (this.boarding) {
			this.boardingTicks++;
			this.mob.getNavigation().stop();
			spider.getNavigation().stop();
			this.mob.getLookControl().setLookAt(spider, 70.0F, 55.0F);
			this.steerBoarding(spider);
			if (this.boardingTicks >= MINIMUM_BOARDING_TICKS
				&& this.mob.distanceToSqr(spider) <= BOARDING_CATCH_DISTANCE_SQUARED) {
				this.completeBoarding(spider);
			} else if (this.boardingTicks >= MAXIMUM_BOARDING_TICKS) {
				this.resetBoarding();
				this.nextBoardingAt = now + BOARDING_RETRY_TICKS;
			}
			return true;
		}

		this.mob.getLookControl().setLookAt(spider, 65.0F, 50.0F);
		if (now >= this.nextBoardingAt && this.mob.distanceToSqr(spider) <= BOARDING_TRIGGER_DISTANCE_SQUARED) {
			this.beginBoarding(spider);
		} else if (this.mob.getNavigation().isDone() || Math.floorMod(this.mob.tickCount, 6) == 0) {
			this.mob.getNavigation().moveTo(spider, Math.min(this.evacuationSpeed, 1.05));
		}
		return true;
	}

	private void beginBoarding(final Spider spider) {
		this.boarding = true;
		this.boardingTicks = 0;
		this.mob.getNavigation().stop();
		spider.getNavigation().stop();
		this.mob.lookAt(EntityAnchorArgument.Anchor.EYES, spider.getEyePosition());
		this.mob.setDeltaMovement(SpiderCombatMath.boardingLeapVelocity(this.mob.position(), spider.position()));
		this.mob.setOnGround(false);
		this.mob.playSound(SoundEvents.SLIME_JUMP_SMALL, 0.35F, 1.08F);
		if (this.mob.level() instanceof ServerLevel level) {
			level.sendParticles(
				ParticleTypes.CLOUD,
				this.mob.getX(),
				this.mob.getY() + 0.1,
				this.mob.getZ(),
				4,
				0.18,
				0.04,
				0.18,
				0.01
			);
		}
	}

	private void steerBoarding(final Spider spider) {
		Vec3 offset = spider.position().subtract(this.mob.position()).multiply(1.0, 0.0, 1.0);
		if (offset.lengthSqr() < 1.0E-7) {
			return;
		}
		Vec3 desired = offset.normalize().scale(Mth.clamp(offset.length() * 0.13, 0.20, 0.34));
		Vec3 movement = this.mob.getDeltaMovement();
		this.mob.setDeltaMovement(
			Mth.lerp(0.30, movement.x, desired.x),
			movement.y,
			Mth.lerp(0.30, movement.z, desired.z)
		);
	}

	private void completeBoarding(final Spider spider) {
		if (!this.mob.startRiding(spider, true, true)) {
			this.resetBoarding();
			this.nextBoardingAt = this.mob.level().getGameTime() + BOARDING_RETRY_TICKS;
			return;
		}
		((SpiderSquadTransportAccess)spider).mobsthinknow$markSquadPassenger(this.mob.getId());
		this.resetBoarding();
		this.mob.getNavigation().stop();
		spider.getNavigation().stop();
		spider.playSound(SoundEvents.SPIDER_STEP, 0.85F, 1.16F);
		if (this.mob.level() instanceof ServerLevel level) {
			level.sendParticles(
				ParticleTypes.POOF,
				spider.getX(),
				spider.getY() + spider.getBbHeight(),
				spider.getZ(),
				8,
				0.35,
				0.25,
				0.35,
				0.02
			);
		}
		SmartSpiderMetrics.casualtyPickup();
	}

	private void resetBoarding() {
		this.boarding = false;
		this.boardingTicks = 0;
	}

	private void faceThreat(final LivingEntity threat) {
		this.mob.getLookControl().setLookAt(threat, 60.0F, 45.0F);
		double x = threat.getX() - this.mob.getX();
		double z = threat.getZ() - this.mob.getZ();
		if (x * x + z * z < 1.0E-6) {
			return;
		}
		float wantedYaw = (float)(Mth.atan2(z, x) * Mth.RAD_TO_DEG) - 90.0F;
		this.mob.setYBodyRot(Mth.approachDegrees(this.mob.yBodyRot, wantedYaw, 18.0F));
	}

	private void playCallout(final boolean escort) {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return;
		}
		var sound = this.mob instanceof Zombie
			? SoundEvents.ZOMBIE_AMBIENT
			: this.mob instanceof AbstractSkeleton
				? SoundEvents.SKELETON_AMBIENT
				: SoundEvents.SPIDER_AMBIENT;
		level.playSound(
			null,
			this.mob,
			sound,
			SoundSource.HOSTILE,
			escort ? 0.85F : 0.65F,
			escort ? 0.78F : 1.28F
		);
	}

	private @Nullable SquadCasualtyDirective readDirective() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled
			|| !config.packSurrounding
			|| !config.squadCasualtyExtraction
			|| !(this.mob.level() instanceof ServerLevel level)
			|| !this.mob.isAlive()
			|| this.mob.isFallFlying()) {
			return null;
		}
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return null;
		}
		SquadCasualtyDirective current = ZombieSquadCoordinator.forLevel(level).casualtyDirectiveFor(this.mob);
		if (current == null) {
			return null;
		}
		if (current.role() == SquadCasualtyDirective.Role.CARRIER) {
			return config.squadSpiderCasualtyTransport
				&& this.mob instanceof Spider
				&& !this.mob.isPassenger()
				&& (!this.mob.isVehicle()
					|| (this.mob.getFirstPassenger() instanceof Mob passenger
						&& passenger.getId() == current.casualtyId()))
				? current
				: null;
		}
		if (current.role() == SquadCasualtyDirective.Role.EVACUEE && this.mob.isPassenger()) {
			return config.squadSpiderCasualtyTransport
				&& this.mob.getVehicle() instanceof Spider spider
				&& spider.getId() == current.escortId()
				? current
				: null;
		}
		return !this.mob.isPassenger() && !this.mob.isVehicle() ? current : null;
	}

	private @Nullable SquadCasualtyDirective readDirectiveFor(final Mob member) {
		return member.level() instanceof ServerLevel level
			? ZombieSquadCoordinator.forLevel(level).casualtyDirectiveFor(member)
			: null;
	}

	private @Nullable Mob mobById(final int entityId) {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(entityId);
		return entity instanceof Mob found ? found : null;
	}
}

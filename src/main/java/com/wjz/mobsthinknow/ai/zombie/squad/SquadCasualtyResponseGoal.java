package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.utility.EscapePathing;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyLanguage;
import com.wjz.mobsthinknow.ai.zombie.ZombieRetreatMemory;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 执行协调器的限时伤员撤离：伤员正向奔跑，护卫横移到威胁与伤员之间并主动举盾/反击。
 *
 * <p>真正的配对只在协调器每个决策周期做一次 O(K) 扫描。本 Goal 每 tick 只读取自己的 O(1) 快照；
 * 火灾求生、空袭和骷髅贴脸紧急脱离仍可用更高活动优先级抢占。</p>
 */
public final class SquadCasualtyResponseGoal extends Goal {
	private static final double DESTINATION_REACHED_SQUARED = 1.35 * 1.35;
	private static final int ATTACK_COOLDOWN_TICKS = 20;
	private static final int SHIELD_RECOVERY_TICKS = 5;

	private final PathfinderMob mob;
	private final double evacuationSpeed;
	private final double escortSpeed;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.CASUALTY_EXTRACTION);
	private @Nullable SquadCasualtyDirective directive;
	private int attackCooldown;
	private long shieldResumeAt;

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
			|| this.mob.isPassenger()
			|| this.mob.isVehicle()) {
			return null;
		}
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return null;
		}
		return ZombieSquadCoordinator.forLevel(level).casualtyDirectiveFor(this.mob);
	}
}

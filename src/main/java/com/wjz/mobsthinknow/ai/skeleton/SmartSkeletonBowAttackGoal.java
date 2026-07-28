package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.MovementMode;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 普通骷髅的远程战斗状态机。
 *
 * <p>关闭总开关或骷髅开关时，整个生命周期直接委托给原版
 * {@link RangedBowAttackGoal}；开启时则在同一 Goal 内完成接敌、持续侧移、近身脱离、
 * 来箭闪避和拉弓射击。这样配置热切换不会让一只骷髅同时运行两个 MOVE/LOOK Goal。</p>
 */
public final class SmartSkeletonBowAttackGoal extends RangedBowAttackGoal<AbstractSkeleton> {
	private static final int BOW_DRAW_TICKS = 20;
	private static final int LOST_SIGHT_CANCEL_TICKS = 15;
	private static final int PROJECTILE_SCAN_INTERVAL_TICKS = 3;
	private static final int MINIMUM_DODGE_TICKS = 7;
	private static final int MAXIMUM_DODGE_TICKS = 10;
	private static final double RETREAT_SPEED_MODIFIER = 1.25;

	private final AbstractSkeleton skeleton;
	private final double speedModifier;
	private boolean smartMode;
	private int attackTime;
	private int seeTime;
	private int approachRepathCooldown;
	private int retreatRepathCooldown;
	private int projectileScanCooldown;
	private int dodgeTicks;
	private int dodgeDirection = 1;
	private int strafeDirection = 1;
	private int strafeSwitchTicks;
	private MovementMode movementMode = MovementMode.APPROACH;

	public SmartSkeletonBowAttackGoal(
		final AbstractSkeleton skeleton,
		final double speedModifier,
		final int vanillaAttackInterval,
		final float attackRadius
	) {
		super(skeleton, speedModifier, vanillaAttackInterval, attackRadius);
		this.skeleton = skeleton;
		this.speedModifier = speedModifier;
	}

	@Override
	public boolean canUse() {
		this.smartMode = smartAiEnabled();
		if (!this.smartMode) {
			return super.canUse();
		}

		LivingEntity target = this.skeleton.getTarget();
		return target != null && target.isAlive() && this.isHoldingBow();
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.smartMode) {
			// 运行中的原版兼容模式遇到热开启时主动结束一次，让下一拍以智能状态重新 start。
			if (smartAiEnabled()) {
				return false;
			}
			return super.canContinueToUse();
		}
		if (!smartAiEnabled()) {
			return false;
		}

		LivingEntity target = this.skeleton.getTarget();
		return target != null && target.isAlive() && this.isHoldingBow();
	}

	@Override
	public void start() {
		super.start();
		if (!this.smartMode) {
			return;
		}

		this.attackTime = this.skeleton.getRandom().nextInt(9);
		this.seeTime = 0;
		this.approachRepathCooldown = 0;
		this.retreatRepathCooldown = 0;
		this.projectileScanCooldown = 0;
		this.dodgeTicks = 0;
		this.dodgeDirection = randomDirection();
		this.strafeDirection = randomDirection();
		this.strafeSwitchTicks = nextStrafeWindow();
		this.movementMode = MovementMode.APPROACH;
	}

	@Override
	public void stop() {
		super.stop();
		if (this.smartMode) {
			this.skeleton.getNavigation().stop();
		}
		this.smartMode = false;
		this.dodgeTicks = 0;
		this.movementMode = MovementMode.APPROACH;
	}

	@Override
	public void tick() {
		if (!this.smartMode) {
			super.tick();
			return;
		}

		LivingEntity target = this.skeleton.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		boolean hasLineOfSight = this.skeleton.getSensing().hasLineOfSight(target);
		updateSightMemory(hasLineOfSight);
		updateProjectileThreat(config);

		double distanceSquared = this.skeleton.distanceToSqr(target);
		MovementMode selected = SkeletonCombatMath.chooseMovement(
			distanceSquared,
			hasLineOfSight,
			config.skeletonPreferredRange,
			this.dodgeTicks > 0
		);
		transitionTo(selected);
		this.skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);

		switch (selected) {
			case APPROACH -> approach(target);
			case STRAFE -> strafe(target, distanceSquared, config.skeletonPreferredRange);
			case RETREAT -> retreat(target);
			case DODGE -> dodge(target, distanceSquared, config.skeletonPreferredRange);
		}

		tickBow(target, hasLineOfSight);
	}

	/** 当前状态仅用于 GameTest、诊断 UI 和后续表现层，不允许外部反向驱动 Goal。 */
	public MovementMode movementMode() {
		return this.movementMode;
	}

	private void approach(final LivingEntity target) {
		this.retreatRepathCooldown = 0;
		if (this.approachRepathCooldown-- <= 0) {
			this.approachRepathCooldown = 8;
			this.skeleton.getNavigation().moveTo(target, this.speedModifier);
		}
	}

	private void strafe(
		final LivingEntity target,
		final double distanceSquared,
		final double configuredPreferredRange
	) {
		this.skeleton.getNavigation().stop();
		this.approachRepathCooldown = 0;
		this.retreatRepathCooldown = 0;
		if (--this.strafeSwitchTicks <= 0) {
			if (this.skeleton.getRandom().nextFloat() < 0.45F) {
				this.strafeDirection = -this.strafeDirection;
			}
			this.strafeSwitchTicks = nextStrafeWindow();
		}

		double preferredRange = validPreferredRange(configuredPreferredRange);
		double distance = Math.sqrt(Math.max(0.0, distanceSquared));
		float forward = distance < preferredRange * 0.82
			? -0.55F
			: distance > preferredRange * 1.12 ? 0.45F : 0.0F;
		float lateral = (target.isBlocking() ? 0.75F : 0.58F) * this.strafeDirection;
		this.skeleton.getMoveControl().strafe(forward, lateral);
	}

	private void retreat(final LivingEntity target) {
		this.approachRepathCooldown = 0;
		if (this.retreatRepathCooldown-- <= 0) {
			this.retreatRepathCooldown = 7;
			Vec3 candidate = DefaultRandomPos.getPosAway(this.skeleton, 9, 4, target.position());
			if (candidate != null
				&& candidate.distanceToSqr(target.position()) > this.skeleton.distanceToSqr(target) + 1.0) {
				this.skeleton.getNavigation().moveTo(
					candidate.x,
					candidate.y,
					candidate.z,
					RETREAT_SPEED_MODIFIER
				);
			} else {
				this.skeleton.getNavigation().stop();
			}
		}

		if (this.skeleton.getNavigation().isDone()) {
			this.skeleton.getMoveControl().strafe(-0.85F, 0.45F * this.strafeDirection);
		}
	}

	private void dodge(
		final LivingEntity target,
		final double distanceSquared,
		final double configuredPreferredRange
	) {
		this.skeleton.getNavigation().stop();
		this.approachRepathCooldown = 0;
		this.retreatRepathCooldown = 0;
		double preferredRange = validPreferredRange(configuredPreferredRange);
		float backwards = distanceSquared < preferredRange * preferredRange * 0.64 ? -0.35F : 0.0F;
		this.skeleton.getMoveControl().strafe(backwards, this.dodgeDirection);
		this.skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
		this.dodgeTicks--;
	}

	private void tickBow(final LivingEntity target, final boolean hasLineOfSight) {
		if (this.skeleton.isUsingItem()) {
			if (!hasLineOfSight && this.seeTime < -LOST_SIGHT_CANCEL_TICKS) {
				this.skeleton.stopUsingItem();
				this.attackTime = Math.max(this.attackTime, 5);
				return;
			}

			int usingTicks = this.skeleton.getTicksUsingItem();
			if (hasLineOfSight && usingTicks >= BOW_DRAW_TICKS) {
				this.skeleton.stopUsingItem();
				this.skeleton.performRangedAttack(target, BowItem.getPowerForTime(usingTicks));
				SmartSkeletonMetrics.shot();
				this.attackTime = nextAttackInterval();
			}
			return;
		}

		this.attackTime--;
		if (this.attackTime <= 0 && this.seeTime >= 3) {
			this.skeleton.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.skeleton, Items.BOW));
		}
	}

	private void updateSightMemory(final boolean hasLineOfSight) {
		boolean previouslyVisible = this.seeTime > 0;
		if (hasLineOfSight != previouslyVisible) {
			this.seeTime = 0;
		}
		this.seeTime += hasLineOfSight ? 1 : -1;
	}

	private void updateProjectileThreat(final MobsThinkNowConfig config) {
		if (!config.skeletonProjectileDodging) {
			this.dodgeTicks = 0;
			return;
		}
		if (this.dodgeTicks > 0 || this.projectileScanCooldown-- > 0) {
			return;
		}

		this.projectileScanCooldown = PROJECTILE_SCAN_INTERVAL_TICKS - 1;
		if (SkeletonProjectileEvasion.nearestIncomingArrow(this.skeleton).isEmpty()) {
			return;
		}

		this.dodgeDirection = randomDirection();
		this.dodgeTicks = MINIMUM_DODGE_TICKS
			+ this.skeleton.getRandom().nextInt(MAXIMUM_DODGE_TICKS - MINIMUM_DODGE_TICKS + 1);
		SmartSkeletonMetrics.projectileDodgeStarted();
	}

	private void transitionTo(final MovementMode selected) {
		if (selected == this.movementMode) {
			return;
		}
		this.movementMode = selected;
		if (selected == MovementMode.RETREAT) {
			SmartSkeletonMetrics.retreatStarted();
		}
	}

	private int nextAttackInterval() {
		int vanillaInterval = this.skeleton.level().getDifficulty() == Difficulty.HARD ? 20 : 40;
		return Math.max(12, vanillaInterval + this.skeleton.getRandom().nextInt(9) - 4);
	}

	private int nextStrafeWindow() {
		return 16 + this.skeleton.getRandom().nextInt(21);
	}

	private int randomDirection() {
		return this.skeleton.getRandom().nextBoolean() ? 1 : -1;
	}

	private boolean smartAiEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.skeletonAiEnabled;
	}

	private static double validPreferredRange(final double configured) {
		return Double.isFinite(configured) && configured > 0.0
			? configured
			: SkeletonCombatMath.DEFAULT_PREFERRED_RANGE;
	}
}

package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.MovementMode;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCoverPlanner.CoverPlan;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 普通骷髅的远程战斗状态机。
 *
 * <p>关闭总开关或骷髅开关时，整个生命周期直接委托给原版
 * {@link RangedBowAttackGoal}；开启时则在同一 Goal 内完成接敌、持续侧移、持弓拉扯、
 * 来箭闪避、掩体探头和拉弓射击。这样配置热切换不会让一只骷髅同时运行两个 MOVE/LOOK Goal。</p>
 */
public final class SmartSkeletonBowAttackGoal extends RangedBowAttackGoal<AbstractSkeleton> {
	private static final int BOW_DRAW_TICKS = 20;
	private static final int LOST_SIGHT_CANCEL_TICKS = 15;
	private static final int PROJECTILE_SCAN_INTERVAL_TICKS = 3;
	private static final int MINIMUM_DODGE_TICKS = 7;
	private static final int MAXIMUM_DODGE_TICKS = 10;
	private static final double COVER_PATH_SPEED_MODIFIER = 1.10;
	private static final double COVER_RETURN_SPEED_MODIFIER = 1.15;
	private static final double COVER_POSITION_REACHED_DISTANCE_SQUARED = 0.49;
	private static final int COVER_TRAVEL_TIMEOUT_TICKS = 60;
	private static final int PEEK_TRAVEL_TIMEOUT_TICKS = 24;
	private static final int PEEK_STABILIZE_TICKS = 2;
	private static final int PEEK_TIMEOUT_TICKS = 12;
	private static final int POST_SHOT_FACING_TICKS = 2;
	private static final int RETURN_TO_COVER_TIMEOUT_TICKS = 30;
	private static final int MAXIMUM_COVER_PLAN_TICKS = 260;

	private final AbstractSkeleton skeleton;
	private final double speedModifier;
	private boolean smartMode;
	private int attackTime;
	private int seeTime;
	private int approachRepathCooldown;
	private int projectileScanCooldown;
	private int dodgeTicks;
	private int dodgeDirection = 1;
	private int strafeDirection = 1;
	private int strafeSwitchTicks;
	private MovementMode movementMode = MovementMode.APPROACH;
	private CoverPhase coverPhase = CoverPhase.INACTIVE;
	private @Nullable CoverPlan coverPlan;
	private int coverSearchCooldown;
	private int coverPhaseTicks;
	private int coverPlanTicks;
	private int coverShotsRemaining;
	private int coverVisibleTicks;

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
		this.projectileScanCooldown = 0;
		this.dodgeTicks = 0;
		this.dodgeDirection = randomDirection();
		this.strafeDirection = randomDirection();
		this.strafeSwitchTicks = nextStrafeWindow();
		this.movementMode = MovementMode.APPROACH;
		// 同 tick 生成的一批骷髅按实体 ID 分摊到三拍内首搜，避免集群同时做 96 格几何检查。
		this.coverSearchCooldown = Math.floorMod(this.skeleton.getId(), 3);
		this.clearCoverState(false);
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
		this.clearCoverState(false);
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
		if (SkeletonSquadOrders.obeyPreparationOrder(this.skeleton, target, this.speedModifier)) {
			this.attackTime = Math.max(this.attackTime, 5);
			this.clearCoverState(false);
			return;
		}
		int intelligence = SkeletonIntelligence.get(this.skeleton);
		double preferredRange = SkeletonCombatMath.intelligenceAdjustedPreferredRange(
			config.skeletonPreferredRange,
			intelligence
		);
		boolean hasLineOfSight = this.skeleton.getSensing().hasLineOfSight(target);
		updateSightMemory(hasLineOfSight);
		updateProjectileThreat(config);

		double distanceSquared = this.skeleton.distanceToSqr(target);
		MovementMode selected = SkeletonCombatMath.chooseMovement(
			distanceSquared,
			hasLineOfSight,
			preferredRange,
			this.dodgeTicks > 0
		);
		this.skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (this.coverPhase != CoverPhase.INACTIVE
			&& (!config.skeletonCoverPeeking
				|| selected == MovementMode.DODGE
				|| selected == MovementMode.KITE)) {
			// 闪箭和近身拉扯永远高于掩体循环；取消蓄力后立即交回普通移动状态机。
			this.clearCoverState(true);
		}

		if (this.coverPhase == CoverPhase.INACTIVE
			&& config.skeletonCoverPeeking
			&& selected != MovementMode.DODGE
			&& selected != MovementMode.KITE
			&& SkeletonCoverPlanner.isUsefulRange(distanceSquared, preferredRange)
			&& !this.skeleton.isUsingItem()) {
			this.tryStartCoverPlan(target, preferredRange);
		}

		if (this.coverPhase != CoverPhase.INACTIVE
			&& this.tickCoverPlan(target, hasLineOfSight, preferredRange)) {
			return;
		}

		transitionTo(selected);
		switch (selected) {
			case APPROACH -> approach(target);
			case STRAFE -> strafe(target, distanceSquared, preferredRange);
			case KITE -> kite(target, intelligence);
			case DODGE -> dodge(target, distanceSquared, preferredRange);
		}

		tickBow(target, hasLineOfSight);
	}

	/** 当前状态仅用于 GameTest、诊断 UI 和后续表现层，不允许外部反向驱动 Goal。 */
	public MovementMode movementMode() {
		return this.movementMode;
	}

	/** 掩体子状态只读暴露给 GameTest 与诊断界面；实际切换仍完全由 Goal 驱动。 */
	public CoverPhase coverPhase() {
		return this.coverPhase;
	}

	public @Nullable CoverPlan coverPlan() {
		return this.coverPlan;
	}

	private void approach(final LivingEntity target) {
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
		this.faceCombatTarget(target);
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

	/**
	 * 拉扯仍属于弓战：正面锁定目标、保持拉弓并用后退/横移输入拉开距离。
	 * 真正放下弓转身奔跑只由高优先级 {@link SkeletonEmergencyDisengageGoal} 执行。
	 */
	private void kite(final LivingEntity target, final int intelligence) {
		this.skeleton.getNavigation().stop();
		this.approachRepathCooldown = 0;
		this.faceCombatTarget(target);
		this.skeleton.getMoveControl().strafe(
			-SkeletonCombatMath.kiteBackwardInput(intelligence),
			SkeletonCombatMath.kiteSidewaysInput(intelligence) * this.strafeDirection
		);
	}

	private void dodge(
		final LivingEntity target,
		final double distanceSquared,
		final double configuredPreferredRange
	) {
		this.skeleton.getNavigation().stop();
		this.approachRepathCooldown = 0;
		this.faceCombatTarget(target);
		double preferredRange = validPreferredRange(configuredPreferredRange);
		float backwards = distanceSquared < preferredRange * preferredRange * 0.64 ? -0.35F : 0.0F;
		this.skeleton.getMoveControl().strafe(backwards, this.dodgeDirection);
		this.dodgeTicks--;
	}

	private void tickBow(final LivingEntity target, final boolean hasLineOfSight) {
		if (this.skeleton.isUsingItem()) {
			if (hasLineOfSight) {
				this.faceCombatTarget(target);
			}
			if (!hasLineOfSight && this.seeTime < -LOST_SIGHT_CANCEL_TICKS) {
				this.skeleton.stopUsingItem();
				this.attackTime = Math.max(this.attackTime, 5);
				return;
			}

			int usingTicks = this.skeleton.getTicksUsingItem();
			if (hasLineOfSight && usingTicks >= BOW_DRAW_TICKS) {
				this.fireArrow(target, usingTicks, false);
			}
			return;
		}

		this.attackTime--;
		if (this.attackTime <= 0 && this.seeTime >= 3) {
			this.skeleton.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.skeleton, Items.BOW));
		}
	}

	private void tryStartCoverPlan(final LivingEntity target, final double preferredRange) {
		if (this.coverSearchCooldown-- > 0) {
			return;
		}
		this.coverSearchCooldown = 40 + this.skeleton.getRandom().nextInt(21);

		List<CoverPlan> plans = SkeletonCoverPlanner.findPlans(this.skeleton, target, preferredRange);
		for (CoverPlan plan : plans) {
			if (isAt(plan.hide()) || this.moveTo(plan.hide(), COVER_PATH_SPEED_MODIFIER)) {
				this.coverPlan = plan;
				this.coverPhase = CoverPhase.MOVING_TO_COVER;
				this.coverPhaseTicks = 0;
				this.coverPlanTicks = 0;
				this.coverVisibleTicks = 0;
				this.coverShotsRemaining = nextCoverBurstSize();
				this.skeleton.stopUsingItem();
				SmartSkeletonMetrics.coverPlanStarted();
				return;
			}
		}
	}

	/**
	 * @return true 表示本 tick 仍由掩体状态机占用移动和弓箭；false 表示计划已经结束，调用者应
	 * 立即回落到普通距离分带逻辑。
	 */
	private boolean tickCoverPlan(
		final LivingEntity target,
		final boolean hasLineOfSight,
		final double preferredRange
	) {
		CoverPlan plan = this.coverPlan;
		if (plan == null || ++this.coverPlanTicks > MAXIMUM_COVER_PLAN_TICKS) {
			this.clearCoverState(true);
			return false;
		}

		this.coverPhaseTicks++;
		return switch (this.coverPhase) {
			case MOVING_TO_COVER -> this.tickMovingToCover(target, plan, preferredRange);
			case DRAWING_IN_COVER -> this.tickDrawingInCover(target, plan, hasLineOfSight);
			case MOVING_TO_PEEK -> this.tickMovingToPeek(target, plan);
			case PEEKING -> this.tickPeeking(target, plan, hasLineOfSight);
			case POST_SHOT_FACING -> this.tickPostShotFacing(target, plan);
			case RETURNING_TO_COVER -> this.tickReturningToCover(target, plan, preferredRange);
			case INACTIVE -> false;
		};
	}

	private boolean tickMovingToCover(
		final LivingEntity target,
		final CoverPlan plan,
		final double preferredRange
	) {
		if (isAt(plan.hide())) {
			this.skeleton.getNavigation().stop();
			if (!isPlanGeometryValid(target, plan, preferredRange)) {
				this.clearCoverState(true);
				return false;
			}
			this.enterCoverPhase(CoverPhase.DRAWING_IN_COVER);
			return true;
		}

		if (this.coverPhaseTicks > COVER_TRAVEL_TIMEOUT_TICKS
			|| this.skeleton.getNavigation().isDone()) {
			this.clearCoverState(true);
			return false;
		}
		return true;
	}

	private boolean tickDrawingInCover(
		final LivingEntity target,
		final CoverPlan plan,
		final boolean hasLineOfSight
	) {
		this.skeleton.getNavigation().stop();
		this.faceCombatTarget(target);
		this.coverVisibleTicks = hasLineOfSight ? this.coverVisibleTicks + 1 : 0;
		if (this.coverVisibleTicks >= 3) {
			// 目标已经绕过墙角，连续三 tick 暴露说明这个藏身格失效，不继续原地蓄力。
			this.clearCoverState(true);
			return false;
		}

		if (this.skeleton.isUsingItem()) {
			if (this.skeleton.getTicksUsingItem() >= BOW_DRAW_TICKS) {
				if (!SkeletonCoverPlanner.hasClearShotFrom(this.skeleton, target, plan.peek())
					|| !this.beginMoveToPeek()) {
					this.clearCoverState(true);
					return false;
				}
			}
			return true;
		}

		this.attackTime--;
		if (this.attackTime <= 0) {
			this.skeleton.startUsingItem(ProjectileUtil.getWeaponHoldingHand(this.skeleton, Items.BOW));
		}
		return true;
	}

	private boolean tickMovingToPeek(final LivingEntity target, final CoverPlan plan) {
		if (!this.skeleton.isUsingItem()) {
			this.clearCoverState(false);
			return false;
		}
		if (isAt(plan.peek())) {
			this.skeleton.getNavigation().stop();
			this.enterCoverPhase(CoverPhase.PEEKING);
			return true;
		}
		if (this.coverPhaseTicks > PEEK_TRAVEL_TIMEOUT_TICKS) {
			this.coverShotsRemaining = 0;
			return this.beginReturnToCover(plan);
		}

		// 藏身格与探头格必定相邻；这里不让导航 MoveControl 把身体扭向侧移方向，
		// 而是按目标朝向换算本地前/侧输入，让弓、头和身体始终与箭的方向一致。
		this.faceCombatTarget(target);
		this.strafeToward(plan.peek());
		return true;
	}

	private boolean tickPeeking(
		final LivingEntity target,
		final CoverPlan plan,
		final boolean hasLineOfSight
	) {
		this.skeleton.getNavigation().stop();
		this.faceCombatTarget(target);
		if (!this.skeleton.isUsingItem()) {
			this.clearCoverState(false);
			return false;
		}

		this.coverVisibleTicks = hasLineOfSight ? this.coverVisibleTicks + 1 : 0;
		if (this.coverVisibleTicks >= PEEK_STABILIZE_TICKS
			&& this.skeleton.getTicksUsingItem() >= BOW_DRAW_TICKS) {
			this.fireArrow(target, this.skeleton.getTicksUsingItem(), true);
			this.coverShotsRemaining--;
			// 保留两个 tick 的正面射后姿态，再转身缩回；避免同一服务端 tick 内导航先把模型扭向掩体，
			// 造成玩家看到“箭从背后反向飞出”。
			this.enterCoverPhase(CoverPhase.POST_SHOT_FACING);
			return true;
		}

		if (this.coverPhaseTicks > PEEK_TIMEOUT_TICKS) {
			// 墙角仍无视线时不傻站暴露；放弃这一箭并先缩回掩体。
			this.coverShotsRemaining = 0;
			return this.beginReturnToCover(plan);
		}
		return true;
	}

	private boolean tickPostShotFacing(final LivingEntity target, final CoverPlan plan) {
		this.skeleton.getNavigation().stop();
		this.faceCombatTarget(target);
		if (this.coverPhaseTicks >= POST_SHOT_FACING_TICKS) {
			return this.beginReturnToCover(plan);
		}
		return true;
	}

	private boolean tickReturningToCover(
		final LivingEntity target,
		final CoverPlan plan,
		final double preferredRange
	) {
		if (isAt(plan.hide())) {
			this.skeleton.getNavigation().stop();
			if (this.coverShotsRemaining > 0 && isPlanGeometryValid(target, plan, preferredRange)) {
				this.enterCoverPhase(CoverPhase.DRAWING_IN_COVER);
				return true;
			}
			this.finishCoverPlan();
			return false;
		}
		if (this.coverPhaseTicks > RETURN_TO_COVER_TIMEOUT_TICKS
			|| this.skeleton.getNavigation().isDone()) {
			this.clearCoverState(true);
			return false;
		}
		return true;
	}

	private boolean beginMoveToPeek() {
		this.enterCoverPhase(CoverPhase.MOVING_TO_PEEK);
		this.skeleton.getNavigation().stop();
		return true;
	}

	private boolean beginReturnToCover(final CoverPlan plan) {
		this.skeleton.stopUsingItem();
		this.enterCoverPhase(CoverPhase.RETURNING_TO_COVER);
		if (isAt(plan.hide())) {
			return true;
		}
		if (this.moveTo(plan.hide(), COVER_RETURN_SPEED_MODIFIER)) {
			return true;
		}
		this.clearCoverState(false);
		return false;
	}

	private boolean isPlanGeometryValid(
		final LivingEntity target,
		final CoverPlan plan,
		final double preferredRange
	) {
		return SkeletonCoverPlanner.isStandable(this.skeleton, plan.hide())
			&& SkeletonCoverPlanner.isStandable(this.skeleton, plan.peek())
			&& SkeletonCoverPlanner.isUsefulRange(
				Vec3.atBottomCenterOf(plan.peek()).distanceToSqr(target.position()),
				preferredRange
			)
			&& SkeletonCoverPlanner.isHiddenFromTarget(this.skeleton, target, plan.hide())
			&& SkeletonCoverPlanner.hasClearShotFrom(this.skeleton, target, plan.peek());
	}

	private boolean moveTo(final BlockPos destination, final double speed) {
		Path path = this.skeleton.getNavigation().createPath(destination, 0);
		return path != null && path.canReach() && this.skeleton.getNavigation().moveTo(path, speed);
	}

	private boolean isAt(final BlockPos position) {
		return this.skeleton.position().distanceToSqr(Vec3.atBottomCenterOf(position))
			<= COVER_POSITION_REACHED_DISTANCE_SQUARED;
	}

	/**
	 * 原版弓箭 Goal 在侧移时调用 Mob.lookAt，而不只给 LookControl 下命令。后者会被正在
	 * 行走的 BodyRotationControl 限制在身体左右 75 度内，正是“模型背身、箭却反向飞出”的根因。
	 */
	private void faceCombatTarget(final LivingEntity target) {
		this.skeleton.lookAt(target, 360.0F, 90.0F);
		float yaw = this.skeleton.getYRot();
		this.skeleton.setYBodyRot(yaw);
		this.skeleton.setYHeadRot(yaw);
		this.skeleton.getLookControl().setLookAt(target, 90.0F, 90.0F);
	}

	/** 把世界坐标方向转换成“身体仍面向目标”时的前后/左右输入。 */
	private void strafeToward(final BlockPos destination) {
		Vec3 offset = Vec3.atBottomCenterOf(destination).subtract(this.skeleton.position());
		SkeletonCombatMath.StrafeInput input = SkeletonCombatMath.targetFacingStrafeInput(
			this.skeleton.getYRot(),
			offset.x,
			offset.z
		);
		if (input.forward() == 0.0F && input.sideways() == 0.0F) {
			return;
		}
		this.skeleton.getMoveControl().strafe(input.forward(), input.sideways());
	}

	private void fireArrow(final LivingEntity target, final int usingTicks, final boolean fromCover) {
		this.faceCombatTarget(target);
		this.skeleton.stopUsingItem();
		this.skeleton.performRangedAttack(target, BowItem.getPowerForTime(usingTicks));
		SmartSkeletonMetrics.shot();
		if (fromCover) {
			SmartSkeletonMetrics.coverShot();
		}
		this.attackTime = nextAttackInterval();
	}

	private void enterCoverPhase(final CoverPhase phase) {
		this.coverPhase = phase;
		this.coverPhaseTicks = 0;
		this.coverVisibleTicks = 0;
	}

	private void finishCoverPlan() {
		this.clearCoverState(false);
		this.coverSearchCooldown = 60 + this.skeleton.getRandom().nextInt(41);
	}

	private void clearCoverState(final boolean stopUsingItem) {
		if (stopUsingItem) {
			this.skeleton.stopUsingItem();
		}
		if (this.coverPhase != CoverPhase.INACTIVE) {
			this.skeleton.getNavigation().stop();
			this.coverSearchCooldown = Math.max(
				this.coverSearchCooldown,
				40 + this.skeleton.getRandom().nextInt(21)
			);
		}
		this.coverPhase = CoverPhase.INACTIVE;
		this.coverPlan = null;
		this.coverPhaseTicks = 0;
		this.coverPlanTicks = 0;
		this.coverShotsRemaining = 0;
		this.coverVisibleTicks = 0;
	}

	private int nextCoverBurstSize() {
		return switch (this.skeleton.level().getDifficulty()) {
			case HARD -> 2 + this.skeleton.getRandom().nextInt(2);
			case NORMAL -> 1 + this.skeleton.getRandom().nextInt(2);
			default -> 1;
		};
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
		if (selected == MovementMode.KITE) {
			SmartSkeletonMetrics.kiteStarted();
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

	public enum CoverPhase {
		INACTIVE,
		MOVING_TO_COVER,
		DRAWING_IN_COVER,
		MOVING_TO_PEEK,
		PEEKING,
		POST_SHOT_FACING,
		RETURNING_TO_COVER
	}
}

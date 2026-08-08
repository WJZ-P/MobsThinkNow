package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner;
import com.wjz.mobsthinknow.shared.ai.RangedSpacingPlanner;

/**
 * 骷髅战斗状态机使用的无世界依赖数学函数。
 *
 * <p>把距离分带、来箭交会与移动目标提前量集中在这里，既方便单元测试，也避免 Goal
 * 把“感知世界”和“如何计算”揉成一段难以验证的 tick 代码。</p>
 */
public final class SkeletonCombatMath {
	public static final double DEFAULT_PREFERRED_RANGE = RangedSpacingPlanner.DEFAULT_PREFERRED_RANGE;
	public static final double PROJECTILE_SPEED = 1.6;
	public static final double MAXIMUM_LEAD_TICKS = 8.0;
	public static final double MAXIMUM_LEAD_DISTANCE = 3.0;
	/** 兼容旧测试与未提供智力的调用：进入偏好射程的 60% 时强制脱离。 */
	public static final double EMERGENCY_DISENGAGE_TRIGGER_RATIO = RangedSpacingPlanner.EMERGENCY_TRIGGER_RATIO;
	/** 接管后必须拉到偏好射程的 90% 才释放，避免在临界点逐 tick 抖动。 */
	public static final double EMERGENCY_DISENGAGE_SAFE_RATIO = RangedSpacingPlanner.EMERGENCY_SAFE_RATIO;

	private SkeletonCombatMath() {
	}

	public static MovementMode chooseMovement(
		final double distanceSquared,
		final boolean hasLineOfSight,
		final double preferredRange,
		final boolean dodging
	) {
		return fromShared(RangedSpacingPlanner.chooseMovement(
			distanceSquared,
			hasLineOfSight,
			preferredRange,
			dodging
		));
	}

	/**
	 * 智力越高，越早识别近身风险并主动保持更远的射击距离；低智力仍保留基础拉扯，
	 * 避免数值差异退化成“会拉扯/完全不会拉扯”的二元开关。
	 */
	public static double intelligenceAdjustedPreferredRange(final double preferredRange, final int intelligence) {
		return RangedSpacingPlanner.intelligenceAdjustedPreferredRange(preferredRange, intelligence);
	}

	public static MovementMode chooseMovement(
		final double distanceSquared,
		final boolean hasLineOfSight,
		final double preferredRange,
		final boolean dodging,
		final int intelligence
	) {
		return chooseMovement(
			distanceSquared,
			hasLineOfSight,
			intelligenceAdjustedPreferredRange(preferredRange, intelligence),
			dodging
		);
	}

	public static double emergencyDisengageTriggerRange(final double preferredRange) {
		return RangedSpacingPlanner.emergencyTriggerRange(preferredRange);
	}

	public static double emergencyDisengageSafeRange(final double preferredRange) {
		return RangedSpacingPlanner.emergencySafeRange(preferredRange);
	}

	public static double emergencyDisengageTriggerRange(final double preferredRange, final int intelligence) {
		return RangedSpacingPlanner.emergencyTriggerRange(preferredRange, intelligence);
	}

	public static double emergencyDisengageSafeRange(final double preferredRange, final int intelligence) {
		return RangedSpacingPlanner.emergencySafeRange(preferredRange, intelligence);
	}

	/**
	 * 启动阈值与结束阈值故意分离：进入六格触发后要真正拉到九格才结束，形成迟滞区，
	 * 防止玩家和骷髅在阈值边缘移动时两个 Goal 反复抢占 MOVE/LOOK。
	 */
	public static boolean shouldStartEmergencyDisengage(
		final double horizontalDistanceSquared,
		final double preferredRange
	) {
		double triggerRange = emergencyDisengageTriggerRange(preferredRange);
		return isValidSquaredDistance(horizontalDistanceSquared)
			&& horizontalDistanceSquared < triggerRange * triggerRange;
	}

	public static boolean shouldContinueEmergencyDisengage(
		final double horizontalDistanceSquared,
		final double preferredRange
	) {
		double safeRange = emergencyDisengageSafeRange(preferredRange);
		return isValidSquaredDistance(horizontalDistanceSquared)
			&& horizontalDistanceSquared < safeRange * safeRange;
	}

	public static boolean shouldStartEmergencyDisengage(
		final double horizontalDistanceSquared,
		final double preferredRange,
		final int intelligence
	) {
		return RangedSpacingPlanner.shouldStartEmergencyDisengage(
			horizontalDistanceSquared,
			preferredRange,
			intelligence
		);
	}

	public static boolean shouldContinueEmergencyDisengage(
		final double horizontalDistanceSquared,
		final double preferredRange,
		final int intelligence
	) {
		return RangedSpacingPlanner.shouldContinueEmergencyDisengage(
			horizontalDistanceSquared,
			preferredRange,
			intelligence
		);
	}

	/** 全力逃跑寻路速度随智力从约 1.29 提升到 1.60。 */
	public static double disengagePathSpeed(final int intelligence) {
		return RangedSpacingPlanner.maximumEscapePathSpeed(intelligence);
	}

	/** 面向目标拉弓后退的输入强度；它不是转身逃跑速度。 */
	public static float kiteBackwardInput(final int intelligence) {
		return RangedSpacingPlanner.kiteBackwardInput(intelligence);
	}

	public static float kiteSidewaysInput(final int intelligence) {
		return RangedSpacingPlanner.kiteSidewaysInput(intelligence);
	}

	public static int disengagePathRefreshTicks(final int intelligence) {
		return RangedSpacingPlanner.pathRefreshTicks(intelligence);
	}

	/**
	 * 以匀速近似计算投射物到目标中心的最近交会时间。返回正无穷表示轨迹无效、已经远离，
	 * 或最近点落在观察时间窗之外。
	 */
	public static double closestApproachTime(
		final double relativeX,
		final double relativeY,
		final double relativeZ,
		final double velocityX,
		final double velocityY,
		final double velocityZ,
		final double horizonTicks
	) {
		return ProjectileEvasionPlanner.closestApproachTicks(
			relativeX,
			relativeY,
			relativeZ,
			velocityX,
			velocityY,
			velocityZ,
			horizonTicks
		);
	}

	public static boolean isIncomingProjectile(
		final double relativeX,
		final double relativeY,
		final double relativeZ,
		final double velocityX,
		final double velocityY,
		final double velocityZ,
		final double horizonTicks,
		final double safetyRadius
	) {
		return ProjectileEvasionPlanner.isIncoming(
			relativeX,
			relativeY,
			relativeZ,
			velocityX,
			velocityY,
			velocityZ,
			horizonTicks,
			safetyRadius
		);
	}

	/**
	 * 根据来箭在最近交会时刻的水平落点选择更安全的侧闪方向，而不是随机赌左或右。
	 * 返回值与 {@code MoveControl.strafe} 的第二个参数同号：{@code 1} 为身体右侧，
	 * {@code -1} 为身体左侧。两侧收益相同时保留调用方提供的随机方向，让正中来箭仍有变化。
	 */
	static int saferProjectileDodgeDirection(
		final double skeletonX,
		final double skeletonZ,
		final double projectileX,
		final double projectileZ,
		final double velocityX,
		final double velocityZ,
		final double closestApproachTicks,
		final float combatYaw,
		final int fallbackDirection
	) {
		return ProjectileEvasionPlanner.saferSide(
			skeletonX,
			skeletonZ,
			projectileX,
			projectileZ,
			velocityX,
			velocityZ,
			closestApproachTicks,
			combatYaw,
			fallbackDirection
		);
	}

	/**
	 * 只预测水平位移，竖直方向仍交给原版弓箭抛物线补偿。提前量同时受时间和三格距离上限
	 * 约束，避免高速目标让骷髅向画面外夸张甩弓。
	 */
	public static HorizontalLead horizontalLead(
		final double targetVelocityX,
		final double targetVelocityZ,
		final double horizontalDistance,
		final double predictionStrength
	) {
		if (!Double.isFinite(targetVelocityX)
			|| !Double.isFinite(targetVelocityZ)
			|| !Double.isFinite(horizontalDistance)
			|| horizontalDistance <= 0.0
			|| !Double.isFinite(predictionStrength)
			|| predictionStrength <= 0.0) {
			return HorizontalLead.ZERO;
		}

		double time = Math.min(MAXIMUM_LEAD_TICKS, horizontalDistance / PROJECTILE_SPEED);
		double strength = Math.clamp(predictionStrength, 0.0, 1.0);
		double x = targetVelocityX * time * strength;
		double z = targetVelocityZ * time * strength;
		double lengthSquared = x * x + z * z;
		if (lengthSquared > MAXIMUM_LEAD_DISTANCE * MAXIMUM_LEAD_DISTANCE) {
			double scale = MAXIMUM_LEAD_DISTANCE / Math.sqrt(lengthSquared);
			x *= scale;
			z *= scale;
		}
		return new HorizontalLead(x, z);
	}

	public static double difficultyPredictionFactor(final int difficultyId) {
		return switch (Math.clamp(difficultyId, 0, 3)) {
			case 0 -> 0.0;
			case 1 -> 0.65;
			case 2 -> 0.82;
			default -> 1.0;
		};
	}

	/**
	 * 把一个水平世界方向换算为指定身体 yaw 下的前后/左右输入。用于骷髅保持瞄准时侧移到
	 * 相邻探头格，避免把导航朝向和射击朝向重新耦合起来。
	 */
	static StrafeInput targetFacingStrafeInput(
		final float bodyYaw,
		final double worldDirectionX,
		final double worldDirectionZ
	) {
		double length = Math.sqrt(worldDirectionX * worldDirectionX + worldDirectionZ * worldDirectionZ);
		if (!Double.isFinite(length) || length < 1.0E-6 || !Float.isFinite(bodyYaw)) {
			return StrafeInput.ZERO;
		}

		double worldX = worldDirectionX / length;
		double worldZ = worldDirectionZ / length;
		double yaw = bodyYaw * Math.PI / 180.0;
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		double forward = -worldX * sin + worldZ * cos;
		double sideways = worldX * cos + worldZ * sin;
		return new StrafeInput(cleanUnitComponent(forward), cleanUnitComponent(sideways));
	}

	private static float cleanUnitComponent(final double value) {
		return Math.abs(value) < 1.0E-6 ? 0.0F : (float)value;
	}

	private static MovementMode fromShared(final RangedSpacingPlanner.MovementMode mode) {
		return switch (mode) {
			case APPROACH -> MovementMode.APPROACH;
			case STRAFE -> MovementMode.STRAFE;
			case KITE -> MovementMode.KITE;
			case DODGE -> MovementMode.DODGE;
		};
	}

	private static boolean isValidSquaredDistance(final double distanceSquared) {
		return Double.isFinite(distanceSquared) && distanceSquared >= 0.0;
	}

	public enum MovementMode {
		APPROACH,
		STRAFE,
		/** 持弓面对目标后退/横移；不等同于放下弓、转身正向奔跑的独立逃生 Goal。 */
		KITE,
		DODGE
	}

	public record HorizontalLead(double x, double z) {
		public static final HorizontalLead ZERO = new HorizontalLead(0.0, 0.0);
	}

	record StrafeInput(float forward, float sideways) {
		static final StrafeInput ZERO = new StrafeInput(0.0F, 0.0F);
	}
}

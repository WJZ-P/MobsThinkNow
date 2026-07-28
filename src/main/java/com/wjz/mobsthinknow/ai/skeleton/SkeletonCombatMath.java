package com.wjz.mobsthinknow.ai.skeleton;

/**
 * 骷髅战斗状态机使用的无世界依赖数学函数。
 *
 * <p>把距离分带、来箭交会与移动目标提前量集中在这里，既方便单元测试，也避免 Goal
 * 把“感知世界”和“如何计算”揉成一段难以验证的 tick 代码。</p>
 */
public final class SkeletonCombatMath {
	public static final double DEFAULT_PREFERRED_RANGE = 10.0;
	public static final double PROJECTILE_SPEED = 1.6;
	public static final double MAXIMUM_LEAD_TICKS = 8.0;
	public static final double MAXIMUM_LEAD_DISTANCE = 3.0;
	/** 玩家进入偏好射程的 60% 时，由高优先级 Goal 强制接管移动。 */
	public static final double EMERGENCY_DISENGAGE_TRIGGER_RATIO = 0.60;
	/** 接管后必须拉到偏好射程的 90% 才释放，避免在临界点逐 tick 抖动。 */
	public static final double EMERGENCY_DISENGAGE_SAFE_RATIO = 0.90;

	private SkeletonCombatMath() {
	}

	public static MovementMode chooseMovement(
		final double distanceSquared,
		final boolean hasLineOfSight,
		final double preferredRange,
		final boolean dodging
	) {
		if (dodging) {
			return MovementMode.DODGE;
		}

		double preferred = validPreferredRange(preferredRange);
		double retreatRange = preferred * 0.60;
		if (Double.isFinite(distanceSquared) && distanceSquared < retreatRange * retreatRange) {
			return MovementMode.RETREAT;
		}

		double pursuitRange = preferred * 1.35;
		if (!hasLineOfSight || !Double.isFinite(distanceSquared) || distanceSquared > pursuitRange * pursuitRange) {
			return MovementMode.APPROACH;
		}
		return MovementMode.STRAFE;
	}

	public static double emergencyDisengageTriggerRange(final double preferredRange) {
		return validPreferredRange(preferredRange) * EMERGENCY_DISENGAGE_TRIGGER_RATIO;
	}

	public static double emergencyDisengageSafeRange(final double preferredRange) {
		return validPreferredRange(preferredRange) * EMERGENCY_DISENGAGE_SAFE_RATIO;
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
		double speedSquared = velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ;
		if (!Double.isFinite(speedSquared) || speedSquared < 1.0E-4 || !Double.isFinite(horizonTicks) || horizonTicks <= 0.0) {
			return Double.POSITIVE_INFINITY;
		}

		double time = (relativeX * velocityX + relativeY * velocityY + relativeZ * velocityZ) / speedSquared;
		if (!Double.isFinite(time) || time < 0.0 || time > horizonTicks) {
			return Double.POSITIVE_INFINITY;
		}
		return time;
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
		double time = closestApproachTime(
			relativeX,
			relativeY,
			relativeZ,
			velocityX,
			velocityY,
			velocityZ,
			horizonTicks
		);
		if (!Double.isFinite(time) || !Double.isFinite(safetyRadius) || safetyRadius <= 0.0) {
			return false;
		}

		double missX = relativeX - velocityX * time;
		double missY = relativeY - velocityY * time;
		double missZ = relativeZ - velocityZ * time;
		return missX * missX + missY * missY + missZ * missZ <= safetyRadius * safetyRadius;
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

	private static double validPreferredRange(final double preferredRange) {
		return Double.isFinite(preferredRange) && preferredRange > 0.0
			? preferredRange
			: DEFAULT_PREFERRED_RANGE;
	}

	private static boolean isValidSquaredDistance(final double distanceSquared) {
		return Double.isFinite(distanceSquared) && distanceSquared >= 0.0;
	}

	public enum MovementMode {
		APPROACH,
		STRAFE,
		RETREAT,
		DODGE
	}

	public record HorizontalLead(double x, double z) {
		public static final HorizontalLead ZERO = new HorizontalLead(0.0, 0.0);
	}
}

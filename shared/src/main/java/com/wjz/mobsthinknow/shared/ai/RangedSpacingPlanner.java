package com.wjz.mobsthinknow.shared.ai;

/**
 * 远程单位的跨平台距离分带、紧急脱离阈值和个体速度曲线。
 *
 * <p>本类只处理标量，不读取实体、世界或导航器。Fabric 与 Paper 因而可以共享同一套
 * “持弓拉扯”和“放下武器全力逃跑”边界，同时各自使用平台导航 API 执行动作。</p>
 */
public final class RangedSpacingPlanner {
	public static final double DEFAULT_PREFERRED_RANGE = 10.0;
	public static final double EMERGENCY_TRIGGER_RATIO = 0.60;
	public static final double EMERGENCY_SAFE_RATIO = 0.90;
	public static final double MINIMUM_ESCAPE_SPEED_FACTOR = 0.68;
	public static final double MAXIMUM_ESCAPE_SPEED_FACTOR = 1.0;

	private RangedSpacingPlanner() {
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
		double kiteRange = preferred * EMERGENCY_TRIGGER_RATIO;
		if (isValidSquaredDistance(distanceSquared) && distanceSquared < kiteRange * kiteRange) {
			return MovementMode.KITE;
		}
		double pursuitRange = preferred * 1.35;
		if (!hasLineOfSight || !isValidSquaredDistance(distanceSquared)
			|| distanceSquared > pursuitRange * pursuitRange) {
			return MovementMode.APPROACH;
		}
		return MovementMode.STRAFE;
	}

	public static double intelligenceAdjustedPreferredRange(
		final double preferredRange,
		final int intelligence
	) {
		return validPreferredRange(preferredRange) * lerp(0.85, 1.15, normalizedIntelligence(intelligence));
	}

	public static double emergencyTriggerRange(final double preferredRange) {
		return validPreferredRange(preferredRange) * EMERGENCY_TRIGGER_RATIO;
	}

	public static double emergencySafeRange(final double preferredRange) {
		return validPreferredRange(preferredRange) * EMERGENCY_SAFE_RATIO;
	}

	public static double emergencyTriggerRange(final double preferredRange, final int intelligence) {
		double ratio = lerp(0.48, 0.72, normalizedIntelligence(intelligence));
		return intelligenceAdjustedPreferredRange(preferredRange, intelligence) * ratio;
	}

	public static double emergencySafeRange(final double preferredRange, final int intelligence) {
		double ratio = lerp(0.78, 1.05, normalizedIntelligence(intelligence));
		return intelligenceAdjustedPreferredRange(preferredRange, intelligence) * ratio;
	}

	public static boolean shouldStartEmergencyDisengage(
		final double horizontalDistanceSquared,
		final double preferredRange,
		final int intelligence
	) {
		double trigger = emergencyTriggerRange(preferredRange, intelligence);
		return isValidSquaredDistance(horizontalDistanceSquared)
			&& horizontalDistanceSquared < trigger * trigger;
	}

	public static boolean shouldContinueEmergencyDisengage(
		final double horizontalDistanceSquared,
		final double preferredRange,
		final int intelligence
	) {
		double safe = emergencySafeRange(preferredRange, intelligence);
		return isValidSquaredDistance(horizontalDistanceSquared)
			&& horizontalDistanceSquared < safe * safe;
	}

	/** 绝对速度上限随智力从约 1.29 平滑上升到 1.60。 */
	public static double maximumEscapePathSpeed(final int intelligence) {
		return lerp(1.285, 1.60, normalizedIntelligence(intelligence));
	}

	/** 相同随机分位下，难度越高个体速度因子越大，但绝不超过旧版速度上限。 */
	public static double escapeSpeedFactor(final DifficultyTier difficulty, final double unitSample) {
		double minimum = switch (difficulty) {
			case PEACEFUL, EASY -> 0.68;
			case NORMAL -> 0.76;
			case HARD -> 0.84;
		};
		double sample = Double.isFinite(unitSample) ? Math.clamp(unitSample, 0.0, 1.0) : 0.0;
		return minimum + (MAXIMUM_ESCAPE_SPEED_FACTOR - minimum) * sample;
	}

	public static double escapePathSpeed(
		final int intelligence,
		final DifficultyTier difficulty,
		final double unitSample
	) {
		return maximumEscapePathSpeed(intelligence) * escapeSpeedFactor(difficulty, unitSample);
	}

	/** 面向敌人拉弓后退的输入强度；不用于紧急逃跑。 */
	public static float kiteBackwardInput(final int intelligence) {
		return (float)lerp(0.68, 1.0, normalizedIntelligence(intelligence));
	}

	public static float kiteSidewaysInput(final int intelligence) {
		return (float)lerp(0.32, 0.56, normalizedIntelligence(intelligence));
	}

	public static int pathRefreshTicks(final int intelligence) {
		return Math.max(3, 9 - IntelligenceDistribution.clamp(intelligence) / 2);
	}

	private static boolean isValidSquaredDistance(final double distanceSquared) {
		return Double.isFinite(distanceSquared) && distanceSquared >= 0.0;
	}

	private static double validPreferredRange(final double preferredRange) {
		return Double.isFinite(preferredRange) && preferredRange > 0.0
			? preferredRange
			: DEFAULT_PREFERRED_RANGE;
	}

	private static double normalizedIntelligence(final int intelligence) {
		return (IntelligenceDistribution.clamp(intelligence) - IntelligenceDistribution.MINIMUM)
			/ (double)(IntelligenceDistribution.MAXIMUM - IntelligenceDistribution.MINIMUM);
	}

	private static double lerp(final double start, final double end, final double amount) {
		return start + (end - start) * amount;
	}

	public enum MovementMode {
		APPROACH,
		STRAFE,
		KITE,
		DODGE
	}
}

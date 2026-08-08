package com.wjz.mobsthinknow.shared.ai;

import java.util.Objects;

/** Fabric 与 Paper 共用的自然生成弩手概率和烟花弹药数量分布。 */
public final class CrossbowLoadoutPlanner {
	public static final int FIREWORK_MINIMUM_INTELLIGENCE = 7;
	public static final int MINIMUM_ROCKETS = 3;
	public static final int MAXIMUM_ROCKETS = 9;

	private CrossbowLoadoutPlanner() {
	}

	public static double effectiveCrossbowChance(
		final double configuredBaseChance,
		final DifficultyTier difficulty,
		final int intelligence
	) {
		Objects.requireNonNull(difficulty, "difficulty");
		double baseChance = finiteChance(configuredBaseChance);
		double difficultyFactor = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 0.65;
			case NORMAL -> 1.0;
			case HARD -> 1.35;
		};
		double intelligenceFactor = 0.75 + IntelligenceDistribution.clamp(intelligence) * 0.05;
		return Math.clamp(baseChance * difficultyFactor * intelligenceFactor, 0.0, 1.0);
	}

	public static double effectiveFireworkChance(
		final double configuredBaseChance,
		final DifficultyTier difficulty,
		final int intelligence
	) {
		Objects.requireNonNull(difficulty, "difficulty");
		int clamped = IntelligenceDistribution.clamp(intelligence);
		if (clamped < FIREWORK_MINIMUM_INTELLIGENCE || difficulty == DifficultyTier.PEACEFUL) {
			return 0.0;
		}
		double mastery = (clamped - FIREWORK_MINIMUM_INTELLIGENCE + 1) / 4.0;
		double difficultyFactor = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 0.70;
			case NORMAL -> 1.0;
			case HARD -> 1.25;
		};
		return Math.clamp(finiteChance(configuredBaseChance) * mastery * difficultyFactor, 0.0, 1.0);
	}

	public static boolean succeeds(final double probability, final double unitSample) {
		double sample = boundedSample(unitSample);
		return sample < finiteChance(probability);
	}

	/** 与旧 Fabric 3～9 枚分布相同；随机源由平台在边界处提供。 */
	public static int rocketCount(
		final DifficultyTier difficulty,
		final int intelligence,
		final double unitSample
	) {
		Objects.requireNonNull(difficulty, "difficulty");
		int difficultyId = difficulty.ordinal();
		int baseline = 2 + difficultyId + IntelligenceDistribution.clamp(intelligence) / 3;
		int jitter = Math.min(2, (int)Math.floor(boundedSample(unitSample) * 3.0));
		return Math.clamp(baseline + jitter, MINIMUM_ROCKETS, MAXIMUM_ROCKETS);
	}

	private static double finiteChance(final double value) {
		return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0;
	}

	private static double boundedSample(final double value) {
		return Double.isFinite(value) ? Math.clamp(value, 0.0, Math.nextDown(1.0)) : 0.0;
	}
}

package com.wjz.mobsthinknow.shared.ai;

/** 智力 1～10 的跨平台出生分布；难度只影响首次生成，不在战斗中重新洗点。 */
public final class IntelligenceDistribution {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private IntelligenceDistribution() {
	}

	public static IntRange rangeFor(final DifficultyTier difficulty) {
		return switch (difficulty) {
			case PEACEFUL, EASY -> new IntRange(1, 7);
			case NORMAL -> new IntRange(2, 9);
			case HARD -> new IntRange(4, 10);
		};
	}

	/**
	 * 使用调用方提供的 [0,1) 样本生成智力，避免共享层依赖任一平台的随机数类型。
	 * 边界值会被夹紧，因此损坏配置或测试夹具也不会产生 1～10 之外的结果。
	 */
	public static int roll(final DifficultyTier difficulty, final double unitSample) {
		IntRange range = rangeFor(difficulty);
		double bounded = Double.isFinite(unitSample) ? Math.clamp(unitSample, 0.0, Math.nextDown(1.0)) : 0.0;
		int width = range.maximum - range.minimum + 1;
		return range.minimum + Math.min(width - 1, (int)Math.floor(bounded * width));
	}

	public static int clamp(final int intelligence) {
		return Math.clamp(intelligence, MINIMUM, MAXIMUM);
	}

	public record IntRange(int minimum, int maximum) {
		public IntRange {
			if (minimum < MINIMUM || maximum > MAXIMUM || minimum > maximum) {
				throw new IllegalArgumentException("Invalid intelligence range.");
			}
		}
	}
}

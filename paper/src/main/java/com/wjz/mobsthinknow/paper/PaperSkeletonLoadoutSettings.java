package com.wjz.mobsthinknow.paper;

/** 普通骷髅自然生成时的跨端一致职业概率。 */
public record PaperSkeletonLoadoutSettings(
	boolean enabled,
	double crossbowChance,
	double fireworkCrossbowChance
) {
	public static PaperSkeletonLoadoutSettings validated(
		final boolean enabled,
		final double crossbowChance,
		final double fireworkCrossbowChance
	) {
		return new PaperSkeletonLoadoutSettings(
			enabled,
			finiteChance(crossbowChance),
			finiteChance(fireworkCrossbowChance)
		);
	}

	private static double finiteChance(final double value) {
		return Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0;
	}
}

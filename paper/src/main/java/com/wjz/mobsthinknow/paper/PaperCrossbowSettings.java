package com.wjz.mobsthinknow.paper;

/** Paper 公开 API 弩手适配参数；单位分别为 tick、方块/tick 与弧度外的 Bukkit spread 值。 */
public record PaperCrossbowSettings(
	boolean enabled,
	int minimumIntelligence,
	int chargeTicks,
	int minimumAimTicks,
	int maximumAimTicks,
	double projectileSpeed,
	double projectileSpread,
	double maximumLeadTicks,
	double gravityPerTickSquared,
	PaperFireworkSettings firework,
	PaperSkeletonLoadoutSettings naturalLoadout
) {
	public static PaperCrossbowSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final int chargeTicks,
		final int minimumAimTicks,
		final int maximumAimTicks,
		final double projectileSpeed,
		final double projectileSpread,
		final double maximumLeadTicks,
		final double gravityPerTickSquared,
		final PaperFireworkSettings firework,
		final PaperSkeletonLoadoutSettings naturalLoadout
	) {
		int minimumAim = Math.clamp(minimumAimTicks, 1, 20);
		return new PaperCrossbowSettings(
			enabled,
			Math.clamp(minimumIntelligence, 1, 10),
			Math.clamp(chargeTicks, 12, 60),
			minimumAim,
			Math.clamp(maximumAimTicks, minimumAim, 40),
			finiteClamp(projectileSpeed, 0.5, 5.0),
			finiteClamp(projectileSpread, 0.0, 14.0),
			finiteClamp(maximumLeadTicks, 0.0, 40.0),
			finiteClamp(gravityPerTickSquared, 0.0, 0.20),
			java.util.Objects.requireNonNull(firework, "firework"),
			java.util.Objects.requireNonNull(naturalLoadout, "naturalLoadout")
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

package com.wjz.mobsthinknow.paper;

/** 烟花弩的有界弹体、安全半径和真实弹药策略。 */
public record PaperFireworkSettings(
	boolean enabled,
	int minimumIntelligence,
	double minimumRange,
	double maximumRange,
	double allyDangerRadius,
	int maximumAllyChecks,
	double projectileSpeed,
	int projectileLifetimeTicks,
	int maximumActiveProjectiles,
	boolean consumeAmmunition
) {
	public static PaperFireworkSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final double minimumRange,
		final double maximumRange,
		final double allyDangerRadius,
		final int maximumAllyChecks,
		final double projectileSpeed,
		final int projectileLifetimeTicks,
		final int maximumActiveProjectiles,
		final boolean consumeAmmunition
	) {
		double minimum = finiteClamp(minimumRange, 4.0, 24.0);
		return new PaperFireworkSettings(
			enabled,
			Math.clamp(minimumIntelligence, 1, 10),
			minimum,
			finiteClamp(maximumRange, minimum, 48.0),
			finiteClamp(allyDangerRadius, 1.5, 8.0),
			Math.clamp(maximumAllyChecks, 1, 100),
			finiteClamp(projectileSpeed, 0.4, 3.0),
			Math.clamp(projectileLifetimeTicks, 10, 100),
			Math.clamp(maximumActiveProjectiles, 1, 128),
			consumeAmmunition
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

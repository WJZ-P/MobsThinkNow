package com.wjz.mobsthinknow.paper;

/** Paper 持械僵尸的独立不可变配置，避免继续扩张平台总配置构造器。 */
public record PaperWeaponSettings(
	boolean enabled,
	int minimumIntelligence,
	double spacingRadius,
	double movementSpeed,
	int repathTicks,
	int axeMinimumIntelligence,
	int axeWindupTicks,
	int axePreparationTimeoutTicks,
	double axeHorizontalSpeed,
	double axeCriticalDamageMultiplier
) {
	public static PaperWeaponSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final double spacingRadius,
		final double movementSpeed,
		final int repathTicks,
		final int axeMinimumIntelligence,
		final int axeWindupTicks,
		final int axePreparationTimeoutTicks,
		final double axeHorizontalSpeed,
		final double axeCriticalDamageMultiplier
	) {
		return new PaperWeaponSettings(
			enabled,
			Math.clamp(minimumIntelligence, 1, 10),
			finiteClamp(spacingRadius, 2.0, 5.0),
			finiteClamp(movementSpeed, 0.8, 1.5),
			Math.clamp(repathTicks, 2, 20),
			Math.clamp(axeMinimumIntelligence, 1, 10),
			Math.clamp(axeWindupTicks, 4, 20),
			Math.clamp(axePreparationTimeoutTicks, 10, 80),
			finiteClamp(axeHorizontalSpeed, 0.20, 0.60),
			finiteClamp(axeCriticalDamageMultiplier, 1.0, 2.5)
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

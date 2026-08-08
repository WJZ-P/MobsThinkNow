package com.wjz.mobsthinknow.paper;

/** Paper 盾卫状态机的不可变配置。 */
public record PaperShieldSettings(
	boolean enabled,
	int minimumIntelligence,
	double raiseDistance,
	double lowerDistance,
	double movementSpeed,
	int repathTicks,
	int minimumGuardTicks,
	int maximumGuardTicks,
	int minimumCounterDelayTicks,
	int maximumCounterDelayTicks,
	int strikeWindowTicks,
	int blockSignalMemoryTicks,
	int minimumBlockUseTicks,
	double minimumFacingDot,
	int axeDisableTicks
) {
	public static PaperShieldSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final double raiseDistance,
		final double lowerDistance,
		final double movementSpeed,
		final int repathTicks,
		final int minimumGuardTicks,
		final int maximumGuardTicks,
		final int minimumCounterDelayTicks,
		final int maximumCounterDelayTicks,
		final int strikeWindowTicks,
		final int blockSignalMemoryTicks,
		final int minimumBlockUseTicks,
		final double minimumFacingDot,
		final double axeDisableSeconds
	) {
		double checkedRaiseDistance = finiteClamp(raiseDistance, 2.5, 10.0);
		int checkedMinimumGuard = Math.clamp(minimumGuardTicks, 4, 60);
		int checkedMinimumCounter = Math.clamp(minimumCounterDelayTicks, 1, 10);
		return new PaperShieldSettings(
			enabled,
			Math.clamp(minimumIntelligence, 1, 10),
			checkedRaiseDistance,
			finiteClamp(lowerDistance, checkedRaiseDistance, 12.0),
			finiteClamp(movementSpeed, 0.8, 1.5),
			Math.clamp(repathTicks, 2, 20),
			checkedMinimumGuard,
			Math.clamp(maximumGuardTicks, checkedMinimumGuard, 100),
			checkedMinimumCounter,
			Math.clamp(maximumCounterDelayTicks, checkedMinimumCounter, 20),
			Math.clamp(strikeWindowTicks, 4, 20),
			Math.clamp(blockSignalMemoryTicks, 5, 40),
			Math.clamp(minimumBlockUseTicks, 0, 20),
			finiteClamp(minimumFacingDot, -0.5, 0.95),
			(int)Math.round(finiteClamp(axeDisableSeconds, 0.0, 10.0) * 20.0)
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

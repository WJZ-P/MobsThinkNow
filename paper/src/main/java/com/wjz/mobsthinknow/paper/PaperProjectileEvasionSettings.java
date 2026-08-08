package com.wjz.mobsthinknow.paper;

/** Immutable Paper-side limits for the centralized projectile sensor and dodge Goal. */
public record PaperProjectileEvasionSettings(
	boolean enabled,
	int minimumIntelligence,
	int maximumTrackedProjectiles,
	int maximumCandidateChecks,
	double scanRadius,
	double dodgeDistance,
	double movementSpeed,
	int cooldownTicks
) {
	public static PaperProjectileEvasionSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final int maximumTrackedProjectiles,
		final int maximumCandidateChecks,
		final double scanRadius,
		final double dodgeDistance,
		final double movementSpeed,
		final int cooldownTicks
	) {
		return new PaperProjectileEvasionSettings(
			enabled,
			Math.clamp(minimumIntelligence, 1, 10),
			Math.clamp(maximumTrackedProjectiles, 16, 1024),
			Math.clamp(maximumCandidateChecks, 4, 128),
			finiteClamp(scanRadius, 4.0, 12.0),
			finiteClamp(dodgeDistance, 1.5, 5.0),
			finiteClamp(movementSpeed, 1.0, 1.6),
			Math.clamp(cooldownTicks, 5, 80)
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

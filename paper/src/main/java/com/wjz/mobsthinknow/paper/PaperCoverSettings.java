package com.wjz.mobsthinknow.paper;

import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.SearchLimits;

/** Immutable Paper cover-peeking settings, validated once during load or reload. */
public record PaperCoverSettings(
	boolean enabled,
	int minimumIntelligence,
	int searchRadius,
	int maximumCandidateChecks,
	int maximumPathChecks,
	int searchCooldownTicks,
	double movementSpeed,
	int minimumHiddenTicks,
	int maximumHiddenTicks,
	int drawTicks,
	int maximumShotsPerCover,
	int cycleTimeoutTicks,
	double targetMovementTolerance
) {
	public static PaperCoverSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final int searchRadius,
		final int maximumCandidateChecks,
		final int maximumPathChecks,
		final int searchCooldownTicks,
		final double movementSpeed,
		final int minimumHiddenTicks,
		final int maximumHiddenTicks,
		final int drawTicks,
		final int maximumShotsPerCover,
		final int cycleTimeoutTicks,
		final double targetMovementTolerance
	) {
		int boundedMinimumHidden = Math.clamp(minimumHiddenTicks, 1, 40);
		int boundedMaximumHidden = Math.clamp(maximumHiddenTicks, boundedMinimumHidden, 80);
		return new PaperCoverSettings(
			enabled,
			Math.clamp(minimumIntelligence, 1, 10),
			Math.clamp(searchRadius, 2, 8),
			Math.clamp(maximumCandidateChecks, 16, 512),
			Math.clamp(maximumPathChecks, 1, 8),
			Math.clamp(searchCooldownTicks, 20, 400),
			finiteClamp(movementSpeed, 0.8, 1.5, 1.10),
			boundedMinimumHidden,
			boundedMaximumHidden,
			Math.clamp(drawTicks, 10, 40),
			Math.clamp(maximumShotsPerCover, 1, 4),
			Math.clamp(cycleTimeoutTicks, 80, 600),
			finiteClamp(targetMovementTolerance, 2.0, 16.0, 6.0)
		);
	}

	public static PaperCoverSettings defaults() {
		return validated(true, 5, 4, 96, 4, 60, 1.10, 4, 8, 20, 2, 240, 6.0);
	}

	public SearchLimits searchLimits() {
		return new SearchLimits(
			this.searchRadius,
			1,
			this.maximumCandidateChecks,
			this.maximumPathChecks,
			0.70,
			1.55
		);
	}

	private static double finiteClamp(
		final double value,
		final double minimum,
		final double maximum,
		final double fallback
	) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
	}
}

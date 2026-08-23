package com.wjz.mobsthinknow.paper;

import com.wjz.mobsthinknow.shared.ai.SpiderWebTrapPlanner;

/** Immutable, reload-safe limits for Paper's temporary predictive cobwebs. */
public record PaperWebTrapSettings(
	boolean enabled,
	int minimumIntelligence,
	int cooldownTicks,
	int lifetimeTicks,
	int maximumActivePerWorld,
	boolean blastContainmentEnabled
) {
	public static PaperWebTrapSettings validated(
		final boolean enabled,
		final int minimumIntelligence,
		final int cooldownTicks,
		final int lifetimeTicks,
		final int maximumActivePerWorld,
		final boolean blastContainmentEnabled
	) {
		return new PaperWebTrapSettings(
			enabled,
			Math.clamp(minimumIntelligence, SpiderWebTrapPlanner.MINIMUM_INTELLIGENCE, 10),
			Math.clamp(cooldownTicks, 80, 600),
			Math.clamp(lifetimeTicks, 60, 400),
			Math.clamp(maximumActivePerWorld, 1, 512),
			blastContainmentEnabled
		);
	}

	public static PaperWebTrapSettings defaults() {
		return validated(
			true,
			SpiderWebTrapPlanner.MINIMUM_INTELLIGENCE,
			SpiderWebTrapPlanner.DEFAULT_COOLDOWN_TICKS,
			SpiderWebTrapPlanner.DEFAULT_LIFETIME_TICKS,
			128,
			true
		);
	}
}

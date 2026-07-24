package com.wjz.mobsthinknow.ai.zombie;

import java.util.concurrent.atomic.AtomicLong;

public final class SmartZombieMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong DECISIONS = new AtomicLong();
	private static final AtomicLong FLANK_DECISIONS = new AtomicLong();
	private static final AtomicLong SEARCH_DECISIONS = new AtomicLong();
	private static final AtomicLong FAILED_PATHS = new AtomicLong();

	private SmartZombieMetrics() {
	}

	public static void goalInstalled() {
		INSTALLED_GOALS.incrementAndGet();
	}

	public static void decision(final ZombieTactic tactic) {
		DECISIONS.incrementAndGet();
		if (tactic == ZombieTactic.FLANK_LEFT || tactic == ZombieTactic.FLANK_RIGHT || tactic == ZombieTactic.SURROUND) {
			FLANK_DECISIONS.incrementAndGet();
		} else if (tactic == ZombieTactic.SEARCH_LAST_SEEN) {
			SEARCH_DECISIONS.incrementAndGet();
		}
	}

	public static void failedPath() {
		FAILED_PATHS.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			DECISIONS.get(),
			FLANK_DECISIONS.get(),
			SEARCH_DECISIONS.get(),
			FAILED_PATHS.get()
		);
	}

	public record Snapshot(long installedGoals, long decisions, long flankDecisions, long searchDecisions, long failedPaths) {
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import java.util.concurrent.atomic.AtomicLong;

public final class SmartZombieMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong DECISIONS = new AtomicLong();
	private static final AtomicLong FLANK_DECISIONS = new AtomicLong();
	private static final AtomicLong SEARCH_DECISIONS = new AtomicLong();
	private static final AtomicLong FAILED_PATHS = new AtomicLong();
	private static final AtomicLong COORDINATOR_TICKS = new AtomicLong();
	private static final AtomicLong SQUADS_FORMED = new AtomicLong();
	private static final AtomicLong SQUADS_DISBANDED = new AtomicLong();
	private static final AtomicLong LEADER_ELECTIONS = new AtomicLong();
	private static final AtomicLong LEADER_REELECTIONS = new AtomicLong();
	private static final AtomicLong SQUAD_CANDIDATE_CHECKS = new AtomicLong();

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

	public static void coordinatorTick() {
		COORDINATOR_TICKS.incrementAndGet();
	}

	public static void squadFormed() {
		SQUADS_FORMED.incrementAndGet();
	}

	public static void squadDisbanded() {
		SQUADS_DISBANDED.incrementAndGet();
	}

	public static void leaderElection(final boolean replacement) {
		LEADER_ELECTIONS.incrementAndGet();
		if (replacement) {
			LEADER_REELECTIONS.incrementAndGet();
		}
	}

	public static void squadCandidateChecks(final int checks) {
		SQUAD_CANDIDATE_CHECKS.addAndGet(checks);
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			DECISIONS.get(),
			FLANK_DECISIONS.get(),
			SEARCH_DECISIONS.get(),
			FAILED_PATHS.get(),
			COORDINATOR_TICKS.get(),
			SQUADS_FORMED.get(),
			SQUADS_DISBANDED.get(),
			LEADER_ELECTIONS.get(),
			LEADER_REELECTIONS.get(),
			SQUAD_CANDIDATE_CHECKS.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long decisions,
		long flankDecisions,
		long searchDecisions,
		long failedPaths,
		long coordinatorTicks,
		long squadsFormed,
		long squadsDisbanded,
		long leaderElections,
		long leaderReelections,
		long squadCandidateChecks
	) {
	}
}

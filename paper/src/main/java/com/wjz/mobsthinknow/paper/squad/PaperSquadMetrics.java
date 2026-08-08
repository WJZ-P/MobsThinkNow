package com.wjz.mobsthinknow.paper.squad;

import java.util.concurrent.atomic.AtomicLong;

/** 小队诊断与性能计数，不参与决策。 */
public final class PaperSquadMetrics {
	private final AtomicLong squadsFormed = new AtomicLong();
	private final AtomicLong membersRecruited = new AtomicLong();
	private final AtomicLong leaderElections = new AtomicLong();
	private final AtomicLong leaderReplacements = new AtomicLong();
	private final AtomicLong phaseTransitions = new AtomicLong();
	private final AtomicLong sharedTargets = new AtomicLong();
	private final AtomicLong friendlyTargetsPrevented = new AtomicLong();
	private final AtomicLong friendlyDamagePrevented = new AtomicLong();
	private final AtomicLong boundedCandidateChecks = new AtomicLong();
	private final AtomicLong orderPaths = new AtomicLong();
	private final AtomicLong orderPathFailures = new AtomicLong();

	public void squadFormed() { this.squadsFormed.incrementAndGet(); }
	public void memberRecruited() { this.membersRecruited.incrementAndGet(); }
	public void leaderElected() { this.leaderElections.incrementAndGet(); }
	public void leaderReplaced() { this.leaderReplacements.incrementAndGet(); }
	public void phaseTransition() { this.phaseTransitions.incrementAndGet(); }
	public void sharedTarget() { this.sharedTargets.incrementAndGet(); }
	public void friendlyTargetPrevented() { this.friendlyTargetsPrevented.incrementAndGet(); }
	public void friendlyDamagePrevented() { this.friendlyDamagePrevented.incrementAndGet(); }
	public void candidateChecks(final int count) { this.boundedCandidateChecks.addAndGet(Math.max(0, count)); }
	public void orderPath() { this.orderPaths.incrementAndGet(); }
	public void orderPathFailure() { this.orderPathFailures.incrementAndGet(); }

	public Snapshot snapshot() {
		return new Snapshot(
			this.squadsFormed.get(),
			this.membersRecruited.get(),
			this.leaderElections.get(),
			this.leaderReplacements.get(),
			this.phaseTransitions.get(),
			this.sharedTargets.get(),
			this.friendlyTargetsPrevented.get(),
			this.friendlyDamagePrevented.get(),
			this.boundedCandidateChecks.get(),
			this.orderPaths.get(),
			this.orderPathFailures.get()
		);
	}

	public record Snapshot(
		long squadsFormed,
		long membersRecruited,
		long leaderElections,
		long leaderReplacements,
		long phaseTransitions,
		long sharedTargets,
		long friendlyTargetsPrevented,
		long friendlyDamagePrevented,
		long boundedCandidateChecks,
		long orderPaths,
		long orderPathFailures
	) {
	}
}

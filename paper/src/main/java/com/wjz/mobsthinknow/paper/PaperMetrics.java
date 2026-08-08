package com.wjz.mobsthinknow.paper;

import java.util.concurrent.atomic.AtomicLong;

/** 插件端只写诊断计数，不反向参与 AI 决策。 */
public final class PaperMetrics {
	private final AtomicLong intelligenceAssignments = new AtomicLong();
	private final AtomicLong retreatGoalsInstalled = new AtomicLong();
	private final AtomicLong retreatGoalsRemoved = new AtomicLong();
	private final AtomicLong retreatStarts = new AtomicLong();
	private final AtomicLong retreatPathFailures = new AtomicLong();
	private final AtomicLong skeletonDisengageGoalsInstalled = new AtomicLong();
	private final AtomicLong skeletonDisengageGoalsRemoved = new AtomicLong();
	private final AtomicLong skeletonDisengageStarts = new AtomicLong();
	private final AtomicLong skeletonDisengagePathFailures = new AtomicLong();

	public void intelligenceAssigned() {
		this.intelligenceAssignments.incrementAndGet();
	}

	public void retreatGoalInstalled() {
		this.retreatGoalsInstalled.incrementAndGet();
	}

	public void retreatGoalRemoved() {
		this.retreatGoalsRemoved.incrementAndGet();
	}

	public void retreatStarted() {
		this.retreatStarts.incrementAndGet();
	}

	public void retreatPathFailed() {
		this.retreatPathFailures.incrementAndGet();
	}

	public void skeletonDisengageGoalInstalled() {
		this.skeletonDisengageGoalsInstalled.incrementAndGet();
	}

	public void skeletonDisengageGoalRemoved() {
		this.skeletonDisengageGoalsRemoved.incrementAndGet();
	}

	public void skeletonDisengageStarted() {
		this.skeletonDisengageStarts.incrementAndGet();
	}

	public void skeletonDisengagePathFailed() {
		this.skeletonDisengagePathFailures.incrementAndGet();
	}

	public Snapshot snapshot() {
		return new Snapshot(
			this.intelligenceAssignments.get(),
			this.retreatGoalsInstalled.get(),
			this.retreatGoalsRemoved.get(),
			this.retreatStarts.get(),
			this.retreatPathFailures.get(),
			this.skeletonDisengageGoalsInstalled.get(),
			this.skeletonDisengageGoalsRemoved.get(),
			this.skeletonDisengageStarts.get(),
			this.skeletonDisengagePathFailures.get()
		);
	}

	public record Snapshot(
		long intelligenceAssignments,
		long retreatGoalsInstalled,
		long retreatGoalsRemoved,
		long retreatStarts,
		long retreatPathFailures,
		long skeletonDisengageGoalsInstalled,
		long skeletonDisengageGoalsRemoved,
		long skeletonDisengageStarts,
		long skeletonDisengagePathFailures
	) {
	}
}

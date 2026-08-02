package com.wjz.mobsthinknow.ai.creeper;

import java.util.concurrent.atomic.AtomicLong;

/** 苦力怕战术诊断计数器；不反向参与决策。 */
public final class SmartCreeperMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong FLANKS = new AtomicLong();
	private static final AtomicLong INTERCEPTS = new AtomicLong();
	private static final AtomicLong MOVING_FUSES = new AtomicLong();
	private static final AtomicLong BREACH_FUSES = new AtomicLong();
	private static final AtomicLong ABORTED_FUSES = new AtomicLong();
	private static final AtomicLong SQUAD_EVACUATIONS = new AtomicLong();

	private SmartCreeperMetrics() {
	}

	public static void goalsInstalled() {
		INSTALLED_GOALS.addAndGet(3L);
	}

	public static void flankStarted() {
		FLANKS.incrementAndGet();
	}

	public static void interceptStarted() {
		INTERCEPTS.incrementAndGet();
	}

	public static void movingFuseStarted() {
		MOVING_FUSES.incrementAndGet();
	}

	public static void breachFuseStarted() {
		BREACH_FUSES.incrementAndGet();
	}

	public static void fuseAborted() {
		ABORTED_FUSES.incrementAndGet();
	}

	public static void squadEvacuationStarted() {
		SQUAD_EVACUATIONS.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			FLANKS.get(),
			INTERCEPTS.get(),
			MOVING_FUSES.get(),
			BREACH_FUSES.get(),
			ABORTED_FUSES.get(),
			SQUAD_EVACUATIONS.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long flanks,
		long intercepts,
		long movingFuses,
		long breachFuses,
		long abortedFuses,
		long squadEvacuations
	) {
	}
}

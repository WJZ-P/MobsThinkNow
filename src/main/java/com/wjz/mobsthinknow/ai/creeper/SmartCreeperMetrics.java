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
	private static final AtomicLong FEINTS = new AtomicLong();
	private static final AtomicLong COMPLETED_FEINTS = new AtomicLong();
	private static final AtomicLong SHIELD_BAITS = new AtomicLong();
	private static final AtomicLong SQUAD_EVACUATIONS = new AtomicLong();
	private static final AtomicLong BLAST_RESERVATIONS_ACQUIRED = new AtomicLong();
	private static final AtomicLong BLAST_RESERVATION_CONFLICTS = new AtomicLong();
	private static final AtomicLong BLAST_RESERVATIONS_RELEASED = new AtomicLong();

	private SmartCreeperMetrics() {
	}

	public static void goalsInstalled() {
		INSTALLED_GOALS.addAndGet(4L);
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

	public static void feintStarted(final boolean targetWasBlocking) {
		FEINTS.incrementAndGet();
		if (targetWasBlocking) {
			SHIELD_BAITS.incrementAndGet();
		}
	}

	public static void feintCompleted() {
		COMPLETED_FEINTS.incrementAndGet();
	}

	public static void squadEvacuationStarted() {
		SQUAD_EVACUATIONS.incrementAndGet();
	}

	public static void blastReservationAcquired() {
		BLAST_RESERVATIONS_ACQUIRED.incrementAndGet();
	}

	public static void blastReservationConflict() {
		BLAST_RESERVATION_CONFLICTS.incrementAndGet();
	}

	public static void blastReservationReleased() {
		BLAST_RESERVATIONS_RELEASED.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			FLANKS.get(),
			INTERCEPTS.get(),
			MOVING_FUSES.get(),
			BREACH_FUSES.get(),
			ABORTED_FUSES.get(),
			FEINTS.get(),
			COMPLETED_FEINTS.get(),
			SHIELD_BAITS.get(),
			SQUAD_EVACUATIONS.get(),
			BLAST_RESERVATIONS_ACQUIRED.get(),
			BLAST_RESERVATION_CONFLICTS.get(),
			BLAST_RESERVATIONS_RELEASED.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long flanks,
		long intercepts,
		long movingFuses,
		long breachFuses,
		long abortedFuses,
		long feints,
		long completedFeints,
		long shieldBaits,
		long squadEvacuations,
		long blastReservationsAcquired,
		long blastReservationConflicts,
		long blastReservationsReleased
	) {
	}
}

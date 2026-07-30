package com.wjz.mobsthinknow.ai.spider;

import java.util.concurrent.atomic.AtomicLong;

	/** 蜘蛛个人战术与跨物种运输战术的诊断计数器，不反向参与决策。 */
public final class SmartSpiderMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong FLANKS = new AtomicLong();
	private static final AtomicLong POUNCES = new AtomicLong();
	private static final AtomicLong REPOSITIONS = new AtomicLong();
	private static final AtomicLong CARRIER_SEARCHES = new AtomicLong();
	private static final AtomicLong CARRIER_CANDIDATE_CHECKS = new AtomicLong();
	private static final AtomicLong CREEPERS_MOUNTED = new AtomicLong();
	private static final AtomicLong DELIVERY_FUSES = new AtomicLong();

	private SmartSpiderMetrics() {
	}

	public static void goalsInstalled() {
		INSTALLED_GOALS.addAndGet(5L);
	}

	public static void flankStarted() {
		FLANKS.incrementAndGet();
	}

	public static void pounceStarted() {
		POUNCES.incrementAndGet();
	}

	public static void repositionStarted() {
		REPOSITIONS.incrementAndGet();
	}

	public static void carrierSearch() {
		CARRIER_SEARCHES.incrementAndGet();
	}

	public static void carrierCandidateChecked() {
		CARRIER_CANDIDATE_CHECKS.incrementAndGet();
	}

	public static void creeperMounted() {
		CREEPERS_MOUNTED.incrementAndGet();
	}

	public static void deliveryFuseStarted() {
		DELIVERY_FUSES.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			FLANKS.get(),
			POUNCES.get(),
			REPOSITIONS.get(),
			CARRIER_SEARCHES.get(),
			CARRIER_CANDIDATE_CHECKS.get(),
			CREEPERS_MOUNTED.get(),
			DELIVERY_FUSES.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long flanks,
		long pounces,
		long repositions,
		long carrierSearches,
		long carrierCandidateChecks,
		long creepersMounted,
		long deliveryFuses
	) {
	}
}

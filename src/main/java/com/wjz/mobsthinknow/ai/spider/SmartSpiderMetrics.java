package com.wjz.mobsthinknow.ai.spider;

import java.util.concurrent.atomic.AtomicLong;

/** 蜘蛛个人战术与跨物种运输战术的诊断计数器，不反向参与决策。 */
public final class SmartSpiderMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong FLANKS = new AtomicLong();
	private static final AtomicLong POUNCES = new AtomicLong();
	private static final AtomicLong REPOSITIONS = new AtomicLong();
	private static final AtomicLong WEB_TRAP_WINDUPS = new AtomicLong();
	private static final AtomicLong WEB_TRAPS_PLACED = new AtomicLong();
	private static final AtomicLong WEB_TRAPS_EXPIRED = new AtomicLong();
	private static final AtomicLong BLAST_CONTAINMENT_WEBS = new AtomicLong();
	private static final AtomicLong CARRIER_SEARCHES = new AtomicLong();
	private static final AtomicLong CARRIER_CANDIDATE_CHECKS = new AtomicLong();
	private static final AtomicLong CREEPERS_MOUNTED = new AtomicLong();
	private static final AtomicLong DELIVERY_FUSES = new AtomicLong();
	private static final AtomicLong COORDINATED_BREACH_STAGING = new AtomicLong();
	private static final AtomicLong MOBILE_FIRE_SUPPORT_MOVES = new AtomicLong();
	private static final AtomicLong TRANSPORT_ROUTE_CHECKS = new AtomicLong();
	private static final AtomicLong TRANSPORT_ROUTE_REJECTIONS = new AtomicLong();
	private static final AtomicLong TRANSPORT_SAFE_DISMOUNTS = new AtomicLong();
	private static final AtomicLong CASUALTY_PICKUPS = new AtomicLong();
	private static final AtomicLong CASUALTY_DROPOFFS = new AtomicLong();

	private SmartSpiderMetrics() {
	}

	public static void goalsInstalled() {
		INSTALLED_GOALS.addAndGet(8L);
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

	public static void webTrapWindup() {
		WEB_TRAP_WINDUPS.incrementAndGet();
	}

	public static void webTrapPlaced() {
		WEB_TRAPS_PLACED.incrementAndGet();
	}

	public static void webTrapExpired() {
		WEB_TRAPS_EXPIRED.incrementAndGet();
	}

	public static void blastContainmentWeb() {
		BLAST_CONTAINMENT_WEBS.incrementAndGet();
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

	public static void coordinatedBreachStaging() {
		COORDINATED_BREACH_STAGING.incrementAndGet();
	}

	public static void mobileFireSupportMove() {
		MOBILE_FIRE_SUPPORT_MOVES.incrementAndGet();
	}

	public static void transportRouteCheck() {
		TRANSPORT_ROUTE_CHECKS.incrementAndGet();
	}

	public static void transportRouteRejected() {
		TRANSPORT_ROUTE_REJECTIONS.incrementAndGet();
	}

	public static void transportSafeDismount(final SpiderTransportRouteEvaluator.Status reason) {
		if (reason != SpiderTransportRouteEvaluator.Status.CLEAR) {
			TRANSPORT_SAFE_DISMOUNTS.incrementAndGet();
		}
	}

	public static void casualtyPickup() {
		CASUALTY_PICKUPS.incrementAndGet();
	}

	public static void casualtyDropoff() {
		CASUALTY_DROPOFFS.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			FLANKS.get(),
			POUNCES.get(),
			REPOSITIONS.get(),
			WEB_TRAP_WINDUPS.get(),
			WEB_TRAPS_PLACED.get(),
			WEB_TRAPS_EXPIRED.get(),
			BLAST_CONTAINMENT_WEBS.get(),
			CARRIER_SEARCHES.get(),
			CARRIER_CANDIDATE_CHECKS.get(),
			CREEPERS_MOUNTED.get(),
			DELIVERY_FUSES.get(),
			COORDINATED_BREACH_STAGING.get(),
			MOBILE_FIRE_SUPPORT_MOVES.get(),
			TRANSPORT_ROUTE_CHECKS.get(),
			TRANSPORT_ROUTE_REJECTIONS.get(),
			TRANSPORT_SAFE_DISMOUNTS.get(),
			CASUALTY_PICKUPS.get(),
			CASUALTY_DROPOFFS.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long flanks,
		long pounces,
		long repositions,
		long webTrapWindups,
		long webTrapsPlaced,
		long webTrapsExpired,
		long blastContainmentWebs,
		long carrierSearches,
		long carrierCandidateChecks,
		long creepersMounted,
		long deliveryFuses,
		long coordinatedBreachStaging,
		long mobileFireSupportMoves,
		long transportRouteChecks,
		long transportRouteRejections,
		long transportSafeDismounts,
		long casualtyPickups,
		long casualtyDropoffs
	) {
	}
}

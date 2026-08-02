package com.wjz.mobsthinknow.ai.enderman;

import java.util.concurrent.atomic.AtomicLong;

/** 末影人职业战斗与苦力怕投送的只读诊断指标；计数器不参与战术决策。 */
public final class SmartEndermanMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong CARRIER_SEARCHES = new AtomicLong();
	private static final AtomicLong CANDIDATE_CHECKS = new AtomicLong();
	private static final AtomicLong PAYLOADS_PICKED_UP = new AtomicLong();
	private static final AtomicLong DELIVERY_TELEPORTS = new AtomicLong();
	private static final AtomicLong PAYLOADS_IGNITED = new AtomicLong();
	private static final AtomicLong COMBAT_TELEPORTS = new AtomicLong();
	private static final AtomicLong SHIELD_BLOCKS = new AtomicLong();
	private static final AtomicLong SHIELD_COUNTER_HITS = new AtomicLong();
	private static final AtomicLong SPEAR_CHARGES = new AtomicLong();
	private static final AtomicLong PROFESSION_HITS = new AtomicLong();

	private SmartEndermanMetrics() {
	}

	public static void goalInstalled() {
		INSTALLED_GOALS.incrementAndGet();
	}

	public static void carrierSearch() {
		CARRIER_SEARCHES.incrementAndGet();
	}

	public static void candidateChecked() {
		CANDIDATE_CHECKS.incrementAndGet();
	}

	public static void payloadPickedUp() {
		PAYLOADS_PICKED_UP.incrementAndGet();
	}

	public static void deliveryTeleport() {
		DELIVERY_TELEPORTS.incrementAndGet();
	}

	public static void payloadIgnited() {
		PAYLOADS_IGNITED.incrementAndGet();
	}

	public static void combatTeleport() {
		COMBAT_TELEPORTS.incrementAndGet();
	}

	public static void shieldBlock() {
		SHIELD_BLOCKS.incrementAndGet();
	}

	public static void shieldCounterHit() {
		SHIELD_COUNTER_HITS.incrementAndGet();
	}

	public static void spearCharge() {
		SPEAR_CHARGES.incrementAndGet();
	}

	public static void professionHit() {
		PROFESSION_HITS.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			CARRIER_SEARCHES.get(),
			CANDIDATE_CHECKS.get(),
			PAYLOADS_PICKED_UP.get(),
			DELIVERY_TELEPORTS.get(),
			PAYLOADS_IGNITED.get(),
			COMBAT_TELEPORTS.get(),
			SHIELD_BLOCKS.get(),
			SHIELD_COUNTER_HITS.get(),
			SPEAR_CHARGES.get(),
			PROFESSION_HITS.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long carrierSearches,
		long candidateChecks,
		long payloadsPickedUp,
		long deliveryTeleports,
		long payloadsIgnited,
		long combatTeleports,
		long shieldBlocks,
		long shieldCounterHits,
		long spearCharges,
		long professionHits
	) {
	}
}

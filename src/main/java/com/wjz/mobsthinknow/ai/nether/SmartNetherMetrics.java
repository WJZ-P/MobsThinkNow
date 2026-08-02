package com.wjz.mobsthinknow.ai.nether;

import java.util.concurrent.atomic.AtomicLong;

/** 下界战术诊断计数器；只记录已经发生的动作，不参与决策。 */
public final class SmartNetherMetrics {
	private static final AtomicLong INSTALLED_CONTROLLERS = new AtomicLong();
	private static final AtomicLong PIGLIN_FORMATION_MOVES = new AtomicLong();
	private static final AtomicLong BLAZE_VOLLEYS = new AtomicLong();
	private static final AtomicLong BLAZE_FIREBALLS = new AtomicLong();
	private static final AtomicLong GHAST_SHOTS = new AtomicLong();
	private static final AtomicLong GHAST_RELOCATIONS = new AtomicLong();
	private static final AtomicLong HOGLIN_CHARGES = new AtomicLong();
	private static final AtomicLong HOGLIN_IMPACTS = new AtomicLong();
	private static final AtomicLong MAGMA_POUNCES = new AtomicLong();

	private SmartNetherMetrics() {
	}

	public static void controllerInstalled() {
		INSTALLED_CONTROLLERS.incrementAndGet();
	}

	public static void piglinFormationMove() {
		PIGLIN_FORMATION_MOVES.incrementAndGet();
	}

	public static void blazeVolley() {
		BLAZE_VOLLEYS.incrementAndGet();
	}

	public static void blazeFireball() {
		BLAZE_FIREBALLS.incrementAndGet();
	}

	public static void ghastShot() {
		GHAST_SHOTS.incrementAndGet();
	}

	public static void ghastRelocation() {
		GHAST_RELOCATIONS.incrementAndGet();
	}

	public static void hoglinCharge() {
		HOGLIN_CHARGES.incrementAndGet();
	}

	public static void hoglinImpact() {
		HOGLIN_IMPACTS.incrementAndGet();
	}

	public static void magmaPounce() {
		MAGMA_POUNCES.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_CONTROLLERS.get(),
			PIGLIN_FORMATION_MOVES.get(),
			BLAZE_VOLLEYS.get(),
			BLAZE_FIREBALLS.get(),
			GHAST_SHOTS.get(),
			GHAST_RELOCATIONS.get(),
			HOGLIN_CHARGES.get(),
			HOGLIN_IMPACTS.get(),
			MAGMA_POUNCES.get()
		);
	}

	public record Snapshot(
		long installedControllers,
		long piglinFormationMoves,
		long blazeVolleys,
		long blazeFireballs,
		long ghastShots,
		long ghastRelocations,
		long hoglinCharges,
		long hoglinImpacts,
		long magmaPounces
	) {
	}
}

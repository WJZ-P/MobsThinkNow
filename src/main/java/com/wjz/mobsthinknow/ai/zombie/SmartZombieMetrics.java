package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
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
	private static final AtomicLong RETREATS = new AtomicLong();
	private static final AtomicLong TERRAIN_BLOCKS_HARVESTED = new AtomicLong();
	private static final AtomicLong TERRAIN_BLOCKS_PLACED = new AtomicLong();
	private static final AtomicLong PERCHED_ATTACKS = new AtomicLong();
	private static final AtomicLong WATER_DEPLOYMENTS = new AtomicLong();
	private static final AtomicLong LAVA_DEPLOYMENTS = new AtomicLong();
	private static final AtomicLong FLUID_RECOVERIES = new AtomicLong();
	private static final AtomicLong FLUID_SOURCES_LOST = new AtomicLong();
	private static final AtomicLong ENGINEER_TNT_CHARGES = new AtomicLong();
	private static final AtomicLong ENGINEER_WATER_DEPLOYMENTS = new AtomicLong();
	private static final AtomicLong ENGINEER_LAVA_DEPLOYMENTS = new AtomicLong();
	private static final AtomicLong ENGINEER_IGNITIONS = new AtomicLong();

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

	public static void retreatTriggered() {
		RETREATS.incrementAndGet();
	}

	public static void terrainBlockHarvested() {
		TERRAIN_BLOCKS_HARVESTED.incrementAndGet();
	}

	public static void terrainBlockPlaced() {
		TERRAIN_BLOCKS_PLACED.incrementAndGet();
	}

	public static void perchedAttack() {
		PERCHED_ATTACKS.incrementAndGet();
	}

	public static void fluidDeployed(final UtilityClass utility) {
		if (utility == UtilityClass.WATER) {
			WATER_DEPLOYMENTS.incrementAndGet();
		} else if (utility == UtilityClass.LAVA) {
			LAVA_DEPLOYMENTS.incrementAndGet();
		}
	}

	public static void fluidRecovered() {
		FLUID_RECOVERIES.incrementAndGet();
	}

	public static void fluidSourceLost() {
		FLUID_SOURCES_LOST.incrementAndGet();
	}

	public static void engineerTntCharge() {
		ENGINEER_TNT_CHARGES.incrementAndGet();
	}

	public static void engineerFluidDeployment(final UtilityClass utility) {
		if (utility == UtilityClass.WATER) {
			ENGINEER_WATER_DEPLOYMENTS.incrementAndGet();
		} else if (utility == UtilityClass.LAVA) {
			ENGINEER_LAVA_DEPLOYMENTS.incrementAndGet();
		}
	}

	public static void engineerIgnition() {
		ENGINEER_IGNITIONS.incrementAndGet();
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
			SQUAD_CANDIDATE_CHECKS.get(),
			RETREATS.get(),
			TERRAIN_BLOCKS_HARVESTED.get(),
			TERRAIN_BLOCKS_PLACED.get(),
			PERCHED_ATTACKS.get(),
			WATER_DEPLOYMENTS.get(),
			LAVA_DEPLOYMENTS.get(),
			FLUID_RECOVERIES.get(),
			FLUID_SOURCES_LOST.get(),
			ENGINEER_TNT_CHARGES.get(),
			ENGINEER_WATER_DEPLOYMENTS.get(),
			ENGINEER_LAVA_DEPLOYMENTS.get(),
			ENGINEER_IGNITIONS.get()
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
		long squadCandidateChecks,
		long retreats,
		long terrainBlocksHarvested,
		long terrainBlocksPlaced,
		long perchedAttacks,
		long waterDeployments,
		long lavaDeployments,
		long fluidRecoveries,
		long fluidSourcesLost,
		long engineerTntCharges,
		long engineerWaterDeployments,
		long engineerLavaDeployments,
		long engineerIgnitions
	) {
	}
}

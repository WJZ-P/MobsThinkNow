package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadAssaultPlan;
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
	private static final AtomicLong ASSAULT_PLANS = new AtomicLong();
	private static final AtomicLong CROSSFIRE_PLANS = new AtomicLong();
	private static final AtomicLong MOUNTED_BREACH_PLANS = new AtomicLong();
	private static final AtomicLong COMBINED_ARMS_PLANS = new AtomicLong();
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
	private static final AtomicLong SWORD_FEINTS = new AtomicLong();
	private static final AtomicLong AXE_WINDUPS = new AtomicLong();
	private static final AtomicLong SHIELD_BASHES = new AtomicLong();
	private static final AtomicLong SHIELD_BASH_HITS = new AtomicLong();
	private static final AtomicLong SHIELD_WALL_ROTATIONS = new AtomicLong();
	private static final AtomicLong LEADER_SOCIAL_GESTURES = new AtomicLong();
	private static final AtomicLong MEMBER_SOCIAL_GESTURES = new AtomicLong();
	private static final AtomicLong BRIEFING_ROUTE_CHECKS = new AtomicLong();
	private static final AtomicLong BRIEFING_ROUTE_OBJECTIONS = new AtomicLong();
	private static final AtomicLong BRIEFING_REPLANS = new AtomicLong();
	private static final AtomicLong COMBAT_ROUTE_FAILURES = new AtomicLong();
	private static final AtomicLong COMBAT_ROUTE_CHECKS = new AtomicLong();
	private static final AtomicLong COMBAT_REPLANS = new AtomicLong();
	private static final AtomicLong COMBAT_REPLAN_SUPPRESSED = new AtomicLong();
	private static final AtomicLong TARGET_TACTIC_CHANGES = new AtomicLong();
	private static final AtomicLong SHARED_DANGERS_REPORTED = new AtomicLong();
	private static final AtomicLong SHARED_DANGERS_AVOIDED = new AtomicLong();
	private static final AtomicLong SECONDARY_THREATS_OBSERVED = new AtomicLong();
	private static final AtomicLong THREAT_ASSIGNMENTS_CHANGED = new AtomicLong();
	private static final AtomicLong CASUALTY_RESPONSES_STARTED = new AtomicLong();
	private static final AtomicLong CASUALTY_RESPONSES_FINISHED = new AtomicLong();
	private static final AtomicLong CASUALTY_GOALS_STARTED = new AtomicLong();
	private static final AtomicLong CASUALTY_ESCORT_HITS = new AtomicLong();
	private static final AtomicLong WEB_AMBUSHES_STARTED = new AtomicLong();
	private static final AtomicLong WEB_AMBUSHES_COMMITTED = new AtomicLong();
	private static final AtomicLong WEB_AMBUSHES_FINISHED = new AtomicLong();
	private static final AtomicLong WEB_AMBUSH_ESCAPES = new AtomicLong();

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

	public static void assaultPlanChosen(final SquadAssaultPlan plan) {
		ASSAULT_PLANS.incrementAndGet();
		if (plan.usesCrossfire()) {
			CROSSFIRE_PLANS.incrementAndGet();
		}
		if (plan.usesMountedBreach()) {
			MOUNTED_BREACH_PLANS.incrementAndGet();
		}
		if (plan == SquadAssaultPlan.COMBINED_ARMS) {
			COMBINED_ARMS_PLANS.incrementAndGet();
		}
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

	public static void swordFeint() {
		SWORD_FEINTS.incrementAndGet();
	}

	public static void axeWindup() {
		AXE_WINDUPS.incrementAndGet();
	}

	public static void shieldBash() {
		SHIELD_BASHES.incrementAndGet();
	}

	public static void shieldBashHit() {
		SHIELD_BASH_HITS.incrementAndGet();
	}

	public static void shieldWallRotation() {
		SHIELD_WALL_ROTATIONS.incrementAndGet();
	}

	public static void leaderSocialGesture() {
		LEADER_SOCIAL_GESTURES.incrementAndGet();
	}

	public static void memberSocialGesture() {
		MEMBER_SOCIAL_GESTURES.incrementAndGet();
	}

	public static void briefingRouteChecks(final int checks) {
		BRIEFING_ROUTE_CHECKS.addAndGet(Math.max(0, checks));
	}

	public static void briefingRouteObjection() {
		BRIEFING_ROUTE_OBJECTIONS.incrementAndGet();
	}

	public static void briefingReplan() {
		BRIEFING_REPLANS.incrementAndGet();
	}

	public static void combatRouteFailure() {
		COMBAT_ROUTE_FAILURES.incrementAndGet();
	}

	public static void combatRouteChecks(final int checks) {
		COMBAT_ROUTE_CHECKS.addAndGet(Math.max(0, checks));
	}

	public static void combatReplan() {
		COMBAT_REPLANS.incrementAndGet();
	}

	public static void combatReplanSuppressed() {
		COMBAT_REPLAN_SUPPRESSED.incrementAndGet();
	}

	public static void targetTacticChanged() {
		TARGET_TACTIC_CHANGES.incrementAndGet();
	}

	public static void sharedDangerReported() {
		SHARED_DANGERS_REPORTED.incrementAndGet();
	}

	public static void sharedDangerAvoided() {
		SHARED_DANGERS_AVOIDED.incrementAndGet();
	}

	public static void secondaryThreatObserved() {
		SECONDARY_THREATS_OBSERVED.incrementAndGet();
	}

	public static void threatAssignmentsChanged(final int members) {
		THREAT_ASSIGNMENTS_CHANGED.addAndGet(Math.max(0, members));
	}

	public static void casualtyResponseStarted() {
		CASUALTY_RESPONSES_STARTED.incrementAndGet();
	}

	public static void casualtyResponseFinished() {
		CASUALTY_RESPONSES_FINISHED.incrementAndGet();
	}

	public static void casualtyGoalStarted() {
		CASUALTY_GOALS_STARTED.incrementAndGet();
	}

	public static void casualtyEscortHit() {
		CASUALTY_ESCORT_HITS.incrementAndGet();
	}

	public static void webAmbushStarted() {
		WEB_AMBUSHES_STARTED.incrementAndGet();
	}

	public static void webAmbushCommitted() {
		WEB_AMBUSHES_COMMITTED.incrementAndGet();
	}

	public static void webAmbushFinished() {
		WEB_AMBUSHES_FINISHED.incrementAndGet();
	}

	public static void webAmbushEscaped() {
		WEB_AMBUSH_ESCAPES.incrementAndGet();
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
			ASSAULT_PLANS.get(),
			CROSSFIRE_PLANS.get(),
			MOUNTED_BREACH_PLANS.get(),
			COMBINED_ARMS_PLANS.get(),
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
			ENGINEER_IGNITIONS.get(),
			SWORD_FEINTS.get(),
			AXE_WINDUPS.get(),
			SHIELD_BASHES.get(),
			SHIELD_BASH_HITS.get(),
			SHIELD_WALL_ROTATIONS.get(),
			LEADER_SOCIAL_GESTURES.get(),
			MEMBER_SOCIAL_GESTURES.get(),
			BRIEFING_ROUTE_CHECKS.get(),
			BRIEFING_ROUTE_OBJECTIONS.get(),
			BRIEFING_REPLANS.get(),
			COMBAT_ROUTE_FAILURES.get(),
			COMBAT_ROUTE_CHECKS.get(),
			COMBAT_REPLANS.get(),
			COMBAT_REPLAN_SUPPRESSED.get(),
			TARGET_TACTIC_CHANGES.get(),
			SHARED_DANGERS_REPORTED.get(),
			SHARED_DANGERS_AVOIDED.get(),
			SECONDARY_THREATS_OBSERVED.get(),
			THREAT_ASSIGNMENTS_CHANGED.get(),
			CASUALTY_RESPONSES_STARTED.get(),
			CASUALTY_RESPONSES_FINISHED.get(),
			CASUALTY_GOALS_STARTED.get(),
			CASUALTY_ESCORT_HITS.get(),
			WEB_AMBUSHES_STARTED.get(),
			WEB_AMBUSHES_COMMITTED.get(),
			WEB_AMBUSHES_FINISHED.get(),
			WEB_AMBUSH_ESCAPES.get()
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
		long assaultPlans,
		long crossfirePlans,
		long mountedBreachPlans,
		long combinedArmsPlans,
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
		long engineerIgnitions,
		long swordFeints,
		long axeWindups,
		long shieldBashes,
		long shieldBashHits,
		long shieldWallRotations,
		long leaderSocialGestures,
		long memberSocialGestures,
		long briefingRouteChecks,
		long briefingRouteObjections,
		long briefingReplans,
		long combatRouteFailures,
		long combatRouteChecks,
		long combatReplans,
		long combatReplanSuppressed,
		long targetTacticChanges,
		long sharedDangersReported,
		long sharedDangersAvoided,
		long secondaryThreatsObserved,
		long threatAssignmentsChanged,
		long casualtyResponsesStarted,
		long casualtyResponsesFinished,
		long casualtyGoalsStarted,
		long casualtyEscortHits,
		long webAmbushesStarted,
		long webAmbushesCommitted,
		long webAmbushesFinished,
		long webAmbushEscapes
	) {
	}
}

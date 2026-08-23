package com.wjz.mobsthinknow.paper;

import java.util.concurrent.atomic.AtomicLong;

/** 插件端只写诊断计数，不反向参与 AI 决策。 */
public final class PaperMetrics {
	private final AtomicLong intelligenceAssignments = new AtomicLong();
	private final AtomicLong retreatGoalsInstalled = new AtomicLong();
	private final AtomicLong retreatGoalsRemoved = new AtomicLong();
	private final AtomicLong retreatStarts = new AtomicLong();
	private final AtomicLong retreatPathFailures = new AtomicLong();
	private final AtomicLong weaponGoalsInstalled = new AtomicLong();
	private final AtomicLong weaponGoalsRemoved = new AtomicLong();
	private final AtomicLong weaponAttacks = new AtomicLong();
	private final AtomicLong weaponSpacingMoves = new AtomicLong();
	private final AtomicLong weaponPathFailures = new AtomicLong();
	private final AtomicLong axeWindups = new AtomicLong();
	private final AtomicLong axeLeaps = new AtomicLong();
	private final AtomicLong axeCriticalAttacks = new AtomicLong();
	private final AtomicLong axeLaunchAirborneRejects = new AtomicLong();
	private final AtomicLong axeLaunchWaterRejects = new AtomicLong();
	private final AtomicLong axeLaunchSightRejects = new AtomicLong();
	private final AtomicLong axeLaunchBandRejects = new AtomicLong();
	private final AtomicLong axeLaunchCollisionRejects = new AtomicLong();
	private final AtomicLong shieldGoalsInstalled = new AtomicLong();
	private final AtomicLong shieldGoalsRemoved = new AtomicLong();
	private final AtomicLong shieldGuards = new AtomicLong();
	private final AtomicLong shieldBlocks = new AtomicLong();
	private final AtomicLong shieldCountersScheduled = new AtomicLong();
	private final AtomicLong shieldStrikeWindows = new AtomicLong();
	private final AtomicLong shieldAttacks = new AtomicLong();
	private final AtomicLong shieldCounterattacks = new AtomicLong();
	private final AtomicLong shieldDisables = new AtomicLong();
	private final AtomicLong shieldPathFailures = new AtomicLong();
	private final AtomicLong skeletonDisengageGoalsInstalled = new AtomicLong();
	private final AtomicLong skeletonDisengageGoalsRemoved = new AtomicLong();
	private final AtomicLong skeletonDisengageStarts = new AtomicLong();
	private final AtomicLong skeletonDisengagePathFailures = new AtomicLong();
	private final AtomicLong skeletonProjectileEvasionGoalsInstalled = new AtomicLong();
	private final AtomicLong skeletonProjectileEvasionGoalsRemoved = new AtomicLong();
	private final AtomicLong skeletonProjectileDodges = new AtomicLong();
	private final AtomicLong skeletonProjectileDodgePathFailures = new AtomicLong();
	private final AtomicLong projectileThreatQueries = new AtomicLong();
	private final AtomicLong projectileThreatCandidatesChecked = new AtomicLong();
	private final AtomicLong projectileThreatsDetected = new AtomicLong();
	private final AtomicLong projectileTrackingCapacityRejects = new AtomicLong();
	private final AtomicLong skeletonCoverGoalsInstalled = new AtomicLong();
	private final AtomicLong skeletonCoverGoalsRemoved = new AtomicLong();
	private final AtomicLong skeletonCoverSearches = new AtomicLong();
	private final AtomicLong skeletonCoverCandidatesChecked = new AtomicLong();
	private final AtomicLong skeletonCoverPlansFound = new AtomicLong();
	private final AtomicLong skeletonCoverCyclesStarted = new AtomicLong();
	private final AtomicLong skeletonCoverPeekShots = new AtomicLong();
	private final AtomicLong skeletonCoverReturnsCompleted = new AtomicLong();
	private final AtomicLong skeletonCoverPathFailures = new AtomicLong();
	private final AtomicLong skeletonCoverCyclesAborted = new AtomicLong();
	private final AtomicLong naturalSkeletonLoadoutInitializations = new AtomicLong();
	private final AtomicLong naturalCrossbowsEquipped = new AtomicLong();
	private final AtomicLong naturalFireworkCrossbowsEquipped = new AtomicLong();
	private final AtomicLong squadRangedGoalsInstalled = new AtomicLong();
	private final AtomicLong squadRangedGoalsRemoved = new AtomicLong();
	private final AtomicLong coordinatedShots = new AtomicLong();
	private final AtomicLong crossbowCharges = new AtomicLong();
	private final AtomicLong crossbowChargePoseTicks = new AtomicLong();
	private final AtomicLong crossbowShots = new AtomicLong();
	private final AtomicLong fireworkLaunches = new AtomicLong();
	private final AtomicLong fireworkDetonations = new AtomicLong();
	private final AtomicLong fireworkTimeouts = new AtomicLong();
	private final AtomicLong fireworkCapacityRejects = new AtomicLong();
	private final AtomicLong friendlyLaneBlocks = new AtomicLong();
	private final AtomicLong firingLaneRepositions = new AtomicLong();
	private final AtomicLong firingLanePathFailures = new AtomicLong();
	private final AtomicLong creeperGoalsInstalled = new AtomicLong();
	private final AtomicLong creeperGoalsRemoved = new AtomicLong();
	private final AtomicLong creeperFlanks = new AtomicLong();
	private final AtomicLong creeperIntercepts = new AtomicLong();
	private final AtomicLong creeperQueueWaits = new AtomicLong();
	private final AtomicLong creeperFuseStarts = new AtomicLong();
	private final AtomicLong creeperMovingFusePaths = new AtomicLong();
	private final AtomicLong creeperFuseAborts = new AtomicLong();
	private final AtomicLong creeperFeints = new AtomicLong();
	private final AtomicLong creeperFeintsCompleted = new AtomicLong();
	private final AtomicLong creeperShieldBaits = new AtomicLong();
	private final AtomicLong creeperFeintPathFailures = new AtomicLong();
	private final AtomicLong blastReservationsAcquired = new AtomicLong();
	private final AtomicLong blastReservationConflicts = new AtomicLong();
	private final AtomicLong blastReservationSaturations = new AtomicLong();
	private final AtomicLong blastReservationsReleased = new AtomicLong();
	private final AtomicLong spiderGoalsInstalled = new AtomicLong();
	private final AtomicLong spiderGoalsRemoved = new AtomicLong();
	private final AtomicLong spiderFlanks = new AtomicLong();
	private final AtomicLong spiderHitAndRuns = new AtomicLong();
	private final AtomicLong spiderPounces = new AtomicLong();
	private final AtomicLong spiderPounceWaits = new AtomicLong();
	private final AtomicLong spiderUnsafeLandingsRejected = new AtomicLong();
	private final AtomicLong spiderPounceReservationsAcquired = new AtomicLong();
	private final AtomicLong spiderPounceReservationConflicts = new AtomicLong();
	private final AtomicLong spiderPounceReservationsReleased = new AtomicLong();
	private final AtomicLong spiderWebTrapWindups = new AtomicLong();
	private final AtomicLong spiderWebTrapsPlaced = new AtomicLong();
	private final AtomicLong spiderWebTrapsRestored = new AtomicLong();
	private final AtomicLong spiderWebTrapPlacementRejects = new AtomicLong();
	private final AtomicLong spiderWebTrapProtectionRejects = new AtomicLong();
	private final AtomicLong spiderWebTrapOwnershipLosses = new AtomicLong();
	private final AtomicLong spiderBlastContainmentWebs = new AtomicLong();
	private final AtomicLong mountedBreachAssemblies = new AtomicLong();
	private final AtomicLong mountedBreachBoardingLeaps = new AtomicLong();
	private final AtomicLong mountedBreachMounts = new AtomicLong();
	private final AtomicLong mountedBreachPayloadReleases = new AtomicLong();
	private final AtomicLong mountedBreachPathFailures = new AtomicLong();
	private final AtomicLong mountedBreachAborts = new AtomicLong();

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

	public void weaponGoalInstalled() {
		this.weaponGoalsInstalled.incrementAndGet();
	}

	public void weaponGoalRemoved() {
		this.weaponGoalsRemoved.incrementAndGet();
	}

	public void weaponAttack() {
		this.weaponAttacks.incrementAndGet();
	}

	public void weaponSpacingMove() {
		this.weaponSpacingMoves.incrementAndGet();
	}

	public void weaponPathFailed() {
		this.weaponPathFailures.incrementAndGet();
	}

	public void axeWindupStarted() {
		this.axeWindups.incrementAndGet();
	}

	public void axeLeapStarted() {
		this.axeLeaps.incrementAndGet();
	}

	public void axeCriticalAttack() {
		this.axeCriticalAttacks.incrementAndGet();
	}

	public void axeLaunchAirborneRejected() {
		this.axeLaunchAirborneRejects.incrementAndGet();
	}

	public void axeLaunchWaterRejected() {
		this.axeLaunchWaterRejects.incrementAndGet();
	}

	public void axeLaunchSightRejected() {
		this.axeLaunchSightRejects.incrementAndGet();
	}

	public void axeLaunchBandRejected() {
		this.axeLaunchBandRejects.incrementAndGet();
	}

	public void axeLaunchCollisionRejected() {
		this.axeLaunchCollisionRejects.incrementAndGet();
	}

	public void shieldGoalInstalled() {
		this.shieldGoalsInstalled.incrementAndGet();
	}

	public void shieldGoalRemoved() {
		this.shieldGoalsRemoved.incrementAndGet();
	}

	public void shieldGuardStarted() {
		this.shieldGuards.incrementAndGet();
	}

	public void shieldBlock() {
		this.shieldBlocks.incrementAndGet();
	}

	public void shieldCounterScheduled() {
		this.shieldCountersScheduled.incrementAndGet();
	}

	public void shieldStrikeWindowOpened() {
		this.shieldStrikeWindows.incrementAndGet();
	}

	public void shieldAttack() {
		this.shieldAttacks.incrementAndGet();
	}

	public void shieldCounterattack() {
		this.shieldCounterattacks.incrementAndGet();
	}

	public void shieldDisabled() {
		this.shieldDisables.incrementAndGet();
	}

	public void shieldPathFailed() {
		this.shieldPathFailures.incrementAndGet();
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

	public void skeletonProjectileEvasionGoalInstalled() {
		this.skeletonProjectileEvasionGoalsInstalled.incrementAndGet();
	}

	public void skeletonProjectileEvasionGoalRemoved() {
		this.skeletonProjectileEvasionGoalsRemoved.incrementAndGet();
	}

	public void skeletonProjectileDodgeStarted() {
		this.skeletonProjectileDodges.incrementAndGet();
	}

	public void skeletonProjectileDodgePathFailed() {
		this.skeletonProjectileDodgePathFailures.incrementAndGet();
	}

	public void projectileThreatQuery() {
		this.projectileThreatQueries.incrementAndGet();
	}

	public void projectileThreatCandidatesChecked(final long count) {
		this.projectileThreatCandidatesChecked.addAndGet(Math.max(0L, count));
	}

	public void projectileThreatDetected() {
		this.projectileThreatsDetected.incrementAndGet();
	}

	public void projectileTrackingCapacityRejected() {
		this.projectileTrackingCapacityRejects.incrementAndGet();
	}

	public void skeletonCoverGoalInstalled() {
		this.skeletonCoverGoalsInstalled.incrementAndGet();
	}

	public void skeletonCoverGoalRemoved() {
		this.skeletonCoverGoalsRemoved.incrementAndGet();
	}

	public void skeletonCoverSearch() {
		this.skeletonCoverSearches.incrementAndGet();
	}

	public void skeletonCoverCandidatesChecked(final long count) {
		this.skeletonCoverCandidatesChecked.addAndGet(Math.max(0L, count));
	}

	public void skeletonCoverPlansFound(final long count) {
		this.skeletonCoverPlansFound.addAndGet(Math.max(0L, count));
	}

	public void skeletonCoverCycleStarted() {
		this.skeletonCoverCyclesStarted.incrementAndGet();
	}

	public void skeletonCoverPeekShot() {
		this.skeletonCoverPeekShots.incrementAndGet();
	}

	public void skeletonCoverReturnCompleted() {
		this.skeletonCoverReturnsCompleted.incrementAndGet();
	}

	public void skeletonCoverPathFailed() {
		this.skeletonCoverPathFailures.incrementAndGet();
	}

	public void skeletonCoverCycleAborted() {
		this.skeletonCoverCyclesAborted.incrementAndGet();
	}

	public CoverSnapshot coverSnapshot() {
		return new CoverSnapshot(
			this.skeletonCoverGoalsInstalled.get(),
			this.skeletonCoverGoalsRemoved.get(),
			this.skeletonCoverSearches.get(),
			this.skeletonCoverCandidatesChecked.get(),
			this.skeletonCoverPlansFound.get(),
			this.skeletonCoverCyclesStarted.get(),
			this.skeletonCoverPeekShots.get(),
			this.skeletonCoverReturnsCompleted.get(),
			this.skeletonCoverPathFailures.get(),
			this.skeletonCoverCyclesAborted.get()
		);
	}

	public void naturalSkeletonLoadoutInitialized() {
		this.naturalSkeletonLoadoutInitializations.incrementAndGet();
	}

	public void naturalCrossbowEquipped() {
		this.naturalCrossbowsEquipped.incrementAndGet();
	}

	public void naturalFireworkCrossbowEquipped() {
		this.naturalFireworkCrossbowsEquipped.incrementAndGet();
	}

	public void squadRangedGoalInstalled() {
		this.squadRangedGoalsInstalled.incrementAndGet();
	}

	public void squadRangedGoalRemoved() {
		this.squadRangedGoalsRemoved.incrementAndGet();
	}

	public void coordinatedShot() {
		this.coordinatedShots.incrementAndGet();
	}

	public void crossbowChargeStarted() {
		this.crossbowCharges.incrementAndGet();
	}

	public void crossbowChargePoseTick() {
		this.crossbowChargePoseTicks.incrementAndGet();
	}

	public void crossbowShot() {
		this.crossbowShots.incrementAndGet();
	}

	public void fireworkLaunched() {
		this.fireworkLaunches.incrementAndGet();
	}

	public void fireworkDetonated() {
		this.fireworkDetonations.incrementAndGet();
	}

	public void fireworkTimedOut() {
		this.fireworkTimeouts.incrementAndGet();
	}

	public void fireworkCapacityRejected() {
		this.fireworkCapacityRejects.incrementAndGet();
	}

	public void friendlyLaneBlocked() {
		this.friendlyLaneBlocks.incrementAndGet();
	}

	public void firingLaneReposition() {
		this.firingLaneRepositions.incrementAndGet();
	}

	public void firingLanePathFailed() {
		this.firingLanePathFailures.incrementAndGet();
	}

	public void creeperGoalInstalled() {
		this.creeperGoalsInstalled.incrementAndGet();
	}

	public void creeperGoalRemoved() {
		this.creeperGoalsRemoved.incrementAndGet();
	}

	public void creeperFlankStarted() {
		this.creeperFlanks.incrementAndGet();
	}

	public void creeperInterceptStarted() {
		this.creeperIntercepts.incrementAndGet();
	}

	public void creeperQueueWait() {
		this.creeperQueueWaits.incrementAndGet();
	}

	public void creeperFuseStarted() {
		this.creeperFuseStarts.incrementAndGet();
	}

	public void creeperMovingFusePath() {
		this.creeperMovingFusePaths.incrementAndGet();
	}

	public void creeperFuseAborted() {
		this.creeperFuseAborts.incrementAndGet();
	}

	public void creeperFeintStarted(final boolean targetWasBlocking) {
		this.creeperFeints.incrementAndGet();
		if (targetWasBlocking) {
			this.creeperShieldBaits.incrementAndGet();
		}
	}

	public void creeperFeintCompleted() {
		this.creeperFeintsCompleted.incrementAndGet();
	}

	public void creeperFeintPathFailed() {
		this.creeperFeintPathFailures.incrementAndGet();
	}

	public void blastReservationAcquired() {
		this.blastReservationsAcquired.incrementAndGet();
	}

	public void blastReservationConflict() {
		this.blastReservationConflicts.incrementAndGet();
	}

	public void blastReservationSaturated() {
		this.blastReservationSaturations.incrementAndGet();
	}

	public void blastReservationReleased() {
		this.blastReservationsReleased.incrementAndGet();
	}

	public void spiderGoalInstalled() {
		this.spiderGoalsInstalled.incrementAndGet();
	}

	public void spiderGoalRemoved() {
		this.spiderGoalsRemoved.incrementAndGet();
	}

	public void spiderFlankStarted() {
		this.spiderFlanks.incrementAndGet();
	}

	public void spiderHitAndRunStarted() {
		this.spiderHitAndRuns.incrementAndGet();
	}

	public void spiderPounceStarted() {
		this.spiderPounces.incrementAndGet();
	}

	public void spiderPounceWait() {
		this.spiderPounceWaits.incrementAndGet();
	}

	public void spiderUnsafeLandingRejected() {
		this.spiderUnsafeLandingsRejected.incrementAndGet();
	}

	public void spiderPounceReservationAcquired() {
		this.spiderPounceReservationsAcquired.incrementAndGet();
	}

	public void spiderPounceReservationConflict() {
		this.spiderPounceReservationConflicts.incrementAndGet();
	}

	public void spiderPounceReservationReleased() {
		this.spiderPounceReservationsReleased.incrementAndGet();
	}

	public void spiderWebTrapWindup() {
		this.spiderWebTrapWindups.incrementAndGet();
	}

	public void spiderWebTrapPlaced() {
		this.spiderWebTrapsPlaced.incrementAndGet();
	}

	public void spiderWebTrapRestored() {
		this.spiderWebTrapsRestored.incrementAndGet();
	}

	public void spiderWebTrapPlacementRejected() {
		this.spiderWebTrapPlacementRejects.incrementAndGet();
	}

	public void spiderWebTrapProtectionRejected() {
		this.spiderWebTrapProtectionRejects.incrementAndGet();
	}

	public void spiderWebTrapOwnershipLost() {
		this.spiderWebTrapOwnershipLosses.incrementAndGet();
	}

	public void spiderBlastContainmentWeb() {
		this.spiderBlastContainmentWebs.incrementAndGet();
	}

	public WebTrapSnapshot webTrapSnapshot() {
		return new WebTrapSnapshot(
			this.spiderWebTrapWindups.get(),
			this.spiderWebTrapsPlaced.get(),
			this.spiderWebTrapsRestored.get(),
			this.spiderWebTrapPlacementRejects.get(),
			this.spiderWebTrapProtectionRejects.get(),
			this.spiderWebTrapOwnershipLosses.get(),
			this.spiderBlastContainmentWebs.get()
		);
	}

	public void mountedBreachAssemblyStarted() {
		this.mountedBreachAssemblies.incrementAndGet();
	}

	public void mountedBreachBoardingLeap() {
		this.mountedBreachBoardingLeaps.incrementAndGet();
	}

	public void mountedBreachMounted() {
		this.mountedBreachMounts.incrementAndGet();
	}

	public void mountedBreachPayloadReleased() {
		this.mountedBreachPayloadReleases.incrementAndGet();
	}

	public void mountedBreachPathFailed() {
		this.mountedBreachPathFailures.incrementAndGet();
	}

	public void mountedBreachAborted() {
		this.mountedBreachAborts.incrementAndGet();
	}

	public Snapshot snapshot() {
		return new Snapshot(
			this.intelligenceAssignments.get(),
			this.retreatGoalsInstalled.get(),
			this.retreatGoalsRemoved.get(),
			this.retreatStarts.get(),
			this.retreatPathFailures.get(),
			this.weaponGoalsInstalled.get(),
			this.weaponGoalsRemoved.get(),
			this.weaponAttacks.get(),
			this.weaponSpacingMoves.get(),
			this.weaponPathFailures.get(),
			this.axeWindups.get(),
			this.axeLeaps.get(),
			this.axeCriticalAttacks.get(),
			this.axeLaunchAirborneRejects.get(),
			this.axeLaunchWaterRejects.get(),
			this.axeLaunchSightRejects.get(),
			this.axeLaunchBandRejects.get(),
			this.axeLaunchCollisionRejects.get(),
			this.shieldGoalsInstalled.get(),
			this.shieldGoalsRemoved.get(),
			this.shieldGuards.get(),
			this.shieldBlocks.get(),
			this.shieldCountersScheduled.get(),
			this.shieldStrikeWindows.get(),
			this.shieldAttacks.get(),
			this.shieldCounterattacks.get(),
			this.shieldDisables.get(),
			this.shieldPathFailures.get(),
			this.skeletonDisengageGoalsInstalled.get(),
			this.skeletonDisengageGoalsRemoved.get(),
			this.skeletonDisengageStarts.get(),
			this.skeletonDisengagePathFailures.get(),
			this.skeletonProjectileEvasionGoalsInstalled.get(),
			this.skeletonProjectileEvasionGoalsRemoved.get(),
			this.skeletonProjectileDodges.get(),
			this.skeletonProjectileDodgePathFailures.get(),
			this.projectileThreatQueries.get(),
			this.projectileThreatCandidatesChecked.get(),
			this.projectileThreatsDetected.get(),
			this.projectileTrackingCapacityRejects.get(),
			this.naturalSkeletonLoadoutInitializations.get(),
			this.naturalCrossbowsEquipped.get(),
			this.naturalFireworkCrossbowsEquipped.get(),
			this.squadRangedGoalsInstalled.get(),
			this.squadRangedGoalsRemoved.get(),
			this.coordinatedShots.get(),
			this.crossbowCharges.get(),
			this.crossbowChargePoseTicks.get(),
			this.crossbowShots.get(),
			this.fireworkLaunches.get(),
			this.fireworkDetonations.get(),
			this.fireworkTimeouts.get(),
			this.fireworkCapacityRejects.get(),
			this.friendlyLaneBlocks.get(),
			this.firingLaneRepositions.get(),
			this.firingLanePathFailures.get(),
			this.creeperGoalsInstalled.get(),
			this.creeperGoalsRemoved.get(),
			this.creeperFlanks.get(),
			this.creeperIntercepts.get(),
			this.creeperQueueWaits.get(),
			this.creeperFuseStarts.get(),
			this.creeperMovingFusePaths.get(),
			this.creeperFuseAborts.get(),
			this.creeperFeints.get(),
			this.creeperFeintsCompleted.get(),
			this.creeperShieldBaits.get(),
			this.creeperFeintPathFailures.get(),
			this.blastReservationsAcquired.get(),
			this.blastReservationConflicts.get(),
			this.blastReservationSaturations.get(),
			this.blastReservationsReleased.get(),
			this.spiderGoalsInstalled.get(),
			this.spiderGoalsRemoved.get(),
			this.spiderFlanks.get(),
			this.spiderHitAndRuns.get(),
			this.spiderPounces.get(),
			this.spiderPounceWaits.get(),
			this.spiderUnsafeLandingsRejected.get(),
			this.spiderPounceReservationsAcquired.get(),
			this.spiderPounceReservationConflicts.get(),
			this.spiderPounceReservationsReleased.get(),
			this.mountedBreachAssemblies.get(),
			this.mountedBreachBoardingLeaps.get(),
			this.mountedBreachMounts.get(),
			this.mountedBreachPayloadReleases.get(),
			this.mountedBreachPathFailures.get(),
			this.mountedBreachAborts.get()
		);
	}

	public record Snapshot(
		long intelligenceAssignments,
		long retreatGoalsInstalled,
		long retreatGoalsRemoved,
		long retreatStarts,
		long retreatPathFailures,
		long weaponGoalsInstalled,
		long weaponGoalsRemoved,
		long weaponAttacks,
		long weaponSpacingMoves,
		long weaponPathFailures,
		long axeWindups,
		long axeLeaps,
		long axeCriticalAttacks,
		long axeLaunchAirborneRejects,
		long axeLaunchWaterRejects,
		long axeLaunchSightRejects,
		long axeLaunchBandRejects,
		long axeLaunchCollisionRejects,
		long shieldGoalsInstalled,
		long shieldGoalsRemoved,
		long shieldGuards,
		long shieldBlocks,
		long shieldCountersScheduled,
		long shieldStrikeWindows,
		long shieldAttacks,
		long shieldCounterattacks,
		long shieldDisables,
		long shieldPathFailures,
		long skeletonDisengageGoalsInstalled,
		long skeletonDisengageGoalsRemoved,
		long skeletonDisengageStarts,
		long skeletonDisengagePathFailures,
		long skeletonProjectileEvasionGoalsInstalled,
		long skeletonProjectileEvasionGoalsRemoved,
		long skeletonProjectileDodges,
		long skeletonProjectileDodgePathFailures,
		long projectileThreatQueries,
		long projectileThreatCandidatesChecked,
		long projectileThreatsDetected,
		long projectileTrackingCapacityRejects,
		long naturalSkeletonLoadoutInitializations,
		long naturalCrossbowsEquipped,
		long naturalFireworkCrossbowsEquipped,
		long squadRangedGoalsInstalled,
		long squadRangedGoalsRemoved,
		long coordinatedShots,
		long crossbowCharges,
		long crossbowChargePoseTicks,
		long crossbowShots,
		long fireworkLaunches,
		long fireworkDetonations,
		long fireworkTimeouts,
		long fireworkCapacityRejects,
		long friendlyLaneBlocks,
		long firingLaneRepositions,
		long firingLanePathFailures,
		long creeperGoalsInstalled,
		long creeperGoalsRemoved,
		long creeperFlanks,
		long creeperIntercepts,
		long creeperQueueWaits,
		long creeperFuseStarts,
		long creeperMovingFusePaths,
		long creeperFuseAborts,
		long creeperFeints,
		long creeperFeintsCompleted,
		long creeperShieldBaits,
		long creeperFeintPathFailures,
		long blastReservationsAcquired,
		long blastReservationConflicts,
		long blastReservationSaturations,
		long blastReservationsReleased,
		long spiderGoalsInstalled,
		long spiderGoalsRemoved,
		long spiderFlanks,
		long spiderHitAndRuns,
		long spiderPounces,
		long spiderPounceWaits,
		long spiderUnsafeLandingsRejected,
		long spiderPounceReservationsAcquired,
		long spiderPounceReservationConflicts,
		long spiderPounceReservationsReleased,
		long mountedBreachAssemblies,
		long mountedBreachBoardingLeaps,
		long mountedBreachMounts,
		long mountedBreachPayloadReleases,
		long mountedBreachPathFailures,
		long mountedBreachAborts
	) {
	}

	public record CoverSnapshot(
		long goalsInstalled,
		long goalsRemoved,
		long searches,
		long candidatesChecked,
		long plansFound,
		long cyclesStarted,
		long peekShots,
		long returnsCompleted,
		long pathFailures,
		long cyclesAborted
	) {
	}

	public record WebTrapSnapshot(
		long windups,
		long placed,
		long restored,
		long placementRejects,
		long protectionRejects,
		long ownershipLosses,
		long blastContainmentWebs
	) {
	}
}

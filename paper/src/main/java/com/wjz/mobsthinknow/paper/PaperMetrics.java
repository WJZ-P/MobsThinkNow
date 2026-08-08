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
}

package com.wjz.mobsthinknow.ai.skeleton;

import java.util.concurrent.atomic.AtomicLong;

/** 服务端诊断计数器；只记录真实状态切换，不参与任何战斗决策。 */
public final class SmartSkeletonMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong INSTALLED_EMERGENCY_GOALS = new AtomicLong();
	private static final AtomicLong EMERGENCY_DISENGAGES = new AtomicLong();
	private static final AtomicLong COVER_PLANS = new AtomicLong();
	private static final AtomicLong COVER_SHOTS = new AtomicLong();
	private static final AtomicLong KITES = new AtomicLong();
	private static final AtomicLong PROJECTILE_DODGES = new AtomicLong();
	private static final AtomicLong SHOTS = new AtomicLong();
	private static final AtomicLong PREDICTIVE_SHOTS = new AtomicLong();
	private static final AtomicLong CROSSBOW_SHOTS = new AtomicLong();
	private static final AtomicLong FIREWORK_CROSSBOW_SHOTS = new AtomicLong();
	private static final AtomicLong FRIENDLY_SHOTS_HELD = new AtomicLong();
	private static final AtomicLong EXPLOSIVE_SHOTS_HELD = new AtomicLong();
	private static final AtomicLong FIRING_LANE_REPLANS = new AtomicLong();

	private SmartSkeletonMetrics() {
	}

	public static void goalInstalled() {
		INSTALLED_GOALS.incrementAndGet();
	}

	public static void emergencyGoalInstalled() {
		INSTALLED_EMERGENCY_GOALS.incrementAndGet();
	}

	public static void emergencyDisengageStarted() {
		EMERGENCY_DISENGAGES.incrementAndGet();
	}

	public static void coverPlanStarted() {
		COVER_PLANS.incrementAndGet();
	}

	public static void coverShot() {
		COVER_SHOTS.incrementAndGet();
	}

	public static void kiteStarted() {
		KITES.incrementAndGet();
	}

	public static void projectileDodgeStarted() {
		PROJECTILE_DODGES.incrementAndGet();
	}

	public static void shot() {
		SHOTS.incrementAndGet();
	}

	public static void predictiveShot() {
		PREDICTIVE_SHOTS.incrementAndGet();
	}

	public static void crossbowShot(final boolean firework) {
		CROSSBOW_SHOTS.incrementAndGet();
		if (firework) {
			FIREWORK_CROSSBOW_SHOTS.incrementAndGet();
		}
	}

	public static void friendlyShotHeld(final boolean explosive) {
		FRIENDLY_SHOTS_HELD.incrementAndGet();
		if (explosive) {
			EXPLOSIVE_SHOTS_HELD.incrementAndGet();
		}
	}

	public static void firingLaneReplan() {
		FIRING_LANE_REPLANS.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			INSTALLED_EMERGENCY_GOALS.get(),
			EMERGENCY_DISENGAGES.get(),
			COVER_PLANS.get(),
			COVER_SHOTS.get(),
			KITES.get(),
			PROJECTILE_DODGES.get(),
			SHOTS.get(),
			PREDICTIVE_SHOTS.get(),
			CROSSBOW_SHOTS.get(),
			FIREWORK_CROSSBOW_SHOTS.get(),
			FRIENDLY_SHOTS_HELD.get(),
			EXPLOSIVE_SHOTS_HELD.get(),
			FIRING_LANE_REPLANS.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long installedEmergencyGoals,
		long emergencyDisengages,
		long coverPlans,
		long coverShots,
		long kites,
		long projectileDodges,
		long shots,
		long predictiveShots,
		long crossbowShots,
		long fireworkCrossbowShots,
		long friendlyShotsHeld,
		long explosiveShotsHeld,
		long firingLaneReplans
	) {
	}
}

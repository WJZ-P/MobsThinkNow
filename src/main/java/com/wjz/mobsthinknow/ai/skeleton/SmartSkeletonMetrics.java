package com.wjz.mobsthinknow.ai.skeleton;

import java.util.concurrent.atomic.AtomicLong;

/** 服务端诊断计数器；只记录真实状态切换，不参与任何战斗决策。 */
public final class SmartSkeletonMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong INSTALLED_EMERGENCY_GOALS = new AtomicLong();
	private static final AtomicLong EMERGENCY_DISENGAGES = new AtomicLong();
	private static final AtomicLong RETREATS = new AtomicLong();
	private static final AtomicLong PROJECTILE_DODGES = new AtomicLong();
	private static final AtomicLong SHOTS = new AtomicLong();
	private static final AtomicLong PREDICTIVE_SHOTS = new AtomicLong();

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

	public static void retreatStarted() {
		RETREATS.incrementAndGet();
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

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			INSTALLED_EMERGENCY_GOALS.get(),
			EMERGENCY_DISENGAGES.get(),
			RETREATS.get(),
			PROJECTILE_DODGES.get(),
			SHOTS.get(),
			PREDICTIVE_SHOTS.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long installedEmergencyGoals,
		long emergencyDisengages,
		long retreats,
		long projectileDodges,
		long shots,
		long predictiveShots
	) {
	}
}

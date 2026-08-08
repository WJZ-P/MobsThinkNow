package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;

/** 平台无关的受击退避判定和固定上界候选生成器。 */
public final class RetreatPlanner {
	private static final double LATERAL_OFFSET = 2.25;

	private RetreatPlanner() {
	}

	public static Trigger trigger(
		final double health,
		final double maximumHealth,
		final double largestRecentDamage,
		final double lowHealthThreshold,
		final double heavyHitThreshold
	) {
		if (!Double.isFinite(maximumHealth) || maximumHealth <= 0.0) {
			return Trigger.NONE;
		}
		double healthFraction = Math.max(0.0, health) / maximumHealth;
		double damageFraction = Math.max(0.0, largestRecentDamage) / maximumHealth;
		boolean lowHealth = healthFraction <= Math.clamp(lowHealthThreshold, 0.0, 1.0);
		boolean heavyHit = damageFraction >= Math.clamp(heavyHitThreshold, 0.0, 1.0);
		if (lowHealth && heavyHit) {
			return Trigger.LOW_HEALTH_AND_HEAVY_HIT;
		}
		if (lowHealth) {
			return Trigger.LOW_HEALTH;
		}
		return heavyHit ? Trigger.HEAVY_HIT : Trigger.NONE;
	}

	public static boolean shouldContinue(
		final long elapsedTicks,
		final int maximumTicks,
		final double distanceSquaredToThreat,
		final double safeDistance
	) {
		if (elapsedTicks < 0L || elapsedTicks >= Math.max(1, maximumTicks)) {
			return false;
		}
		double boundedSafeDistance = Math.max(0.0, safeDistance);
		return distanceSquaredToThreat < boundedSafeDistance * boundedSafeDistance;
	}

	/**
	 * 返回固定五个、全部位于攻击者背向半平面的候选。适配器按顺序调用自己的寻路器，首个可达点即为
	 * 结果；因此每次重算最多五次寻路，不随周围实体或方块数量增长。
	 */
	public static List<Vec3d> candidateDestinations(
		final Vec3d actor,
		final Vec3d threat,
		final double minimumDistance,
		final double maximumDistance,
		final double distanceSample,
		final int stableSide
	) {
		double minimum = Math.max(1.0, Math.min(minimumDistance, maximumDistance));
		double maximum = Math.max(minimum, Math.max(minimumDistance, maximumDistance));
		double sample = Double.isFinite(distanceSample) ? Math.clamp(distanceSample, 0.0, 1.0) : 0.5;
		double selectedDistance = minimum + (maximum - minimum) * sample;
		Vec3d fallback = stableSide < 0 ? new Vec3d(-1.0, 0.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
		Vec3d away = actor.subtract(threat).horizontalUnitOr(fallback);
		Vec3d side = new Vec3d(-away.z(), 0.0, away.x()).scale(stableSide < 0 ? -1.0 : 1.0);
		Vec3d center = actor.add(away.scale(selectedDistance));
		return List.of(
			center,
			center.add(side.scale(LATERAL_OFFSET)),
			center.add(side.scale(-LATERAL_OFFSET)),
			actor.add(away.scale(minimum)),
			actor.add(away.scale(maximum))
		);
	}

	public enum Trigger {
		NONE,
		LOW_HEALTH,
		HEAVY_HIT,
		LOW_HEALTH_AND_HEAVY_HIT;

		public boolean active() {
			return this != NONE;
		}

		public boolean includesHeavyHit() {
			return this == HEAVY_HIT || this == LOW_HEALTH_AND_HEAVY_HIT;
		}
	}
}

package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/** 苦力怕跨平台的观察、拦截、侧翼、引信距离和追踪速度决策。 */
public final class CreeperTacticalPlanner {
	private static final double VANILLA_FUSE_DISTANCE = 3.0;
	private static final double WATCHING_DOT_THRESHOLD = 0.35;

	private CreeperTacticalPlanner() {
	}

	public static boolean isTargetWatching(final Vec3d targetLook, final Vec3d targetToCreeper) {
		Vec3d look = targetLook.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d towardCreeper = targetToCreeper.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		return look.x() * towardCreeper.x() + look.z() * towardCreeper.z() >= WATCHING_DOT_THRESHOLD;
	}

	public static ApproachMode chooseApproach(
		final int intelligence,
		final boolean targetWatching,
		final boolean targetBlocking,
		final boolean hasLineOfSight,
		final double distanceSquared,
		final boolean flankingEnabled,
		final int stableSide
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		if (flankingEnabled
			&& iq >= 6
			&& hasLineOfSight
			&& distanceSquared >= 16.0
			&& distanceSquared <= 400.0
			&& (targetWatching || targetBlocking)) {
			return stableSide < 0 ? ApproachMode.FLANK_LEFT : ApproachMode.FLANK_RIGHT;
		}
		return iq >= 4 ? ApproachMode.INTERCEPT : ApproachMode.DIRECT;
	}

	public static Vec3d approachDestination(
		final ApproachMode mode,
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final Vec3d targetLook,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		Vec3d prediction = cappedHorizontal(targetVelocity.scale(3.0 + iq * 0.6), 3.5);
		Vec3d predictedTarget = targetPosition.add(prediction);
		if (mode == ApproachMode.DIRECT) {
			return targetPosition;
		}
		if (mode == ApproachMode.INTERCEPT) {
			return predictedTarget;
		}
		Vec3d facing = targetLook.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d lateral = new Vec3d(-facing.z(), 0.0, facing.x());
		double side = mode == ApproachMode.FLANK_LEFT ? -1.0 : 1.0;
		return predictedTarget
			.add(facing.scale(-(2.0 + iq * 0.08)))
			.add(lateral.scale(side * (2.2 + iq * 0.06)));
	}

	public static double approachSpeed(final int intelligence, final DifficultyTier difficulty) {
		double difficultyBonus = switch (difficulty) {
			case PEACEFUL, EASY -> 0.0;
			case NORMAL -> 0.03;
			case HARD -> 0.06;
		};
		return Math.min(1.22, 1.0 + IntelligenceDistribution.clamp(intelligence) * 0.012 + difficultyBonus);
	}

	public static double fuseStartDistance(
		final double configuredMaximum,
		final int intelligence,
		final boolean powered,
		final DifficultyTier difficulty
	) {
		double maximum = Math.clamp(configuredMaximum, VANILLA_FUSE_DISTANCE, 5.0);
		double skill = (IntelligenceDistribution.clamp(intelligence) - 1) / 9.0;
		double difficultyBoost = switch (difficulty) {
			case PEACEFUL, EASY -> 0.0;
			case NORMAL -> 0.08;
			case HARD -> 0.16;
		};
		double fraction = Math.clamp(skill * 0.82 + difficultyBoost, 0.0, 1.0);
		return VANILLA_FUSE_DISTANCE
			+ (maximum - VANILLA_FUSE_DISTANCE) * fraction
			+ (powered ? 0.5 : 0.0);
	}

	public static double movingFuseSpeed(
		final double configuredMaximum,
		final int intelligence,
		final DifficultyTier difficulty
	) {
		double maximum = Math.clamp(configuredMaximum, 1.0, 1.5);
		double difficultyBoost = switch (difficulty) {
			case PEACEFUL, EASY -> 0.0;
			case NORMAL -> 0.08;
			case HARD -> 0.16;
		};
		double fraction = Math.clamp(
			0.35 + IntelligenceDistribution.clamp(intelligence) * 0.055 + difficultyBoost,
			0.0,
			1.0
		);
		return 1.0 + (maximum - 1.0) * fraction;
	}

	public static boolean shouldStartFuse(
		final double distanceSquared,
		final double startDistance,
		final boolean hasLineOfSight,
		final boolean breachableBarrier,
		final boolean targetWatching,
		final boolean targetBlocking,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		if (!hasLineOfSight) {
			double breachDistance = Math.min(5.0, startDistance + 0.75);
			return iq >= 8 && breachableBarrier && distanceSquared <= breachDistance * breachDistance;
		}
		double effectiveDistance = iq >= 7 && targetWatching && targetBlocking
			? Math.max(3.05, startDistance - 0.45)
			: startDistance;
		return distanceSquared <= effectiveDistance * effectiveDistance;
	}

	public static boolean shouldContinueFuse(
		final double distanceSquared,
		final double startDistance,
		final boolean hasLineOfSight,
		final boolean breachableBarrier,
		final double fuseProgress,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		double abortDistance = startDistance + 3.0 + iq * 0.08;
		if (!Double.isFinite(distanceSquared) || distanceSquared > abortDistance * abortDistance) {
			return false;
		}
		if (hasLineOfSight) {
			return true;
		}
		double breachCommitDistance = Math.min(5.5, startDistance + 1.5);
		if (iq >= 8 && breachableBarrier && distanceSquared <= breachCommitDistance * breachCommitDistance) {
			return true;
		}
		double lateCommitDistance = startDistance + 2.0;
		return iq >= 6 && fuseProgress >= 0.62 && distanceSquared <= lateCommitDistance * lateCommitDistance;
	}

	public static Vec3d fuseDestination(
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final double fuseProgress,
		final int intelligence
	) {
		double remainingLeadTicks = Math.max(
			2.0,
			(1.0 - Math.clamp(fuseProgress, 0.0, 1.0)) * (5.0 + IntelligenceDistribution.clamp(intelligence) * 0.5)
		);
		return targetPosition.add(cappedHorizontal(targetVelocity.scale(remainingLeadTicks), 3.0));
	}

	public static int repathTicks(final int intelligence) {
		return Math.max(4, 11 - IntelligenceDistribution.clamp(intelligence) / 2);
	}

	private static Vec3d cappedHorizontal(final Vec3d value, final double maximumLength) {
		Vec3d horizontal = value.horizontal();
		double lengthSquared = horizontal.horizontalLengthSquared();
		return lengthSquared <= maximumLength * maximumLength
			? horizontal
			: horizontal.scale(maximumLength / Math.sqrt(lengthSquared));
	}

	public enum ApproachMode {
		DIRECT,
		INTERCEPT,
		FLANK_LEFT,
		FLANK_RIGHT;

		public boolean isFlanking() {
			return this == FLANK_LEFT || this == FLANK_RIGHT;
		}
	}
}

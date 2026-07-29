package com.wjz.mobsthinknow.ai.creeper;

import net.minecraft.world.phys.Vec3;

/** 苦力怕战术中的纯数学决策；实体查询和导航始终留在服务器主线程。 */
public final class CreeperCombatMath {
	private static final double VANILLA_FUSE_DISTANCE = 3.0;
	private static final double WATCHING_DOT_THRESHOLD = 0.35;

	private CreeperCombatMath() {
	}

	public static boolean isTargetWatching(final Vec3 targetLook, final Vec3 targetToCreeper) {
		Vec3 look = horizontalUnit(targetLook, new Vec3(0.0, 0.0, 1.0));
		Vec3 towardCreeper = horizontalUnit(targetToCreeper, new Vec3(0.0, 0.0, 1.0));
		return look.dot(towardCreeper) >= WATCHING_DOT_THRESHOLD;
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
		int iq = CreeperIntelligence.clamp(intelligence);
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

	public static Vec3 approachDestination(
		final ApproachMode mode,
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final Vec3 targetLook,
		final int intelligence
	) {
		int iq = CreeperIntelligence.clamp(intelligence);
		Vec3 prediction = cappedHorizontal(targetVelocity.scale(3.0 + iq * 0.6), 3.5);
		Vec3 predictedTarget = targetPosition.add(prediction);
		if (mode == ApproachMode.DIRECT) {
			return targetPosition;
		}
		if (mode == ApproachMode.INTERCEPT) {
			return predictedTarget;
		}

		Vec3 facing = horizontalUnit(targetLook, new Vec3(0.0, 0.0, 1.0));
		Vec3 lateral = new Vec3(-facing.z, 0.0, facing.x);
		double side = mode == ApproachMode.FLANK_LEFT ? -1.0 : 1.0;
		return predictedTarget
			.add(facing.scale(-(2.0 + iq * 0.08)))
			.add(lateral.scale(side * (2.2 + iq * 0.06)));
	}

	public static double approachSpeed(final int intelligence, final int difficultyId) {
		double difficultyBonus = switch (Math.clamp(difficultyId, 0, 3)) {
			case 0, 1 -> 0.0;
			case 2 -> 0.03;
			default -> 0.06;
		};
		return Math.min(1.22, 1.0 + CreeperIntelligence.clamp(intelligence) * 0.012 + difficultyBonus);
	}

	public static double fuseStartDistance(
		final double configuredMaximum,
		final int intelligence,
		final boolean powered,
		final int difficultyId
	) {
		double maximum = clamp(configuredMaximum, VANILLA_FUSE_DISTANCE, 5.0);
		double skill = (CreeperIntelligence.clamp(intelligence) - 1) / 9.0;
		double difficultyBoost = switch (Math.clamp(difficultyId, 0, 3)) {
			case 0, 1 -> 0.0;
			case 2 -> 0.08;
			default -> 0.16;
		};
		double fraction = clamp(skill * 0.82 + difficultyBoost, 0.0, 1.0);
		return VANILLA_FUSE_DISTANCE
			+ (maximum - VANILLA_FUSE_DISTANCE) * fraction
			+ (powered ? 0.5 : 0.0);
	}

	/** 配置值是绝对上限；智力和难度只决定个体能逼近这个上限多少。 */
	public static double movingFuseSpeed(
		final double configuredMaximum,
		final int intelligence,
		final int difficultyId
	) {
		double maximum = clamp(configuredMaximum, 1.0, 1.5);
		double difficultyBoost = switch (Math.clamp(difficultyId, 0, 3)) {
			case 0, 1 -> 0.0;
			case 2 -> 0.08;
			default -> 0.16;
		};
		double fraction = clamp(0.35 + CreeperIntelligence.clamp(intelligence) * 0.055 + difficultyBoost, 0.0, 1.0);
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
		int iq = CreeperIntelligence.clamp(intelligence);
		if (!hasLineOfSight) {
			double breachDistance = Math.min(5.0, startDistance + 0.75);
			return iq >= 8 && breachableBarrier && distanceSquared <= breachDistance * breachDistance;
		}

		// 高智力苦力怕面对正举盾观察自己的目标时先多走半格再鸣响，给绕后 Goal 留出空间。
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
		final float fuseProgress,
		final int intelligence
	) {
		int iq = CreeperIntelligence.clamp(intelligence);
		double abortDistance = startDistance + 3.0 + iq * 0.08;
		if (distanceSquared > abortDistance * abortDistance) {
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
		return iq >= 6 && fuseProgress >= 0.62F && distanceSquared <= lateCommitDistance * lateCommitDistance;
	}

	public static Vec3 fuseDestination(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final float fuseProgress,
		final int intelligence
	) {
		double remainingLeadTicks = Math.max(2.0, (1.0 - clamp(fuseProgress, 0.0, 1.0)) * (5.0 + intelligence * 0.5));
		return targetPosition.add(cappedHorizontal(targetVelocity.scale(remainingLeadTicks), 3.0));
	}

	public static int repathTicks(final int intelligence) {
		return Math.max(4, 11 - CreeperIntelligence.clamp(intelligence) / 2);
	}

	private static Vec3 cappedHorizontal(final Vec3 value, final double maximumLength) {
		Vec3 horizontal = new Vec3(value.x, 0.0, value.z);
		double lengthSquared = horizontal.horizontalDistanceSqr();
		if (lengthSquared <= maximumLength * maximumLength) {
			return horizontal;
		}
		return horizontal.normalize().scale(maximumLength);
	}

	private static Vec3 horizontalUnit(final Vec3 value, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(value.x, 0.0, value.z);
		return horizontal.horizontalDistanceSqr() < 1.0E-6 ? fallback : horizontal.normalize();
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
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

package com.wjz.mobsthinknow.ai.creeper;

import com.wjz.mobsthinknow.shared.ai.CreeperTacticalPlanner;
import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import net.minecraft.world.phys.Vec3;

/** Fabric 的 Minecraft 向量适配层；实际苦力怕数学决策由 shared 与 Paper 共用。 */
public final class CreeperCombatMath {
	private CreeperCombatMath() {
	}

	public static boolean isTargetWatching(final Vec3 targetLook, final Vec3 targetToCreeper) {
		return CreeperTacticalPlanner.isTargetWatching(toShared(targetLook), toShared(targetToCreeper));
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
		return fromShared(CreeperTacticalPlanner.chooseApproach(
			intelligence,
			targetWatching,
			targetBlocking,
			hasLineOfSight,
			distanceSquared,
			flankingEnabled,
			stableSide
		));
	}

	public static Vec3 approachDestination(
		final ApproachMode mode,
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final Vec3 targetLook,
		final int intelligence
	) {
		return toMinecraft(CreeperTacticalPlanner.approachDestination(
			toShared(mode),
			toShared(targetPosition),
			toShared(targetVelocity),
			toShared(targetLook),
			intelligence
		));
	}

	public static double approachSpeed(final int intelligence, final int difficultyId) {
		return CreeperTacticalPlanner.approachSpeed(intelligence, DifficultyTier.fromNumericId(difficultyId));
	}

	public static double fuseStartDistance(
		final double configuredMaximum,
		final int intelligence,
		final boolean powered,
		final int difficultyId
	) {
		return CreeperTacticalPlanner.fuseStartDistance(
			configuredMaximum,
			intelligence,
			powered,
			DifficultyTier.fromNumericId(difficultyId)
		);
	}

	public static double movingFuseSpeed(
		final double configuredMaximum,
		final int intelligence,
		final int difficultyId
	) {
		return CreeperTacticalPlanner.movingFuseSpeed(
			configuredMaximum,
			intelligence,
			DifficultyTier.fromNumericId(difficultyId)
		);
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
		return CreeperTacticalPlanner.shouldStartFuse(
			distanceSquared,
			startDistance,
			hasLineOfSight,
			breachableBarrier,
			targetWatching,
			targetBlocking,
			intelligence
		);
	}

	public static boolean shouldContinueFuse(
		final double distanceSquared,
		final double startDistance,
		final boolean hasLineOfSight,
		final boolean breachableBarrier,
		final float fuseProgress,
		final int intelligence
	) {
		return CreeperTacticalPlanner.shouldContinueFuse(
			distanceSquared,
			startDistance,
			hasLineOfSight,
			breachableBarrier,
			fuseProgress,
			intelligence
		);
	}

	public static Vec3 fuseDestination(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final float fuseProgress,
		final int intelligence
	) {
		return toMinecraft(CreeperTacticalPlanner.fuseDestination(
			toShared(targetPosition),
			toShared(targetVelocity),
			fuseProgress,
			intelligence
		));
	}

	public static int repathTicks(final int intelligence) {
		return CreeperTacticalPlanner.repathTicks(intelligence);
	}

	private static Vec3d toShared(final Vec3 vector) {
		return new Vec3d(vector.x, vector.y, vector.z);
	}

	private static Vec3 toMinecraft(final Vec3d vector) {
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	private static ApproachMode fromShared(final CreeperTacticalPlanner.ApproachMode mode) {
		return switch (mode) {
			case DIRECT -> ApproachMode.DIRECT;
			case INTERCEPT -> ApproachMode.INTERCEPT;
			case FLANK_LEFT -> ApproachMode.FLANK_LEFT;
			case FLANK_RIGHT -> ApproachMode.FLANK_RIGHT;
		};
	}

	private static CreeperTacticalPlanner.ApproachMode toShared(final ApproachMode mode) {
		return switch (mode) {
			case DIRECT -> CreeperTacticalPlanner.ApproachMode.DIRECT;
			case INTERCEPT -> CreeperTacticalPlanner.ApproachMode.INTERCEPT;
			case FLANK_LEFT -> CreeperTacticalPlanner.ApproachMode.FLANK_LEFT;
			case FLANK_RIGHT -> CreeperTacticalPlanner.ApproachMode.FLANK_RIGHT;
		};
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

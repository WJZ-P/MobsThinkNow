package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
import com.wjz.mobsthinknow.shared.ai.SpiderTacticalPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import net.minecraft.world.phys.Vec3;

/** Fabric 的 Vec3 边界适配；蜘蛛战术标量与向量数学由 shared 和 Paper 共用。 */
public final class SpiderCombatMath {
	private SpiderCombatMath() {
	}

	public static boolean isTargetWatching(final Vec3 targetLook, final Vec3 targetToSpider) {
		return SpiderTacticalPlanner.isTargetWatching(toShared(targetLook), toShared(targetToSpider));
	}

	public static ApproachMode chooseApproach(
		final int intelligence,
		final boolean watching,
		final boolean blocking,
		final boolean visible,
		final int repositionTicks,
		final int stableSide
	) {
		return fromShared(SpiderTacticalPlanner.chooseApproach(
			intelligence,
			watching,
			blocking,
			visible,
			repositionTicks,
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
		return toMinecraft(SpiderTacticalPlanner.approachDestination(
			toShared(mode),
			toShared(targetPosition),
			toShared(targetVelocity),
			toShared(targetLook),
			intelligence
		));
	}

	public static boolean canPredictivePounce(
		final int intelligence,
		final boolean visible,
		final boolean onGround,
		final double distanceSquared
	) {
		return SpiderTacticalPlanner.canPredictivePounce(intelligence, visible, onGround, distanceSquared);
	}

	public static Vec3 pounceVelocity(
		final Vec3 spiderPosition,
		final Vec3 currentMovement,
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final int intelligence,
		final int difficultyId
	) {
		return toMinecraft(SpiderTacticalPlanner.pounceVelocity(
			toShared(spiderPosition),
			toShared(currentMovement),
			toShared(targetPosition),
			toShared(targetVelocity),
			intelligence,
			DifficultyTier.fromNumericId(difficultyId)
		));
	}

	public static int pounceCooldownTicks(final int intelligence, final double unitSample) {
		return SpiderTacticalPlanner.pounceCooldownTicks(intelligence, unitSample);
	}

	public static double approachSpeed(final int intelligence, final int difficultyId) {
		return SpiderTacticalPlanner.approachSpeed(intelligence, DifficultyTier.fromNumericId(difficultyId));
	}

	public static int repathTicks(final int intelligence) {
		return SpiderTacticalPlanner.repathTicks(intelligence);
	}

	public static int repositionTicks(final int intelligence) {
		return SpiderTacticalPlanner.repositionTicks(intelligence);
	}

	public static Vec3 carrierDestination(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final int combinedIntelligence
	) {
		return toMinecraft(SpiderTacticalPlanner.carrierDestination(
			toShared(targetPosition),
			toShared(targetVelocity),
			combinedIntelligence
		));
	}

	public static double carrierSpeed(
		final double configuredMaximum,
		final int combinedIntelligence,
		final int difficultyId
	) {
		return SpiderTacticalPlanner.carrierSpeed(
			configuredMaximum,
			combinedIntelligence,
			DifficultyTier.fromNumericId(difficultyId)
		);
	}

	public static double randomizedCarrierMaximum(final double configuredMaximum, final double randomSample) {
		return SpiderTacticalPlanner.randomizedCarrierMaximum(configuredMaximum, randomSample);
	}

	public static Vec3 boardingLeapVelocity(final Vec3 creeperPosition, final Vec3 spiderPosition) {
		return toMinecraft(SpiderTacticalPlanner.boardingLeapVelocity(
			toShared(creeperPosition),
			toShared(spiderPosition)
		));
	}

	private static Vec3d toShared(final Vec3 vector) {
		return new Vec3d(vector.x, vector.y, vector.z);
	}

	private static Vec3 toMinecraft(final Vec3d vector) {
		return new Vec3(vector.x(), vector.y(), vector.z());
	}

	private static ApproachMode fromShared(final SpiderTacticalPlanner.ApproachMode mode) {
		return switch (mode) {
			case DIRECT -> ApproachMode.DIRECT;
			case INTERCEPT -> ApproachMode.INTERCEPT;
			case FLANK_LEFT -> ApproachMode.FLANK_LEFT;
			case FLANK_RIGHT -> ApproachMode.FLANK_RIGHT;
			case REPOSITION_LEFT -> ApproachMode.REPOSITION_LEFT;
			case REPOSITION_RIGHT -> ApproachMode.REPOSITION_RIGHT;
		};
	}

	private static SpiderTacticalPlanner.ApproachMode toShared(final ApproachMode mode) {
		return switch (mode) {
			case DIRECT -> SpiderTacticalPlanner.ApproachMode.DIRECT;
			case INTERCEPT -> SpiderTacticalPlanner.ApproachMode.INTERCEPT;
			case FLANK_LEFT -> SpiderTacticalPlanner.ApproachMode.FLANK_LEFT;
			case FLANK_RIGHT -> SpiderTacticalPlanner.ApproachMode.FLANK_RIGHT;
			case REPOSITION_LEFT -> SpiderTacticalPlanner.ApproachMode.REPOSITION_LEFT;
			case REPOSITION_RIGHT -> SpiderTacticalPlanner.ApproachMode.REPOSITION_RIGHT;
		};
	}

	public enum ApproachMode {
		DIRECT,
		INTERCEPT,
		FLANK_LEFT,
		FLANK_RIGHT,
		REPOSITION_LEFT,
		REPOSITION_RIGHT;

		public boolean isFlank() {
			return this == FLANK_LEFT || this == FLANK_RIGHT;
		}

		public boolean isReposition() {
			return this == REPOSITION_LEFT || this == REPOSITION_RIGHT;
		}
	}
}

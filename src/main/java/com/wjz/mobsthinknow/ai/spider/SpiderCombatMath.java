package com.wjz.mobsthinknow.ai.spider;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 与实体生命周期解耦的蜘蛛绕侧、预判跳扑和运输冲锋数学。 */
public final class SpiderCombatMath {
	private static final double EPSILON = 1.0E-7;

	private SpiderCombatMath() {
	}

	public static boolean isTargetWatching(final Vec3 targetLook, final Vec3 targetToSpider) {
		Vec3 look = horizontalUnit(targetLook, new Vec3(0.0, 0.0, 1.0));
		Vec3 towardSpider = horizontalUnit(targetToSpider, Vec3.ZERO);
		return towardSpider.lengthSqr() > EPSILON && look.dot(towardSpider) >= 0.72;
	}

	public static ApproachMode chooseApproach(
		final int intelligence,
		final boolean watching,
		final boolean blocking,
		final boolean visible,
		final int repositionTicks,
		final int stableSide
	) {
		int side = stableSide < 0 ? -1 : 1;
		if (repositionTicks > 0 && intelligence >= 5) {
			return side < 0 ? ApproachMode.REPOSITION_LEFT : ApproachMode.REPOSITION_RIGHT;
		}
		if (intelligence >= 6 && visible && (watching || blocking)) {
			return side < 0 ? ApproachMode.FLANK_LEFT : ApproachMode.FLANK_RIGHT;
		}
		return intelligence >= 4 && visible ? ApproachMode.INTERCEPT : ApproachMode.DIRECT;
	}

	public static Vec3 approachDestination(
		final ApproachMode mode,
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final Vec3 targetLook,
		final int intelligence
	) {
		Vec3 forward = horizontalUnit(targetLook, new Vec3(0.0, 0.0, 1.0));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		return switch (mode) {
			case DIRECT -> targetPosition;
			case INTERCEPT -> targetPosition.add(clampHorizontal(targetVelocity, 0.38).scale(3.0 + intelligence * 0.45));
			case FLANK_LEFT -> targetPosition.subtract(forward.scale(2.1)).subtract(right.scale(2.35));
			case FLANK_RIGHT -> targetPosition.subtract(forward.scale(2.1)).add(right.scale(2.35));
			case REPOSITION_LEFT -> targetPosition.subtract(forward.scale(3.35)).subtract(right.scale(3.0));
			case REPOSITION_RIGHT -> targetPosition.subtract(forward.scale(3.35)).add(right.scale(3.0));
		};
	}

	public static boolean canPredictivePounce(
		final int intelligence,
		final boolean visible,
		final boolean onGround,
		final double distanceSquared
	) {
		return intelligence >= 4
			&& visible
			&& onGround
			&& distanceSquared >= 6.25
			&& distanceSquared <= 49.0;
	}

	public static Vec3 pounceVelocity(
		final Vec3 spiderPosition,
		final Vec3 currentMovement,
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final int intelligence,
		final int difficultyId
	) {
		Vec3 predictedTarget = targetPosition.add(clampHorizontal(targetVelocity, 0.38).scale(2.5 + intelligence * 0.35));
		Vec3 horizontal = horizontalUnit(predictedTarget.subtract(spiderPosition), Vec3.ZERO);
		double horizontalSpeed = Mth.clamp(0.40 + intelligence * 0.014 + difficultyId * 0.012, 0.44, 0.60);
		double verticalSpeed = Mth.clamp(0.38 + intelligence * 0.007, 0.40, 0.46);
		Vec3 blendedHorizontal = clampHorizontal(horizontal.scale(horizontalSpeed).add(
			currentMovement.x * 0.12,
			0.0,
			currentMovement.z * 0.12
		), 0.60);
		return new Vec3(blendedHorizontal.x, verticalSpeed, blendedHorizontal.z);
	}

	public static double approachSpeed(final int intelligence, final int difficultyId) {
		return Mth.clamp(0.98 + intelligence * 0.017 + difficultyId * 0.025, 1.0, 1.25);
	}

	public static int repathTicks(final int intelligence) {
		return Math.max(3, 10 - intelligence / 2);
	}

	public static int repositionTicks(final int intelligence) {
		return Mth.clamp(8 + intelligence, 10, 18);
	}

	public static Vec3 carrierDestination(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final int combinedIntelligence
	) {
		return targetPosition.add(
			clampHorizontal(targetVelocity, 0.42).scale(3.0 + SpiderIntelligence.clamp(combinedIntelligence) * 0.45)
		);
	}

	public static double carrierSpeed(
		final double configuredMaximum,
		final int combinedIntelligence,
		final int difficultyId
	) {
		double progress = Mth.clamp((combinedIntelligence - 1) / 9.0 * 0.75 + difficultyId / 3.0 * 0.25, 0.0, 1.0);
		return Mth.lerp(progress, 1.15, configuredMaximum);
	}

	private static Vec3 clampHorizontal(final Vec3 vector, final double maximumLength) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		double lengthSquared = horizontal.lengthSqr();
		if (lengthSquared <= maximumLength * maximumLength) {
			return horizontal;
		}
		return horizontal.normalize().scale(maximumLength);
	}

	private static Vec3 horizontalUnit(final Vec3 vector, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.lengthSqr() > EPSILON ? horizontal.normalize() : fallback;
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

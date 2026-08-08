package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/** 蜘蛛跨平台的绕侧、命中后重定位、预测跳扑与载具速度数学。 */
public final class SpiderTacticalPlanner {
	private static final double EPSILON = 1.0E-7;
	private static final double BASE_CARRIER_SPEED = 1.10;
	private static final double MINIMUM_RANDOM_CARRIER_FACTOR = 0.88;
	private static final double MAXIMUM_RANDOM_CARRIER_FACTOR = 1.0;

	private SpiderTacticalPlanner() {
	}

	public static boolean isTargetWatching(final Vec3d targetLook, final Vec3d targetToSpider) {
		Vec3d look = targetLook.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d towardSpider = targetToSpider.horizontal();
		if (towardSpider.horizontalLengthSquared() <= EPSILON) {
			return false;
		}
		towardSpider = towardSpider.horizontalUnitOr(Vec3d.ZERO);
		return look.x() * towardSpider.x() + look.z() * towardSpider.z() >= 0.72;
	}

	public static ApproachMode chooseApproach(
		final int intelligence,
		final boolean watching,
		final boolean blocking,
		final boolean visible,
		final int repositionTicks,
		final int stableSide
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		int side = stableSide < 0 ? -1 : 1;
		if (repositionTicks > 0 && iq >= 5) {
			return side < 0 ? ApproachMode.REPOSITION_LEFT : ApproachMode.REPOSITION_RIGHT;
		}
		if (iq >= 6 && visible && (watching || blocking)) {
			return side < 0 ? ApproachMode.FLANK_LEFT : ApproachMode.FLANK_RIGHT;
		}
		return iq >= 4 && visible ? ApproachMode.INTERCEPT : ApproachMode.DIRECT;
	}

	public static Vec3d approachDestination(
		final ApproachMode mode,
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final Vec3d targetLook,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		Vec3d forward = targetLook.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		return switch (mode) {
			case DIRECT -> targetPosition;
			case INTERCEPT -> targetPosition.add(cappedHorizontal(targetVelocity, 0.38).scale(3.0 + iq * 0.45));
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
		return IntelligenceDistribution.clamp(intelligence) >= 4
			&& visible
			&& onGround
			&& Double.isFinite(distanceSquared)
			&& distanceSquared >= 6.25
			&& distanceSquared <= 49.0;
	}

	public static Vec3d predictedPounceLanding(
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		return targetPosition.add(cappedHorizontal(targetVelocity, 0.38).scale(2.5 + iq * 0.35));
	}

	public static Vec3d pounceVelocity(
		final Vec3d spiderPosition,
		final Vec3d currentMovement,
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final int intelligence,
		final DifficultyTier difficulty
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		Vec3d predictedTarget = predictedPounceLanding(targetPosition, targetVelocity, iq);
		Vec3d horizontal = predictedTarget.subtract(spiderPosition).horizontalUnitOr(Vec3d.ZERO);
		double difficultyId = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 1.0;
			case NORMAL -> 2.0;
			case HARD -> 3.0;
		};
		double horizontalSpeed = Math.clamp(0.40 + iq * 0.014 + difficultyId * 0.012, 0.44, 0.60);
		double verticalSpeed = Math.clamp(0.38 + iq * 0.007, 0.40, 0.46);
		Vec3d blended = cappedHorizontal(
			horizontal.scale(horizontalSpeed).add(new Vec3d(
				currentMovement.x() * 0.12,
				0.0,
				currentMovement.z() * 0.12
			)),
			0.60
		);
		return new Vec3d(blended.x(), verticalSpeed, blended.z());
	}

	public static int pounceCooldownTicks(final int intelligence, final double unitSample) {
		double sample = Double.isFinite(unitSample) ? Math.clamp(unitSample, 0.0, 1.0) : 0.0;
		return Math.max(18, 36 - IntelligenceDistribution.clamp(intelligence))
			+ Math.min(8, (int)Math.floor(sample * 9.0));
	}

	public static double approachSpeed(final int intelligence, final DifficultyTier difficulty) {
		int difficultyId = switch (difficulty) {
			case PEACEFUL -> 0;
			case EASY -> 1;
			case NORMAL -> 2;
			case HARD -> 3;
		};
		return Math.clamp(0.98 + IntelligenceDistribution.clamp(intelligence) * 0.017 + difficultyId * 0.025, 1.0, 1.25);
	}

	public static int repathTicks(final int intelligence) {
		return Math.max(3, 10 - IntelligenceDistribution.clamp(intelligence) / 2);
	}

	public static int repositionTicks(final int intelligence) {
		return Math.clamp(8 + IntelligenceDistribution.clamp(intelligence), 10, 18);
	}

	public static Vec3d carrierDestination(
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final int combinedIntelligence
	) {
		return targetPosition.add(
			cappedHorizontal(targetVelocity, 0.42)
				.scale(3.0 + IntelligenceDistribution.clamp(combinedIntelligence) * 0.45)
		);
	}

	public static double carrierSpeed(
		final double configuredMaximum,
		final int combinedIntelligence,
		final DifficultyTier difficulty
	) {
		double difficultyProgress = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 1.0 / 3.0;
			case NORMAL -> 2.0 / 3.0;
			case HARD -> 1.0;
		};
		double progress = Math.clamp(
			(IntelligenceDistribution.clamp(combinedIntelligence) - 1) / 9.0 * 0.75
				+ difficultyProgress * 0.25,
			0.0,
			1.0
		);
		double maximum = Math.max(1.0, configuredMaximum);
		double base = Math.min(BASE_CARRIER_SPEED, maximum);
		return base + (maximum - base) * progress;
	}

	public static double randomizedCarrierMaximum(final double configuredMaximum, final double unitSample) {
		double sample = Double.isFinite(unitSample) ? Math.clamp(unitSample, 0.0, 1.0) : 0.0;
		double factor = MINIMUM_RANDOM_CARRIER_FACTOR
			+ (MAXIMUM_RANDOM_CARRIER_FACTOR - MINIMUM_RANDOM_CARRIER_FACTOR) * sample;
		return Math.min(configuredMaximum, Math.max(BASE_CARRIER_SPEED, configuredMaximum * factor));
	}

	public static Vec3d boardingLeapVelocity(final Vec3d payloadPosition, final Vec3d spiderPosition) {
		Vec3d offset = spiderPosition.subtract(payloadPosition).horizontal();
		double distance = Math.sqrt(offset.horizontalLengthSquared());
		Vec3d direction = distance > EPSILON ? offset.scale(1.0 / distance) : Vec3d.ZERO;
		double horizontalSpeed = Math.clamp(distance * 0.13, 0.20, 0.34);
		return new Vec3d(direction.x() * horizontalSpeed, 0.38, direction.z() * horizontalSpeed);
	}

	private static Vec3d cappedHorizontal(final Vec3d vector, final double maximumLength) {
		Vec3d horizontal = vector.horizontal();
		double lengthSquared = horizontal.horizontalLengthSquared();
		return lengthSquared <= maximumLength * maximumLength
			? horizontal
			: horizontal.scale(maximumLength / Math.sqrt(lengthSquared));
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

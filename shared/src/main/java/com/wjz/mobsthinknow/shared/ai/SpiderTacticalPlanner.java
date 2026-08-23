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
		return isTargetWatching(targetLook.x(), targetLook.z(), targetToSpider.x(), targetToSpider.z());
	}

	public static boolean isTargetWatching(
		final double targetLookX,
		final double targetLookZ,
		final double targetToSpiderX,
		final double targetToSpiderZ
	) {
		double lookX = targetLookX;
		double lookZ = targetLookZ;
		double lookLengthSquared = lookX * lookX + lookZ * lookZ;
		if (lookLengthSquared < 1.0E-9) {
			lookX = 0.0;
			lookZ = 1.0;
		} else {
			double inverseLookLength = 1.0 / Math.sqrt(lookLengthSquared);
			lookX *= inverseLookLength;
			lookZ *= inverseLookLength;
		}
		double towardLengthSquared = targetToSpiderX * targetToSpiderX
			+ targetToSpiderZ * targetToSpiderZ;
		if (towardLengthSquared <= EPSILON) {
			return false;
		}
		double inverseTowardLength = 1.0 / Math.sqrt(towardLengthSquared);
		return lookX * targetToSpiderX * inverseTowardLength
			+ lookZ * targetToSpiderZ * inverseTowardLength >= 0.72;
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
		if (mode == ApproachMode.DIRECT) {
			return targetPosition;
		}
		if (mode == ApproachMode.INTERCEPT) {
			return leadPosition(targetPosition, targetVelocity, 0.38, 3.0 + iq * 0.45);
		}
		double forwardX = targetLook.x();
		double forwardZ = targetLook.z();
		double forwardLengthSquared = forwardX * forwardX + forwardZ * forwardZ;
		if (forwardLengthSquared < 1.0E-9) {
			forwardX = 0.0;
			forwardZ = 1.0;
		} else {
			double inverseForwardLength = 1.0 / Math.sqrt(forwardLengthSquared);
			forwardX *= inverseForwardLength;
			forwardZ *= inverseForwardLength;
		}
		return switch (mode) {
			case DIRECT, INTERCEPT -> throw new IllegalStateException("non-positional mode reached geometry switch");
			case FLANK_LEFT -> offset(targetPosition, forwardX, forwardZ, -2.1, -2.35);
			case FLANK_RIGHT -> offset(targetPosition, forwardX, forwardZ, -2.1, 2.35);
			case REPOSITION_LEFT -> offset(targetPosition, forwardX, forwardZ, -3.35, -3.0);
			case REPOSITION_RIGHT -> offset(targetPosition, forwardX, forwardZ, -3.35, 3.0);
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
		return leadPosition(targetPosition, targetVelocity, 0.38, 2.5 + iq * 0.35);
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
		double directionX = predictedTarget.x() - spiderPosition.x();
		double directionZ = predictedTarget.z() - spiderPosition.z();
		double directionLengthSquared = directionX * directionX + directionZ * directionZ;
		if (directionLengthSquared < 1.0E-9) {
			directionX = 1.0;
			directionZ = 0.0;
		} else {
			double inverseDirectionLength = 1.0 / Math.sqrt(directionLengthSquared);
			directionX *= inverseDirectionLength;
			directionZ *= inverseDirectionLength;
		}
		double difficultyId = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 1.0;
			case NORMAL -> 2.0;
			case HARD -> 3.0;
		};
		double horizontalSpeed = Math.clamp(0.40 + iq * 0.014 + difficultyId * 0.012, 0.44, 0.60);
		double verticalSpeed = Math.clamp(0.38 + iq * 0.007, 0.40, 0.46);
		double blendedX = directionX * horizontalSpeed + currentMovement.x() * 0.12;
		double blendedZ = directionZ * horizontalSpeed + currentMovement.z() * 0.12;
		double blendedLengthSquared = blendedX * blendedX + blendedZ * blendedZ;
		if (blendedLengthSquared > 0.60 * 0.60) {
			double scale = 0.60 / Math.sqrt(blendedLengthSquared);
			blendedX *= scale;
			blendedZ *= scale;
		}
		return new Vec3d(blendedX, verticalSpeed, blendedZ);
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
		return leadPosition(
			targetPosition,
			targetVelocity,
			0.42,
			3.0 + IntelligenceDistribution.clamp(combinedIntelligence) * 0.45
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
		double offsetX = spiderPosition.x() - payloadPosition.x();
		double offsetZ = spiderPosition.z() - payloadPosition.z();
		double distance = Math.hypot(offsetX, offsetZ);
		double directionX = distance > EPSILON ? offsetX / distance : 0.0;
		double directionZ = distance > EPSILON ? offsetZ / distance : 0.0;
		double horizontalSpeed = Math.clamp(distance * 0.13, 0.20, 0.34);
		return new Vec3d(directionX * horizontalSpeed, 0.38, directionZ * horizontalSpeed);
	}

	private static Vec3d leadPosition(
		final Vec3d origin,
		final Vec3d velocity,
		final double maximumVelocity,
		final double ticks
	) {
		double velocityX = velocity.x();
		double velocityZ = velocity.z();
		double lengthSquared = velocityX * velocityX + velocityZ * velocityZ;
		if (lengthSquared > maximumVelocity * maximumVelocity) {
			double scale = maximumVelocity / Math.sqrt(lengthSquared);
			velocityX *= scale;
			velocityZ *= scale;
		}
		return new Vec3d(origin.x() + velocityX * ticks, origin.y(), origin.z() + velocityZ * ticks);
	}

	private static Vec3d offset(
		final Vec3d origin,
		final double forwardX,
		final double forwardZ,
		final double forwardDistance,
		final double lateralDistance
	) {
		return new Vec3d(
			origin.x() + forwardX * forwardDistance - forwardZ * lateralDistance,
			origin.y(),
			origin.z() + forwardZ * forwardDistance + forwardX * lateralDistance
		);
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

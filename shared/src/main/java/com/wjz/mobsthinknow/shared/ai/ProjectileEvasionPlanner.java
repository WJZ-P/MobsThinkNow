package com.wjz.mobsthinknow.shared.ai;

/**
 * Platform-neutral projectile interception and lateral-evasion math.
 *
 * <p>The planner accepts scalar coordinates instead of Minecraft or Bukkit vectors. Fabric and Paper therefore
 * share the same definition of an incoming projectile while keeping entity queries and movement platform-local.</p>
 */
public final class ProjectileEvasionPlanner {
	private static final double MINIMUM_SPEED_SQUARED = 1.0E-4;
	private static final double CENTER_EPSILON = 1.0E-6;
	private static final ReactionProfile[] REACTION_PROFILES = createReactionProfiles();

	private ProjectileEvasionPlanner() {
	}

	/** Returns the closest future tick, or positive infinity when no valid point exists in the horizon. */
	public static double closestApproachTicks(
		final double relativeX,
		final double relativeY,
		final double relativeZ,
		final double velocityX,
		final double velocityY,
		final double velocityZ,
		final double horizonTicks
	) {
		double speedSquared = velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ;
		if (!Double.isFinite(relativeX)
			|| !Double.isFinite(relativeY)
			|| !Double.isFinite(relativeZ)
			|| !Double.isFinite(velocityX)
			|| !Double.isFinite(velocityY)
			|| !Double.isFinite(velocityZ)
			|| !Double.isFinite(horizonTicks)
			|| speedSquared < MINIMUM_SPEED_SQUARED
			|| horizonTicks <= 0.0) {
			return Double.POSITIVE_INFINITY;
		}

		double time = (relativeX * velocityX + relativeY * velocityY + relativeZ * velocityZ) / speedSquared;
		return Double.isFinite(time) && time >= 0.0 && time <= horizonTicks
			? time
			: Double.POSITIVE_INFINITY;
	}

	/** Predicts whether the projectile center will enter the supplied spherical safety envelope. */
	public static boolean isIncoming(
		final double relativeX,
		final double relativeY,
		final double relativeZ,
		final double velocityX,
		final double velocityY,
		final double velocityZ,
		final double horizonTicks,
		final double safetyRadius
	) {
		double time = closestApproachTicks(
			relativeX,
			relativeY,
			relativeZ,
			velocityX,
			velocityY,
			velocityZ,
			horizonTicks
		);
		return isIncomingAtClosestApproach(
			relativeX,
			relativeY,
			relativeZ,
			velocityX,
			velocityY,
			velocityZ,
			time,
			safetyRadius
		);
	}

	/** Reuses an already-computed closest-approach tick without repeating projection math. */
	public static boolean isIncomingAtClosestApproach(
		final double relativeX,
		final double relativeY,
		final double relativeZ,
		final double velocityX,
		final double velocityY,
		final double velocityZ,
		final double closestApproachTicks,
		final double safetyRadius
	) {
		if (!Double.isFinite(relativeX)
			|| !Double.isFinite(relativeY)
			|| !Double.isFinite(relativeZ)
			|| !Double.isFinite(velocityX)
			|| !Double.isFinite(velocityY)
			|| !Double.isFinite(velocityZ)
			|| !Double.isFinite(closestApproachTicks)
			|| closestApproachTicks < 0.0
			|| !Double.isFinite(safetyRadius)
			|| safetyRadius <= 0.0) {
			return false;
		}

		double missX = relativeX - velocityX * closestApproachTicks;
		double missY = relativeY - velocityY * closestApproachTicks;
		double missZ = relativeZ - velocityZ * closestApproachTicks;
		return missX * missX + missY * missY + missZ * missZ <= safetyRadius * safetyRadius;
	}

	/**
	 * Selects the horizontal dodge side that moves away from the predicted miss point.
	 *
	 * @return {@code 1} for actor-right or {@code -1} for actor-left
	 */
	public static int saferSide(
		final double actorX,
		final double actorZ,
		final double projectileX,
		final double projectileZ,
		final double velocityX,
		final double velocityZ,
		final double closestApproachTicks,
		final double combatYawDegrees,
		final int fallbackDirection
	) {
		int fallback = fallbackDirection < 0 ? -1 : 1;
		if (!Double.isFinite(actorX)
			|| !Double.isFinite(actorZ)
			|| !Double.isFinite(projectileX)
			|| !Double.isFinite(projectileZ)
			|| !Double.isFinite(velocityX)
			|| !Double.isFinite(velocityZ)
			|| !Double.isFinite(closestApproachTicks)
			|| !Double.isFinite(combatYawDegrees)
			|| closestApproachTicks < 0.0) {
			return fallback;
		}

		double predictedX = projectileX + velocityX * closestApproachTicks;
		double predictedZ = projectileZ + velocityZ * closestApproachTicks;
		if (!Double.isFinite(predictedX) || !Double.isFinite(predictedZ)) {
			return fallback;
		}

		// Minecraft yaw zero faces +Z, making the actor's right axis (cos(yaw), sin(yaw)).
		double yaw = Math.toRadians(combatYawDegrees);
		double missOnRightAxis = (predictedX - actorX) * Math.cos(yaw)
			+ (predictedZ - actorZ) * Math.sin(yaw);
		if (Math.abs(missOnRightAxis) < CENTER_EPSILON) {
			return fallback;
		}
		return missOnRightAxis > 0.0 ? -1 : 1;
	}

	/** Creates a monotonic, hard-bounded IQ reaction profile. */
	public static ReactionProfile reactionProfile(final int intelligence) {
		int iq = Math.clamp(intelligence, 1, 10);
		return REACTION_PROFILES[iq - 1];
	}

	private static ReactionProfile[] createReactionProfiles() {
		ReactionProfile[] profiles = new ReactionProfile[10];
		for (int iq = 1; iq <= profiles.length; iq++) {
			double progress = (iq - 1) / 9.0;
			profiles[iq - 1] = new ReactionProfile(
				6 - (int)Math.floor(progress * 4.0),
				4.5 + progress * 3.5,
				0.90 + progress * 0.25,
				6 + (int)Math.floor(progress * 2.0),
				8 + (int)Math.floor(progress * 3.0)
			);
		}
		return profiles;
	}

	/** Converts a stable unit sample into an inclusive, bounded dodge duration. */
	public static int dodgeTicks(final ReactionProfile profile, final double unitSample) {
		if (profile == null) {
			return 0;
		}
		double sample = Double.isFinite(unitSample) ? Math.clamp(unitSample, 0.0, 1.0) : 0.0;
		int span = profile.maximumDodgeTicks() - profile.minimumDodgeTicks();
		return profile.minimumDodgeTicks() + Math.min(span, (int)Math.floor(sample * (span + 1)));
	}

	public record ReactionProfile(
		int scanIntervalTicks,
		double predictionHorizonTicks,
		double safetyRadius,
		int minimumDodgeTicks,
		int maximumDodgeTicks
	) {
		public ReactionProfile {
			if (scanIntervalTicks < 1
				|| !Double.isFinite(predictionHorizonTicks)
				|| predictionHorizonTicks <= 0.0
				|| !Double.isFinite(safetyRadius)
				|| safetyRadius <= 0.0
				|| minimumDodgeTicks < 1
				|| maximumDodgeTicks < minimumDodgeTicks) {
				throw new IllegalArgumentException("invalid projectile evasion reaction profile");
			}
		}
	}
}

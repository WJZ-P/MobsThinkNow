package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import java.util.Objects;

/**
 * Platform-neutral geometry and cadence for predictive spider web traps.
 *
 * <p>The planner only consumes immutable snapshots and always returns five candidate centers. Platform adapters own
 * block probing, protection events, placement and restoration, keeping both Fabric and Paper on the same bounded
 * decision path without leaking either platform's entity types into the shared kernel.</p>
 */
public final class SpiderWebTrapPlanner {
	public static final int MINIMUM_INTELLIGENCE = 7;
	public static final int DEFAULT_COOLDOWN_TICKS = 240;
	public static final int DEFAULT_LIFETIME_TICKS = 160;
	public static final int MAXIMUM_CANDIDATE_CENTERS = 5;
	private static final double MINIMUM_TARGET_DISTANCE_SQUARED = 3.25 * 3.25;
	private static final double MAXIMUM_TARGET_DISTANCE_SQUARED = 9.0 * 9.0;
	private static final double MAXIMUM_HORIZONTAL_LEAD = 3.25;
	private static final double DODGE_LANE_OFFSET = 0.82;
	private static final double CONTAINMENT_LANE_OFFSET = 0.96;
	private static final double MINIMUM_DIRECTION_SQUARED = 1.0E-6;

	private SpiderWebTrapPlanner() {
	}

	public static boolean canPlan(
		final int intelligence,
		final boolean targetVisible,
		final boolean spiderOnGround,
		final boolean carryingPassenger,
		final double targetDistanceSquared
	) {
		return intelligence >= MINIMUM_INTELLIGENCE
			&& targetVisible
			&& spiderOnGround
			&& !carryingPassenger
			&& Double.isFinite(targetDistanceSquared)
			&& targetDistanceSquared >= MINIMUM_TARGET_DISTANCE_SQUARED
			&& targetDistanceSquared <= MAXIMUM_TARGET_DISTANCE_SQUARED;
	}

	/**
	 * Follow meaningful horizontal velocity; for a stationary target, use less than one block of look-direction intent.
	 */
	public static Vec3d predictedPosition(
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final Vec3d targetLook,
		final int intelligence
	) {
		Objects.requireNonNull(targetPosition, "targetPosition");
		Objects.requireNonNull(targetVelocity, "targetVelocity");
		Objects.requireNonNull(targetLook, "targetLook");
		int iq = Math.clamp(intelligence, MINIMUM_INTELLIGENCE, IntelligenceDistribution.MAXIMUM);
		Vec3d horizontalVelocity = targetVelocity.horizontal();
		Vec3d intent;
		if (horizontalVelocity.horizontalLengthSquared() >= 0.0025) {
			intent = horizontalVelocity.scale(4.5 + (iq - MINIMUM_INTELLIGENCE) * 0.75);
		} else {
			Vec3d horizontalLook = targetLook.horizontal();
			intent = horizontalLook.horizontalLengthSquared() < MINIMUM_DIRECTION_SQUARED
				? Vec3d.ZERO
				: horizontalLook.horizontalUnitOr(Vec3d.ZERO)
					.scale(0.62 + (iq - MINIMUM_INTELLIGENCE) * 0.08);
		}
		intent = capHorizontal(intent, MAXIMUM_HORIZONTAL_LEAD);
		return targetPosition.add(intent);
	}

	/** Center lane first, then both dodge lanes and short forward/backward corrections. */
	public static List<Vec3d> candidateCenters(
		final Vec3d targetPosition,
		final Vec3d predictedPosition,
		final Vec3d targetLook,
		final int stableSide
	) {
		Objects.requireNonNull(targetPosition, "targetPosition");
		Objects.requireNonNull(predictedPosition, "predictedPosition");
		Objects.requireNonNull(targetLook, "targetLook");
		Vec3d heading = predictedPosition.subtract(targetPosition).horizontal();
		if (heading.horizontalLengthSquared() < MINIMUM_DIRECTION_SQUARED) {
			heading = targetLook.horizontal();
		}
		heading = heading.horizontalUnitOr(new Vec3d(1.0, 0.0, 0.0));
		Vec3d side = new Vec3d(-heading.z(), 0.0, heading.x()).scale(stableSide < 0 ? -1.0 : 1.0);
		return List.of(
			predictedPosition,
			predictedPosition.add(side.scale(DODGE_LANE_OFFSET)),
			predictedPosition.add(side.scale(-DODGE_LANE_OFFSET)),
			predictedPosition.add(heading.scale(0.78)),
			predictedPosition.add(heading.scale(-0.72))
		);
	}

	/**
	 * When a squad creeper commits its fuse, cover the lane leading away from the predicted blast center.
	 */
	public static List<Vec3d> blastEscapeCandidateCenters(
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final Vec3d blastCenter,
		final int stableSide
	) {
		Objects.requireNonNull(targetPosition, "targetPosition");
		Objects.requireNonNull(targetVelocity, "targetVelocity");
		Objects.requireNonNull(blastCenter, "blastCenter");
		Vec3d away = targetPosition.subtract(blastCenter).horizontal();
		if (away.horizontalLengthSquared() < MINIMUM_DIRECTION_SQUARED) {
			away = targetVelocity.horizontal();
		}
		away = away.horizontalUnitOr(new Vec3d(1.0, 0.0, 0.0));
		Vec3d velocity = targetVelocity.horizontal();
		if (velocity.horizontalLengthSquared() >= 0.0025 && horizontalDot(velocity, away) > 0.0) {
			away = away.scale(0.72)
				.add(velocity.horizontalUnitOr(away).scale(0.28))
				.horizontalUnitOr(away);
		}
		Vec3d side = new Vec3d(-away.z(), 0.0, away.x()).scale(stableSide < 0 ? -1.0 : 1.0);
		Vec3d escape = targetPosition.add(away.scale(2.05));
		return List.of(
			escape,
			escape.add(side.scale(CONTAINMENT_LANE_OFFSET)),
			escape.add(side.scale(-CONTAINMENT_LANE_OFFSET)),
			escape.add(away.scale(0.78)),
			escape.add(away.scale(-0.72))
		);
	}

	public static boolean mayBypassCooldownForBlast(
		final boolean cooldownReady,
		final int activeCreeperId,
		final int lastSupportedCreeperId
	) {
		return mayBypassCooldownForBlast(
			cooldownReady,
			activeCreeperId > 0 && activeCreeperId != lastSupportedCreeperId
		);
	}

	/** Platform adapters with UUID ownership can pass the already-bounded new-threat decision directly. */
	public static boolean mayBypassCooldownForBlast(
		final boolean cooldownReady,
		final boolean newBlastOpportunity
	) {
		return cooldownReady || newBlastOpportunity;
	}

	/** Intelligence and difficulty only modestly reduce the configured density-controlling cooldown. */
	public static int cooldownTicks(
		final int configuredTicks,
		final int intelligence,
		final int difficultyId,
		final int randomExtraTicks
	) {
		int skillReduction = Math.clamp(intelligence - MINIMUM_INTELLIGENCE, 0, 3) * 8;
		int difficultyReduction = Math.clamp(difficultyId, 0, 3) * 4;
		long calculated = (long)configuredTicks
			- skillReduction
			- difficultyReduction
			+ Math.max(0L, (long)randomExtraTicks);
		return (int)Math.clamp(calculated, 60L, Integer.MAX_VALUE);
	}

	private static Vec3d capHorizontal(final Vec3d value, final double maximumLength) {
		double lengthSquared = value.horizontalLengthSquared();
		return lengthSquared <= maximumLength * maximumLength
			? value.horizontal()
			: value.horizontal().scale(maximumLength / Math.sqrt(lengthSquared));
	}

	private static double horizontalDot(final Vec3d first, final Vec3d second) {
		return first.x() * second.x() + first.z() * second.z();
	}
}

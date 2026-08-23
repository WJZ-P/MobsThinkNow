package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.junit.jupiter.api.Test;

class SpiderTacticalPlannerTest {
	@Test
	void watchedTargetAndPostHitStateSelectDistinctSideModes() {
		assertEquals(
			SpiderTacticalPlanner.ApproachMode.FLANK_LEFT,
			SpiderTacticalPlanner.chooseApproach(6, true, false, true, 0, -1)
		);
		assertEquals(
			SpiderTacticalPlanner.ApproachMode.REPOSITION_RIGHT,
			SpiderTacticalPlanner.chooseApproach(8, false, false, true, 12, 1)
		);
	}

	@Test
	void pounceRangeIsBoundedAndLeadsMovingTargets() {
		assertFalse(SpiderTacticalPlanner.canPredictivePounce(3, true, true, 16.0));
		assertFalse(SpiderTacticalPlanner.canPredictivePounce(8, true, true, 64.0));
		assertTrue(SpiderTacticalPlanner.canPredictivePounce(8, true, true, 25.0));
		Vec3d stationary = SpiderTacticalPlanner.pounceVelocity(
			Vec3d.ZERO, Vec3d.ZERO, new Vec3d(5.0, 0.0, 0.0), Vec3d.ZERO, 8, DifficultyTier.NORMAL
		);
		Vec3d moving = SpiderTacticalPlanner.pounceVelocity(
			Vec3d.ZERO, Vec3d.ZERO, new Vec3d(5.0, 0.0, 0.0), new Vec3d(0.0, 0.0, 0.25),
			8, DifficultyTier.NORMAL
		);
		assertTrue(moving.z() > stationary.z());
		assertTrue(moving.y() >= 0.40 && moving.y() <= 0.46);
	}

	@Test
	void pounceCooldownHasInclusiveNineTickJitterWithoutGoingBelowFloor() {
		assertEquals(35, SpiderTacticalPlanner.pounceCooldownTicks(1, 0.0));
		assertEquals(43, SpiderTacticalPlanner.pounceCooldownTicks(1, 1.0));
		assertEquals(26, SpiderTacticalPlanner.pounceCooldownTicks(10, 0.0));
	}

	@Test
	void carrierSpeedRetainsLowerRandomizedMaximum() {
		assertEquals(1.232, SpiderTacticalPlanner.randomizedCarrierMaximum(1.40, 0.0), 1.0E-9);
		assertEquals(1.40, SpiderTacticalPlanner.randomizedCarrierMaximum(1.40, 1.0), 1.0E-9);
		assertEquals(1.40, SpiderTacticalPlanner.carrierSpeed(1.40, 10, DifficultyTier.HARD), 1.0E-9);
	}

	@Test
	void boardingLeapUsesReadableVerticalArc() {
		Vec3d velocity = SpiderTacticalPlanner.boardingLeapVelocity(
			new Vec3d(4.0, 0.0, 2.0),
			new Vec3d(2.0, 0.0, 2.0)
		);
		assertTrue(velocity.x() < -0.20);
		assertEquals(0.38, velocity.y());
		assertEquals(0.0, velocity.z());
	}

	@Test
	void allocationReducedMathMatchesOriginalVectorFormulas() {
		Vec3d target = new Vec3d(7.5, 64.0, -3.25);
		Vec3d velocity = new Vec3d(0.52, 0.1, -0.31);
		Vec3d look = new Vec3d(-0.3, 0.8, 0.7);
		for (SpiderTacticalPlanner.ApproachMode mode : SpiderTacticalPlanner.ApproachMode.values()) {
			for (int iq : new int[] {-2, 1, 6, 10, 20}) {
				assertVectorEquals(
					legacyApproachDestination(mode, target, velocity, look, iq),
					SpiderTacticalPlanner.approachDestination(mode, target, velocity, look, iq)
				);
			}
		}

		for (Vec3d targetLook : new Vec3d[] {look, Vec3d.ZERO, new Vec3d(1.0, 4.0, 0.0)}) {
			for (Vec3d toward : new Vec3d[] {
				new Vec3d(4.0, 2.0, -1.0),
				new Vec3d(-0.25, 0.0, 0.9),
				Vec3d.ZERO
			}) {
				boolean expected = legacyIsTargetWatching(targetLook, toward);
				assertEquals(expected, SpiderTacticalPlanner.isTargetWatching(targetLook, toward));
				assertEquals(expected, SpiderTacticalPlanner.isTargetWatching(
					targetLook.x(), targetLook.z(), toward.x(), toward.z()
				));
			}
		}

		for (int iq : new int[] {1, 6, 10}) {
			assertVectorEquals(
				legacyPredictedPounceLanding(target, velocity, iq),
				SpiderTacticalPlanner.predictedPounceLanding(target, velocity, iq)
			);
			for (DifficultyTier difficulty : DifficultyTier.values()) {
				Vec3d spider = new Vec3d(-2.0, 63.0, 5.0);
				Vec3d movement = new Vec3d(0.15, -0.2, 0.08);
				assertVectorEquals(
					legacyPounceVelocity(spider, movement, target, velocity, iq, difficulty),
					SpiderTacticalPlanner.pounceVelocity(spider, movement, target, velocity, iq, difficulty)
				);
			}
			assertVectorEquals(
				legacyCarrierDestination(target, velocity, iq),
				SpiderTacticalPlanner.carrierDestination(target, velocity, iq)
			);
		}

		for (Vec3d spider : new Vec3d[] {target, new Vec3d(-8.0, 70.0, 2.0)}) {
			assertVectorEquals(
				legacyBoardingLeapVelocity(target, spider),
				SpiderTacticalPlanner.boardingLeapVelocity(target, spider)
			);
		}
	}

	private static boolean legacyIsTargetWatching(final Vec3d lookVector, final Vec3d targetToSpider) {
		Vec3d look = lookVector.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d towardSpider = targetToSpider.horizontal();
		if (towardSpider.horizontalLengthSquared() <= 1.0E-7) {
			return false;
		}
		towardSpider = towardSpider.horizontalUnitOr(Vec3d.ZERO);
		return look.x() * towardSpider.x() + look.z() * towardSpider.z() >= 0.72;
	}

	private static Vec3d legacyApproachDestination(
		final SpiderTacticalPlanner.ApproachMode mode,
		final Vec3d target,
		final Vec3d velocity,
		final Vec3d look,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		Vec3d forward = look.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		return switch (mode) {
			case DIRECT -> target;
			case INTERCEPT -> target.add(legacyCappedHorizontal(velocity, 0.38).scale(3.0 + iq * 0.45));
			case FLANK_LEFT -> target.subtract(forward.scale(2.1)).subtract(right.scale(2.35));
			case FLANK_RIGHT -> target.subtract(forward.scale(2.1)).add(right.scale(2.35));
			case REPOSITION_LEFT -> target.subtract(forward.scale(3.35)).subtract(right.scale(3.0));
			case REPOSITION_RIGHT -> target.subtract(forward.scale(3.35)).add(right.scale(3.0));
		};
	}

	private static Vec3d legacyPredictedPounceLanding(
		final Vec3d target,
		final Vec3d velocity,
		final int intelligence
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		return target.add(legacyCappedHorizontal(velocity, 0.38).scale(2.5 + iq * 0.35));
	}

	private static Vec3d legacyPounceVelocity(
		final Vec3d spider,
		final Vec3d movement,
		final Vec3d target,
		final Vec3d velocity,
		final int intelligence,
		final DifficultyTier difficulty
	) {
		int iq = IntelligenceDistribution.clamp(intelligence);
		Vec3d predicted = legacyPredictedPounceLanding(target, velocity, iq);
		Vec3d horizontal = predicted.subtract(spider).horizontalUnitOr(Vec3d.ZERO);
		double difficultyId = switch (difficulty) {
			case PEACEFUL -> 0.0;
			case EASY -> 1.0;
			case NORMAL -> 2.0;
			case HARD -> 3.0;
		};
		double horizontalSpeed = Math.clamp(0.40 + iq * 0.014 + difficultyId * 0.012, 0.44, 0.60);
		double verticalSpeed = Math.clamp(0.38 + iq * 0.007, 0.40, 0.46);
		Vec3d blended = legacyCappedHorizontal(
			horizontal.scale(horizontalSpeed).add(new Vec3d(movement.x() * 0.12, 0.0, movement.z() * 0.12)),
			0.60
		);
		return new Vec3d(blended.x(), verticalSpeed, blended.z());
	}

	private static Vec3d legacyCarrierDestination(
		final Vec3d target,
		final Vec3d velocity,
		final int intelligence
	) {
		return target.add(
			legacyCappedHorizontal(velocity, 0.42)
				.scale(3.0 + IntelligenceDistribution.clamp(intelligence) * 0.45)
		);
	}

	private static Vec3d legacyBoardingLeapVelocity(final Vec3d payload, final Vec3d spider) {
		Vec3d offset = spider.subtract(payload).horizontal();
		double distance = Math.sqrt(offset.horizontalLengthSquared());
		Vec3d direction = distance > 1.0E-7 ? offset.scale(1.0 / distance) : Vec3d.ZERO;
		double horizontalSpeed = Math.clamp(distance * 0.13, 0.20, 0.34);
		return new Vec3d(direction.x() * horizontalSpeed, 0.38, direction.z() * horizontalSpeed);
	}

	private static Vec3d legacyCappedHorizontal(final Vec3d vector, final double maximumLength) {
		Vec3d horizontal = vector.horizontal();
		double lengthSquared = horizontal.horizontalLengthSquared();
		return lengthSquared <= maximumLength * maximumLength
			? horizontal
			: horizontal.scale(maximumLength / Math.sqrt(lengthSquared));
	}

	private static void assertVectorEquals(final Vec3d expected, final Vec3d actual) {
		assertEquals(expected.x(), actual.x(), 1.0E-12);
		assertEquals(expected.y(), actual.y(), 1.0E-12);
		assertEquals(expected.z(), actual.z(), 1.0E-12);
	}
}

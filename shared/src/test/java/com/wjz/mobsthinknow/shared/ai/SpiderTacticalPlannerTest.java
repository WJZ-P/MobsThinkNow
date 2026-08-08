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
}

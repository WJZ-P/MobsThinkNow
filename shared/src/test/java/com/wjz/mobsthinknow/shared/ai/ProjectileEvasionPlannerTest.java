package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectileEvasionPlannerTest {
	@Test
	void predictsDirectHitsButRejectsMissesOutgoingAndLateShots() {
		assertTrue(ProjectileEvasionPlanner.isIncoming(0.0, 0.0, 5.0, 0.0, 0.0, 1.0, 8.0, 1.15));
		assertFalse(ProjectileEvasionPlanner.isIncoming(2.0, 0.0, 5.0, 0.0, 0.0, 1.0, 8.0, 1.15));
		assertFalse(ProjectileEvasionPlanner.isIncoming(0.0, 0.0, 5.0, 0.0, 0.0, -1.0, 8.0, 1.15));
		assertFalse(ProjectileEvasionPlanner.isIncoming(0.0, 0.0, 10.0, 0.0, 0.0, 1.0, 8.0, 1.15));
		assertEquals(
			Double.POSITIVE_INFINITY,
			ProjectileEvasionPlanner.closestApproachTicks(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 8.0)
		);
	}

	@Test
	void choosesTheSideAwayFromThePredictedMissPoint() {
		assertEquals(1, ProjectileEvasionPlanner.saferSide(
			0.0, 0.0, 4.0, 0.6, -0.75, 0.0, 4.0 / 0.75, -90.0, -1
		));
		assertEquals(-1, ProjectileEvasionPlanner.saferSide(
			0.0, 0.0, 4.0, -0.6, -0.75, 0.0, 4.0 / 0.75, -90.0, 1
		));
		assertEquals(-1, ProjectileEvasionPlanner.saferSide(
			0.0, 0.0, 4.0, 0.0, -1.0, 0.0, 4.0, -90.0, -7
		));
		assertEquals(1, ProjectileEvasionPlanner.saferSide(
			0.0, 0.0, Double.NaN, 0.0, -1.0, 0.0, 4.0, -90.0, 0
		));
	}

	@Test
	void intelligenceImprovesReactionWithoutRemovingHardBounds() {
		var low = ProjectileEvasionPlanner.reactionProfile(-100);
		var high = ProjectileEvasionPlanner.reactionProfile(100);
		assertTrue(high.scanIntervalTicks() < low.scanIntervalTicks());
		assertTrue(high.predictionHorizonTicks() > low.predictionHorizonTicks());
		assertTrue(high.safetyRadius() > low.safetyRadius());
		assertEquals(low.minimumDodgeTicks(), ProjectileEvasionPlanner.dodgeTicks(low, Double.NaN));
		assertEquals(high.maximumDodgeTicks(), ProjectileEvasionPlanner.dodgeTicks(high, 1.0));
		assertEquals(high.minimumDodgeTicks(), ProjectileEvasionPlanner.dodgeTicks(high, Double.POSITIVE_INFINITY));
	}
}

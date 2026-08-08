package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RangedSpacingPlannerTest {
	@Test
	void movementBandsSeparateKitingFromOrdinarySpacing() {
		assertEquals(
			RangedSpacingPlanner.MovementMode.KITE,
			RangedSpacingPlanner.chooseMovement(5.9 * 5.9, true, 10.0, false)
		);
		assertEquals(
			RangedSpacingPlanner.MovementMode.STRAFE,
			RangedSpacingPlanner.chooseMovement(6.0 * 6.0, true, 10.0, false)
		);
		assertEquals(
			RangedSpacingPlanner.MovementMode.APPROACH,
			RangedSpacingPlanner.chooseMovement(14.0 * 14.0, true, 10.0, false)
		);
	}

	@Test
	void emergencyThresholdsHaveHysteresisAndScaleWithIntelligence() {
		double lowTrigger = RangedSpacingPlanner.emergencyTriggerRange(10.0, 1);
		double highTrigger = RangedSpacingPlanner.emergencyTriggerRange(10.0, 10);
		double highSafe = RangedSpacingPlanner.emergencySafeRange(10.0, 10);
		assertTrue(highTrigger > lowTrigger);
		assertTrue(highSafe > highTrigger);
		assertTrue(RangedSpacingPlanner.shouldStartEmergencyDisengage(highTrigger * highTrigger - 0.01, 10.0, 10));
		assertFalse(RangedSpacingPlanner.shouldStartEmergencyDisengage(highTrigger * highTrigger, 10.0, 10));
		assertTrue(RangedSpacingPlanner.shouldContinueEmergencyDisengage(highTrigger * highTrigger, 10.0, 10));
		assertFalse(RangedSpacingPlanner.shouldContinueEmergencyDisengage(highSafe * highSafe, 10.0, 10));
	}

	@Test
	void difficultyRaisesSameEscapePercentileWithoutExceedingPreviousMaximum() {
		double easy = RangedSpacingPlanner.escapeSpeedFactor(DifficultyTier.EASY, 0.35);
		double normal = RangedSpacingPlanner.escapeSpeedFactor(DifficultyTier.NORMAL, 0.35);
		double hard = RangedSpacingPlanner.escapeSpeedFactor(DifficultyTier.HARD, 0.35);
		assertTrue(easy < normal);
		assertTrue(normal < hard);
		for (DifficultyTier difficulty : DifficultyTier.values()) {
			assertEquals(1.0, RangedSpacingPlanner.escapeSpeedFactor(difficulty, 1.0));
			assertTrue(RangedSpacingPlanner.escapeSpeedFactor(difficulty, 0.0) >= 0.68);
		}
	}

	@Test
	void intelligenceControlsKiteInputsSpeedAndRefreshCadence() {
		assertTrue(RangedSpacingPlanner.kiteBackwardInput(10) > RangedSpacingPlanner.kiteBackwardInput(1));
		assertTrue(RangedSpacingPlanner.kiteSidewaysInput(10) > RangedSpacingPlanner.kiteSidewaysInput(1));
		assertTrue(RangedSpacingPlanner.maximumEscapePathSpeed(10)
			> RangedSpacingPlanner.maximumEscapePathSpeed(1));
		assertTrue(RangedSpacingPlanner.pathRefreshTicks(10) < RangedSpacingPlanner.pathRefreshTicks(1));
	}
}

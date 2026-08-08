package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CrossbowLoadoutPlannerTest {
	@Test
	void difficultyAndIntelligenceIncreaseCrossbowChance() {
		double easyLow = CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.EASY, 1);
		double hardLow = CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.HARD, 1);
		double hardHigh = CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.HARD, 10);
		assertTrue(hardLow > easyLow);
		assertTrue(hardHigh > hardLow);
		assertEquals(0.0, CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.PEACEFUL, 10));
	}

	@Test
	void fireworksRequireMasteryAndScaleWithDifficulty() {
		assertEquals(
			0.0,
			CrossbowLoadoutPlanner.effectiveFireworkChance(0.25, DifficultyTier.HARD, 6)
		);
		assertTrue(
			CrossbowLoadoutPlanner.effectiveFireworkChance(0.25, DifficultyTier.HARD, 10)
				> CrossbowLoadoutPlanner.effectiveFireworkChance(0.25, DifficultyTier.HARD, 7)
		);
	}

	@Test
	void probabilityAndRocketSamplesAreClamped() {
		assertTrue(CrossbowLoadoutPlanner.succeeds(2.0, 0.99));
		assertFalse(CrossbowLoadoutPlanner.succeeds(Double.NaN, 0.0));
		assertEquals(3, CrossbowLoadoutPlanner.rocketCount(DifficultyTier.EASY, 1, Double.NaN));
		assertEquals(9, CrossbowLoadoutPlanner.rocketCount(DifficultyTier.HARD, 10, 1.0));
		for (DifficultyTier difficulty : DifficultyTier.values()) {
			for (int intelligence = -5; intelligence <= 20; intelligence++) {
				int count = CrossbowLoadoutPlanner.rocketCount(difficulty, intelligence, 0.5);
				assertTrue(count >= CrossbowLoadoutPlanner.MINIMUM_ROCKETS);
				assertTrue(count <= CrossbowLoadoutPlanner.MAXIMUM_ROCKETS);
			}
		}
	}
}

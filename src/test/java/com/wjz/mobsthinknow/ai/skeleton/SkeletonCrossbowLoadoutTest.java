package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.ai.CrossbowLoadoutPlanner;
import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
import org.junit.jupiter.api.Test;

class SkeletonCrossbowLoadoutTest {
	@Test
	void crossbowChanceRisesWithDifficultyAndIntelligence() {
		double easyLow = CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.EASY, 1);
		double hardLow = CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.HARD, 1);
		double hardHigh = CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.HARD, 10);
		assertTrue(hardLow > easyLow);
		assertTrue(hardHigh > hardLow);
		assertEquals(0.0, CrossbowLoadoutPlanner.effectiveCrossbowChance(0.18, DifficultyTier.PEACEFUL, 10));
	}

	@Test
	void explosiveCrossbowsAreRestrictedToHighIntelligence() {
		assertEquals(0.0, CrossbowLoadoutPlanner.effectiveFireworkChance(0.25, DifficultyTier.HARD, 6));
		assertTrue(
			CrossbowLoadoutPlanner.effectiveFireworkChance(0.25, DifficultyTier.HARD, 10)
				> CrossbowLoadoutPlanner.effectiveFireworkChance(0.25, DifficultyTier.HARD, 7)
		);
	}

}

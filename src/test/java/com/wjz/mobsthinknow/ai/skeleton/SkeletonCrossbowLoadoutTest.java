package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkeletonCrossbowLoadoutTest {
	@Test
	void crossbowChanceRisesWithDifficultyAndIntelligence() {
		double easyLow = SkeletonCrossbowLoadout.effectiveCrossbowChance(0.18, 1, 1);
		double hardLow = SkeletonCrossbowLoadout.effectiveCrossbowChance(0.18, 3, 1);
		double hardHigh = SkeletonCrossbowLoadout.effectiveCrossbowChance(0.18, 3, 10);
		assertTrue(hardLow > easyLow);
		assertTrue(hardHigh > hardLow);
		assertEquals(0.0, SkeletonCrossbowLoadout.effectiveCrossbowChance(0.18, 0, 10));
	}

	@Test
	void explosiveCrossbowsAreRestrictedToHighIntelligence() {
		assertEquals(0.0, SkeletonCrossbowLoadout.effectiveFireworkChance(0.25, 3, 6));
		assertTrue(
			SkeletonCrossbowLoadout.effectiveFireworkChance(0.25, 3, 10)
				> SkeletonCrossbowLoadout.effectiveFireworkChance(0.25, 3, 7)
		);
	}

}

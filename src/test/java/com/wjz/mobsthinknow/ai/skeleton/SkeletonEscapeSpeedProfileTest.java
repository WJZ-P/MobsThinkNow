package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkeletonEscapeSpeedProfileTest {
	@Test
	void higherDifficultyRaisesTheSameIndividualsSpeedPercentile() {
		float easy = SkeletonEscapeSpeedProfile.factorForRoll(1, 0.35F);
		float normal = SkeletonEscapeSpeedProfile.factorForRoll(2, 0.35F);
		float hard = SkeletonEscapeSpeedProfile.factorForRoll(3, 0.35F);
		assertTrue(easy < normal);
		assertTrue(normal < hard);
	}

	@Test
	void previousSpeedCurveRemainsTheAbsoluteMaximum() {
		for (int difficulty = 0; difficulty <= 3; difficulty++) {
			assertEquals(1.0F, SkeletonEscapeSpeedProfile.factorForRoll(difficulty, 1.0F));
			assertTrue(SkeletonEscapeSpeedProfile.factorForRoll(difficulty, 0.0F) >= 0.68F);
			assertTrue(SkeletonEscapeSpeedProfile.factorForRoll(difficulty, 0.5F) <= 1.0F);
		}
	}
}

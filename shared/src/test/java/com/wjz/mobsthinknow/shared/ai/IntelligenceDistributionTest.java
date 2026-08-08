package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IntelligenceDistributionTest {
	@Test
	void difficultyRaisesBothEndsOfTheSpawnRange() {
		assertEquals(new IntelligenceDistribution.IntRange(1, 7), IntelligenceDistribution.rangeFor(DifficultyTier.EASY));
		assertEquals(new IntelligenceDistribution.IntRange(2, 9), IntelligenceDistribution.rangeFor(DifficultyTier.NORMAL));
		assertEquals(new IntelligenceDistribution.IntRange(4, 10), IntelligenceDistribution.rangeFor(DifficultyTier.HARD));
	}

	@Test
	void unitSamplesMapToInclusiveRangeWithoutEscaping() {
		assertEquals(2, IntelligenceDistribution.roll(DifficultyTier.NORMAL, 0.0));
		assertEquals(9, IntelligenceDistribution.roll(DifficultyTier.NORMAL, 1.0));
		assertEquals(2, IntelligenceDistribution.roll(DifficultyTier.NORMAL, Double.NaN));
	}

	@Test
	void clampKeepsPersistedValuesInsideProtocolRange() {
		assertEquals(1, IntelligenceDistribution.clamp(-10));
		assertEquals(7, IntelligenceDistribution.clamp(7));
		assertEquals(10, IntelligenceDistribution.clamp(99));
	}
}

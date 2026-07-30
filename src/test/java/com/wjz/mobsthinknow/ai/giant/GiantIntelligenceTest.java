package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class GiantIntelligenceTest {
	@Test
	void difficultyRaisesBirthRangeAndAverage() {
		GiantIntelligence.IntRange easy = GiantIntelligence.rangeForDifficulty(Difficulty.EASY);
		GiantIntelligence.IntRange normal = GiantIntelligence.rangeForDifficulty(Difficulty.NORMAL);
		GiantIntelligence.IntRange hard = GiantIntelligence.rangeForDifficulty(Difficulty.HARD);

		assertEquals(new GiantIntelligence.IntRange(2, 7), easy);
		assertEquals(new GiantIntelligence.IntRange(4, 9), normal);
		assertEquals(new GiantIntelligence.IntRange(6, 10), hard);
		assertTrue(midpoint(easy) < midpoint(normal));
		assertTrue(midpoint(normal) < midpoint(hard));
	}

	private static double midpoint(final GiantIntelligence.IntRange range) {
		return (range.minimum() + range.maximum()) * 0.5;
	}
}

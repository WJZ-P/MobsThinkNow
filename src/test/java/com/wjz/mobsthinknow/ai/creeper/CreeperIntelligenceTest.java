package com.wjz.mobsthinknow.ai.creeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class CreeperIntelligenceTest {
	@Test
	void difficultyRaisesBothMinimumAndAverageBirthIntelligence() {
		CreeperIntelligence.IntRange easy = CreeperIntelligence.rangeForDifficulty(Difficulty.EASY);
		CreeperIntelligence.IntRange normal = CreeperIntelligence.rangeForDifficulty(Difficulty.NORMAL);
		CreeperIntelligence.IntRange hard = CreeperIntelligence.rangeForDifficulty(Difficulty.HARD);

		assertEquals(new CreeperIntelligence.IntRange(1, 7), easy);
		assertEquals(new CreeperIntelligence.IntRange(2, 9), normal);
		assertEquals(new CreeperIntelligence.IntRange(4, 10), hard);
		assertTrue(midpoint(easy) < midpoint(normal));
		assertTrue(midpoint(normal) < midpoint(hard));
	}

	private static double midpoint(final CreeperIntelligence.IntRange range) {
		return (range.minimum() + range.maximum()) * 0.5;
	}
}

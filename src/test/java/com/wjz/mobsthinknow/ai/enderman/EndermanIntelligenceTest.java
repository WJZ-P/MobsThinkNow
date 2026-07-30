package com.wjz.mobsthinknow.ai.enderman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class EndermanIntelligenceTest {
	@Test
	void difficultyRaisesBothMinimumAndAverageBirthIntelligence() {
		EndermanIntelligence.IntRange easy = EndermanIntelligence.rangeForDifficulty(Difficulty.EASY);
		EndermanIntelligence.IntRange normal = EndermanIntelligence.rangeForDifficulty(Difficulty.NORMAL);
		EndermanIntelligence.IntRange hard = EndermanIntelligence.rangeForDifficulty(Difficulty.HARD);

		assertEquals(new EndermanIntelligence.IntRange(1, 7), easy);
		assertEquals(new EndermanIntelligence.IntRange(2, 9), normal);
		assertEquals(new EndermanIntelligence.IntRange(4, 10), hard);
		assertTrue(midpoint(easy) < midpoint(normal));
		assertTrue(midpoint(normal) < midpoint(hard));
	}

	private static double midpoint(final EndermanIntelligence.IntRange range) {
		return (range.minimum() + range.maximum()) * 0.5;
	}
}

package com.wjz.mobsthinknow.ai.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class SpiderIntelligenceTest {
	@Test
	void difficultyRaisesNaturalBirthRange() {
		assertEquals(1, SpiderIntelligence.rangeForDifficulty(Difficulty.EASY).minimum());
		assertEquals(7, SpiderIntelligence.rangeForDifficulty(Difficulty.EASY).maximum());
		assertEquals(2, SpiderIntelligence.rangeForDifficulty(Difficulty.NORMAL).minimum());
		assertEquals(9, SpiderIntelligence.rangeForDifficulty(Difficulty.NORMAL).maximum());
		assertEquals(4, SpiderIntelligence.rangeForDifficulty(Difficulty.HARD).minimum());
		assertEquals(10, SpiderIntelligence.rangeForDifficulty(Difficulty.HARD).maximum());
	}

	@Test
	void rolledValuesStayInsideTheirDifficultyRange() {
		RandomSource random = RandomSource.create(12345L);
		for (int index = 0; index < 100; index++) {
			int intelligence = SpiderIntelligence.roll(Difficulty.HARD, random);
			assertTrue(intelligence >= 4 && intelligence <= 10);
		}
	}
}

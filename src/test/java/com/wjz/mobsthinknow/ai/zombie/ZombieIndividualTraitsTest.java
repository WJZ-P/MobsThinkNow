package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class ZombieIndividualTraitsTest {
	@Test
	void hardDifficultyRaisesEveryTraitAverageForSameRoll() {
		for (ZombieIndividualTraits.Trait trait : ZombieIndividualTraits.Trait.values()) {
			double easy = ZombieIndividualTraits.traitAmount(Difficulty.EASY, 0.5, 0.5, trait);
			double hard = ZombieIndividualTraits.traitAmount(Difficulty.HARD, 0.5, 0.5, trait);
			assertTrue(hard > easy, () -> trait + " did not increase with difficulty");
		}
	}

	@Test
	void individualRollStillProducesVariationInsideOneDifficulty() {
		double slow = ZombieIndividualTraits.traitAmount(
			Difficulty.NORMAL, 0.5, 0.0, ZombieIndividualTraits.Trait.SPEED
		);
		double fast = ZombieIndividualTraits.traitAmount(
			Difficulty.NORMAL, 0.5, 1.0, ZombieIndividualTraits.Trait.SPEED
		);
		assertTrue(fast > slow);
	}
}

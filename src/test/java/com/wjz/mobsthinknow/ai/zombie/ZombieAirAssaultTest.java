package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class ZombieAirAssaultTest {
	@Test
	void rocketCountAlwaysStaysInsideRequestedRange() {
		for (Difficulty difficulty : Difficulty.values()) {
			assertEquals(ZombieAirAssault.MINIMUM_ROCKETS, ZombieAirAssault.rocketCount(difficulty, 0.0));
			assertEquals(ZombieAirAssault.MAXIMUM_ROCKETS, ZombieAirAssault.rocketCount(difficulty, 1.0));
			for (int sample = 0; sample <= 1000; sample++) {
				int count = ZombieAirAssault.rocketCount(difficulty, sample / 1000.0);
				assertTrue(count >= 16 && count <= 64);
			}
		}
	}

	@Test
	void higherDifficultyRaisesTheSameRandomRollAndTheAverage() {
		long easyTotal = 0L;
		long normalTotal = 0L;
		long hardTotal = 0L;
		for (int sample = 0; sample <= 1000; sample++) {
			double roll = sample / 1000.0;
			int easy = ZombieAirAssault.rocketCount(Difficulty.EASY, roll);
			int normal = ZombieAirAssault.rocketCount(Difficulty.NORMAL, roll);
			int hard = ZombieAirAssault.rocketCount(Difficulty.HARD, roll);
			assertTrue(easy <= normal && normal <= hard);
			easyTotal += easy;
			normalTotal += normal;
			hardTotal += hard;
		}
		assertTrue(easyTotal < normalTotal && normalTotal < hardTotal);
	}

	@Test
	void invalidRollFallsBackToMinimumInsteadOfLeakingAnInvalidStackSize() {
		assertEquals(16, ZombieAirAssault.rocketCount(Difficulty.HARD, Double.NaN));
		assertEquals(16, ZombieAirAssault.rocketCount(Difficulty.HARD, Double.NEGATIVE_INFINITY));
		assertEquals(16, ZombieAirAssault.rocketCount(Difficulty.EASY, Double.POSITIVE_INFINITY));
	}
}

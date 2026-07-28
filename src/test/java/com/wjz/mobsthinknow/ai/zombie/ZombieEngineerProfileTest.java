package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class ZombieEngineerProfileTest {
	@Test
	void scalesEligibleEngineerChanceByDifficulty() {
		assertEquals(0.0, ZombieEngineerProfile.effectiveChance(0.08, Difficulty.PEACEFUL, 1.0F), 0.000001);
		assertEquals(0.06, ZombieEngineerProfile.effectiveChance(0.08, Difficulty.EASY, 1.0F), 0.000001);
		assertEquals(0.08, ZombieEngineerProfile.effectiveChance(0.08, Difficulty.NORMAL, 1.0F), 0.000001);
		assertEquals(0.10, ZombieEngineerProfile.effectiveChance(0.08, Difficulty.HARD, 1.0F), 0.000001);
	}

	@Test
	void appliesRegionalFloorAndClampsFinalProbability() {
		assertEquals(0.09, ZombieEngineerProfile.effectiveChance(0.08, Difficulty.HARD, 0.0F), 0.000001);
		assertEquals(1.0, ZombieEngineerProfile.effectiveChance(5.0, Difficulty.HARD, 1.0F), 0.000001);
		assertEquals(0.0, ZombieEngineerProfile.effectiveChance(Double.NaN, Difficulty.HARD, 1.0F), 0.000001);
	}

	@Test
	void treatsThresholdAsExclusiveProbabilityBoundary() {
		assertTrue(ZombieEngineerProfile.shouldAssign(0.0799, 0.08, Difficulty.NORMAL, 1.0F));
		assertFalse(ZombieEngineerProfile.shouldAssign(0.08, 0.08, Difficulty.NORMAL, 1.0F));
		assertFalse(ZombieEngineerProfile.shouldAssign(Double.NaN, 0.08, Difficulty.NORMAL, 1.0F));
	}
}

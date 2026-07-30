package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import org.junit.jupiter.api.Test;

class GiantZombieProfileTest {
	@Test
	void replacementChanceScalesWithDifficulty() {
		assertEquals(0.0, GiantZombieProfile.chanceFor(Difficulty.PEACEFUL, 0.01));
		assertEquals(0.004, GiantZombieProfile.chanceFor(Difficulty.EASY, 0.01));
		assertEquals(0.01, GiantZombieProfile.chanceFor(Difficulty.NORMAL, 0.01));
		assertEquals(0.02, GiantZombieProfile.chanceFor(Difficulty.HARD, 0.01));
		assertEquals(1.0, GiantZombieProfile.chanceFor(Difficulty.HARD, 0.75));
	}

	@Test
	void commandLoadConversionAndJockeySpawnsNeverRecursivelyReplace() {
		assertFalse(GiantZombieProfile.eligibleSpawnReason(EntitySpawnReason.COMMAND));
		assertFalse(GiantZombieProfile.eligibleSpawnReason(EntitySpawnReason.LOAD));
		assertFalse(GiantZombieProfile.eligibleSpawnReason(EntitySpawnReason.CONVERSION));
		assertFalse(GiantZombieProfile.eligibleSpawnReason(EntitySpawnReason.JOCKEY));
		assertFalse(GiantZombieProfile.eligibleSpawnReason(EntitySpawnReason.DIMENSION_TRAVEL));
		assertTrue(GiantZombieProfile.eligibleSpawnReason(EntitySpawnReason.NATURAL));
	}

	@Test
	void rollUsesStrictUpperBoundaryAndRespectsMasterSwitches() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.giantZombieSpawnChance = 0.10;
		assertTrue(GiantZombieProfile.shouldReplace(Difficulty.NORMAL, EntitySpawnReason.NATURAL, 0.0999, config));
		assertFalse(GiantZombieProfile.shouldReplace(Difficulty.NORMAL, EntitySpawnReason.NATURAL, 0.10, config));
		config.giantZombieAiEnabled = false;
		assertFalse(GiantZombieProfile.shouldReplace(Difficulty.HARD, EntitySpawnReason.NATURAL, 0.0, config));
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

class ZombieSpecialEquipmentTest {
	@Test
	void oneRollPartitionsWaterThenLavaThenOrdinary() {
		assertEquals(UtilityClass.WATER, ZombieSpecialEquipment.selectUtility(0.01, 0.04, 0.02, 1.0));
		assertEquals(UtilityClass.LAVA, ZombieSpecialEquipment.selectUtility(0.05, 0.04, 0.02, 1.0));
		assertEquals(UtilityClass.NONE, ZombieSpecialEquipment.selectUtility(0.07, 0.04, 0.02, 1.0));
	}

	@Test
	void hardDifficultyRaisesSpecialistOdds() {
		assertTrue(
			ZombieSpecialEquipment.difficultyFactor(Difficulty.HARD, 1.0)
				> ZombieSpecialEquipment.difficultyFactor(Difficulty.EASY, 1.0)
		);
	}

	@Test
	void overfullConfiguredOddsKeepTheirRelativeWeights() {
		assertEquals(UtilityClass.WATER, ZombieSpecialEquipment.selectUtility(0.49, 1.0, 1.0, 2.0));
		assertEquals(UtilityClass.LAVA, ZombieSpecialEquipment.selectUtility(0.51, 1.0, 1.0, 2.0));
	}

	@Test
	void configuredDefaultMatchesVanillaEquipmentDropChance() {
		assertEquals(0.085F, ZombieSpecialEquipment.vanillaEquipmentDropChance(), 0.00001F);
	}
}

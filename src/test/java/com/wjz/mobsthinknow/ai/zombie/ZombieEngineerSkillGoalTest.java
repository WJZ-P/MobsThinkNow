package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import org.junit.jupiter.api.Test;

class ZombieEngineerSkillGoalTest {
	@Test
	void mapsRandomRollToInclusiveSixToTenSecondDelay() {
		assertEquals(120, ZombieEngineerSkillGoal.skillDelayTicks(0.0));
		assertEquals(160, ZombieEngineerSkillGoal.skillDelayTicks(0.50));
		assertEquals(200, ZombieEngineerSkillGoal.skillDelayTicks(1.0));
		assertEquals(120, ZombieEngineerSkillGoal.skillDelayTicks(Double.NaN));
	}

	@Test
	void usesShorterLavaHoldAndStableEntityIdJitter() {
		assertEquals(45, ZombieEngineerSkillGoal.fluidHoldTicks(UtilityClass.WATER, 0));
		assertEquals(60, ZombieEngineerSkillGoal.fluidHoldTicks(UtilityClass.WATER, 15));
		assertEquals(32, ZombieEngineerSkillGoal.fluidHoldTicks(UtilityClass.LAVA, 0));
		assertEquals(41, ZombieEngineerSkillGoal.fluidHoldTicks(UtilityClass.LAVA, 9));
	}

	@Test
	void directIgnitionLastsFiveSeconds() {
		assertEquals(5.0F, ZombieEngineerSkillGoal.ignitionDurationSeconds());
	}
}

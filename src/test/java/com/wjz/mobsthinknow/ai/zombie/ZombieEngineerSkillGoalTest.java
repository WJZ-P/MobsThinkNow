package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	void repairsOneQuarterOfMaximumDurabilityWithOnePointFloor() {
		assertEquals(1, ZombieEngineerSkillGoal.repairAmount(1));
		assertEquals(1, ZombieEngineerSkillGoal.repairAmount(3));
		assertEquals(25, ZombieEngineerSkillGoal.repairAmount(100));
		assertEquals(390, ZombieEngineerSkillGoal.repairAmount(1561));
	}
}

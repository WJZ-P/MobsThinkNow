package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SquadAssaultPlannerTest {
	@Test
	void lowIntelligenceFallsBackToSwarmEvenWithACompleteRoster() {
		assertEquals(
			SquadAssaultPlan.SWARM,
			SquadAssaultPlanner.choose(new SquadComposition(2, 2, 1, 2, 1, 1), 4)
		);
	}

	@Test
	void fourCoreSpeciesUnlockCombinedArmsForBrilliantLeader() {
		assertEquals(
			SquadAssaultPlan.COMBINED_ARMS,
			SquadAssaultPlanner.choose(new SquadComposition(1, 1, 1, 1, 0, 0), 8)
		);
	}

	@Test
	void spiderAndCreeperUnlockMountedBreach() {
		assertEquals(
			SquadAssaultPlan.MOUNTED_BREACH,
			SquadAssaultPlanner.choose(new SquadComposition(0, 0, 1, 1, 0, 0), 7)
		);
	}

	@Test
	void twoShootersAndMeleeUnlockCrossfire() {
		assertEquals(
			SquadAssaultPlan.CROSSFIRE,
			SquadAssaultPlanner.choose(new SquadComposition(1, 2, 0, 0, 0, 0), 7)
		);
	}

	@Test
	void shieldAndFireSupportUnlockShieldWedge() {
		assertEquals(
			SquadAssaultPlan.SHIELD_WEDGE,
			SquadAssaultPlanner.choose(new SquadComposition(2, 1, 0, 0, 1, 0), 6)
		);
	}

	@Test
	void mixedMeleeAndRangedUnlockPinAndFlank() {
		assertEquals(
			SquadAssaultPlan.PIN_AND_FLANK,
			SquadAssaultPlanner.choose(new SquadComposition(1, 1, 0, 0, 0, 0), 5)
		);
	}

	@Test
	void completeCombinedArmsRosterHasPriorityOverSpecialistPlans() {
		assertEquals(
			SquadAssaultPlan.COMBINED_ARMS,
			SquadAssaultPlanner.choose(new SquadComposition(2, 2, 1, 2, 1, 1), 10)
		);
	}
}

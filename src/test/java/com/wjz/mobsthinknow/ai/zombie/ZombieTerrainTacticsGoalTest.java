package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZombieTerrainTacticsGoalTest {
	@Test
	void elevatedTargetHeightUsesOnlyTheNecessaryWholeBlocks() {
		assertEquals(0, ZombieTerrainTacticsGoal.requiredElevationPillarHeight(64.0, 65.99, 4));
		assertEquals(2, ZombieTerrainTacticsGoal.requiredElevationPillarHeight(64.0, 66.0, 4));
		assertEquals(3, ZombieTerrainTacticsGoal.requiredElevationPillarHeight(64.0, 67.0, 4));
		assertEquals(4, ZombieTerrainTacticsGoal.requiredElevationPillarHeight(64.0, 67.25, 4));
	}

	@Test
	void elevatedTargetBeyondTheBoundedBuildHeightIsRejected() {
		assertEquals(0, ZombieTerrainTacticsGoal.requiredElevationPillarHeight(64.0, 68.01, 4));
		assertEquals(0, ZombieTerrainTacticsGoal.requiredElevationPillarHeight(64.0, 67.0, 0));
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SquadShieldWallPlannerTest {
	@Test
	void twoShieldBearersReceiveMirroredNonOverlappingSlots() {
		SquadShieldWallPlanner.Slot left = SquadShieldWallPlanner.slotFor(0, 2);
		SquadShieldWallPlanner.Slot right = SquadShieldWallPlanner.slotFor(1, 2);

		assertEquals(-0.625, left.lateralOffset());
		assertEquals(0.625, right.lateralOffset());
		assertEquals(0.0, left.depthOffset());
		assertEquals(0.0, right.depthOffset());
	}

	@Test
	void largeWallsWrapIntoCenteredRowsInsteadOfGrowingWithoutBound() {
		assertEquals(-2.5, SquadShieldWallPlanner.slotFor(0, 7).lateralOffset());
		assertEquals(2.5, SquadShieldWallPlanner.slotFor(4, 7).lateralOffset());
		assertEquals(-0.625, SquadShieldWallPlanner.slotFor(5, 7).lateralOffset());
		assertEquals(0.625, SquadShieldWallPlanner.slotFor(6, 7).lateralOffset());
		assertEquals(1.15, SquadShieldWallPlanner.slotFor(6, 7).depthOffset());
	}

	@Test
	void holdBeatsKeepEveryShieldClosed() {
		assertEquals(-1, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.PREPARE, 2L, 8L, 3));
		assertEquals(-1, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.SUPPRESS, 2L, 4L, 3));
		assertEquals(-1, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.RESET, 2L, 4L, 3));
	}

	@Test
	void exactlyOneStrikerRotatesAcrossCommitAndExploit() {
		assertEquals(1, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.COMMIT, 1L, 0L, 3));
		assertEquals(2, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.COMMIT, 1L, 16L, 3));
		// EXPLOIT starts after the 24-tick COMMIT phase, so it initially keeps rank 2.
		assertEquals(2, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.EXPLOIT, 1L, 0L, 3));
		assertEquals(0, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.EXPLOIT, 1L, 8L, 3));

		assertEquals(SquadShieldOrder.STRIKE, SquadShieldWallPlanner.orderFor(2, 2, 3));
		assertEquals(SquadShieldOrder.GUARD, SquadShieldWallPlanner.orderFor(1, 2, 3));
	}

	@Test
	void oneShieldFallsBackToItsExistingSoloStateMachine() {
		assertEquals(-1, SquadShieldWallPlanner.strikerRank(SquadCombatBeat.COMMIT, 0L, 0L, 1));
		assertEquals(SquadShieldOrder.NONE, SquadShieldWallPlanner.orderFor(0, -1, 1));
		assertThrows(IllegalArgumentException.class, () -> SquadShieldWallPlanner.slotFor(2, 2));
	}
}

package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadCombatBeat;
import org.junit.jupiter.api.Test;

class SkeletonVolleyTimingTest {
	@Test
	void shootersAreSpreadAcrossTheFinalFourPreCommitTicks() {
		long executeAt = 200L;
		boolean[] occupied = new boolean[4];
		for (long key = 1L; key <= 64L; key++) {
			long releaseAt = SkeletonVolleyTiming.releaseAt(executeAt, key);
			assertTrue(releaseAt >= executeAt - 3L && releaseAt <= executeAt);
			occupied[(int)(releaseAt - (executeAt - 3L))] = true;
		}
		for (boolean laneOccupied : occupied) {
			assertTrue(laneOccupied, "The stable hash left one volley lane unused.");
		}
	}

	@Test
	void suppressHoldsEachShooterUntilItsAssignedLane() {
		long executeAt = 100L;
		long key = 19L;
		long releaseAt = SkeletonVolleyTiming.releaseAt(executeAt, key);
		assertFalse(SkeletonVolleyTiming.mayRelease(
			SquadCombatBeat.SUPPRESS,
			executeAt,
			releaseAt - 1L,
			key,
			false
		));
		assertTrue(SkeletonVolleyTiming.mayRelease(
			SquadCombatBeat.SUPPRESS,
			executeAt,
			releaseAt,
			key,
			false
		));
	}

	@Test
	void defenseOverridesCadenceButNormalPreparationDoesNot() {
		assertFalse(SkeletonVolleyTiming.mayRelease(SquadCombatBeat.PREPARE, 80L, 60L, 7L, false));
		assertFalse(SkeletonVolleyTiming.mayRelease(SquadCombatBeat.RESET, 80L, 60L, 7L, false));
		assertTrue(SkeletonVolleyTiming.mayRelease(SquadCombatBeat.PREPARE, 80L, 60L, 7L, true));
		assertTrue(SkeletonVolleyTiming.mayRelease(SquadCombatBeat.COMMIT, 80L, 80L, 7L, false));
		assertTrue(SkeletonVolleyTiming.mayRelease(SquadCombatBeat.EXPLOIT, 80L, 90L, 7L, false));
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SquadCombatCadenceTest {
	@Test
	void deploymentSharesOnePrepareSuppressAndCommitTimeline() {
		long armedAt = 100L;
		long commitAt = 132L;
		assertEquals(SquadCombatBeat.PREPARE, SquadCombatCadence.deploymentWindow(armedAt, commitAt, 110L).beat());
		SquadCombatCadence.Window suppress = SquadCombatCadence.deploymentWindow(armedAt, commitAt, 124L);
		assertEquals(SquadCombatBeat.SUPPRESS, suppress.beat());
		assertEquals(commitAt, suppress.executeAt());
		assertEquals(commitAt, suppress.endsAt());
	}

	@Test
	void combatCycleMovesThroughAllBeatsAndReturnsToCommit() {
		long firstCommit = 200L;
		assertEquals(SquadCombatBeat.COMMIT, SquadCombatCadence.combatWindow(firstCommit, firstCommit).beat());
		assertEquals(SquadCombatBeat.EXPLOIT, SquadCombatCadence.combatWindow(firstCommit, firstCommit + 24L).beat());
		assertEquals(SquadCombatBeat.RESET, SquadCombatCadence.combatWindow(firstCommit, firstCommit + 64L).beat());
		assertEquals(SquadCombatBeat.PREPARE, SquadCombatCadence.combatWindow(firstCommit, firstCommit + 80L).beat());
		assertEquals(SquadCombatBeat.SUPPRESS, SquadCombatCadence.combatWindow(firstCommit, firstCommit + 104L).beat());
		SquadCombatCadence.Window next = SquadCombatCadence.combatWindow(
			firstCommit,
			firstCommit + SquadCombatCadence.CYCLE_TICKS
		);
		assertEquals(SquadCombatBeat.COMMIT, next.beat());
		assertEquals(1L, next.cycle());
	}

	@Test
	void initialDelayAlwaysLeavesEnoughTimeToDrawABow() {
		for (int intelligence = 1; intelligence <= 10; intelligence++) {
			for (long squadId = 1L; squadId <= 32L; squadId++) {
				assertTrue(SquadCombatCadence.initialCommitDelay(intelligence, squadId) >= 25);
			}
		}
	}
}

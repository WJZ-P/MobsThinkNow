package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SquadLeaderElectionTest {
	@Test
	void intelligenceWinsBeforeHealth() {
		int leader = SquadLeaderElection.elect(
			List.of(
				new SquadLeaderCandidate(10, 6, 20.0F),
				new SquadLeaderCandidate(11, 9, 1.0F),
				new SquadLeaderCandidate(12, 7, 20.0F)
			)
		).orElseThrow();

		assertEquals(11, leader);
	}

	@Test
	void healthThenEntityIdBreakTiesDeterministically() {
		int healthyLeader = SquadLeaderElection.elect(
			List.of(new SquadLeaderCandidate(8, 7, 18.0F), new SquadLeaderCandidate(3, 7, 12.0F))
		).orElseThrow();
		int lowerIdLeader = SquadLeaderElection.elect(
			List.of(new SquadLeaderCandidate(8, 7, 18.0F), new SquadLeaderCandidate(3, 7, 18.0F))
		).orElseThrow();

		assertEquals(8, healthyLeader);
		assertEquals(3, lowerIdLeader);
	}

	@Test
	void emptyElectionHasNoWinner() {
		assertTrue(SquadLeaderElection.elect(List.of()).isEmpty());
	}
}

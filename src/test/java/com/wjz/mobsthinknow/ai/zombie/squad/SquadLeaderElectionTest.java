package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SquadLeaderElectionTest {
	@Test
	void intelligenceAlwaysWinsBeforeTheRandomTicket() {
		int leader = SquadLeaderElection.elect(
			List.of(
				new SquadLeaderCandidate(10, 6, 1L),
				new SquadLeaderCandidate(11, 9, 99L),
				new SquadLeaderCandidate(12, 7, 0L)
			)
		).orElseThrow();

		assertEquals(11, leader);
	}

	@Test
	void randomTicketBreaksMaximumIntelligenceTieBeforeEntityId() {
		int randomWinner = SquadLeaderElection.elect(
			List.of(new SquadLeaderCandidate(3, 7, 80L), new SquadLeaderCandidate(8, 7, 12L))
		).orElseThrow();
		int idFallback = SquadLeaderElection.elect(
			List.of(new SquadLeaderCandidate(8, 7, 12L), new SquadLeaderCandidate(3, 7, 12L))
		).orElseThrow();

		assertEquals(8, randomWinner);
		assertEquals(3, idFallback);
	}

	@Test
	void emptyElectionHasNoWinner() {
		assertTrue(SquadLeaderElection.elect(List.of()).isEmpty());
	}
}

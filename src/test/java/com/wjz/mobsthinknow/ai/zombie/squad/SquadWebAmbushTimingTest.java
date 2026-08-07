package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class SquadWebAmbushTimingTest {
	@Test
	void confirmedWebContactSchedulesVolleyBeforeMeleeCommit() {
		SquadCombatCadence.Window opening = SquadWebAmbushTiming.window(100L, 100L, 100L, 4L);
		assertNotNull(opening);
		assertEquals(SquadCombatBeat.SUPPRESS, opening.beat());
		assertEquals(106L, opening.executeAt());
		assertEquals(4L, opening.cycle());

		SquadCombatCadence.Window commit = SquadWebAmbushTiming.window(100L, 106L, 106L, 4L);
		assertNotNull(commit);
		assertEquals(SquadCombatBeat.COMMIT, commit.beat());
		assertEquals(106L, commit.startedAt());
	}

	@Test
	void escapingBeforeTheOrderCancelsTheCharge() {
		assertNotNull(SquadWebAmbushTiming.window(20L, 22L, 24L, 0L));
		assertNull(SquadWebAmbushTiming.window(20L, 22L, 25L, 0L));
		assertNull(SquadWebAmbushTiming.window(20L, 22L, 26L, 0L));
	}

	@Test
	void committedChargeKeepsOnlyShortEscapeMomentumAndHasAHardEnd() {
		assertNotNull(SquadWebAmbushTiming.window(0L, 5L, 6L, 0L));
		assertNotNull(SquadWebAmbushTiming.window(0L, 5L, 13L, 0L));
		assertNull(SquadWebAmbushTiming.window(0L, 5L, 14L, 0L));

		assertNotNull(SquadWebAmbushTiming.window(0L, 29L, 29L, 0L));
		assertNull(SquadWebAmbushTiming.window(0L, 30L, 30L, 0L));
	}

	@Test
	void invalidOrFutureSnapshotsNeverCreateAWindow() {
		assertNull(SquadWebAmbushTiming.window(-1L, 0L, 0L, 0L));
		assertNull(SquadWebAmbushTiming.window(10L, 9L, 10L, 0L));
		assertNull(SquadWebAmbushTiming.window(10L, 10L, 9L, 0L));
	}
}

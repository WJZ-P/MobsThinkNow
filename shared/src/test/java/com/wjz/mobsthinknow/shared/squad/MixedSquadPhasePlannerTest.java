package com.wjz.mobsthinknow.shared.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MixedSquadPhasePlannerTest {
	private static final MixedSquadPhasePlanner.Timings TIMINGS =
		new MixedSquadPhasePlanner.Timings(40, 30, 40, 20);

	@Test
	void normalSequenceContainsVisibleBriefingAndDeployment() {
		assertEquals(
			MixedSquadState.BRIEFING,
			MixedSquadPhasePlanner.next(MixedSquadState.FORMING, 10, true, false, false, TIMINGS)
		);
		assertEquals(
			MixedSquadState.DEPLOYING,
			MixedSquadPhasePlanner.next(MixedSquadState.BRIEFING, 30, true, false, false, TIMINGS)
		);
		assertEquals(
			MixedSquadState.ENGAGING,
			MixedSquadPhasePlanner.next(MixedSquadState.DEPLOYING, 5, true, false, false, TIMINGS)
		);
	}

	@Test
	void primitiveTimingsMatchTheRecordEntryPoint() {
		for (MixedSquadState state : MixedSquadState.values()) {
			for (long elapsed : new long[] {-1L, 0L, 19L, 40L, 200L}) {
				for (boolean quorum : new boolean[] {false, true}) {
					assertEquals(
						MixedSquadPhasePlanner.next(state, elapsed, quorum, false, false, TIMINGS),
						MixedSquadPhasePlanner.next(state, elapsed, quorum, false, false, 40, 30, 40, 20)
					);
				}
			}
		}
	}

	@Test
	void closeThreatOverridesMeetingAndLeaderChangeReorganizes() {
		assertEquals(
			MixedSquadState.ENGAGING,
			MixedSquadPhasePlanner.next(MixedSquadState.BRIEFING, 1, false, true, false, TIMINGS)
		);
		assertEquals(
			MixedSquadState.REORGANIZING,
			MixedSquadPhasePlanner.next(MixedSquadState.ENGAGING, 200, true, false, true, TIMINGS)
		);
	}
}

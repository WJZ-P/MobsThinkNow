package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.squad.MixedSquadRole;
import org.junit.jupiter.api.Test;

class SquadVolleyPlannerTest {
	@Test
	void rightWingFiresHalfACycleAfterLeftWing() {
		int interval = 40;
		int left = SquadVolleyPlanner.releaseOffset(MixedSquadRole.RANGED_LEFT, 0, interval);
		int right = SquadVolleyPlanner.releaseOffset(MixedSquadRole.RANGED_RIGHT, 0, interval);
		assertEquals(interval / 2, Math.floorMod(right - left, interval));
	}

	@Test
	void nextReleaseIsNeverInThePastAndIsDeterministic() {
		long first = SquadVolleyPlanner.nextReleaseTick(123L, MixedSquadRole.RANGED_LEFT, 17, 40);
		long second = SquadVolleyPlanner.nextReleaseTick(123L, MixedSquadRole.RANGED_LEFT, 17, 40);
		assertEquals(first, second);
		assertTrue(first >= 123L);
		assertTrue(first < 163L);
	}

	@Test
	void intelligenceSpeedsCadenceAndChargingWithinBounds() {
		assertTrue(SquadVolleyPlanner.shotIntervalTicks(28, 10)
			< SquadVolleyPlanner.shotIntervalTicks(28, 1));
		assertTrue(SquadVolleyPlanner.chargeTicks(16, 10)
			< SquadVolleyPlanner.chargeTicks(16, 1));
		assertEquals(32, SquadVolleyPlanner.shotIntervalTicks(-99, 10));
		assertEquals(8, SquadVolleyPlanner.chargeTicks(-99, 10));
	}
}

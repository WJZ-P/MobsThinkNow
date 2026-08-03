package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SquadThreatMemoryTest {
	@Test
	void directAttackIsImmediatelyCredibleAndThenDecays() {
		SquadThreatMemory memory = new SquadThreatMemory();
		memory.observe(7, SquadThreatMemory.Evidence.DIRECT_ATTACK, 100L);

		assertEquals(80.0, memory.snapshot(100L).getFirst().score());
		assertEquals(70.0, memory.snapshot(120L).getFirst().score());
		assertTrue(memory.snapshot(260L).isEmpty());
	}

	@Test
	void memoryIsBoundedAndRepeatedVisibleEvidenceAccumulates() {
		SquadThreatMemory memory = new SquadThreatMemory();
		for (int observation = 0; observation < 8; observation++) {
			memory.observe(1, SquadThreatMemory.Evidence.VISIBLE_TARGET, observation);
		}
		assertTrue(memory.snapshot(8L).getFirst().score() >= 60.0);

		for (int target = 2; target <= 12; target++) {
			memory.observe(target, SquadThreatMemory.Evidence.DIRECT_ATTACK, 10L);
		}
		assertEquals(SquadThreatMemory.MAXIMUM_THREATS, memory.snapshot(10L).size());
	}

	@Test
	void reportsWhetherANewWeakThreatSurvivedBoundedEviction() {
		SquadThreatMemory memory = new SquadThreatMemory();
		for (int target = 1; target <= SquadThreatMemory.MAXIMUM_THREATS; target++) {
			memory.observe(target, SquadThreatMemory.Evidence.DIRECT_ATTACK, 0L);
		}

		SquadThreatMemory.ObservationResult result = memory.observe(
			99,
			SquadThreatMemory.Evidence.VISIBLE_TARGET,
			0L
		);
		assertTrue(!result.retained());
		assertEquals(99, result.evictedTargetId());
	}
}

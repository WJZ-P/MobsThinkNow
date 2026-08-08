package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.entity.CreatureSpawnEvent;
import org.junit.jupiter.api.Test;

class PaperSkeletonLoadoutServiceTest {
	@Test
	void onlyWorldGeneratedSkeletonsReceiveRandomProfessions() {
		assertTrue(PaperSkeletonLoadoutService.isEligibleReason(CreatureSpawnEvent.SpawnReason.NATURAL));
		assertTrue(PaperSkeletonLoadoutService.isEligibleReason(CreatureSpawnEvent.SpawnReason.JOCKEY));
		assertTrue(PaperSkeletonLoadoutService.isEligibleReason(CreatureSpawnEvent.SpawnReason.TRAP));
		assertFalse(PaperSkeletonLoadoutService.isEligibleReason(CreatureSpawnEvent.SpawnReason.COMMAND));
		assertFalse(PaperSkeletonLoadoutService.isEligibleReason(CreatureSpawnEvent.SpawnReason.CUSTOM));
		assertFalse(PaperSkeletonLoadoutService.isEligibleReason(CreatureSpawnEvent.SpawnReason.SPAWNER));
	}
}

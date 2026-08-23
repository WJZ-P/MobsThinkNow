package com.wjz.mobsthinknow.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

final class MtnPaperCommandTest {
	@Test
	void ordinarySenderOnlyCompletesPublicActions() {
		assertEquals(List.of("status", "inspect"), MtnPaperCommand.actionSuggestions(false, ""));
		assertTrue(MtnPaperCommand.actionSuggestions(false, "re").isEmpty());
	}

	@Test
	void administratorCompletesMutationAndDiagnosticActionsCaseInsensitively() {
		assertEquals(List.of("reload"), MtnPaperCommand.actionSuggestions(true, "RE"));
		assertEquals(List.of("spawn", "spawnall"), MtnPaperCommand.actionSuggestions(true, "spawn"));
	}

	@Test
	void spawnPlacementReservesTheFullWitherSkeletonHeight() {
		assertEquals(3, PaperTestSpawner.requiredClearanceBlocks(EntityType.WITHER_SKELETON));
		assertEquals(2, PaperTestSpawner.requiredClearanceBlocks(EntityType.PARCHED));
		assertEquals(2, PaperTestSpawner.requiredClearanceBlocks(EntityType.SPIDER));
	}
}

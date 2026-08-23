package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class SquadDangerMemoryTest {
	@Test
	void mergesSeverityAndExpiresWithoutGrowing() {
		SquadDangerMemory memory = new SquadDangerMemory(4);
		BlockPos position = new BlockPos(4, 64, -2);

		memory.report(position, SquadDangerKind.ROUTE_BLOCKED, 1, 10L, 10L);
		memory.report(position, SquadDangerKind.DANGEROUS_DROP, 3, 12L, 20L);

		assertEquals(1, memory.activeEntryCount(20L));
		assertEquals(3, memory.snapshot(20L).getFirst().severity());
		assertEquals(SquadDangerKind.DANGEROUS_DROP, memory.snapshot(20L).getFirst().kind());
		assertTrue(memory.isDangerousNear(position.offset(1, 1, 0), 1, 1, 20L));
		assertFalse(memory.isDangerousNear(position.offset(2, 0, 0), 1, 1, 20L));
		assertEquals(0, memory.activeEntryCount(33L));
	}

	@Test
	void capacityEvictsTheEntryThatExpiresFirst() {
		SquadDangerMemory memory = new SquadDangerMemory(2);
		BlockPos longLived = new BlockPos(0, 64, 0);
		BlockPos shortLived = new BlockPos(1, 64, 0);
		BlockPos replacement = new BlockPos(2, 64, 0);

		memory.report(longLived, SquadDangerKind.ROUTE_BLOCKED, 1, 0L, 100L);
		memory.report(shortLived, SquadDangerKind.FLUID, 1, 0L, 20L);
		memory.report(replacement, SquadDangerKind.OPENABLE_TRAP, 2, 1L, 50L);

		assertEquals(2, memory.activeEntryCount(1L));
		assertTrue(memory.isDangerousNear(longLived, 0, 0, 1L));
		assertFalse(memory.isDangerousNear(shortLived, 0, 0, 1L));
		assertTrue(memory.isDangerousNear(replacement, 0, 0, 1L));
	}

	@Test
	void expirySaturatesInsteadOfWrappingIntoThePast() {
		SquadDangerMemory memory = new SquadDangerMemory(1);
		BlockPos position = new BlockPos(0, 64, 0);
		memory.report(position, SquadDangerKind.ROUTE_BLOCKED, 1, Long.MAX_VALUE - 2L, 20L);
		assertEquals(Long.MAX_VALUE, memory.snapshot(Long.MAX_VALUE - 1L).getFirst().expiresAt());
		assertEquals(1, memory.activeEntryCount(Long.MAX_VALUE));
	}
}

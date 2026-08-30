package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PaperWebTrapServiceTest {
	@Test
	void chunkIndexIsolatesWorldsHandlesNegativeCoordinatesAndUnlinksEmptyBuckets() {
		PaperWebTrapService.ChunkIndex index = new PaperWebTrapService.ChunkIndex();
		UUID world = UUID.randomUUID();
		UUID otherWorld = UUID.randomUUID();
		PaperWebTrapService.BlockKey origin = new PaperWebTrapService.BlockKey(world, 15, 64, 15);
		PaperWebTrapService.BlockKey east = new PaperWebTrapService.BlockKey(world, 16, 64, 15);
		PaperWebTrapService.BlockKey negative = new PaperWebTrapService.BlockKey(world, -1, 64, -1);
		PaperWebTrapService.BlockKey other = new PaperWebTrapService.BlockKey(otherWorld, 15, 64, 15);
		for (PaperWebTrapService.BlockKey key : List.of(origin, east, negative, other)) {
			index.add(key);
		}

		assertEquals(List.of(origin), index.snapshot(world, 0, 0));
		assertEquals(List.of(east), index.snapshot(world, 1, 0));
		assertEquals(List.of(negative), index.snapshot(world, -1, -1));
		assertEquals(List.of(other), index.snapshot(otherWorld, 0, 0));
		assertEquals(4, index.chunkCount());

		index.remove(negative);
		assertEquals(List.of(), index.snapshot(world, -1, -1));
		assertEquals(3, index.chunkCount());
		index.clear();
		assertEquals(0, index.chunkCount());
	}
}

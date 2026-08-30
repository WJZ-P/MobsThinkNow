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

	@Test
	void worldIndexKeepsCountsSnapshotsAndRemovalConsistent() {
		PaperWebTrapService.WorldIndex index = new PaperWebTrapService.WorldIndex();
		UUID firstWorld = UUID.randomUUID();
		UUID secondWorld = UUID.randomUUID();
		PaperWebTrapService.BlockKey first = new PaperWebTrapService.BlockKey(firstWorld, 1, 64, 1);
		PaperWebTrapService.BlockKey second = new PaperWebTrapService.BlockKey(firstWorld, 18, 64, 1);
		PaperWebTrapService.BlockKey isolated = new PaperWebTrapService.BlockKey(secondWorld, 1, 64, 1);
		index.add(first);
		index.add(second);
		index.add(isolated);

		assertEquals(List.of(first, second), index.snapshot(firstWorld));
		assertEquals(2, index.count(firstWorld));
		assertEquals(1, index.count(secondWorld));
		assertEquals(2, index.worldIds().size());

		index.remove(first);
		assertEquals(List.of(second), index.snapshot(firstWorld));
		index.remove(second);
		assertEquals(0, index.count(firstWorld));
		assertEquals(List.of(secondWorld), index.worldIds());
	}
}

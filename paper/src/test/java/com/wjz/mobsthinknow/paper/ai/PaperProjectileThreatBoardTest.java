package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PaperProjectileThreatBoardTest {
	@Test
	void packedCellsKeepSignedAxesDistinctAcrossThePlayableWorld() {
		int[] horizontal = {-2_500_001, -1, 0, 1, 2_500_001};
		int[] vertical = {-171, -1, 0, 1, 171};
		Set<Long> packed = new HashSet<>();
		for (int x : horizontal) {
			for (int y : vertical) {
				for (int z : horizontal) {
					packed.add(PaperProjectileThreatBoard.packedCell(x, y, z));
				}
			}
		}

		assertEquals(horizontal.length * vertical.length * horizontal.length, packed.size());
		assertNotEquals(
			PaperProjectileThreatBoard.packedCell(-1, 0, 0),
			PaperProjectileThreatBoard.packedCell(0, 0, 0)
		);
	}

	@Test
	void linkedArrowBucketPreservesOrderAcrossRemovalAndReentry() {
		PaperProjectileThreatBoard.ArrowBucket bucket = new PaperProjectileThreatBoard.ArrowBucket();
		var first = trackedArrow();
		var moving = trackedArrow();
		var last = trackedArrow();
		bucket.add(first);
		bucket.add(moving);
		bucket.add(last);
		assertEquals(3, bucket.size());
		assertSame(first, bucket.first());
		assertSame(moving, first.bucketNext());

		bucket.remove(moving);
		assertEquals(2, bucket.size());
		assertSame(last, first.bucketNext());
		assertFalse(bucket.isEmpty());
		bucket.add(moving);
		assertSame(moving, last.bucketNext());

		bucket.remove(first);
		bucket.remove(last);
		bucket.remove(moving);
		assertTrue(bucket.isEmpty());
	}

	@Test
	void globalArrowChainPreservesOldestOrderAcrossRemovalAndReentry() {
		PaperProjectileThreatBoard.TrackedArrowChain chain = new PaperProjectileThreatBoard.TrackedArrowChain();
		var first = trackedArrow();
		var moving = trackedArrow();
		var last = trackedArrow();
		chain.add(first);
		chain.add(moving);
		chain.add(last);
		assertEquals(3, chain.size());
		assertSame(first, chain.first());

		chain.remove(first);
		assertSame(moving, chain.first());
		chain.remove(moving);
		chain.add(moving);
		assertSame(last, chain.first());
		assertEquals(2, chain.size());

		chain.clear();
		assertEquals(0, chain.size());
		assertNull(chain.first());
	}

	private static PaperProjectileThreatBoard.TrackedArrow trackedArrow() {
		return new PaperProjectileThreatBoard.TrackedArrow(
			UUID.randomUUID(),
			null,
			UUID.randomUUID(),
			1L
		);
	}
}

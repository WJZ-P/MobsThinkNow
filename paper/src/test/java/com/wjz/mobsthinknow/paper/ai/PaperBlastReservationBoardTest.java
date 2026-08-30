package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PaperBlastReservationBoardTest {
	@Test
	void packedCellsKeepSignedHorizontalAxesDistinct() {
		int[] coordinates = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
		Set<Long> packed = new HashSet<>();
		for (int x : coordinates) {
			for (int z : coordinates) {
				packed.add(PaperBlastReservationBoard.packedCell(x, z));
			}
		}
		assertEquals(coordinates.length * coordinates.length, packed.size());
		assertNotEquals(
			PaperBlastReservationBoard.packedCell(-1, 0),
			PaperBlastReservationBoard.packedCell(0, 0)
		);
	}

	@Test
	void reservationBucketPreservesOrderAcrossRemovalAndReentry() {
		PaperBlastReservationBoard.ReservationBucket bucket = new PaperBlastReservationBoard.ReservationBucket();
		PaperBlastReservationBoard.Reservation first = reservation(1L);
		PaperBlastReservationBoard.Reservation moving = reservation(2L);
		PaperBlastReservationBoard.Reservation last = reservation(3L);
		bucket.add(first);
		bucket.add(moving);
		bucket.add(last);
		assertEquals(3, bucket.size());
		assertSame(first, bucket.first());

		bucket.remove(first);
		assertSame(moving, bucket.first());
		bucket.remove(moving);
		bucket.add(moving);
		assertSame(last, bucket.first());
		assertEquals(2, bucket.size());

		bucket.remove(last);
		bucket.remove(moving);
		assertTrue(bucket.isEmpty());
	}

	private static PaperBlastReservationBoard.Reservation reservation(final long cell) {
		return new PaperBlastReservationBoard.Reservation(
			UUID.randomUUID(), UUID.randomUUID(), 0.0, 0.0, 100L, 120L, cell
		);
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadBlastReservationBookTest {
	@Test
	void blocksSameTargetAndOverlappingBlastAreas() {
		SquadBlastReservationBook book = new SquadBlastReservationBook();

		assertEquals(
			SquadBlastReservationBook.Decision.ACQUIRED,
			book.reserve(1, 90, Vec3.ZERO, 6.0, false, 10L, 8L)
		);
		assertFalse(book.canReserve(2, 90, new Vec3(20.0, 0.0, 0.0), 6.0, 10L));
		assertFalse(book.canReserve(2, 91, new Vec3(2.0, 0.0, 0.0), 6.0, 10L));
		assertTrue(book.canReserve(2, 91, new Vec3(20.0, 0.0, 0.0), 6.0, 10L));
	}

	@Test
	void forcedFuseCoexistsAndReservationsExpireOrRelease() {
		SquadBlastReservationBook book = new SquadBlastReservationBook();
		book.reserve(1, 90, Vec3.ZERO, 6.0, false, 0L, 5L);

		assertEquals(
			SquadBlastReservationBook.Decision.ACQUIRED,
			book.reserve(2, 90, Vec3.ZERO, 6.0, true, 1L, 8L)
		);
		assertEquals(2, book.activeCount(1L));
		book.release(2);
		assertNull(book.reservationFor(2, 1L));
		assertEquals(0, book.activeCount(6L));
	}

	@Test
	void movingReservationMustRecheckNewOverlap() {
		SquadBlastReservationBook book = new SquadBlastReservationBook();
		book.reserve(1, 90, Vec3.ZERO, 6.0, false, 0L, 8L);
		book.reserve(2, 91, new Vec3(20.0, 0.0, 0.0), 6.0, false, 0L, 8L);

		assertFalse(book.renew(2, new Vec3(2.0, 0.0, 0.0), 1L, 8L));
		assertTrue(book.renew(2, new Vec3(18.0, 0.0, 0.0), 1L, 8L));
	}

	@Test
	void rejectsInvalidGeometryAndSaturatesExpiry() {
		SquadBlastReservationBook book = new SquadBlastReservationBook();
		assertFalse(book.canReserve(0, 1, Vec3.ZERO, 6.0, 0L));
		assertFalse(book.canReserve(1, 1, new Vec3(Double.NaN, 0.0, 0.0), 6.0, 0L));
		assertFalse(book.canReserve(1, 1, Vec3.ZERO, Double.NaN, 0L));
		assertEquals(
			SquadBlastReservationBook.Decision.CONFLICT,
			book.reserve(1, 1, Vec3.ZERO, Double.NaN, false, 0L, 10L)
		);
		book.reserve(1, 1, Vec3.ZERO, 6.0, false, Long.MAX_VALUE - 2L, 10L);
		assertEquals(Long.MAX_VALUE, book.reservationFor(1, Long.MAX_VALUE - 1L).expiresAt());
		assertThrows(IllegalArgumentException.class, () -> new SquadBlastReservationBook.Reservation(
			1, 1, null, 6.0, false, 10L
		));
	}
}

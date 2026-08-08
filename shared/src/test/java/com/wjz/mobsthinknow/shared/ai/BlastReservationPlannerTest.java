package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.junit.jupiter.api.Test;

class BlastReservationPlannerTest {
	@Test
	void conflictRequiresBothSpatialAndTemporalOverlap() {
		Vec3d origin = Vec3d.ZERO;
		assertTrue(BlastReservationPlanner.conflicts(origin, 100, new Vec3d(5.9, 0.0, 0.0), 120, 6.0, 24));
		assertFalse(BlastReservationPlanner.conflicts(origin, 100, new Vec3d(6.0, 0.0, 0.0), 120, 6.0, 24));
		assertFalse(BlastReservationPlanner.conflicts(origin, 100, new Vec3d(1.0, 0.0, 0.0), 124, 6.0, 24));
	}

	@Test
	void stagingPointIsBehindAndOnStableSide() {
		Vec3d left = BlastReservationPlanner.stagingPoint(
			new Vec3d(10.0, 3.0, 10.0),
			new Vec3d(0.0, 0.0, 1.0),
			-1,
			7.0
		);
		Vec3d right = BlastReservationPlanner.stagingPoint(
			new Vec3d(10.0, 3.0, 10.0),
			new Vec3d(0.0, 0.0, 1.0),
			1,
			7.0
		);
		assertTrue(left.z() < 10.0);
		assertTrue(right.z() < 10.0);
		assertTrue((left.x() - 10.0) * (right.x() - 10.0) < 0.0);
		assertEquals(3.0, left.y());
	}

	@Test
	void negativeCoordinatesUseFloorCells() {
		assertEquals(-1, BlastReservationPlanner.cellCoordinate(-0.1, 8.0));
		assertEquals(-2, BlastReservationPlanner.cellCoordinate(-8.1, 8.0));
		assertEquals(1, BlastReservationPlanner.cellCoordinate(8.0, 8.0));
	}
}

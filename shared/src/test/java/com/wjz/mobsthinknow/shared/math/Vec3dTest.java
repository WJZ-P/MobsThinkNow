package com.wjz.mobsthinknow.shared.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class Vec3dTest {
	@Test
	void horizontalNormalizationNeverLeaksVerticalMotion() {
		Vec3d unit = new Vec3d(3.0, 8.0, 4.0).horizontalUnitOr(Vec3d.ZERO);

		assertEquals(0.6, unit.x(), 1.0E-9);
		assertEquals(0.0, unit.y(), 1.0E-9);
		assertEquals(0.8, unit.z(), 1.0E-9);
	}

	@Test
	void invalidCoordinatesFailAtThePlatformBoundary() {
		assertThrows(IllegalArgumentException.class, () -> new Vec3d(Double.NaN, 0.0, 0.0));
	}
}

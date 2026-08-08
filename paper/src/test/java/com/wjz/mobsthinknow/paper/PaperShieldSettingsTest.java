package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PaperShieldSettingsTest {
	@Test
	void keepsCrossFieldRangesOrderedAfterClamping() {
		PaperShieldSettings settings = PaperShieldSettings.validated(
			true, 4, 8.0, 3.0, 1.1, 6, 50, 10, 8, 2, 10, 20, 5, 0.0
		);

		assertEquals(8.0, settings.raiseDistance());
		assertEquals(8.0, settings.lowerDistance());
		assertEquals(50, settings.minimumGuardTicks());
		assertEquals(50, settings.maximumGuardTicks());
		assertEquals(8, settings.minimumCounterDelayTicks());
		assertEquals(8, settings.maximumCounterDelayTicks());
	}

	@Test
	void convertsNonFiniteDistancesAndSpeedToSafeMinimums() {
		PaperShieldSettings settings = PaperShieldSettings.validated(
			true, 4, Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, 6, 12, 28, 2, 4, 10, 20, 5, 0.0
		);

		assertEquals(2.5, settings.raiseDistance());
		assertEquals(2.5, settings.lowerDistance());
		assertEquals(0.8, settings.movementSpeed());
	}
}

package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperSettingsTest {
	@Test
	void validatesUnsafeConfigurationOnceAtReloadBoundary() {
		PaperSettings settings = PaperSettings.validated(
			true,
			true,
			true,
			99,
			Double.NaN,
			5.0,
			999,
			1.0,
			9.0,
			0
		);

		assertTrue(settings.enabled());
		assertEquals(10, settings.zombieRetreatMinimumIntelligence());
		assertEquals(0.05, settings.retreatHealthThreshold());
		assertEquals(1.0, settings.retreatHeavyHitThreshold());
		assertEquals(200, settings.retreatMaximumTicks());
		assertEquals(2.0, settings.retreatSafeDistance());
		assertEquals(2.0, settings.retreatSpeed());
		assertEquals(2, settings.damageMemoryTicks());
	}

	@Test
	void preservesTheFabricCompatibleRetreatDefaults() {
		PaperSettings settings = PaperSettings.validated(
			true, true, true, 1, 0.20, 0.30, 100, 5.0, 1.50, 20
		);

		assertEquals(0.20, settings.retreatHealthThreshold());
		assertEquals(0.30, settings.retreatHeavyHitThreshold());
		assertEquals(100, settings.retreatMaximumTicks());
		assertEquals(5.0, settings.retreatSafeDistance());
		assertEquals(1.50, settings.retreatSpeed());
	}
}

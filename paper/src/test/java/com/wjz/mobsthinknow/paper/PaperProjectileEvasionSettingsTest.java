package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class PaperProjectileEvasionSettingsTest {
	@Test
	void validatesAllRuntimeAndComplexityBounds() {
		var settings = PaperProjectileEvasionSettings.validated(
			false,
			99,
			1,
			999,
			Double.NaN,
			99.0,
			0.0,
			999
		);
		assertFalse(settings.enabled());
		assertEquals(10, settings.minimumIntelligence());
		assertEquals(16, settings.maximumTrackedProjectiles());
		assertEquals(128, settings.maximumCandidateChecks());
		assertEquals(4.0, settings.scanRadius());
		assertEquals(5.0, settings.dodgeDistance());
		assertEquals(1.0, settings.movementSpeed());
		assertEquals(80, settings.cooldownTicks());
	}
}

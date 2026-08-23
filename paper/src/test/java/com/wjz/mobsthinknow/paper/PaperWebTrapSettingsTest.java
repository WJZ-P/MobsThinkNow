package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.ai.SpiderWebTrapPlanner;
import org.junit.jupiter.api.Test;

class PaperWebTrapSettingsTest {
	@Test
	void invalidValuesAreClampedToBoundedWorldBudgets() {
		PaperWebTrapSettings settings = PaperWebTrapSettings.validated(true, -20, 0, 9999, 9999, true);

		assertTrue(settings.enabled());
		assertEquals(7, settings.minimumIntelligence());
		assertEquals(80, settings.cooldownTicks());
		assertEquals(400, settings.lifetimeTicks());
		assertEquals(512, settings.maximumActivePerWorld());
		assertTrue(settings.blastContainmentEnabled());
	}

	@Test
	void defaultsMatchFabricTrapCadence() {
		PaperWebTrapSettings settings = PaperWebTrapSettings.defaults();

		assertEquals(7, settings.minimumIntelligence());
		assertEquals(SpiderWebTrapPlanner.DEFAULT_COOLDOWN_TICKS, settings.cooldownTicks());
		assertEquals(SpiderWebTrapPlanner.DEFAULT_LIFETIME_TICKS, settings.lifetimeTicks());
		assertEquals(128, settings.maximumActivePerWorld());
	}
}

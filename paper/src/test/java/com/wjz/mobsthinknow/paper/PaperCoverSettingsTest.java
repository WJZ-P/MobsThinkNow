package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperCoverSettingsTest {
	@Test
	void invalidValuesAreNormalizedIntoBoundedBudgets() {
		PaperCoverSettings settings = PaperCoverSettings.validated(
			true,
			99,
			99,
			9999,
			999,
			-1,
			Double.NaN,
			30,
			2,
			99,
			0,
			9999,
			Double.POSITIVE_INFINITY
		);

		assertEquals(10, settings.minimumIntelligence());
		assertEquals(8, settings.searchRadius());
		assertEquals(512, settings.maximumCandidateChecks());
		assertEquals(8, settings.maximumPathChecks());
		assertEquals(20, settings.searchCooldownTicks());
		assertEquals(1.10, settings.movementSpeed());
		assertEquals(30, settings.minimumHiddenTicks());
		assertEquals(30, settings.maximumHiddenTicks());
		assertEquals(40, settings.drawTicks());
		assertEquals(1, settings.maximumShotsPerCover());
		assertEquals(600, settings.cycleTimeoutTicks());
		assertEquals(6.0, settings.targetMovementTolerance());
		assertTrue(settings.searchLimits().maximumRawCandidates() <= 512);
	}
}

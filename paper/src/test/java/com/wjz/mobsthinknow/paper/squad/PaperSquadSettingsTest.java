package com.wjz.mobsthinknow.paper.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperSquadSettingsTest {
	@Test
	void validatesBudgetsAndKeepsDefaultTwentyMemberCap() {
		PaperSquadSettings defaults = PaperSquadSettings.validated(
			true, true, true, 16.0, 2, 20, 64, 5, 40, 30, 40, 20, 8.0, 48.0, 100
		);
		assertTrue(defaults.enabled());
		assertEquals(20, defaults.maximumMembers());
		assertEquals(64, defaults.rawScanLimit());

		PaperSquadSettings clamped = PaperSquadSettings.validated(
			true, true, true, Double.NaN, 99, 999, 0, 0, 0, 999, 0, 0, 99.0, 1.0, 9999
		);
		assertEquals(8.0, clamped.formationRadius());
		assertEquals(8, clamped.minimumMembers());
		assertEquals(100, clamped.maximumMembers());
		assertEquals(16, clamped.rawScanLimit());
		assertEquals(2, clamped.heartbeatTicks());
		assertEquals(400, clamped.targetMemoryTicks());
	}
}

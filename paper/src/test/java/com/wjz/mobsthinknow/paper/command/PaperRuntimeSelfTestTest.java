package com.wjz.mobsthinknow.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PaperRuntimeSelfTestTest {
	@Test
	void coverProbeSettlementCoversBothIncompletePhasesAndRemainsBounded() {
		assertTrue(PaperRuntimeSelfTest.shouldSettleCoverProbe(0L, 0L, true, 0));
		assertTrue(PaperRuntimeSelfTest.shouldSettleCoverProbe(1L, 0L, true, 59));
		assertFalse(PaperRuntimeSelfTest.shouldSettleCoverProbe(0L, 0L, false, 0));
		assertFalse(PaperRuntimeSelfTest.shouldSettleCoverProbe(0L, 0L, true, 60));
		assertFalse(PaperRuntimeSelfTest.shouldSettleCoverProbe(1L, 1L, true, 0));
	}
}

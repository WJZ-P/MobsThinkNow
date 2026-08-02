package com.wjz.mobsthinknow.ai.creeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreeperBlastEvacuationMathTest {
	@Test
	void poweredCreeperUsesTheLargerVanillaDamageEnvelope() {
		assertEquals(6.75, CreeperBlastEvacuationMath.dangerRadius(false), 1.0E-9);
		assertEquals(12.75, CreeperBlastEvacuationMath.dangerRadius(true), 1.0E-9);
		assertEquals(7.75, CreeperBlastEvacuationMath.releaseRadius(false), 1.0E-9);
		assertEquals(13.75, CreeperBlastEvacuationMath.releaseRadius(true), 1.0E-9);
	}

	@Test
	void triggerAndReleaseLinesHaveHysteresis() {
		double trigger = CreeperBlastEvacuationMath.dangerRadius(false);
		double release = CreeperBlastEvacuationMath.releaseRadius(false);
		assertTrue(CreeperBlastEvacuationMath.isInsideDanger(trigger * trigger - 0.01, false));
		assertFalse(CreeperBlastEvacuationMath.isInsideDanger(trigger * trigger, false));
		assertTrue(CreeperBlastEvacuationMath.shouldContinue(trigger * trigger, false));
		assertFalse(CreeperBlastEvacuationMath.shouldContinue(release * release, false));
	}

	@Test
	void urgencyAcceleratesWithoutExceedingTheEvacuationCap() {
		assertEquals(1.30, CreeperBlastEvacuationMath.evacuationSpeed(-1.0F), 1.0E-9);
		assertEquals(1.425, CreeperBlastEvacuationMath.evacuationSpeed(0.5F), 1.0E-9);
		assertEquals(1.55, CreeperBlastEvacuationMath.evacuationSpeed(2.0F), 1.0E-9);
		assertEquals(5.0, CreeperBlastEvacuationMath.pathStep(20.0, false), 1.0E-9);
		assertEquals(10.0, CreeperBlastEvacuationMath.pathStep(0.0, true), 1.0E-9);
	}
}

package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

final class PaperFireworkBoltServiceTest {
	@Test
	void rejectsNullZeroAndNonFiniteDirectionsBeforeSpawning() {
		assertFalse(PaperFireworkBoltService.isUsableDirection(null));
		assertFalse(PaperFireworkBoltService.isUsableDirection(new Vector()));
		assertFalse(PaperFireworkBoltService.isUsableDirection(new Vector(Double.NaN, 0.0, 1.0)));
		assertFalse(PaperFireworkBoltService.isUsableDirection(new Vector(0.0, Double.POSITIVE_INFINITY, 1.0)));
		assertTrue(PaperFireworkBoltService.isUsableDirection(new Vector(0.1, 0.2, 1.0)));
	}
}

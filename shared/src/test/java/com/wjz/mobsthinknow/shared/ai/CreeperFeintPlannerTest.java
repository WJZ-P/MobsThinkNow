package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.junit.jupiter.api.Test;

class CreeperFeintPlannerTest {
	@Test
	void requiresSkillAttentionSightAndAStagingBand() {
		assertTrue(CreeperFeintPlanner.shouldFeint(10, true, true, true, false, false, 0.0, 36.0, 4.0));
		assertTrue(CreeperFeintPlanner.shouldFeint(8, true, true, false, true, false, 0.0, 30.0, 4.0));
		assertFalse(CreeperFeintPlanner.shouldFeint(7, true, true, true, false, false, 0.0, 36.0, 4.0));
		assertFalse(CreeperFeintPlanner.shouldFeint(10, true, false, true, false, false, 0.0, 36.0, 4.0));
		assertFalse(CreeperFeintPlanner.shouldFeint(10, true, true, true, false, true, 0.0, 36.0, 4.0));
		assertFalse(CreeperFeintPlanner.shouldFeint(10, true, true, true, false, false, 0.02, 36.0, 4.0));
		assertFalse(CreeperFeintPlanner.shouldFeint(10, true, true, true, false, false, 0.0, Double.NaN, 4.0));
	}

	@Test
	void destinationIsPredictiveBoundedAndOnAStableRearSide() {
		Vec3d destination = CreeperFeintPlanner.repositionDestination(
			new Vec3d(10.0, 4.0, 10.0),
			new Vec3d(100.0, 20.0, 0.0),
			new Vec3d(0.0, 0.0, 1.0),
			1,
			10
		);
		assertTrue(destination.z() < 10.0);
		assertTrue(destination.x() > 5.0 && destination.x() < 5.5);
		assertEquals(4.0, destination.y());
		assertEquals(81.0, destination.distanceSquared(new Vec3d(12.5, 4.0, 10.0)), 1.0E-9);
	}

	@Test
	void timingHasHardPrimeBoundsAndDeterministicJitter() {
		assertEquals(6, CreeperFeintPlanner.primeTicks(-1.0));
		assertEquals(8, CreeperFeintPlanner.primeTicks(1.0));
		assertEquals(26, CreeperFeintPlanner.repositionTicks(Double.NaN));
		assertEquals(40, CreeperFeintPlanner.repositionTicks(1.0));
		assertEquals(192, CreeperFeintPlanner.cooldownTicks(240, 0.0));
		assertEquals(288, CreeperFeintPlanner.cooldownTicks(240, 1.0));
	}
}

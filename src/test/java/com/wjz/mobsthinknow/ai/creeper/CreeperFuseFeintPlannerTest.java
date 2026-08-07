package com.wjz.mobsthinknow.ai.creeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CreeperFuseFeintPlannerTest {
	@Test
	void onlySkilledUnpoweredCreepersFeintAWatchingTargetOutsideTheRealFuseEnvelope() {
		assertTrue(CreeperFuseFeintPlanner.shouldFeint(10, true, true, true, false, false, 0.0, 36.0, 4.0));
		assertFalse(CreeperFuseFeintPlanner.shouldFeint(7, true, true, true, false, false, 0.0, 36.0, 4.0));
		assertFalse(CreeperFuseFeintPlanner.shouldFeint(10, true, true, true, false, true, 0.0, 36.0, 4.0));
		assertFalse(CreeperFuseFeintPlanner.shouldFeint(10, true, true, false, false, false, 0.0, 36.0, 4.0));
		assertFalse(CreeperFuseFeintPlanner.shouldFeint(10, true, true, true, false, false, 0.0, 16.0, 4.0));
		assertFalse(CreeperFuseFeintPlanner.shouldFeint(10, true, true, true, false, false, 0.0, 81.0, 4.0));
	}

	@Test
	void sideRearDestinationKeepsAReadableStagingRadius() {
		Vec3 destination = CreeperFuseFeintPlanner.repositionDestination(
			new Vec3(10.0, 4.0, 10.0),
			Vec3.ZERO,
			new Vec3(0.0, 0.0, 1.0),
			1,
			10
		);

		assertTrue(destination.z < 10.0, "Feint did not retreat behind the watched target.");
		assertTrue(Math.abs(destination.x - 10.0) > 3.0, "Feint did not produce a meaningful side step.");
		assertTrue(destination.distanceTo(new Vec3(10.0, 4.0, 10.0)) > 4.5);
		assertEquals(4.0, destination.y, 1.0E-9);
	}

	@Test
	void timingHasHardFuseSafetyAndStaggeredCooldownBounds() {
		assertEquals(6, CreeperFuseFeintPlanner.primeTicks(0.0));
		assertEquals(8, CreeperFuseFeintPlanner.primeTicks(1.0));
		assertEquals(26, CreeperFuseFeintPlanner.repositionTicks(0.0));
		assertEquals(40, CreeperFuseFeintPlanner.repositionTicks(1.0));
		assertEquals(192, CreeperFuseFeintPlanner.cooldownTicks(240, 0.0));
		assertEquals(288, CreeperFuseFeintPlanner.cooldownTicks(240, 1.0));
	}
}

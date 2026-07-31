package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GiantMeleeMotionTest {
	@Test
	void everyDamagingActionLocksSeveralTicksBeforeItsImpact() {
		for (GiantMeleeAction action : GiantMeleeAction.values()) {
			if (!action.isActive()) {
				continue;
			}
			assertTrue(action.aimLockTick() < action.impactTick(), action.name());
			assertTrue(GiantMeleeMotion.tracksTarget(action, action.aimLockTick() - 1), action.name());
			assertFalse(GiantMeleeMotion.tracksTarget(action, action.aimLockTick()), action.name());
		}
	}

	@Test
	void limitedTurnUsesTheShortestArcAndNeverExceedsItsBudget() {
		Vec3 current = new Vec3(0.0, 0.0, 1.0);
		Vec3 desired = new Vec3(1.0, 0.0, 0.0);
		Vec3 turned = GiantMeleeMotion.turnToward(current, desired, Math.toRadians(14.0));

		assertEquals(Math.sin(Math.toRadians(14.0)), turned.x, 1.0E-8);
		assertEquals(Math.cos(Math.toRadians(14.0)), turned.z, 1.0E-8);
		assertEquals(1.0, turned.length(), 1.0E-8);
	}

	@Test
	void completeRootMotionCurveAddsUpToItsDesignedStepDistance() {
		assertEquals(0.56, totalStep(GiantMeleeAction.SWEEP_RIGHT), 1.0E-9);
		assertEquals(0.42, totalStep(GiantMeleeAction.SLAP_LEFT), 1.0E-9);
		assertEquals(0.30, totalStep(GiantMeleeAction.GROUND_SMASH), 1.0E-9);
		assertEquals(0.82, totalStep(GiantMeleeAction.KICK_RIGHT), 1.0E-9);
		assertEquals(0.92, totalStep(GiantMeleeAction.GRAB_LEFT), 1.0E-9);
		assertEquals(0.0, totalStep(GiantMeleeAction.STOMP_RIGHT), 1.0E-9);
	}

	@Test
	void rootMotionFinishesBeforeDirectionLock() {
		for (GiantMeleeAction action : GiantMeleeAction.values()) {
			assertEquals(0.0, GiantMeleeMotion.forwardStep(action, action.aimLockTick()), 1.0E-9, action.name());
		}
	}

	private static double totalStep(final GiantMeleeAction action) {
		double total = 0.0;
		for (int tick = 0; tick <= action.durationTicks(); tick++) {
			total += GiantMeleeMotion.forwardStep(action, tick);
		}
		return total;
	}
}

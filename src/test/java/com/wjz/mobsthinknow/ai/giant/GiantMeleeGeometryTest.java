package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GiantMeleeGeometryTest {
	@Test
	void sweepCoversWideFrontArcButNotDeepRearTargets() {
		assertTrue(GiantMeleeGeometry.contains(GiantMeleeAction.SWEEP_RIGHT, 3.0, 5.0, 1.0));
		assertFalse(GiantMeleeGeometry.contains(GiantMeleeAction.SWEEP_RIGHT, -2.0, 0.0, 1.0));
	}

	@Test
	void slapUsesANarrowForwardLane() {
		assertTrue(GiantMeleeGeometry.contains(GiantMeleeAction.SLAP_LEFT, 4.0, 1.5, 1.0));
		assertFalse(GiantMeleeGeometry.contains(GiantMeleeAction.SLAP_LEFT, 4.0, 2.5, 1.0));
	}

	@Test
	void stompRemainsLocalToTheGiantsFeet() {
		assertTrue(GiantMeleeGeometry.contains(GiantMeleeAction.STOMP_RIGHT, 2.0, 2.0, 0.0));
		assertFalse(GiantMeleeGeometry.contains(GiantMeleeAction.STOMP_RIGHT, 4.3, 0.0, 0.0));
	}

	@Test
	void groundSmashCentersItsBlastInFrontInsteadOfBehind() {
		assertTrue(GiantMeleeGeometry.contains(GiantMeleeAction.GROUND_SMASH, 3.25, 4.30, 0.0));
		assertFalse(GiantMeleeGeometry.contains(GiantMeleeAction.GROUND_SMASH, -0.7, 0.0, 0.0));
	}

	@Test
	void kickUsesAForwardLaneInsteadOfAFullFootRadius() {
		assertTrue(GiantMeleeGeometry.contains(GiantMeleeAction.KICK_RIGHT, 3.5, 1.2, 0.5));
		assertFalse(GiantMeleeGeometry.contains(GiantMeleeAction.KICK_RIGHT, 3.5, 2.0, 0.5));
	}

	@Test
	void grabRequiresTheTelegraphedTargetToRemainNearTheChosenPalm() {
		assertTrue(GiantMeleeGeometry.contains(GiantMeleeAction.GRAB_LEFT, 3.0, 1.2, 1.0));
		assertFalse(GiantMeleeGeometry.contains(GiantMeleeAction.GRAB_LEFT, 3.0, 1.8, 1.0));
	}
}

package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GiantMeleeAnimationTest {
	@Test
	void leftSweepMirrorsRightSweepWithoutMovingTheOtherArm() {
		GiantMeleeAnimation.BodyPose right =
			GiantMeleeAnimation.sample(GiantMeleeAction.SWEEP_RIGHT, 0.42F);
		GiantMeleeAnimation.BodyPose left =
			GiantMeleeAnimation.sample(GiantMeleeAction.SWEEP_LEFT, 0.42F);

		assertEquals(right.rightArm().xRot(), left.leftArm().xRot(), 1.0E-6F);
		assertEquals(-right.rightArm().yRot(), left.leftArm().yRot(), 1.0E-6F);
		assertEquals(-right.rightArm().zRot(), left.leftArm().zRot(), 1.0E-6F);
		assertEquals(0.0F, right.leftArm().weight(), 1.0E-6F);
		assertEquals(0.0F, left.rightArm().weight(), 1.0E-6F);
	}

	@Test
	void groundSmashRaisesBothArmsThenDrivesTorsoIntoImpact() {
		GiantMeleeAnimation.BodyPose overhead =
			GiantMeleeAnimation.sample(GiantMeleeAction.GROUND_SMASH, 0.40F);
		GiantMeleeAnimation.BodyPose impact =
			GiantMeleeAnimation.sample(GiantMeleeAction.GROUND_SMASH, 0.58F);

		assertTrue(overhead.rightArm().xRot() < -2.6F);
		assertTrue(overhead.leftArm().xRot() < -2.6F);
		assertTrue(impact.body().xRot() > 0.35F);
		assertTrue(impact.rightArm().xRot() > 0.15F);
	}

	@Test
	void rightStompMovesRightLegAndUsesLeftArmAsCounterbalance() {
		GiantMeleeAnimation.BodyPose lifted =
			GiantMeleeAnimation.sample(GiantMeleeAction.STOMP_RIGHT, 0.40F);

		assertTrue(lifted.rightLeg().xRot() < -1.0F);
		assertEquals(0.0F, lifted.leftLeg().weight(), 1.0E-6F);
		assertTrue(lifted.leftArm().weight() > 0.4F);
	}

	@Test
	void everyActionRecoversToVanillaPoseAtItsEnd() {
		for (GiantMeleeAction action : GiantMeleeAction.values()) {
			GiantMeleeAnimation.BodyPose end = GiantMeleeAnimation.sample(action, 1.0F);
			assertEquals(0.0F, end.rightArm().weight(), 1.0E-6F, action.name());
			assertEquals(0.0F, end.leftArm().weight(), 1.0E-6F, action.name());
			assertEquals(0.0F, end.body().weight(), 1.0E-6F, action.name());
			assertEquals(0.0F, end.rightLeg().weight(), 1.0E-6F, action.name());
			assertEquals(0.0F, end.leftLeg().weight(), 1.0E-6F, action.name());
		}
	}
}

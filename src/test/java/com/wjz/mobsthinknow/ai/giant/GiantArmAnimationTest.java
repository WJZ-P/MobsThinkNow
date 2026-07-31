package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GiantArmAnimationTest {
	@Test
	void leftHandIsAnExactMirrorOfRightHand() {
		GiantArmAnimation.ArmPose right =
			GiantArmAnimation.handPose(GiantHand.RIGHT, GiantHandPhase.AIMING, 0.63F);
		GiantArmAnimation.ArmPose left =
			GiantArmAnimation.handPose(GiantHand.LEFT, GiantHandPhase.AIMING, 0.63F);

		assertEquals(right.xRot(), left.xRot(), 1.0E-6F);
		assertEquals(-right.yRot(), left.yRot(), 1.0E-6F);
		assertEquals(-right.zRot(), left.zRot(), 1.0E-6F);
		assertEquals(right.weight(), left.weight(), 1.0E-6F);
	}

	@Test
	void aimAndThrowUseDistinctReadableKeyframes() {
		GiantArmAnimation.ArmPose hold =
			GiantArmAnimation.handPose(GiantHand.RIGHT, GiantHandPhase.HOLDING, 1.0F);
		GiantArmAnimation.ArmPose aim =
			GiantArmAnimation.handPose(GiantHand.RIGHT, GiantHandPhase.AIMING, 1.0F);
		GiantArmAnimation.ArmPose release =
			GiantArmAnimation.handPose(GiantHand.RIGHT, GiantHandPhase.THROWING, 0.55F);

		assertTrue(aim.xRot() > hold.xRot() + 1.0F, "Aim did not visibly pull the arm back.");
		assertTrue(release.xRot() < hold.xRot() - 0.5F, "Throw did not drive the arm forward.");
	}

	@Test
	void cooldownAndHeadTransferRecoverNaturally() {
		GiantArmAnimation.ArmPose cooldownStart =
			GiantArmAnimation.handPose(GiantHand.RIGHT, GiantHandPhase.COOLDOWN, 0.0F);
		GiantArmAnimation.ArmPose cooldownEnd =
			GiantArmAnimation.handPose(GiantHand.RIGHT, GiantHandPhase.COOLDOWN, 1.0F);
		GiantArmAnimation.ArmPose headTransferPeak =
			GiantArmAnimation.boardingPose(GiantBoardingPhase.TO_HEAD, 0.65F);
		GiantArmAnimation.ArmPose headTransferEnd =
			GiantArmAnimation.boardingPose(GiantBoardingPhase.TO_HEAD, 1.0F);

		assertEquals(1.0F, cooldownStart.weight(), 1.0E-6F);
		assertEquals(0.0F, cooldownEnd.weight(), 1.0E-6F);
		assertTrue(headTransferPeak.xRot() < -2.5F, "Boarding arm never reached above the Giant head.");
		assertEquals(0.0F, headTransferEnd.weight(), 1.0E-6F);
	}
}

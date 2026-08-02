package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieBodyAnimationTest {
	private static final float EPSILON = 1.0E-5F;

	@Test
	void commandHasReadableWindupHoldAndRecovery() {
		ZombieBodyAnimation.BodyPose start = ZombieBodyAnimation.sample(ZombieBodyAction.COMMAND, 0.0F, 0.0F);
		ZombieBodyAnimation.BodyPose hold = ZombieBodyAnimation.sample(ZombieBodyAction.COMMAND, 8.0F, 8.0F);
		ZombieBodyAnimation.BodyPose end = ZombieBodyAnimation.sample(ZombieBodyAction.COMMAND, 18.0F, 18.0F);

		assertEquals(0.0F, start.rightArm().weight(), EPSILON);
		assertTrue(hold.rightArm().weight() > 0.95F);
		assertTrue(hold.rightArm().xRot() < -1.2F);
		assertTrue(hold.body().yRot() < 0.0F);
		assertEquals(0.0F, end.rightArm().weight(), EPSILON);
	}

	@Test
	void warCryRaisesMirroredArmsAndLooksUp() {
		ZombieBodyAnimation.BodyPose pose = ZombieBodyAnimation.sample(ZombieBodyAction.WAR_CRY, 8.0F, 8.0F);

		assertEquals(pose.rightArm().xRot(), pose.leftArm().xRot(), EPSILON);
		assertEquals(-pose.rightArm().yRot(), pose.leftArm().yRot(), EPSILON);
		assertEquals(-pose.rightArm().zRot(), pose.leftArm().zRot(), EPSILON);
		assertTrue(pose.rightArm().xRot() < -2.0F);
		assertTrue(pose.head().xRot() < 0.0F);
	}

	@Test
	void meetingCallRaisesOneArmAndWavesItAcrossTwoBeats() {
		ZombieBodyAnimation.BodyPose firstWave = ZombieBodyAnimation.sample(
			ZombieBodyAction.CALL_TO_MEETING,
			3.0F,
			3.0F
		);
		ZombieBodyAnimation.BodyPose secondWave = ZombieBodyAnimation.sample(
			ZombieBodyAction.CALL_TO_MEETING,
			9.0F,
			9.0F
		);

		assertTrue(firstWave.rightArm().xRot() < -2.0F);
		assertTrue(firstWave.rightArm().weight() > 0.8F);
		assertTrue(firstWave.rightArm().zRot() - secondWave.rightArm().zRot() > 0.5F);
		assertTrue(firstWave.leftArm().xRot() > -1.0F);
	}

	@Test
	void surveyScansBothSidesWhileKeepingAStableStance() {
		ZombieBodyAnimation.BodyPose left = ZombieBodyAnimation.sample(
			ZombieBodyAction.SURVEY_MEMBERS,
			3.0F,
			3.0F
		);
		ZombieBodyAnimation.BodyPose right = ZombieBodyAnimation.sample(
			ZombieBodyAction.SURVEY_MEMBERS,
			9.0F,
			9.0F
		);

		assertTrue(left.head().yRot() > 0.5F);
		assertTrue(right.head().yRot() < -0.5F);
		assertEquals(left.body().yRot(), -right.body().yRot(), EPSILON);
		assertEquals(0.0F, left.rightLeg().weight(), EPSILON);
	}

	@Test
	void leftAndRightOrdersAreExactFullBodyMirrors() {
		ZombieBodyAnimation.BodyPose left = ZombieBodyAnimation.sample(
			ZombieBodyAction.COMMAND_LEFT,
			9.0F,
			9.0F
		);
		ZombieBodyAnimation.BodyPose right = ZombieBodyAnimation.sample(
			ZombieBodyAction.COMMAND_RIGHT,
			9.0F,
			9.0F
		);

		assertEquals(right.rightArm().xRot(), left.leftArm().xRot(), EPSILON);
		assertEquals(-right.rightArm().yRot(), left.leftArm().yRot(), EPSILON);
		assertEquals(right.leftArm().xRot(), left.rightArm().xRot(), EPSILON);
		assertEquals(-right.body().yRot(), left.body().yRot(), EPSILON);
		assertEquals(-right.head().yRot(), left.head().yRot(), EPSILON);
	}

	@Test
	void nodAndHeadShakeUseDifferentAxesAndTwoReadableBeats() {
		ZombieBodyAnimation.BodyPose nod = ZombieBodyAnimation.sample(ZombieBodyAction.NOD, 3.0F, 3.0F);
		ZombieBodyAnimation.BodyPose shake = ZombieBodyAnimation.sample(ZombieBodyAction.SHAKE_HEAD, 2.0F, 2.0F);

		assertTrue(nod.head().xRot() > 0.3F);
		assertEquals(0.0F, nod.head().yRot(), EPSILON);
		assertTrue(shake.head().yRot() > 0.35F);
		assertEquals(0.0F, shake.head().xRot(), EPSILON);
		assertTrue(shake.body().yRot() < 0.0F);
	}

	@Test
	void conferMirrorsForLeftHandAndAdvanceOrderSweepsForward() {
		ZombieBodyAnimation.BodyPose conferRight = ZombieBodyAnimation.sample(
			ZombieBodyAction.CONFER,
			8.0F,
			8.0F,
			true
		);
		ZombieBodyAnimation.BodyPose conferLeft = ZombieBodyAnimation.sample(
			ZombieBodyAction.CONFER,
			8.0F,
			8.0F,
			false
		);
		ZombieBodyAnimation.BodyPose advanceWindup = ZombieBodyAnimation.sample(
			ZombieBodyAction.ADVANCE_ORDER,
			3.0F,
			3.0F,
			true
		);
		ZombieBodyAnimation.BodyPose advanceRelease = ZombieBodyAnimation.sample(
			ZombieBodyAction.ADVANCE_ORDER,
			10.0F,
			10.0F,
			true
		);

		assertEquals(conferRight.rightArm().xRot(), conferLeft.leftArm().xRot(), EPSILON);
		assertEquals(-conferRight.head().yRot(), conferLeft.head().yRot(), EPSILON);
		assertTrue(advanceWindup.rightArm().xRot() < -2.2F);
		assertTrue(advanceRelease.rightArm().xRot() > -1.4F);
	}

	@Test
	void retreatLeansIntoSprintButOffsetsHeadUpward() {
		ZombieBodyAnimation.BodyPose pose = ZombieBodyAnimation.sample(ZombieBodyAction.RETREAT, 6.0F, 3.5F);

		assertTrue(pose.body().weight() > 0.80F);
		assertTrue(pose.body().xRot() > 0.0F);
		assertTrue(pose.head().xRot() < 0.0F);
		assertTrue(Math.abs(pose.rightArm().xRot() - pose.leftArm().xRot()) > 0.2F);
	}

	@Test
	void swordAndAxeReadyPosesUseDifferentSilhouettesAndMirrorForLeftHand() {
		ZombieBodyAnimation.BodyPose swordRight = ZombieBodyAnimation.combatReady(
			ZombieProfession.SWORDSMAN,
			true,
			10.0F,
			0.0F
		);
		ZombieBodyAnimation.BodyPose swordLeft = ZombieBodyAnimation.combatReady(
			ZombieProfession.SWORDSMAN,
			false,
			10.0F,
			0.0F
		);
		ZombieBodyAnimation.BodyPose axeRight = ZombieBodyAnimation.combatReady(
			ZombieProfession.AXEMAN,
			true,
			10.0F,
			0.0F
		);

		assertTrue(swordRight.rightArm().weight() > 0.0F);
		assertEquals(0.0F, swordRight.leftArm().weight(), EPSILON);
		assertEquals(0.0F, swordLeft.rightArm().weight(), EPSILON);
		assertEquals(swordRight.rightArm().xRot(), swordLeft.leftArm().xRot(), EPSILON);
		assertEquals(-swordRight.rightArm().yRot(), swordLeft.leftArm().yRot(), EPSILON);
		assertTrue(axeRight.rightArm().xRot() < swordRight.rightArm().xRot());
	}

	@Test
	void utilityProfessionsKeepTheirExistingItemSpecificPoses() {
		assertEquals(
			ZombieBodyAnimation.BodyPose.NONE,
			ZombieBodyAnimation.combatReady(ZombieProfession.ENGINEER, true, 0.0F, 0.0F)
		);
		assertEquals(
			ZombieBodyAnimation.BodyPose.NONE,
			ZombieBodyAnimation.combatReady(ZombieProfession.AIR_ASSAULT, true, 0.0F, 0.0F)
		);
	}

	@Test
	void swordFeintHasAFalseLungeAndRecoversWithoutAnImpactPose() {
		ZombieBodyAnimation.BodyPose lunge = ZombieBodyAnimation.sample(
			ZombieBodyAction.SWORD_FEINT,
			6.0F,
			6.0F,
			true
		);
		ZombieBodyAnimation.BodyPose recovery = ZombieBodyAnimation.sample(
			ZombieBodyAction.SWORD_FEINT,
			17.0F,
			17.0F,
			true
		);

		assertTrue(lunge.rightArm().weight() > 0.9F);
		assertTrue(lunge.body().xRot() > 0.0F);
		assertTrue(recovery.rightArm().weight() < lunge.rightArm().weight());
	}

	@Test
	void axeWindupAndLeapKeepWeaponOverheadAcrossTakeoff() {
		ZombieBodyAnimation.BodyPose windup = ZombieBodyAnimation.sample(
			ZombieBodyAction.AXE_WINDUP,
			4.0F,
			4.0F,
			true
		);
		ZombieBodyAnimation.BodyPose leap = ZombieBodyAnimation.sample(
			ZombieBodyAction.AXE_LEAP,
			4.0F,
			10.0F,
			true
		);

		assertTrue(windup.rightArm().xRot() < -2.4F);
		assertTrue(windup.body().xRot() > 0.25F);
		assertTrue(leap.rightArm().xRot() < -2.6F);
		assertTrue(leap.leftLeg().xRot() > 0.5F);
	}

	@Test
	void offhandShieldBashAndEngineerToolPoseMirrorEveryBodyPart() {
		ZombieBodyAnimation.BodyPose rightBash = ZombieBodyAnimation.sample(
			ZombieBodyAction.SHIELD_BASH,
			7.0F,
			7.0F,
			true
		);
		ZombieBodyAnimation.BodyPose leftBash = ZombieBodyAnimation.sample(
			ZombieBodyAction.SHIELD_BASH,
			7.0F,
			7.0F,
			false
		);
		ZombieBodyAnimation.BodyPose engineer = ZombieBodyAnimation.sample(
			ZombieBodyAction.ENGINEER_WORK,
			6.0F,
			6.0F,
			true
		);

		assertEquals(rightBash.rightArm().xRot(), leftBash.leftArm().xRot(), EPSILON);
		assertEquals(-rightBash.rightArm().yRot(), leftBash.leftArm().yRot(), EPSILON);
		assertEquals(rightBash.rightLeg().xRot(), leftBash.leftLeg().xRot(), EPSILON);
		assertTrue(engineer.body().xRot() > 0.45F);
		assertTrue(engineer.rightLeg().xRot() > 1.0F);
	}
}

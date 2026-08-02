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

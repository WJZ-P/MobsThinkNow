package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.HorizontalLead;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.MovementMode;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.StrafeInput;
import org.junit.jupiter.api.Test;

class SkeletonCombatMathTest {
	@Test
	void distanceBandsPreferKiteThenStrafeThenApproach() {
		assertEquals(MovementMode.KITE, SkeletonCombatMath.chooseMovement(5.9 * 5.9, true, 10.0, false));
		assertEquals(MovementMode.STRAFE, SkeletonCombatMath.chooseMovement(6.0 * 6.0, true, 10.0, false));
		assertEquals(MovementMode.STRAFE, SkeletonCombatMath.chooseMovement(13.5 * 13.5, true, 10.0, false));
		assertEquals(MovementMode.APPROACH, SkeletonCombatMath.chooseMovement(13.6 * 13.6, true, 10.0, false));
		assertEquals(MovementMode.APPROACH, SkeletonCombatMath.chooseMovement(10.0 * 10.0, false, 10.0, false));
	}

	@Test
	void dodgeAlwaysOverridesOrdinaryDistanceSelection() {
		for (double distanceSquared : new double[]{1.0, 100.0, 1000.0, Double.NaN}) {
			assertEquals(
				MovementMode.DODGE,
				SkeletonCombatMath.chooseMovement(distanceSquared, false, 10.0, true)
			);
		}
	}

	@Test
	void emergencyDisengageUsesSeparateTriggerAndSafeThresholds() {
		assertEquals(6.0, SkeletonCombatMath.emergencyDisengageTriggerRange(10.0));
		assertEquals(9.0, SkeletonCombatMath.emergencyDisengageSafeRange(10.0));
		assertTrue(SkeletonCombatMath.shouldStartEmergencyDisengage(5.99 * 5.99, 10.0));
		assertFalse(SkeletonCombatMath.shouldStartEmergencyDisengage(6.0 * 6.0, 10.0));

		// 已经开始的脱离行为穿过六格后仍继续，直到真正抵达九格安全线。
		assertTrue(SkeletonCombatMath.shouldContinueEmergencyDisengage(8.99 * 8.99, 10.0));
		assertFalse(SkeletonCombatMath.shouldContinueEmergencyDisengage(9.0 * 9.0, 10.0));
	}

	@Test
	void emergencyDisengageRejectsInvalidDistancesAndFallsBackToDefaultRange() {
		assertEquals(6.0, SkeletonCombatMath.emergencyDisengageTriggerRange(Double.NaN));
		assertEquals(9.0, SkeletonCombatMath.emergencyDisengageSafeRange(-10.0));
		assertFalse(SkeletonCombatMath.shouldStartEmergencyDisengage(Double.NaN, 10.0));
		assertFalse(SkeletonCombatMath.shouldContinueEmergencyDisengage(-1.0, 10.0));
	}

	@Test
	void higherIntelligenceKeepsMoreDistanceAndEscapesMoreDecisively() {
		double lowPreferred = SkeletonCombatMath.intelligenceAdjustedPreferredRange(10.0, 1);
		double highPreferred = SkeletonCombatMath.intelligenceAdjustedPreferredRange(10.0, 10);
		assertTrue(highPreferred > lowPreferred);
		assertTrue(
			SkeletonCombatMath.emergencyDisengageTriggerRange(10.0, 10)
				> SkeletonCombatMath.emergencyDisengageTriggerRange(10.0, 1)
		);
		assertTrue(
			SkeletonCombatMath.emergencyDisengageSafeRange(10.0, 10)
				> SkeletonCombatMath.emergencyDisengageSafeRange(10.0, 1)
		);
		assertTrue(SkeletonCombatMath.disengagePathSpeed(10) > SkeletonCombatMath.disengagePathSpeed(1));
		assertTrue(SkeletonCombatMath.kiteBackwardInput(10) > SkeletonCombatMath.kiteBackwardInput(1));
		assertTrue(SkeletonCombatMath.kiteSidewaysInput(10) > SkeletonCombatMath.kiteSidewaysInput(1));
		assertTrue(SkeletonCombatMath.disengagePathRefreshTicks(10) < SkeletonCombatMath.disengagePathRefreshTicks(1));
	}

	@Test
	void directIncomingArrowIsDetectedButMissAndOutgoingArrowAreRejected() {
		assertTrue(SkeletonCombatMath.isIncomingProjectile(
			0.0, 0.0, 5.0,
			0.0, 0.0, 1.0,
			8.0, 1.15
		));
		assertFalse(SkeletonCombatMath.isIncomingProjectile(
			2.0, 0.0, 5.0,
			0.0, 0.0, 1.0,
			8.0, 1.15
		));
		assertFalse(SkeletonCombatMath.isIncomingProjectile(
			0.0, 0.0, 5.0,
			0.0, 0.0, -1.0,
			8.0, 1.15
		));
	}

	@Test
	void arrowOutsideEightTickHorizonDoesNotTriggerEarlyDodge() {
		assertFalse(SkeletonCombatMath.isIncomingProjectile(
			0.0, 0.0, 10.0,
			0.0, 0.0, 1.0,
			8.0, 1.15
		));
		assertEquals(
			Double.POSITIVE_INFINITY,
			SkeletonCombatMath.closestApproachTime(0.0, 0.0, 5.0, 0.0, 0.0, 0.0, 8.0)
		);
	}

	@Test
	void horizontalLeadUsesTravelTimeStrengthAndHardDistanceCap() {
		HorizontalLead ordinary = SkeletonCombatMath.horizontalLead(0.30, 0.0, 16.0, 0.65);
		assertEquals(1.56, ordinary.x(), 1.0E-12);
		assertEquals(0.0, ordinary.z(), 1.0E-12);

		HorizontalLead capped = SkeletonCombatMath.horizontalLead(10.0, 10.0, 100.0, 1.0);
		assertEquals(3.0, Math.sqrt(capped.x() * capped.x() + capped.z() * capped.z()), 1.0E-12);
		assertEquals(HorizontalLead.ZERO, SkeletonCombatMath.horizontalLead(Double.NaN, 0.0, 10.0, 1.0));
		assertEquals(HorizontalLead.ZERO, SkeletonCombatMath.horizontalLead(0.3, 0.0, 10.0, 0.0));
	}

	@Test
	void predictionAccuracyRisesMonotonicallyWithDifficulty() {
		assertEquals(0.0, SkeletonCombatMath.difficultyPredictionFactor(0));
		assertEquals(0.65, SkeletonCombatMath.difficultyPredictionFactor(1));
		assertEquals(0.82, SkeletonCombatMath.difficultyPredictionFactor(2));
		assertEquals(1.0, SkeletonCombatMath.difficultyPredictionFactor(3));
		assertTrue(
			SkeletonCombatMath.difficultyPredictionFactor(1)
				< SkeletonCombatMath.difficultyPredictionFactor(2)
		);
	}

	@Test
	void worldDirectionConvertsToLocalStrafeWithoutTurningTheBody() {
		assertEquals(new StrafeInput(1.0F, 0.0F), SkeletonCombatMath.targetFacingStrafeInput(0.0F, 0.0, 1.0));
		assertEquals(new StrafeInput(0.0F, 1.0F), SkeletonCombatMath.targetFacingStrafeInput(0.0F, 1.0, 0.0));
		assertEquals(new StrafeInput(0.0F, -1.0F), SkeletonCombatMath.targetFacingStrafeInput(90.0F, 0.0, -1.0));
		assertEquals(StrafeInput.ZERO, SkeletonCombatMath.targetFacingStrafeInput(0.0F, 0.0, 0.0));
	}
}

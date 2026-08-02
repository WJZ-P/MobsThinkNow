package com.wjz.mobsthinknow.ai.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import org.junit.jupiter.api.Test;

class NetherTacticsPolicyTest {
	@Test
	void blazeVolleyScalesFromTwoToFourShotsWithDifficulty() {
		assertEquals(2, SmartBlazeAttackGoal.volleySize(0));
		assertEquals(2, SmartBlazeAttackGoal.volleySize(1));
		assertEquals(3, SmartBlazeAttackGoal.volleySize(2));
		assertEquals(4, SmartBlazeAttackGoal.volleySize(3));
		assertEquals(4, SmartBlazeAttackGoal.volleySize(99));
	}

	@Test
	void blazeChargeTelegraphShortensButNeverDisappears() {
		assertEquals(36, SmartBlazeAttackGoal.chargeTicks(0));
		assertEquals(36, SmartBlazeAttackGoal.chargeTicks(1));
		assertEquals(30, SmartBlazeAttackGoal.chargeTicks(2));
		assertEquals(24, SmartBlazeAttackGoal.chargeTicks(3));
		assertEquals(24, SmartBlazeAttackGoal.chargeTicks(99));
	}

	@Test
	void eachControllerHonorsMasterAndFeatureSwitches() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		assertTrue(SmartBlazeAttackGoal.enabled(config));
		assertTrue(PiglinBattleLineController.enabled(config));
		assertTrue(HoglinChargeController.enabled(config));
		assertTrue(GhastArtilleryPolicy.enabled(config));

		config.netherAiEnabled = false;
		assertFalse(SmartBlazeAttackGoal.enabled(config));
		assertFalse(PiglinBattleLineController.enabled(config));
		assertFalse(HoglinChargeController.enabled(config));
		assertFalse(GhastArtilleryPolicy.enabled(config));

		config.netherAiEnabled = true;
		config.blazeCombatTactics = false;
		config.piglinFormationTactics = false;
		config.hoglinChargeTactics = false;
		config.ghastArtilleryTactics = false;
		assertFalse(SmartBlazeAttackGoal.enabled(config));
		assertFalse(PiglinBattleLineController.enabled(config));
		assertFalse(HoglinChargeController.enabled(config));
		assertFalse(GhastArtilleryPolicy.enabled(config));
	}

	@Test
	void ghastExpandedVerticalBandHasAnInclusiveSixteenBlockBoundary() {
		assertTrue(GhastArtilleryPolicy.withinVerticalBand(10.0, 26.0));
		assertTrue(GhastArtilleryPolicy.withinVerticalBand(10.0, -6.0));
		assertFalse(GhastArtilleryPolicy.withinVerticalBand(10.0, 26.01));
		assertFalse(GhastArtilleryPolicy.withinVerticalBand(10.0, -6.01));
	}

	@Test
	void hoglinImpulseIsMonotonicAcrossTheValidatedConfigRange() {
		double minimum = HoglinChargeController.chargeImpulse(MobsThinkNowConfig.MINIMUM_HOGLIN_CHARGE_SPEED);
		double normal = HoglinChargeController.chargeImpulse(MobsThinkNowConfig.DEFAULT_HOGLIN_CHARGE_SPEED);
		double maximum = HoglinChargeController.chargeImpulse(MobsThinkNowConfig.MAXIMUM_HOGLIN_CHARGE_SPEED);

		assertEquals(0.48, minimum, 1.0E-9);
		assertTrue(minimum < normal && normal < maximum, "Charge impulse did not scale monotonically.");
		assertTrue(maximum < 0.65, "Validated charge impulse escaped its physical safety cap.");
	}
}

package com.wjz.mobsthinknow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class MobsThinkNowConfigTest {
	@Test
	void missingEngineerFieldsInOlderJsonKeepCodeDefaults() {
		MobsThinkNowConfig config = new Gson().fromJson("{\"enabled\":true}", MobsThinkNowConfig.class);

		assertEquals(true, config.engineerSkills);
		assertEquals(0.08, config.engineerSpawnChance);
		assertEquals(true, config.engineerTntSkill);
		assertEquals(true, config.engineerFluidSkills);
		assertEquals(true, config.engineerIgnitionSkill);
		assertEquals(true, config.skeletonAiEnabled);
		assertEquals(true, config.skeletonEmergencyDisengage);
		assertEquals(true, config.skeletonCoverPeeking);
		assertEquals(true, config.skeletonFiringLaneReposition);
		assertEquals(true, config.skeletonProjectileDodging);
		assertEquals(true, config.skeletonPredictiveAim);
		assertEquals(10.0, config.skeletonPreferredRange);
		assertEquals(0.65, config.skeletonAimPredictionStrength);
		assertEquals(true, config.creeperAiEnabled);
		assertEquals(true, config.creeperFlanking);
		assertEquals(true, config.creeperMovingFuse);
		assertEquals(true, config.creeperSquadEvacuation);
		assertEquals(true, config.zombieProfessionSkins);
		assertEquals(true, config.zombieBodyLanguage);
		assertEquals(4, config.zombieAnimationBlendTicks);
		assertEquals(true, config.dynamicSquadReplanning);
		assertEquals(true, config.observableTargetTactics);
		assertEquals(true, config.swordFeints);
		assertEquals(7, config.swordFeintMinimumIntelligence);
		assertEquals(0.35, config.swordFeintChance);
		assertEquals(true, config.shieldBashes);
		assertEquals(7, config.shieldBashMinimumIntelligence);
		assertEquals(0.35, config.shieldBashChance);
		assertEquals(2.0, config.shieldBashDamage);
		assertEquals(1.25, config.shieldBashKnockback);
		assertEquals(true, config.creeperWallBreaching);
		assertEquals(4.0, config.creeperMaximumFuseStartDistance);
		assertEquals(1.25, config.creeperFuseMovementSpeed);
		assertEquals(true, config.spiderAiEnabled);
		assertEquals(true, config.spiderPredictivePounce);
		assertEquals(true, config.spiderHitAndRun);
		assertEquals(true, config.spiderCreeperCoordination);
		assertEquals(true, config.spiderTransportRouteAssessment);
		assertEquals(8.0, config.spiderCreeperSearchRadius);
		assertEquals(1.40, config.spiderCreeperCarrierSpeed);
		assertEquals(true, config.endermanAiEnabled);
		assertEquals(true, config.endermanCreeperDelivery);
		assertEquals(16.0, config.endermanCreeperSearchRadius);
		assertEquals(300, config.endermanCreeperDeliveryCooldownTicks);
		assertEquals(3.0, config.endermanCreeperDropDistance);
		assertEquals(0.80, config.endermanCreeperFrontDeliveryChance);
		assertEquals(true, config.giantZombieAiEnabled);
		assertEquals(0.01, config.giantZombieSpawnChance);
		assertEquals(160.0, config.giantZombieMaximumHealth);
		assertEquals(14.0, config.giantZombieAttackDamage);
		assertEquals(0.16, config.giantZombieMovementSpeed);
		assertEquals(true, config.giantZombiePayloadThrowing);
		assertEquals(true, config.giantZombieMeleeActions);
		assertEquals(true, config.netherAiEnabled);
		assertEquals(true, config.netherProfessionSkins);
		assertEquals(true, config.piglinFormationTactics);
		assertEquals(true, config.blazeCombatTactics);
		assertEquals(10.0, config.blazePreferredRange);
		assertEquals(0.70, config.netherPredictionStrength);
		assertEquals(true, config.ghastArtilleryTactics);
		assertEquals(true, config.hoglinChargeTactics);
		assertEquals(1.15, config.hoglinChargeSpeed);
		assertEquals(true, config.magmaCubePredictivePounce);
		assertEquals(0.68, config.magmaCubePounceSpeed);
	}

	@Test
	void defaultsToTwentyCoordinatedZombies() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();

		assertEquals(20, config.maximumCoordinatedZombies);
		assertEquals(64, config.briefingTicks);
		assertEquals(48, config.regroupTicks);
	}

	@Test
	void clampsUnsafeValues() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.decisionIntervalTicks = 1;
		config.targetMemoryTicks = 500;
		config.maximumCoordinatedZombies = 1000;
		config.minimumSquadSize = 1000;
		config.squadFormationIntervalTicks = 1;
		config.briefingTicks = 1;
		config.regroupTicks = 1;
		config.rallyQuorum = 9.0;
		config.coordinationRadius = Double.POSITIVE_INFINITY;
		config.formationRadius = 0.5;
		config.tacticalSpeedModifier = Double.NaN;
		config.zombieAnimationBlendTicks = 99;

		config.validate();

		assertEquals(4, config.decisionIntervalTicks);
		assertEquals(200, config.targetMemoryTicks);
		assertEquals(100, config.maximumCoordinatedZombies);
		assertEquals(100, config.minimumSquadSize);
		assertEquals(4, config.squadFormationIntervalTicks);
		assertEquals(60, config.briefingTicks);
		assertEquals(40, config.regroupTicks);
		assertEquals(1.0, config.rallyQuorum);
		assertEquals(4.0, config.coordinationRadius);
		assertEquals(2.0, config.formationRadius);
		assertEquals(0.75, config.tacticalSpeedModifier);
		assertEquals(8, config.zombieAnimationBlendTicks);
	}

	@Test
	void clampsCoordinatedZombieLimitToFour() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.maximumCoordinatedZombies = 1;

		config.validate();

		assertEquals(4, config.maximumCoordinatedZombies);
	}

	@Test
	void animationBlendCanBeDisabledWithoutGoingNegative() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.zombieAnimationBlendTicks = -10;

		config.validate();

		assertEquals(0, config.zombieAnimationBlendTicks);
	}

	@Test
	void armedSquadsAreOffByDefaultWhileVisualsAreOn() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();

		assertEquals(false, config.armedSquads);
		assertEquals(true, config.weaponCombatTactics);
		assertEquals(true, config.swordFeints);
		assertEquals(7, config.swordFeintMinimumIntelligence);
		assertEquals(0.35, config.swordFeintChance);
		assertEquals(true, config.shieldBashes);
		assertEquals(7, config.shieldBashMinimumIntelligence);
		assertEquals(0.35, config.shieldBashChance);
		assertEquals(2.0, config.shieldBashDamage);
		assertEquals(1.25, config.shieldBashKnockback);
		assertEquals(true, config.spearAirAssault);
		assertEquals(0.50, config.spearRocketEfficiency);
		assertEquals(true, config.squadVisualEffects);
		assertEquals(true, config.zombieProfessionSkins);
		assertEquals(true, config.zombieBodyLanguage);
		assertEquals(true, config.squadRoleNameTags);
		assertEquals(true, config.individualTraits);
		assertEquals(true, config.squadIgnoreFriendlyFire);
		assertEquals(true, config.retreatTactics);
		assertEquals(0.20, config.retreatHealthThreshold);
		assertEquals(0.30, config.retreatHeavyHitThreshold);
		assertEquals(100, config.retreatMaximumTicks);
		assertEquals(5.0, config.retreatSafeDistance);
		assertEquals(1.50, config.retreatSpeedModifier);
		assertEquals(true, config.foodScavenging);
		assertEquals(6, config.foodMinimumIntelligence);
		assertEquals(true, config.terrainTactics);
		assertEquals(true, config.sunlightSurvival);
		assertEquals(true, config.smartTraversal);
		assertEquals(8, config.terrainMinimumIntelligence);
		assertEquals(8, config.terrainBlockInventoryLimit);
		assertEquals(true, config.engineerSkills);
		assertEquals(0.08, config.engineerSpawnChance);
		assertEquals(true, config.engineerTntSkill);
		assertEquals(true, config.engineerFluidSkills);
		assertEquals(true, config.engineerIgnitionSkill);
		assertEquals(0.10, config.squadSpeedBonus);
		assertEquals(0.85, config.armedChanceHard);
		assertEquals(0.25, config.armedShieldChance);
		assertEquals(true, config.specialEquipment);
		assertEquals(true, config.fluidTactics);
		assertEquals(0.04, config.waterBucketChance);
		assertEquals(0.02, config.lavaBucketChance);
		assertEquals(0.085, config.specialEquipmentDropChance);
	}

	@Test
	void clampsRetreatValues() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.retreatHealthThreshold = 0.9;
		config.retreatHeavyHitThreshold = 9.0;
		config.retreatMaximumTicks = 500;
		config.retreatSafeDistance = 100.0;
		config.retreatSpeedModifier = 3.0;
		config.foodMinimumIntelligence = 99;
		config.terrainMinimumIntelligence = 1;
		config.terrainBlockInventoryLimit = 99;
		config.engineerSpawnChance = 9.0;
		config.waterBucketChance = 4.0;
		config.lavaBucketChance = -2.0;
		config.specialEquipmentDropChance = Double.NaN;

		config.validate();

		assertEquals(0.5, config.retreatHealthThreshold);
		assertEquals(1.0, config.retreatHeavyHitThreshold);
		assertEquals(200, config.retreatMaximumTicks);
		assertEquals(16.0, config.retreatSafeDistance);
		assertEquals(2.0, config.retreatSpeedModifier);
		assertEquals(10, config.foodMinimumIntelligence);
		assertEquals(6, config.terrainMinimumIntelligence);
		assertEquals(16, config.terrainBlockInventoryLimit);
		assertEquals(1.0, config.engineerSpawnChance);
		assertEquals(1.0, config.waterBucketChance);
		assertEquals(0.0, config.lavaBucketChance);
		assertEquals(0.0, config.specialEquipmentDropChance);
	}

	@Test
	void clampsSquadSpeedBonus() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.squadSpeedBonus = 5.0;
		config.validate();
		assertEquals(0.5, config.squadSpeedBonus);

		config.squadSpeedBonus = -1.0;
		config.validate();
		assertEquals(0.0, config.squadSpeedBonus);
	}

	@Test
	void clampsArmedSquadValues() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.armedChanceEasy = -0.5;
		config.armedChanceNormal = 3.0;
		config.armedChanceHard = Double.NaN;
		config.armedShieldChance = 7.0;
		config.armedShieldBreakSeconds = 99.0;
		config.armedFlankSpeedBonus = 2.0;
		config.spearRocketEfficiency = 4.0;
		config.swordFeintMinimumIntelligence = -5;
		config.swordFeintChance = 9.0;
		config.shieldBashMinimumIntelligence = 99;
		config.shieldBashChance = Double.NaN;
		config.shieldBashDamage = 99.0;
		config.shieldBashKnockback = -2.0;

		config.validate();

		assertEquals(0.0, config.armedChanceEasy);
		assertEquals(1.0, config.armedChanceNormal);
		assertEquals(0.0, config.armedChanceHard);
		assertEquals(1.0, config.armedShieldChance);
		assertEquals(10.0, config.armedShieldBreakSeconds);
		assertEquals(0.35, config.armedFlankSpeedBonus);
		assertEquals(1.0, config.spearRocketEfficiency);
		assertEquals(1, config.swordFeintMinimumIntelligence);
		assertEquals(1.0, config.swordFeintChance);
		assertEquals(10, config.shieldBashMinimumIntelligence);
		assertEquals(0.0, config.shieldBashChance);
		assertEquals(8.0, config.shieldBashDamage);
		assertEquals(0.0, config.shieldBashKnockback);

		config.spearRocketEfficiency = -2.0;
		config.validate();
		assertEquals(0.0, config.spearRocketEfficiency);
	}

	@Test
	void clampsSkeletonRangedTactics() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.skeletonPreferredRange = 100.0;
		config.skeletonAimPredictionStrength = 3.0;

		config.validate();

		assertEquals(16.0, config.skeletonPreferredRange);
		assertEquals(1.0, config.skeletonAimPredictionStrength);

		config.skeletonPreferredRange = Double.NaN;
		config.skeletonAimPredictionStrength = -1.0;
		config.validate();
		assertEquals(6.0, config.skeletonPreferredRange);
		assertEquals(0.0, config.skeletonAimPredictionStrength);
	}

	@Test
	void clampsCreeperThreatEnvelope() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.creeperMaximumFuseStartDistance = 99.0;
		config.creeperFuseMovementSpeed = 9.0;
		config.validate();

		assertEquals(5.0, config.creeperMaximumFuseStartDistance);
		assertEquals(1.5, config.creeperFuseMovementSpeed);

		config.creeperMaximumFuseStartDistance = Double.NaN;
		config.creeperFuseMovementSpeed = -1.0;
		config.validate();
		assertEquals(3.0, config.creeperMaximumFuseStartDistance);
		assertEquals(1.0, config.creeperFuseMovementSpeed);
	}

	@Test
	void clampsSpiderCarrierEnvelope() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.spiderCreeperSearchRadius = 99.0;
		config.spiderCreeperCarrierSpeed = 9.0;
		config.validate();

		assertEquals(16.0, config.spiderCreeperSearchRadius);
		assertEquals(1.70, config.spiderCreeperCarrierSpeed);

		config.spiderCreeperSearchRadius = Double.NaN;
		config.spiderCreeperCarrierSpeed = -1.0;
		config.validate();
		assertEquals(4.0, config.spiderCreeperSearchRadius);
		assertEquals(1.10, config.spiderCreeperCarrierSpeed);
	}

	@Test
	void clampsEndermanDeliveryEnvelope() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.endermanCreeperSearchRadius = 100.0;
		config.endermanCreeperDeliveryCooldownTicks = 5000;
		config.endermanCreeperDropDistance = 99.0;
		config.endermanCreeperFrontDeliveryChance = 4.0;
		config.validate();

		assertEquals(32.0, config.endermanCreeperSearchRadius);
		assertEquals(1200, config.endermanCreeperDeliveryCooldownTicks);
		assertEquals(6.0, config.endermanCreeperDropDistance);
		assertEquals(1.0, config.endermanCreeperFrontDeliveryChance);

		config.endermanCreeperSearchRadius = Double.NaN;
		config.endermanCreeperDeliveryCooldownTicks = -5;
		config.endermanCreeperDropDistance = -1.0;
		config.endermanCreeperFrontDeliveryChance = Double.NaN;
		config.validate();
		assertEquals(6.0, config.endermanCreeperSearchRadius);
		assertEquals(100, config.endermanCreeperDeliveryCooldownTicks);
		assertEquals(2.0, config.endermanCreeperDropDistance);
		assertEquals(0.0, config.endermanCreeperFrontDeliveryChance);
	}

	@Test
	void clampsGiantZombieProfile() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.giantZombieSpawnChance = 5.0;
		config.giantZombieMaximumHealth = 999.0;
		config.giantZombieAttackDamage = -5.0;
		config.giantZombieMovementSpeed = Double.NaN;
		config.validate();

		assertEquals(1.0, config.giantZombieSpawnChance);
		assertEquals(400.0, config.giantZombieMaximumHealth);
		assertEquals(4.0, config.giantZombieAttackDamage);
		assertEquals(0.08, config.giantZombieMovementSpeed);
	}

	@Test
	void clampsNetherCombatEnvelope() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.blazePreferredRange = 100.0;
		config.netherPredictionStrength = 5.0;
		config.hoglinChargeSpeed = 9.0;
		config.magmaCubePounceSpeed = -1.0;

		config.validate();

		assertEquals(MobsThinkNowConfig.MAXIMUM_BLAZE_PREFERRED_RANGE, config.blazePreferredRange);
		assertEquals(1.0, config.netherPredictionStrength);
		assertEquals(MobsThinkNowConfig.MAXIMUM_HOGLIN_CHARGE_SPEED, config.hoglinChargeSpeed);
		assertEquals(MobsThinkNowConfig.MINIMUM_MAGMA_CUBE_POUNCE_SPEED, config.magmaCubePounceSpeed);

		config.blazePreferredRange = Double.NaN;
		config.netherPredictionStrength = Double.NaN;
		config.hoglinChargeSpeed = Double.NaN;
		config.magmaCubePounceSpeed = Double.NaN;
		config.validate();

		assertEquals(MobsThinkNowConfig.MINIMUM_BLAZE_PREFERRED_RANGE, config.blazePreferredRange);
		assertEquals(0.0, config.netherPredictionStrength);
		assertEquals(MobsThinkNowConfig.MINIMUM_HOGLIN_CHARGE_SPEED, config.hoglinChargeSpeed);
		assertEquals(MobsThinkNowConfig.MINIMUM_MAGMA_CUBE_POUNCE_SPEED, config.magmaCubePounceSpeed);
	}
}

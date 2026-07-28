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
		assertEquals(true, config.skeletonProjectileDodging);
		assertEquals(true, config.skeletonPredictiveAim);
		assertEquals(10.0, config.skeletonPreferredRange);
		assertEquals(0.65, config.skeletonAimPredictionStrength);
	}

	@Test
	void defaultsToTwentyCoordinatedZombies() {
		assertEquals(20, new MobsThinkNowConfig().maximumCoordinatedZombies);
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
		config.rallyQuorum = 9.0;
		config.coordinationRadius = Double.POSITIVE_INFINITY;
		config.formationRadius = 0.5;
		config.tacticalSpeedModifier = Double.NaN;

		config.validate();

		assertEquals(4, config.decisionIntervalTicks);
		assertEquals(200, config.targetMemoryTicks);
		assertEquals(100, config.maximumCoordinatedZombies);
		assertEquals(100, config.minimumSquadSize);
		assertEquals(4, config.squadFormationIntervalTicks);
		assertEquals(8, config.briefingTicks);
		assertEquals(1.0, config.rallyQuorum);
		assertEquals(4.0, config.coordinationRadius);
		assertEquals(2.0, config.formationRadius);
		assertEquals(0.75, config.tacticalSpeedModifier);
	}

	@Test
	void clampsCoordinatedZombieLimitToFour() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.maximumCoordinatedZombies = 1;

		config.validate();

		assertEquals(4, config.maximumCoordinatedZombies);
	}

	@Test
	void armedSquadsAreOffByDefaultWhileVisualsAreOn() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();

		assertEquals(false, config.armedSquads);
		assertEquals(true, config.weaponCombatTactics);
		assertEquals(true, config.spearAirAssault);
		assertEquals(0.50, config.spearRocketEfficiency);
		assertEquals(true, config.squadVisualEffects);
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

		config.validate();

		assertEquals(0.0, config.armedChanceEasy);
		assertEquals(1.0, config.armedChanceNormal);
		assertEquals(0.0, config.armedChanceHard);
		assertEquals(1.0, config.armedShieldChance);
		assertEquals(10.0, config.armedShieldBreakSeconds);
		assertEquals(0.35, config.armedFlankSpeedBonus);
		assertEquals(1.0, config.spearRocketEfficiency);

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
}

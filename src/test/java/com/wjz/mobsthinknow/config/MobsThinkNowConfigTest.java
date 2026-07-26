package com.wjz.mobsthinknow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MobsThinkNowConfigTest {
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
		assertEquals(true, config.squadVisualEffects);
		assertEquals(true, config.squadRoleNameTags);
		assertEquals(true, config.baitTactics);
		assertEquals(0.10, config.squadSpeedBonus);
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
		config.armedShieldBreakSeconds = 99.0;
		config.armedFlankSpeedBonus = 2.0;

		config.validate();

		assertEquals(0.0, config.armedChanceEasy);
		assertEquals(1.0, config.armedChanceNormal);
		assertEquals(0.0, config.armedChanceHard);
		assertEquals(10.0, config.armedShieldBreakSeconds);
		assertEquals(0.35, config.armedFlankSpeedBonus);
	}
}

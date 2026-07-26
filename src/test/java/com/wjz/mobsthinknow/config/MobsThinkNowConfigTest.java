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
}

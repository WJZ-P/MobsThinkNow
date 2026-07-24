package com.wjz.mobsthinknow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MobsThinkNowConfigTest {
	@Test
	void clampsUnsafeValues() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.decisionIntervalTicks = 1;
		config.targetMemoryTicks = 500;
		config.maximumCoordinatedZombies = 100;
		config.coordinationRadius = Double.POSITIVE_INFINITY;
		config.formationRadius = 0.5;
		config.tacticalSpeedModifier = Double.NaN;

		config.validate();

		assertEquals(4, config.decisionIntervalTicks);
		assertEquals(200, config.targetMemoryTicks);
		assertEquals(16, config.maximumCoordinatedZombies);
		assertEquals(4.0, config.coordinationRadius);
		assertEquals(2.0, config.formationRadius);
		assertEquals(0.75, config.tacticalSpeedModifier);
	}
}

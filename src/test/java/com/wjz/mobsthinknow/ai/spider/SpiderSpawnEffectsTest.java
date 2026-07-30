package com.wjz.mobsthinknow.ai.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpiderSpawnEffectsTest {
	@Test
	void speedEffectRollUsesOnePercentLevelTwoAndFourPercentLevelOne() {
		assertEquals(1, SpiderSpawnEffects.speedAmplifier(0.0));
		assertEquals(1, SpiderSpawnEffects.speedAmplifier(0.009999));
		assertEquals(0, SpiderSpawnEffects.speedAmplifier(0.01));
		assertEquals(0, SpiderSpawnEffects.speedAmplifier(0.049999));
		assertEquals(-1, SpiderSpawnEffects.speedAmplifier(0.05));
		assertEquals(-1, SpiderSpawnEffects.speedAmplifier(1.0));
	}
}

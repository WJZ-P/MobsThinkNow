package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZombieVoiceProfileTest {
	@Test
	void rollMapsToStableBoundedVoiceRange() {
		assertEquals(0.86F, ZombieVoiceProfile.factorForRoll(0.0F), 0.0001F);
		assertEquals(1.00F, ZombieVoiceProfile.factorForRoll(0.5F), 0.0001F);
		assertEquals(1.14F, ZombieVoiceProfile.factorForRoll(1.0F), 0.0001F);
		assertEquals(0.86F, ZombieVoiceProfile.factorForRoll(-5.0F), 0.0001F);
		assertEquals(1.14F, ZombieVoiceProfile.factorForRoll(5.0F), 0.0001F);
	}
}

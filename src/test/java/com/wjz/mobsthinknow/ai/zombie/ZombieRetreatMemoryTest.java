package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZombieRetreatMemoryTest {
	@Test
	void actualDamageUsesHealthDifferenceAfterAllMitigation() {
		assertEquals(6.0F, ZombieRetreatMemory.actualHealthDamage(20.0F, 14.0F));
		assertEquals(0.25F, ZombieRetreatMemory.actualHealthDamage(4.0F, 3.75F));
	}

	@Test
	void healingDuringAHookNeverBecomesNegativeDamage() {
		assertEquals(0.0F, ZombieRetreatMemory.actualHealthDamage(10.0F, 12.0F));
	}
}

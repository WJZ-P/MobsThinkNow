package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieShieldCombatTest {
	@Test
	void guardRollMapsToInclusiveRandomEndpoints() {
		assertEquals(12, ZombieShieldCombat.guardDurationTicks(12, 28, 0));
		assertEquals(28, ZombieShieldCombat.guardDurationTicks(12, 28, 16));
		assertThrows(
			IllegalArgumentException.class,
			() -> ZombieShieldCombat.guardDurationTicks(12, 28, 17)
		);
	}

	@Test
	void strikeOpensForAnAttackOrExpiredBluffOnlyWhenWeaponIsReady() {
		assertFalse(ZombieShieldCombat.shouldOpenStrike(false, 19L, 20L, true));
		assertTrue(ZombieShieldCombat.shouldOpenStrike(false, 20L, 20L, true));
		assertTrue(ZombieShieldCombat.shouldOpenStrike(true, 10L, 20L, true));
		assertFalse(ZombieShieldCombat.shouldOpenStrike(true, 10L, 20L, false));
		assertFalse(ZombieShieldCombat.shouldOpenStrike(false, 20L, 20L, false));
	}

	@Test
	void incomingAttackSignalHasABoundedReactionWindow() {
		assertTrue(ZombieShieldCombat.isFreshAttackSignal(100L, 100L));
		assertTrue(ZombieShieldCombat.isFreshAttackSignal(120L, 100L));
		assertFalse(ZombieShieldCombat.isFreshAttackSignal(121L, 100L));
		assertFalse(ZombieShieldCombat.isFreshAttackSignal(99L, 100L));
	}
}

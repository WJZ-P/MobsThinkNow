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
	void strikeRespectsCounterDelayOrExpiredBluffAndWeaponCooldown() {
		assertFalse(ZombieShieldCombat.shouldOpenStrike(false, 19L, Long.MIN_VALUE, 20L, true));
		assertTrue(ZombieShieldCombat.shouldOpenStrike(false, 20L, Long.MIN_VALUE, 20L, true));
		assertFalse(ZombieShieldCombat.shouldOpenStrike(true, 101L, 102L, 20L, true));
		assertTrue(ZombieShieldCombat.shouldOpenStrike(true, 102L, 102L, 20L, true));
		assertFalse(ZombieShieldCombat.shouldOpenStrike(true, 102L, 102L, 20L, false));
		assertFalse(ZombieShieldCombat.shouldOpenStrike(false, 20L, Long.MIN_VALUE, 20L, false));
	}

	@Test
	void counterDelayRollMapsToTwoThroughFourTicks() {
		assertEquals(2, ZombieShieldCombat.counterDelayTicks(0));
		assertEquals(4, ZombieShieldCombat.counterDelayTicks(2));
		assertThrows(IllegalArgumentException.class, () -> ZombieShieldCombat.counterDelayTicks(-1));
		assertThrows(IllegalArgumentException.class, () -> ZombieShieldCombat.counterDelayTicks(3));
	}

	@Test
	void incomingAttackSignalHasABoundedReactionWindow() {
		assertTrue(ZombieShieldCombat.isFreshAttackSignal(100L, 100L));
		assertTrue(ZombieShieldCombat.isFreshAttackSignal(120L, 100L));
		assertFalse(ZombieShieldCombat.isFreshAttackSignal(121L, 100L));
		assertFalse(ZombieShieldCombat.isFreshAttackSignal(99L, 100L));
	}

	@Test
	void shieldBashRequiresFeatureIntelligenceAndOneSuccessfulRoll() {
		assertTrue(ZombieShieldCombat.shouldScheduleBash(true, 8, 7, 0.34, 0.35));
		assertFalse(ZombieShieldCombat.shouldScheduleBash(false, 8, 7, 0.0, 1.0));
		assertFalse(ZombieShieldCombat.shouldScheduleBash(true, 6, 7, 0.0, 1.0));
		assertFalse(ZombieShieldCombat.shouldScheduleBash(true, 8, 7, 0.35, 0.35));
	}
}

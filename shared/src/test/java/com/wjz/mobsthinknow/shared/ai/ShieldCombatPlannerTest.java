package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShieldCombatPlannerTest {
	@Test
	void inclusiveRollsMapToBothEndpoints() {
		assertEquals(12, ShieldCombatPlanner.guardDurationTicks(12, 28, 0));
		assertEquals(28, ShieldCombatPlanner.guardDurationTicks(12, 28, 16));
		assertEquals(2, ShieldCombatPlanner.counterDelayTicks(2, 4, 0));
		assertEquals(4, ShieldCombatPlanner.counterDelayTicks(2, 4, 2));
		assertThrows(IllegalArgumentException.class, () ->
			ShieldCombatPlanner.counterDelayTicks(4, 2, 0));
		assertThrows(IllegalArgumentException.class, () ->
			ShieldCombatPlanner.guardDurationTicks(12, 28, 17));
	}

	@Test
	void realCounterSignalOverridesTheBluffDeadlineButNotCooldown() {
		assertFalse(ShieldCombatPlanner.shouldOpenStrike(false, 19L, Long.MIN_VALUE, 20L, true));
		assertTrue(ShieldCombatPlanner.shouldOpenStrike(false, 20L, Long.MIN_VALUE, 20L, true));
		assertFalse(ShieldCombatPlanner.shouldOpenStrike(true, 101L, 102L, 20L, true));
		assertTrue(ShieldCombatPlanner.shouldOpenStrike(true, 102L, 102L, 20L, true));
		assertFalse(ShieldCombatPlanner.shouldOpenStrike(true, 102L, 102L, 20L, false));
	}

	@Test
	void attackSignalsHaveABoundedNonFutureLifetime() {
		assertTrue(ShieldCombatPlanner.isFreshSignal(100L, 100L, 20L));
		assertTrue(ShieldCombatPlanner.isFreshSignal(120L, 100L, 20L));
		assertFalse(ShieldCombatPlanner.isFreshSignal(121L, 100L, 20L));
		assertFalse(ShieldCombatPlanner.isFreshSignal(99L, 100L, 20L));
		assertFalse(ShieldCombatPlanner.isFreshSignal(100L, 100L, -1L));
	}

	@Test
	void bashRollRequiresFeatureIntelligenceAndFiniteProbability() {
		assertTrue(ShieldCombatPlanner.shouldScheduleBash(true, 8, 7, 0.34, 0.35));
		assertFalse(ShieldCombatPlanner.shouldScheduleBash(false, 8, 7, 0.0, 1.0));
		assertFalse(ShieldCombatPlanner.shouldScheduleBash(true, 6, 7, 0.0, 1.0));
		assertFalse(ShieldCombatPlanner.shouldScheduleBash(true, 8, 7, 0.35, 0.35));
		assertFalse(ShieldCombatPlanner.shouldScheduleBash(true, 8, 7, Double.NaN, 1.0));
	}

	@Test
	void directionalGuardAcceptsTheConfiguredFrontArcOnly() {
		assertTrue(ShieldCombatPlanner.canGuardDirection(0.0, 1.0, 0.0, 5.0, 0.0));
		assertTrue(ShieldCombatPlanner.canGuardDirection(1.0, 1.0, 4.0, 4.0, 0.95));
		assertTrue(ShieldCombatPlanner.canGuardDirection(0.0, 1.0, 1.0, 1.0, 0.70));
		assertFalse(ShieldCombatPlanner.canGuardDirection(0.0, 1.0, 1.0, 1.0, 0.71));
		assertFalse(ShieldCombatPlanner.canGuardDirection(0.0, 1.0, 0.0, -5.0, 0.0));
		assertFalse(ShieldCombatPlanner.canGuardDirection(0.0, 0.0, 0.0, 1.0, 0.0));
		assertFalse(ShieldCombatPlanner.canGuardDirection(Double.NaN, 1.0, 0.0, 1.0, 0.0));
		assertFalse(ShieldCombatPlanner.canGuardDirection(0.0, 1.0, 0.0, 1.0, 2.0));
	}

	@Test
	void shieldDisableWindowsAreExclusiveAndOverflowSafe() {
		assertEquals(160L, ShieldCombatPlanner.disabledUntil(100L, 60L));
		assertTrue(ShieldCombatPlanner.isDisabled(159L, 160L));
		assertFalse(ShieldCombatPlanner.isDisabled(160L, 160L));
		assertEquals(Long.MAX_VALUE, ShieldCombatPlanner.disabledUntil(Long.MAX_VALUE - 2L, 10L));
		assertThrows(IllegalArgumentException.class, () -> ShieldCombatPlanner.disabledUntil(10L, -1L));
	}
}

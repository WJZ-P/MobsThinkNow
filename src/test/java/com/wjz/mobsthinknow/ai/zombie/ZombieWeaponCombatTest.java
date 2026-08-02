package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ZombieWeaponCombatTest {
	@Test
	void convertsPlayerStyleAttackSpeedIntoWholeTickCooldown() {
		assertEquals(25, ZombieWeaponCombat.attackCooldownTicksFromSpeed(0.8, true));
		assertEquals(23, ZombieWeaponCombat.attackCooldownTicksFromSpeed(0.9, true));
		assertEquals(20, ZombieWeaponCombat.attackCooldownTicksFromSpeed(1.0, true));
		assertEquals(13, ZombieWeaponCombat.attackCooldownTicksFromSpeed(1.6, true));
	}

	@Test
	void missingOrInvalidAttackSpeedFallsBackToVanillaMobInterval() {
		assertEquals(20, ZombieWeaponCombat.attackCooldownTicksFromSpeed(4.0, false));
		assertEquals(20, ZombieWeaponCombat.attackCooldownTicksFromSpeed(0.0, true));
		assertEquals(20, ZombieWeaponCombat.attackCooldownTicksFromSpeed(Double.NaN, true));
	}

	@Test
	void spacingPointStaysOnCircleAndEntityParityCanChooseEitherDirection() {
		Vec3 target = Vec3.ZERO;
		Vec3 zombie = new Vec3(3.0, 0.0, 0.0);
		Vec3 clockwise = ZombieWeaponCombat.spacingDestination(zombie, target, 2.8, true);
		Vec3 counterClockwise = ZombieWeaponCombat.spacingDestination(zombie, target, 2.8, false);

		assertEquals(2.8 * 2.8, ZombieWeaponCombat.horizontalDistanceSquared(clockwise, target), 1.0E-6);
		assertEquals(2.8 * 2.8, ZombieWeaponCombat.horizontalDistanceSquared(counterClockwise, target), 1.0E-6);
		assertTrue(clockwise.z > 0.0);
		assertTrue(counterClockwise.z < 0.0);
	}

	@Test
	void swordFeintRequiresOneEligibleBlockingCycleAndUsesStrictProbabilityBoundary() {
		assertTrue(ZombieWeaponCombat.shouldStartSwordFeint(true, 8, 7, true, 4.0, 0.34, 0.35));
		assertTrue(!ZombieWeaponCombat.shouldStartSwordFeint(false, 8, 7, true, 4.0, 0.0, 1.0));
		assertTrue(!ZombieWeaponCombat.shouldStartSwordFeint(true, 6, 7, true, 4.0, 0.0, 1.0));
		assertTrue(!ZombieWeaponCombat.shouldStartSwordFeint(true, 8, 7, false, 4.0, 0.0, 1.0));
		assertTrue(!ZombieWeaponCombat.shouldStartSwordFeint(true, 8, 7, true, 100.0, 0.0, 1.0));
		assertTrue(!ZombieWeaponCombat.shouldStartSwordFeint(true, 8, 7, true, 4.0, 0.35, 0.35));
	}
}

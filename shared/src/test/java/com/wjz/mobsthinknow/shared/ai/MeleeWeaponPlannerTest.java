package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.junit.jupiter.api.Test;

class MeleeWeaponPlannerTest {
	@Test
	void convertsExplicitPlayerAttackSpeedIntoWholeTickCooldown() {
		assertEquals(25, MeleeWeaponPlanner.attackCooldownTicks(0.8, true));
		assertEquals(23, MeleeWeaponPlanner.attackCooldownTicks(0.9, true));
		assertEquals(20, MeleeWeaponPlanner.attackCooldownTicks(1.0, true));
		assertEquals(13, MeleeWeaponPlanner.attackCooldownTicks(1.6, true));
		assertEquals(5, MeleeWeaponPlanner.attackCooldownTicks(100.0, true));
	}

	@Test
	void missingOrInvalidSpeedUsesVanillaMobInterval() {
		assertEquals(20, MeleeWeaponPlanner.attackCooldownTicks(4.0, false));
		assertEquals(20, MeleeWeaponPlanner.attackCooldownTicks(0.0, true));
		assertEquals(20, MeleeWeaponPlanner.attackCooldownTicks(Double.NaN, true));
	}

	@Test
	void spacingPointStaysOnCircleAndParityChoosesEitherSide() {
		Vec3d target = Vec3d.ZERO;
		Vec3d attacker = new Vec3d(3.0, 0.0, 0.0);
		Vec3d clockwise = MeleeWeaponPlanner.spacingDestination(attacker, target, 2.8, true);
		Vec3d counterClockwise = MeleeWeaponPlanner.spacingDestination(attacker, target, 2.8, false);

		assertEquals(2.8 * 2.8, MeleeWeaponPlanner.horizontalDistanceSquared(clockwise, target), 1.0E-6);
		assertEquals(2.8 * 2.8, MeleeWeaponPlanner.horizontalDistanceSquared(counterClockwise, target), 1.0E-6);
		assertTrue(clockwise.z() > 0.0);
		assertTrue(counterClockwise.z() < 0.0);
	}

	@Test
	void axeLaunchBandIncludesItsEdgesAndRejectsVerticalMismatch() {
		Vec3d attacker = Vec3d.ZERO;
		assertTrue(MeleeWeaponPlanner.isAxeLaunchBand(attacker, new Vec3d(1.8, 1.25, 0.0), 1.8, 3.3, 1.25));
		assertTrue(MeleeWeaponPlanner.isAxeLaunchBand(attacker, new Vec3d(3.3, 0.0, 0.0), 1.8, 3.3, 1.25));
		assertFalse(MeleeWeaponPlanner.isAxeLaunchBand(attacker, new Vec3d(1.79, 0.0, 0.0), 1.8, 3.3, 1.25));
		assertFalse(MeleeWeaponPlanner.isAxeLaunchBand(attacker, new Vec3d(2.0, 1.26, 0.0), 1.8, 3.3, 1.25));
	}

	@Test
	void leapVelocityPreservesVerticalMotionAndGuidanceIsBounded() {
		Vec3d launch = MeleeWeaponPlanner.axeLeapVelocity(Vec3d.ZERO, new Vec3d(0.0, 0.0, 5.0), 0.42, 0.34);
		assertEquals(0.0, launch.x(), 1.0E-9);
		assertEquals(0.42, launch.y(), 1.0E-9);
		assertEquals(0.34, launch.z(), 1.0E-9);

		Vec3d guided = MeleeWeaponPlanner.guideAxeLeap(
			new Vec3d(0.34, -0.1, 0.0),
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 5.0),
			0.34,
			0.20
		);
		assertEquals(0.272, guided.x(), 1.0E-9);
		assertEquals(-0.1, guided.y(), 1.0E-9);
		assertEquals(0.068, guided.z(), 1.0E-9);
	}

	@Test
	void invalidTuningFailsAtTheSharedBoundary() {
		assertThrows(IllegalArgumentException.class, () ->
			MeleeWeaponPlanner.spacingDestination(Vec3d.ZERO, Vec3d.ZERO, 0.0, true));
		assertThrows(IllegalArgumentException.class, () ->
			MeleeWeaponPlanner.axeLeapVelocity(Vec3d.ZERO, Vec3d.ZERO, 0.0, Double.NaN));
	}
}

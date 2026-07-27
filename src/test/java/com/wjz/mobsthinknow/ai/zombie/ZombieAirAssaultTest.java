package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ZombieAirAssaultTest {
	@Test
	void rocketCountAlwaysStaysInsideRequestedRange() {
		for (Difficulty difficulty : Difficulty.values()) {
			assertEquals(ZombieAirAssault.MINIMUM_ROCKETS, ZombieAirAssault.rocketCount(difficulty, 0.0));
			assertEquals(ZombieAirAssault.MAXIMUM_ROCKETS, ZombieAirAssault.rocketCount(difficulty, 1.0));
			for (int sample = 0; sample <= 1000; sample++) {
				int count = ZombieAirAssault.rocketCount(difficulty, sample / 1000.0);
				assertTrue(count >= 16 && count <= 64);
			}
		}
	}

	@Test
	void higherDifficultyRaisesTheSameRandomRollAndTheAverage() {
		long easyTotal = 0L;
		long normalTotal = 0L;
		long hardTotal = 0L;
		for (int sample = 0; sample <= 1000; sample++) {
			double roll = sample / 1000.0;
			int easy = ZombieAirAssault.rocketCount(Difficulty.EASY, roll);
			int normal = ZombieAirAssault.rocketCount(Difficulty.NORMAL, roll);
			int hard = ZombieAirAssault.rocketCount(Difficulty.HARD, roll);
			assertTrue(easy <= normal && normal <= hard);
			easyTotal += easy;
			normalTotal += normal;
			hardTotal += hard;
		}
		assertTrue(easyTotal < normalTotal && normalTotal < hardTotal);
	}

	@Test
	void invalidRollFallsBackToMinimumInsteadOfLeakingAnInvalidStackSize() {
		assertEquals(16, ZombieAirAssault.rocketCount(Difficulty.HARD, Double.NaN));
		assertEquals(16, ZombieAirAssault.rocketCount(Difficulty.HARD, Double.NEGATIVE_INFINITY));
		assertEquals(16, ZombieAirAssault.rocketCount(Difficulty.EASY, Double.POSITIVE_INFINITY));
	}

	@Test
	void fullRocketEfficiencyExactlyMatchesTheVanillaMovementFormula() {
		Vec3 movement = new Vec3(0.35, -0.08, 0.22);
		Vec3 look = new Vec3(0.6, 0.2, 0.7745966692414834);
		Vec3 vanilla = movement.add(
			look.x * 0.1 + (look.x * 1.5 - movement.x) * 0.5,
			look.y * 0.1 + (look.y * 1.5 - movement.y) * 0.5,
			look.z * 0.1 + (look.z * 1.5 - movement.z) * 0.5
		);
		Vec3 scaled = ZombieAirAssault.rocketBoostMovement(movement, look, 1.0);

		assertEquals(vanilla.x, scaled.x, 1.0E-12);
		assertEquals(vanilla.y, scaled.y, 1.0E-12);
		assertEquals(vanilla.z, scaled.z, 1.0E-12);
	}

	@Test
	void halfEfficiencyConvergesToHalfTheVanillaStableSpeed() {
		Vec3 movement = Vec3.ZERO;
		for (int tick = 0; tick < 200; tick++) {
			movement = ZombieAirAssault.rocketBoostMovement(movement, new Vec3(1.0, 0.0, 0.0), 0.5);
		}

		assertEquals(0.85, movement.x, 1.0E-9);
		assertEquals(0.0, movement.y, 1.0E-12);
		assertEquals(0.0, movement.z, 1.0E-12);
		assertEquals(Vec3.ZERO, ZombieAirAssault.rocketBoostMovement(new Vec3(0.2, 0.0, 0.0), Vec3.ZERO, 0.0)
			.subtract(new Vec3(0.2, 0.0, 0.0)));
	}

	@Test
	void orbitDelayAlwaysStaysInsideThreeToSixSeconds() {
		assertEquals(60, ZombieSpearAirAssaultGoal.orbitDurationTicks(0.0));
		assertEquals(120, ZombieSpearAirAssaultGoal.orbitDurationTicks(1.0));
		assertEquals(60, ZombieSpearAirAssaultGoal.orbitDurationTicks(Double.NaN));
		for (int sample = 0; sample <= 1000; sample++) {
			int duration = ZombieSpearAirAssaultGoal.orbitDurationTicks(sample / 1000.0);
			assertTrue(duration >= 60 && duration <= 120);
		}
	}

	@Test
	void eachOrbitBudgetsOnlyOneOrTwoRockets() {
		assertEquals(1, ZombieSpearAirAssaultGoal.orbitRocketCount(0.0));
		assertEquals(2, ZombieSpearAirAssaultGoal.orbitRocketCount(1.0));
		assertEquals(1, ZombieSpearAirAssaultGoal.orbitRocketCount(Double.NaN));
		for (int sample = 0; sample <= 1000; sample++) {
			int count = ZombieSpearAirAssaultGoal.orbitRocketCount(sample / 1000.0);
			assertTrue(count >= 1 && count <= 2);
		}
	}

	@Test
	void orbitRocketTimingLeavesARealInertiaWindow() {
		assertEquals(12, ZombieSpearAirAssaultGoal.orbitFirstRocketDelayTicks(0.0));
		assertEquals(24, ZombieSpearAirAssaultGoal.orbitFirstRocketDelayTicks(1.0));
		assertEquals(48, ZombieSpearAirAssaultGoal.orbitRocketGapTicks(0.0));
		assertEquals(68, ZombieSpearAirAssaultGoal.orbitRocketGapTicks(1.0));
		assertEquals(40, ZombieSpearAirAssaultGoal.rocketCooldownTicks(0.0));
		assertEquals(60, ZombieSpearAirAssaultGoal.rocketCooldownTicks(1.0));
		for (int sample = 0; sample <= 1000; sample++) {
			double roll = sample / 1000.0;
			int gap = ZombieSpearAirAssaultGoal.orbitRocketGapTicks(roll);
			int cooldown = ZombieSpearAirAssaultGoal.rocketCooldownTicks(roll);
			assertTrue(gap >= 48 && gap <= 68);
			assertTrue(cooldown >= 40 && cooldown <= 60);
			assertTrue(gap > 40, "Orbit rockets must leave more glide time than the old maximum cooldown.");
		}
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	void orbitDelayAlwaysStaysInsideTheShortOnePointTwoToTwoSecondWindow() {
		assertEquals(24, ZombieSpearAirAssaultGoal.orbitDurationTicks(0.0));
		assertEquals(40, ZombieSpearAirAssaultGoal.orbitDurationTicks(1.0));
		assertEquals(24, ZombieSpearAirAssaultGoal.orbitDurationTicks(Double.NaN));
		for (int sample = 0; sample <= 1000; sample++) {
			int duration = ZombieSpearAirAssaultGoal.orbitDurationTicks(sample / 1000.0);
			assertTrue(duration >= 24 && duration <= 40);
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
		assertEquals(10, ZombieSpearAirAssaultGoal.orbitFirstRocketDelayTicks(0.0));
		assertEquals(18, ZombieSpearAirAssaultGoal.orbitFirstRocketDelayTicks(1.0));
		assertEquals(32, ZombieSpearAirAssaultGoal.orbitRocketGapTicks(0.0));
		assertEquals(44, ZombieSpearAirAssaultGoal.orbitRocketGapTicks(1.0));
		assertEquals(28, ZombieSpearAirAssaultGoal.rocketCooldownTicks(0.0));
		assertEquals(40, ZombieSpearAirAssaultGoal.rocketCooldownTicks(1.0));
		for (int sample = 0; sample <= 1000; sample++) {
			double roll = sample / 1000.0;
			int gap = ZombieSpearAirAssaultGoal.orbitRocketGapTicks(roll);
			int cooldown = ZombieSpearAirAssaultGoal.rocketCooldownTicks(roll);
			assertTrue(gap >= 32 && gap <= 44);
			assertTrue(cooldown >= 28 && cooldown <= 40);
			assertTrue(gap >= 32, "Orbit rockets must retain at least 1.6 seconds of inertial glide.");
		}
	}

	@Test
	void orbitHardDeadlineAlwaysHandsControlToTheAttack() {
		assertFalse(ZombieSpearAirAssaultGoal.shouldBeginArming(120L, 100L, 172L, false, false));
		assertFalse(ZombieSpearAirAssaultGoal.shouldBeginArming(120L, 100L, 172L, true, false));
		assertTrue(ZombieSpearAirAssaultGoal.shouldBeginArming(120L, 100L, 172L, true, true));
		assertTrue(
			ZombieSpearAirAssaultGoal.shouldBeginArming(172L, 100L, 172L, false, false),
			"The hard deadline must defeat both an unfinished rocket plan and stale line-of-sight."
		);
	}

	@Test
	void visualRotationUsesTheShortestArcWithoutSnapping() {
		float firstStep = ZombieSpearAirAssaultGoal.approachRotation(179.0F, -179.0F, 1.0F);
		assertEquals(180.0F, firstStep, 1.0E-6F);
		// 181° 与 -179° 是同一朝向；保留连续角度可避免跨越边界时让渲染插值走长弧。
		assertEquals(181.0F, ZombieSpearAirAssaultGoal.approachRotation(firstStep, -179.0F, 1.0F), 1.0E-6F);
		assertEquals(15.0F, ZombieSpearAirAssaultGoal.approachRotation(0.0F, 90.0F, 15.0F), 1.0E-6F);
	}

	@Test
	void velocitySteeringPreservesSpeedAndObeysItsAngularLimit() {
		Vec3 current = new Vec3(2.0, 0.0, 0.0);
		Vec3 desired = new Vec3(0.0, 0.0, 3.0);
		Vec3 turned = ZombieSpearAirAssaultGoal.turnDirectionToward(current, desired, 5.0);
		double turnAngle = Math.toDegrees(Math.acos(Math.clamp(current.normalize().dot(turned), -1.0, 1.0)));

		assertEquals(1.0, turned.length(), 1.0E-12);
		assertEquals(5.0, turnAngle, 1.0E-9);
		assertTrue(turned.dot(desired.normalize()) > current.normalize().dot(desired.normalize()));

		Vec3 opposite = ZombieSpearAirAssaultGoal.turnDirectionToward(
			new Vec3(1.0, 0.0, 0.0),
			new Vec3(-1.0, 0.0, 0.0),
			5.0
		);
		assertTrue(Double.isFinite(opposite.x) && Double.isFinite(opposite.y) && Double.isFinite(opposite.z));
		assertEquals(1.0, opposite.length(), 1.0E-12);
	}
}

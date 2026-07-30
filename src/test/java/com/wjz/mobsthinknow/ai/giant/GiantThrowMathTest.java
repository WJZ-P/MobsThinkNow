package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GiantThrowMathTest {
	@Test
	void launchPointsTowardTargetAndKeepsReadableSpeedCap() {
		Vec3 velocity = GiantThrowMath.launchVelocity(Vec3.ZERO, new Vec3(18.0, 0.0, 6.0), Vec3.ZERO);
		double horizontal = Math.hypot(velocity.x, velocity.z);

		assertTrue(velocity.x > 0.0);
		assertTrue(velocity.z > 0.0);
		assertTrue(velocity.y >= 0.28 && velocity.y <= 0.96);
		assertTrue(horizontal <= GiantThrowMath.maximumHorizontalSpeed() + 1.0E-9);
	}

	@Test
	void movingTargetIsLedWithoutReversingThrowDirection() {
		Vec3 stationary = GiantThrowMath.launchVelocity(Vec3.ZERO, new Vec3(20.0, 0.0, 0.0), Vec3.ZERO);
		Vec3 moving = GiantThrowMath.launchVelocity(Vec3.ZERO, new Vec3(20.0, 0.0, 0.0), new Vec3(0.0, 0.0, 0.25));

		assertTrue(moving.x > 0.0);
		assertTrue(moving.z > stationary.z);
	}
}

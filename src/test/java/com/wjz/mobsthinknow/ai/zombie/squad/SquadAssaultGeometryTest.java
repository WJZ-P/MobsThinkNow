package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadAssaultGeometryTest {
	private static final double EPSILON = 1.0E-6;

	@Test
	void crossfireAlternatesShootersAcrossTheTargetsFacingAxis() {
		Vec3 target = new Vec3(10.0, 4.0, -2.0);
		Vec3 left = SquadAssaultGeometry.crossfirePosition(target, new Vec3(0.0, 0.0, 1.0), Vec3.ZERO, 12.0, 0);
		Vec3 right = SquadAssaultGeometry.crossfirePosition(target, new Vec3(0.0, 0.0, 1.0), Vec3.ZERO, 12.0, 1);

		assertTrue(left.x < target.x, "The first shooter should occupy one lateral side.");
		assertTrue(right.x > target.x, "The second shooter should occupy the opposite lateral side.");
		assertEquals(12.0, horizontalDistance(target, left), EPSILON);
		assertEquals(12.0, horizontalDistance(target, right), EPSILON);
		assertEquals(target.y, left.y, EPSILON);
		assertEquals(target.y, right.y, EPSILON);
	}

	@Test
	void zeroLengthFacingFallsBackWithoutProducingNan() {
		Vec3 position = SquadAssaultGeometry.crossfirePosition(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, 9.0, 0);

		assertTrue(Double.isFinite(position.x));
		assertTrue(Double.isFinite(position.y));
		assertTrue(Double.isFinite(position.z));
		assertEquals(9.0, horizontalDistance(Vec3.ZERO, position), EPSILON);
	}

	@Test
	void mountedBreachStagesBehindAndAlternatesSides() {
		Vec3 target = new Vec3(3.0, 2.0, 7.0);
		Vec3 first = SquadAssaultGeometry.mountedBreachStaging(
			target,
			new Vec3(0.0, 0.0, 1.0),
			Vec3.ZERO,
			0
		);
		Vec3 second = SquadAssaultGeometry.mountedBreachStaging(
			target,
			new Vec3(0.0, 0.0, 1.0),
			Vec3.ZERO,
			1
		);

		assertTrue(first.z < target.z && second.z < target.z, "Both bomb runs should stage behind the target.");
		assertTrue(first.x < target.x && second.x > target.x, "Bombers should split across opposite sides.");
		assertEquals(target.y, first.y, EPSILON);
		assertEquals(target.y, second.y, EPSILON);
	}

	private static double horizontalDistance(final Vec3 first, final Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}

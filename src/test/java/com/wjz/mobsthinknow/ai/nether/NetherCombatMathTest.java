package com.wjz.mobsthinknow.ai.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class NetherCombatMathTest {
	private static final double EPSILON = 1.0E-9;

	@Test
	void predictionStrengthAndLeadTimeAreBothBounded() {
		Vec3 target = new Vec3(1.0, 2.0, 3.0);
		Vec3 velocity = new Vec3(1.0, 0.0, -0.5);

		assertVectorEquals(
			new Vec3(9.0, 2.0, -1.0),
			NetherCombatMath.predictedPoint(target, velocity, 100.0, 1.0, 1.0, 8.0)
		);
		assertVectorEquals(
			target,
			NetherCombatMath.predictedPoint(target, velocity, 100.0, 1.0, 0.0, 8.0)
		);
		assertVectorEquals(
			new Vec3(13.0, 2.0, -3.0),
			NetherCombatMath.predictedPoint(target, velocity, 100.0, 1.0, 9.0, 8.0)
		);
	}

	@Test
	void horizontalRotationPreservesLengthAndDropsVerticalComponent() {
		Vec3 rotated = NetherCombatMath.rotateHorizontal(new Vec3(3.0, 12.0, 4.0), Math.PI * 0.5);

		assertVectorEquals(new Vec3(-4.0, 0.0, 3.0), rotated);
		assertEquals(5.0, rotated.length(), EPSILON);
	}

	@Test
	void zeroLengthFallbackIsStableAndNormalizedPerEntity() {
		Vec3 first = NetherCombatMath.horizontalUnitOrEntityFallback(Vec3.ZERO, 17);
		Vec3 repeated = NetherCombatMath.horizontalUnitOrEntityFallback(Vec3.ZERO, 17);
		Vec3 other = NetherCombatMath.horizontalUnitOrEntityFallback(Vec3.ZERO, 18);

		assertVectorEquals(first, repeated);
		assertEquals(1.0, first.length(), EPSILON);
		assertEquals(0.0, first.y, EPSILON);
		assertTrue(first.distanceToSqr(other) > 1.0E-6, "Different entity lanes collapsed to one direction.");
	}

	@Test
	void predictivePounceDirectionFacesTheBoundedFuturePoint() {
		Vec3 direction = NetherCombatMath.predictiveHorizontalDirection(
			Vec3.ZERO,
			new Vec3(4.0, 0.0, 0.0),
			new Vec3(0.0, 0.0, 1.0),
			2.0,
			1
		);

		assertEquals(1.0, direction.length(), EPSILON);
		assertEquals(0.0, direction.y, EPSILON);
		assertTrue(direction.x > 0.0 && direction.z > 0.0, "Direction did not lead the moving target.");
		assertVectorEquals(new Vec3(4.0, 0.0, 2.0).normalize(), direction);
	}

	private static void assertVectorEquals(final Vec3 expected, final Vec3 actual) {
		assertEquals(expected.x, actual.x, EPSILON);
		assertEquals(expected.y, actual.y, EPSILON);
		assertEquals(expected.z, actual.z, EPSILON);
	}
}

package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiringLanePlannerTest {
	@Test
	void detectsTheNearestAllyInsideTheShotCapsule() {
		FiringLanePlanner.Result<String> result = FiringLanePlanner.check(
			new Vec3d(0.0, 1.5, 0.0),
			new Vec3d(0.0, 1.5, 12.0),
			List.of(
				new FiringLanePlanner.Ally<>("far", new Vec3d(0.1, 1.5, 8.0), 0.7),
				new FiringLanePlanner.Ally<>("near", new Vec3d(0.2, 1.5, 3.0), 0.7)
			),
			20
		);
		assertFalse(result.clear());
		assertEquals("near", result.blocker());
		assertEquals(2, result.checks());
	}

	@Test
	void ignoresAlliesOutsideTheCapsuleAndAtEndpoints() {
		FiringLanePlanner.Result<String> result = FiringLanePlanner.check(
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 10.0),
			List.of(
				new FiringLanePlanner.Ally<>("side", new Vec3d(2.0, 0.0, 5.0), 0.7),
				new FiringLanePlanner.Ally<>("shooter", new Vec3d(0.0, 0.0, 0.0), 1.0),
				new FiringLanePlanner.Ally<>("target-edge", new Vec3d(0.0, 0.0, 10.0), 1.0)
			),
			20
		);
		assertTrue(result.clear());
		assertNull(result.blocker());
	}

	@Test
	void hardCheckLimitBoundsWork() {
		FiringLanePlanner.Result<String> result = FiringLanePlanner.check(
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 10.0),
			List.of(
				new FiringLanePlanner.Ally<>("clear", new Vec3d(3.0, 0.0, 2.0), 0.5),
				new FiringLanePlanner.Ally<>("would-block", new Vec3d(0.0, 0.0, 5.0), 0.5)
			),
			1
		);
		assertTrue(result.clear());
		assertEquals(1, result.checks());
	}

	@Test
	void boundedClearResultsReuseTheSameImmutableInstance() {
		Vec3d target = new Vec3d(0.0, 0.0, 10.0);
		List<FiringLanePlanner.Ally<String>> allies = List.of(
			new FiringLanePlanner.Ally<>("first", 3.0, 0.0, 2.0, 0.5),
			new FiringLanePlanner.Ally<>("second", -3.0, 0.0, 7.0, 0.5)
		);
		FiringLanePlanner.Result<String> first = FiringLanePlanner.check(Vec3d.ZERO, target, allies, 20);
		FiringLanePlanner.Result<String> second = FiringLanePlanner.check(Vec3d.ZERO, target, allies, 20);

		assertSame(first, second);
		assertTrue(first.clear());
		assertNull(first.blocker());
		assertEquals(2, first.checks());
	}

	@Test
	void lateralCandidateUsesStableOppositeSides() {
		Vec3d left = FiringLanePlanner.lateralReposition(Vec3d.ZERO, new Vec3d(0.0, 0.0, 10.0), -1, 3.0);
		Vec3d right = FiringLanePlanner.lateralReposition(Vec3d.ZERO, new Vec3d(0.0, 0.0, 10.0), 1, 3.0);
		assertEquals(-left.x(), right.x(), 1.0E-9);
		assertEquals(left.z(), right.z(), 1.0E-9);
		assertTrue(left.z() < 0.0);
	}

	@Test
	void allocationReducedLateralMathMatchesTheOriginalVectorFormula() {
		Vec3d[] shooters = {
			Vec3d.ZERO,
			new Vec3d(4.25, -2.0, 8.5),
			new Vec3d(-17.0, 64.0, 3.0)
		};
		Vec3d[] targets = {
			new Vec3d(0.0, 7.0, 12.0),
			new Vec3d(-8.0, 3.0, 5.0),
			new Vec3d(-17.0, 100.0, 3.0)
		};
		double[] distances = {-5.0, 1.0, 2.75, 6.0, 99.0, Double.NaN};
		for (int index = 0; index < shooters.length; index++) {
			for (int side : new int[] {-1, 0, 1}) {
				for (double distance : distances) {
					Vec3d actual = FiringLanePlanner.lateralReposition(
						shooters[index],
						targets[index],
						side,
						distance
					);
					Vec3d expected = legacyLateralReposition(
						shooters[index],
						targets[index],
						side,
						distance
					);
					assertVectorEquals(expected, actual);
				}
		}	}
	}

	private static Vec3d legacyLateralReposition(
		final Vec3d shooter,
		final Vec3d target,
		final int stableSide,
		final double configuredDistance
	) {
		Vec3d forward = target.subtract(shooter).horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		double side = stableSide < 0 ? -1.0 : 1.0;
		double distance = Double.isFinite(configuredDistance)
			? Math.clamp(configuredDistance, 1.0, 6.0)
			: 3.0;
		return shooter
			.add(right.scale(side * distance))
			.subtract(forward.scale(Math.min(1.5, distance * 0.30)));
	}

	@Test
	void scalarAllySnapshotMatchesTheVectorCompatibilityConstructor() {
		Vec3d origin = new Vec3d(0.0, 1.5, 0.0);
		Vec3d target = new Vec3d(0.0, 1.5, 10.0);
		var vector = FiringLanePlanner.check(
			origin,
			target,
			List.of(new FiringLanePlanner.Ally<>("ally", new Vec3d(0.2, 1.5, 4.0), 0.7)),
			4
		);
		var scalar = FiringLanePlanner.check(
			origin,
			target,
			List.of(new FiringLanePlanner.Ally<>("ally", 0.2, 1.5, 4.0, 0.7)),
			4
		);
		assertEquals(vector, scalar);
	}

	@Test
	void genericCollectionFallbackPreservesListResults() {
		Vec3d origin = Vec3d.ZERO;
		Vec3d target = new Vec3d(0.0, 0.0, 10.0);
		List<FiringLanePlanner.Ally<String>> allies = List.of(
			new FiringLanePlanner.Ally<>("clear", 3.0, 0.0, 2.0, 0.5),
			new FiringLanePlanner.Ally<>("block", 0.0, 0.0, 5.0, 0.5)
		);
		assertEquals(
			FiringLanePlanner.check(origin, target, allies, 4),
			FiringLanePlanner.check(origin, target, new LinkedHashSet<>(allies), 4)
		);
	}

	private static void assertVectorEquals(final Vec3d expected, final Vec3d actual) {
		assertEquals(expected.x(), actual.x(), 1.0E-12);
		assertEquals(expected.y(), actual.y(), 1.0E-12);
		assertEquals(expected.z(), actual.z(), 1.0E-12);
	}
}

package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrossbowCombatPlannerTest {
	@Test
	void intelligenceShortensChargeAndAimWithoutRemovingReadableWindup() {
		assertTrue(CrossbowCombatPlanner.chargeTicks(25, 10) < CrossbowCombatPlanner.chargeTicks(25, 1));
		assertEquals(10, CrossbowCombatPlanner.chargeTicks(-99, 10));
		int low = CrossbowCombatPlanner.aimDelayTicks(6, 12, 1, 42, 7L);
		int high = CrossbowCombatPlanner.aimDelayTicks(6, 12, 10, 42, 7L);
		assertTrue(low >= 6 && low <= 12);
		assertTrue(high >= 3 && high <= 9);
		assertEquals(high, CrossbowCombatPlanner.aimDelayTicks(6, 12, 10, 42, 7L));
	}

	@Test
	void interceptLeadsMovingTargetAndCompensatesGravity() {
		CrossbowCombatPlanner.AimSolution solution = CrossbowCombatPlanner.intercept(
			new Vec3d(0.0, 1.5, 0.0),
			new Vec3d(0.0, 1.5, 20.0),
			new Vec3d(0.20, 0.0, 0.0),
			3.15,
			0.05,
			20.0
		);
		assertTrue(solution.aimPoint().x() > 1.0);
		assertTrue(solution.aimPoint().y() > 1.5);
		assertTrue(solution.flightTicks() > 5.0 && solution.flightTicks() < 8.0);
		double unitLengthSquared = solution.direction().distanceSquared(Vec3d.ZERO);
		assertEquals(1.0, unitLengthSquared, 1.0E-9);
	}

	@Test
	void interceptClampsInvalidTuningAndZeroLengthShot() {
		CrossbowCombatPlanner.AimSolution solution = CrossbowCombatPlanner.intercept(
			Vec3d.ZERO,
			Vec3d.ZERO,
			Vec3d.ZERO,
			Double.NaN,
			Double.POSITIVE_INFINITY,
			Double.NaN
		);
		assertEquals(new Vec3d(0.0, 0.0, 1.0), solution.direction());
		assertEquals(0.0, solution.flightTicks());
	}

	@Test
	void pointOnlyInterceptMatchesTheFullSolution() {
		Vec3d shooter = new Vec3d(-3.0, 1.5, 4.0);
		Vec3d target = new Vec3d(8.0, 2.25, 24.0);
		Vec3d velocity = new Vec3d(0.24, -0.03, -0.12);
		CrossbowCombatPlanner.AimSolution full = CrossbowCombatPlanner.intercept(
			shooter,
			target,
			velocity,
			2.6,
			0.05,
			20.0
		);
		Vec3d pointOnly = CrossbowCombatPlanner.interceptPoint(
			shooter,
			target,
			velocity,
			2.6,
			0.05,
			20.0
		);

		assertEquals(full.aimPoint(), pointOnly);
	}

	@Test
	void blastSafetyRejectsRangeAndNearestCheckedAlly() {
		Vec3d shooter = Vec3d.ZERO;
		assertEquals(
			CrossbowCombatPlanner.BlastStatus.TOO_CLOSE,
			CrossbowCombatPlanner.assessBlast(shooter, new Vec3d(0.0, 0.0, 3.0), List.of(), 6.0, 30.0, 3.0, 20).status()
		);
		CrossbowCombatPlanner.BlastSafety<String> blocked = CrossbowCombatPlanner.assessBlast(
			shooter,
			new Vec3d(0.0, 0.0, 16.0),
			List.of(
				new CrossbowCombatPlanner.BlastAlly<>("safe", new Vec3d(8.0, 0.0, 16.0), 0.5),
				new CrossbowCombatPlanner.BlastAlly<>("friend", new Vec3d(1.0, 0.0, 16.0), 0.7)
			),
			6.0,
			30.0,
			3.0,
			20
		);
		assertFalse(blocked.clear());
		assertEquals("friend", blocked.blocker());
		assertEquals(2, blocked.checks());
	}

	@Test
	void blastChecksAreHardBounded() {
		CrossbowCombatPlanner.BlastSafety<String> result = CrossbowCombatPlanner.assessBlast(
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 12.0),
			List.of(
				new CrossbowCombatPlanner.BlastAlly<>("clear", new Vec3d(10.0, 0.0, 12.0), 0.5),
				new CrossbowCombatPlanner.BlastAlly<>("unchecked", new Vec3d(0.0, 0.0, 12.0), 0.5)
			),
			6.0,
			30.0,
			3.0,
			1
		);
		assertTrue(result.clear());
		assertEquals(1, result.checks());
	}

	@Test
	void movingTargetSafetyUsesThePredictedImpactRatherThanItsOldPosition() {
		Vec3d shooter = Vec3d.ZERO;
		Vec3d currentTarget = new Vec3d(0.0, 0.0, 20.0);
		CrossbowCombatPlanner.AimSolution aim = CrossbowCombatPlanner.intercept(
			shooter,
			currentTarget,
			new Vec3d(0.35, 0.0, 0.0),
			1.6,
			0.0,
			20.0
		);
		var ally = new CrossbowCombatPlanner.BlastAlly<>("future-ally", aim.aimPoint(), 0.5);

		assertTrue(CrossbowCombatPlanner.assessBlast(
			shooter, currentTarget, List.of(ally), 6.0, 30.0, 3.0, 20
		).clear());
		assertEquals(
			CrossbowCombatPlanner.BlastStatus.ALLY_IN_BLAST,
			CrossbowCombatPlanner.assessBlast(
				shooter, aim.aimPoint(), List.of(ally), 6.0, 30.0, 3.0, 20
			).status()
		);
	}

	@Test
	void scalarBlastAllyMatchesTheVectorCompatibilityConstructor() {
		Vec3d shooter = Vec3d.ZERO;
		Vec3d target = new Vec3d(0.0, 0.0, 16.0);
		var vector = CrossbowCombatPlanner.assessBlast(
			shooter,
			target,
			List.of(new CrossbowCombatPlanner.BlastAlly<>("ally", new Vec3d(0.5, 0.0, 16.0), 0.7)),
			6.0,
			30.0,
			3.0,
			4
		);
		var scalar = CrossbowCombatPlanner.assessBlast(
			shooter,
			target,
			List.of(new CrossbowCombatPlanner.BlastAlly<>("ally", 0.5, 0.0, 16.0, 0.7)),
			6.0,
			30.0,
			3.0,
			4
		);
		assertEquals(vector, scalar);
	}
}

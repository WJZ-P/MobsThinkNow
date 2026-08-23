package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpiderWebTrapPlannerTest {
	@Test
	void onlyGroundedVisibleUnladenHighIntelligenceSpidersPlanAtUsefulRange() {
		assertTrue(SpiderWebTrapPlanner.canPlan(7, true, true, false, 25.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(6, true, true, false, 25.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(10, false, true, false, 25.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(10, true, false, false, 25.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(10, true, true, true, 25.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(10, true, true, false, 4.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(10, true, true, false, 100.0));
		assertFalse(SpiderWebTrapPlanner.canPlan(10, true, true, false, Double.NaN));
	}

	@Test
	void predictionLeadsMovingTargetsAndCapsExtremeVelocity() {
		Vec3d origin = new Vec3d(10.0, 4.0, 10.0);
		Vec3d predicted = SpiderWebTrapPlanner.predictedPosition(
			origin,
			new Vec3d(0.2, 0.8, -0.1),
			new Vec3d(0.0, 0.0, 1.0),
			10
		);

		assertTrue(predicted.x() > origin.x());
		assertTrue(predicted.z() < origin.z());
		assertEquals(origin.y(), predicted.y());
		assertTrue(horizontalDistance(predicted, origin) <= 3.25 + 1.0E-6);

		Vec3d capped = SpiderWebTrapPlanner.predictedPosition(
			origin,
			new Vec3d(20.0, 0.0, 0.0),
			Vec3d.ZERO,
			10
		);
		assertEquals(3.25, horizontalDistance(capped, origin), 1.0E-6);
	}

	@Test
	void stationaryTargetsUseAConservativeLookIntent() {
		Vec3d origin = new Vec3d(2.0, 3.0, 4.0);
		Vec3d predicted = SpiderWebTrapPlanner.predictedPosition(
			origin,
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, -1.0),
			7
		);

		assertEquals(origin.x(), predicted.x(), 1.0E-6);
		assertEquals(origin.z() - 0.62, predicted.z(), 1.0E-6);
	}

	@Test
	void candidateListIsBoundedImmutableAndCoversBothDodgeLanes() {
		Vec3d target = new Vec3d(0.0, 2.0, 0.0);
		Vec3d predicted = new Vec3d(2.0, 2.0, 0.0);
		List<Vec3d> candidates = SpiderWebTrapPlanner.candidateCenters(target, predicted, Vec3d.ZERO, 1);

		assertEquals(SpiderWebTrapPlanner.MAXIMUM_CANDIDATE_CENTERS, candidates.size());
		assertEquals(predicted, candidates.getFirst());
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.z() > 0.8));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.z() < -0.8));
		assertThrows(UnsupportedOperationException.class, () -> candidates.add(Vec3d.ZERO));
	}

	@Test
	void cooldownRemainsBoundedAndCannotOverflow() {
		assertEquals(240, SpiderWebTrapPlanner.cooldownTicks(240, 7, 0, 0));
		assertEquals(204, SpiderWebTrapPlanner.cooldownTicks(240, 10, 3, 0));
		assertEquals(244, SpiderWebTrapPlanner.cooldownTicks(240, 10, 3, 40));
		assertEquals(60, SpiderWebTrapPlanner.cooldownTicks(10, 10, 3, 0));
		assertEquals(Integer.MAX_VALUE, SpiderWebTrapPlanner.cooldownTicks(
			Integer.MAX_VALUE, 7, 0, Integer.MAX_VALUE
		));
	}

	@Test
	void blastContainmentCoversTheEscapeLaneAwayFromTheCreeper() {
		Vec3d blast = new Vec3d(0.0, 2.0, 0.0);
		Vec3d target = new Vec3d(3.0, 2.0, 0.0);
		List<Vec3d> candidates = SpiderWebTrapPlanner.blastEscapeCandidateCenters(
			target,
			new Vec3d(0.15, 0.0, 0.04),
			blast,
			1
		);

		assertEquals(SpiderWebTrapPlanner.MAXIMUM_CANDIDATE_CENTERS, candidates.size());
		assertTrue(candidates.getFirst().x() > target.x() + 1.9);
		assertTrue(candidates.stream().allMatch(candidate ->
			candidate.distanceSquared(blast) > target.distanceSquared(blast)
		));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.z() > 0.7));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.z() < -0.7));
	}

	@Test
	void eachNewPrimedCreeperCanRequestOneEmergencyWebDuringCooldown() {
		assertTrue(SpiderWebTrapPlanner.mayBypassCooldownForBlast(false, 42, 0));
		assertFalse(SpiderWebTrapPlanner.mayBypassCooldownForBlast(false, 42, 42));
		assertTrue(SpiderWebTrapPlanner.mayBypassCooldownForBlast(false, 43, 42));
		assertTrue(SpiderWebTrapPlanner.mayBypassCooldownForBlast(true, 0, 0));
		assertTrue(SpiderWebTrapPlanner.mayBypassCooldownForBlast(false, true));
		assertFalse(SpiderWebTrapPlanner.mayBypassCooldownForBlast(false, false));
	}

	private static double horizontalDistance(final Vec3d first, final Vec3d second) {
		double x = first.x() - second.x();
		double z = first.z() - second.z();
		return Math.sqrt(x * x + z * z);
	}
}

package com.wjz.mobsthinknow.ai.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
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
	}

	@Test
	void predictionLeadsMovingTargetsAndCapsExtremeVelocity() {
		Vec3 origin = new Vec3(10.0, 4.0, 10.0);
		Vec3 predicted = SpiderWebTrapPlanner.predictedPosition(
			origin,
			new Vec3(0.2, 0.8, -0.1),
			new Vec3(0.0, 0.0, 1.0),
			10
		);

		assertTrue(predicted.x > origin.x);
		assertTrue(predicted.z < origin.z);
		assertEquals(origin.y, predicted.y);
		assertTrue(predicted.subtract(origin).horizontalDistance() <= 3.25 + 1.0E-6);

		Vec3 capped = SpiderWebTrapPlanner.predictedPosition(
			origin,
			new Vec3(20.0, 0.0, 0.0),
			Vec3.ZERO,
			10
		);
		assertEquals(3.25, capped.subtract(origin).horizontalDistance(), 1.0E-6);
	}

	@Test
	void stationaryTargetsUseAConservativeLookIntent() {
		Vec3 origin = new Vec3(2.0, 3.0, 4.0);
		Vec3 predicted = SpiderWebTrapPlanner.predictedPosition(
			origin,
			Vec3.ZERO,
			new Vec3(0.0, 0.0, -1.0),
			7
		);

		assertEquals(origin.x, predicted.x, 1.0E-6);
		assertEquals(origin.z - 0.62, predicted.z, 1.0E-6);
	}

	@Test
	void candidateListIsBoundedAndCoversBothDodgeLanes() {
		Vec3 target = new Vec3(0.0, 2.0, 0.0);
		Vec3 predicted = new Vec3(2.0, 2.0, 0.0);
		List<Vec3> candidates = SpiderWebTrapPlanner.candidateCenters(target, predicted, Vec3.ZERO, 1);

		assertEquals(5, candidates.size());
		assertEquals(predicted, candidates.getFirst());
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.z > 0.8));
		assertTrue(candidates.stream().anyMatch(candidate -> candidate.z < -0.8));
	}

	@Test
	void cooldownRemainsBoundedAndSkillOnlyMakesAModestDifference() {
		assertEquals(240, SpiderWebTrapPlanner.cooldownTicks(240, 7, 0, 0));
		assertEquals(204, SpiderWebTrapPlanner.cooldownTicks(240, 10, 3, 0));
		assertEquals(244, SpiderWebTrapPlanner.cooldownTicks(240, 10, 3, 40));
		assertEquals(60, SpiderWebTrapPlanner.cooldownTicks(10, 10, 3, 0));
	}
}

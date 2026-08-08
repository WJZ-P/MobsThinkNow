package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetreatPlannerTest {
	@Test
	void lowHealthAndHeavyHitsRemainIndependentTriggers() {
		assertEquals(RetreatPlanner.Trigger.LOW_HEALTH, RetreatPlanner.trigger(4.0, 20.0, 1.0, 0.20, 0.30));
		assertEquals(RetreatPlanner.Trigger.HEAVY_HIT, RetreatPlanner.trigger(18.0, 20.0, 6.0, 0.20, 0.30));
		assertEquals(
			RetreatPlanner.Trigger.LOW_HEALTH_AND_HEAVY_HIT,
			RetreatPlanner.trigger(3.0, 20.0, 7.0, 0.20, 0.30)
		);
		assertEquals(RetreatPlanner.Trigger.NONE, RetreatPlanner.trigger(18.0, 20.0, 2.0, 0.20, 0.30));
	}

	@Test
	void retreatEndsAtEitherHardTimeOrSafeDistance() {
		assertTrue(RetreatPlanner.shouldContinue(99L, 100, 24.9, 5.0));
		assertFalse(RetreatPlanner.shouldContinue(100L, 100, 1.0, 5.0));
		assertFalse(RetreatPlanner.shouldContinue(20L, 100, 25.0, 5.0));
	}

	@Test
	void candidatesAreBoundedAndStayBehindTheActorFromThreat() {
		Vec3d actor = new Vec3d(4.0, 70.0, 0.0);
		Vec3d threat = new Vec3d(0.0, 70.0, 0.0);
		List<Vec3d> candidates = RetreatPlanner.candidateDestinations(actor, threat, 5.0, 9.0, 0.5, 1);

		assertEquals(5, candidates.size());
		assertTrue(candidates.stream().allMatch(candidate -> candidate.x() > actor.x()));
		assertEquals(actor.y(), candidates.getFirst().y());
		assertTrue(candidates.get(1).z() > candidates.getFirst().z());
		assertTrue(candidates.get(2).z() < candidates.getFirst().z());
	}

	@Test
	void coincidentPositionsUseStableSideFallback() {
		Vec3d position = new Vec3d(3.0, 64.0, 3.0);
		List<Vec3d> left = RetreatPlanner.candidateDestinations(position, position, 5.0, 9.0, 0.5, -1);
		List<Vec3d> right = RetreatPlanner.candidateDestinations(position, position, 5.0, 9.0, 0.5, 1);

		assertTrue(left.getFirst().x() < position.x());
		assertTrue(right.getFirst().x() > position.x());
	}
}

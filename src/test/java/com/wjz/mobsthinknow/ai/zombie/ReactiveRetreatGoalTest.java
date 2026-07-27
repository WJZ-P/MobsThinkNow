package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ReactiveRetreatGoalTest {
	@Test
	void retreatHealthIncludesTheTwentyPercentBoundary() {
		assertTrue(ReactiveRetreatGoal.isRetreatHealth(4.0F, 20.0F, 0.20));
		assertTrue(ReactiveRetreatGoal.isRetreatHealth(3.5F, 20.0F, 0.20));
		assertFalse(ReactiveRetreatGoal.isRetreatHealth(4.01F, 20.0F, 0.20));
	}

	@Test
	void deadOrInvalidEntitiesDoNotStartRetreating() {
		assertFalse(ReactiveRetreatGoal.isRetreatHealth(0.0F, 20.0F, 0.20));
		assertFalse(ReactiveRetreatGoal.isRetreatHealth(1.0F, 0.0F, 0.20));
	}

	@Test
	void heavyHitIncludesThirtyPercentBoundary() {
		assertTrue(ReactiveRetreatGoal.isHeavyHit(6.0F, 20.0F, 0.30));
		assertTrue(ReactiveRetreatGoal.isHeavyHit(7.0F, 20.0F, 0.30));
		assertFalse(ReactiveRetreatGoal.isHeavyHit(5.99F, 20.0F, 0.30));
		assertFalse(ReactiveRetreatGoal.isHeavyHit(0.0F, 20.0F, 0.30));
		assertFalse(ReactiveRetreatGoal.isHeavyHit(6.0F, 0.0F, 0.30));
	}

	@Test
	void retreatEndsAtEitherTheDistanceOrTimeBoundary() {
		Vec3 attacker = Vec3.ZERO;

		assertTrue(ReactiveRetreatGoal.shouldContinueRetreat(99L, 100L, new Vec3(4.99, 80.0, 0.0), attacker, 5.0));
		assertFalse(ReactiveRetreatGoal.shouldContinueRetreat(100L, 100L, new Vec3(4.99, 0.0, 0.0), attacker, 5.0));
		assertFalse(ReactiveRetreatGoal.shouldContinueRetreat(99L, 100L, new Vec3(5.0, 0.0, 0.0), attacker, 5.0));
	}

	@Test
	void safeDistanceUsesHorizontalSeparationOnly() {
		Vec3 attacker = Vec3.ZERO;

		assertFalse(ReactiveRetreatGoal.hasReachedSafeDistance(new Vec3(0.0, 20.0, 0.0), attacker, 5.0));
		assertFalse(ReactiveRetreatGoal.hasReachedSafeDistance(new Vec3(4.99, 20.0, 0.0), attacker, 5.0));
		assertTrue(ReactiveRetreatGoal.hasReachedSafeDistance(new Vec3(3.0, 20.0, 4.0), attacker, 5.0));
	}

}

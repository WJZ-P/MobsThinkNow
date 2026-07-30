package com.wjz.mobsthinknow.ai.spider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.spider.SpiderCombatMath.ApproachMode;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SpiderCombatMathTest {
	@Test
	void watchedTargetSelectsStableFlankOnlyAtTheSkillThreshold() {
		assertEquals(ApproachMode.INTERCEPT, SpiderCombatMath.chooseApproach(5, true, true, true, 0, -1));
		assertEquals(ApproachMode.FLANK_LEFT, SpiderCombatMath.chooseApproach(6, true, false, true, 0, -1));
		assertEquals(ApproachMode.FLANK_RIGHT, SpiderCombatMath.chooseApproach(10, false, true, true, 0, 1));
	}

	@Test
	void postHitRepositionOverridesOrdinaryInterception() {
		assertEquals(ApproachMode.REPOSITION_LEFT, SpiderCombatMath.chooseApproach(8, false, false, true, 12, -1));
		Vec3 destination = SpiderCombatMath.approachDestination(
			ApproachMode.REPOSITION_LEFT,
			new Vec3(10.0, 2.0, 10.0),
			Vec3.ZERO,
			new Vec3(0.0, 0.0, 1.0),
			8
		);
		assertTrue(destination.z < 7.0, "Reposition point did not leave melee distance behind the target.");
		assertTrue(destination.x > 12.0, "Left/right orbit offset disappeared from the reposition point.");
	}

	@Test
	void predictivePounceHasBoundedRangeAndLeadsMovingTarget() {
		assertFalse(SpiderCombatMath.canPredictivePounce(3, true, true, 16.0));
		assertFalse(SpiderCombatMath.canPredictivePounce(8, true, true, 64.0));
		assertTrue(SpiderCombatMath.canPredictivePounce(8, true, true, 25.0));

		Vec3 stationary = SpiderCombatMath.pounceVelocity(
			Vec3.ZERO, Vec3.ZERO, new Vec3(5.0, 0.0, 0.0), Vec3.ZERO, 8, 2
		);
		Vec3 moving = SpiderCombatMath.pounceVelocity(
			Vec3.ZERO, Vec3.ZERO, new Vec3(5.0, 0.0, 0.0), new Vec3(0.0, 0.0, 0.25), 8, 2
		);
		assertTrue(moving.z > stationary.z, "Moving target prediction did not add lateral lead.");
		assertTrue(moving.y >= 0.40 && moving.y <= 0.46, "Pounce vertical speed escaped its visual envelope.");
	}

	@Test
	void harderSmarterCarrierApproachesConfiguredSpeedCap() {
		double easy = SpiderCombatMath.carrierSpeed(1.55, 4, 1);
		double normal = SpiderCombatMath.carrierSpeed(1.55, 7, 2);
		double hard = SpiderCombatMath.carrierSpeed(1.55, 10, 3);
		assertTrue(easy < normal);
		assertTrue(normal < hard);
		assertEquals(1.55, hard, 1.0E-9);
	}

	@Test
	void targetWatchingIgnoresPitch() {
		assertTrue(SpiderCombatMath.isTargetWatching(
			new Vec3(1.0, -3.0, 0.0),
			new Vec3(4.0, 2.0, 0.0)
		));
		assertFalse(SpiderCombatMath.isTargetWatching(
			new Vec3(-1.0, 0.0, 0.0),
			new Vec3(4.0, 0.0, 0.0)
		));
	}
}

package com.wjz.mobsthinknow.ai.creeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.creeper.CreeperCombatMath.ApproachMode;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class CreeperCombatMathTest {
	@Test
	void watchedOrBlockingTargetSelectsStableFlankOnlyForSkilledCreepers() {
		assertEquals(
			ApproachMode.FLANK_LEFT,
			CreeperCombatMath.chooseApproach(8, true, false, true, 100.0, true, -1)
		);
		assertEquals(
			ApproachMode.FLANK_RIGHT,
			CreeperCombatMath.chooseApproach(8, false, true, true, 100.0, true, 1)
		);
		assertEquals(
			ApproachMode.INTERCEPT,
			CreeperCombatMath.chooseApproach(5, true, true, true, 100.0, true, -1)
		);
	}

	@Test
	void flankDestinationIsBehindAndToTheSideOfTargetFacing() {
		Vec3 destination = CreeperCombatMath.approachDestination(
			ApproachMode.FLANK_RIGHT,
			new Vec3(10.0, 4.0, 10.0),
			Vec3.ZERO,
			new Vec3(0.0, 0.0, 1.0),
			10
		);

		assertTrue(destination.z < 10.0, "Flank point was not behind the target.");
		assertTrue(Math.abs(destination.x - 10.0) >= 2.0, "Flank point lacked a meaningful side offset.");
		assertEquals(4.0, destination.y, 1.0E-9);
	}

	@Test
	void highDifficultyRaisesThreatWithoutExceedingConfiguredMovementCap() {
		double easy = CreeperCombatMath.movingFuseSpeed(1.25, 6, 1);
		double normal = CreeperCombatMath.movingFuseSpeed(1.25, 6, 2);
		double hard = CreeperCombatMath.movingFuseSpeed(1.25, 6, 3);

		assertTrue(easy < normal);
		assertTrue(normal < hard);
		assertEquals(1.25, CreeperCombatMath.movingFuseSpeed(1.25, 10, 3), 1.0E-9);
	}

	@Test
	void softWallCommitNeedsHighIntelligenceAndRunawayTargetForcesAbort() {
		assertFalse(CreeperCombatMath.shouldStartFuse(16.0, 4.0, false, true, false, false, 7));
		assertTrue(CreeperCombatMath.shouldStartFuse(16.0, 4.0, false, true, false, false, 8));
		assertFalse(CreeperCombatMath.shouldContinueFuse(100.0, 4.0, true, false, 0.8F, 10));
		assertTrue(CreeperCombatMath.shouldContinueFuse(16.0, 4.0, false, true, 0.2F, 10));
	}

	@Test
	void targetWatchingUsesHorizontalFacingInsteadOfPitch() {
		assertTrue(CreeperCombatMath.isTargetWatching(
			new Vec3(1.0, -4.0, 0.0),
			new Vec3(3.0, 2.0, 0.0)
		));
		assertFalse(CreeperCombatMath.isTargetWatching(
			new Vec3(-1.0, 0.0, 0.0),
			new Vec3(3.0, 0.0, 0.0)
		));
	}
}

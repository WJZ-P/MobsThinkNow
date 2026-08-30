package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.junit.jupiter.api.Test;

class CreeperTacticalPlannerTest {
	@Test
	void skilledWatchedCreeperUsesStableFlank() {
		assertEquals(
			CreeperTacticalPlanner.ApproachMode.FLANK_LEFT,
			CreeperTacticalPlanner.chooseApproach(8, true, false, true, 100.0, true, -1)
		);
		assertEquals(
			CreeperTacticalPlanner.ApproachMode.INTERCEPT,
			CreeperTacticalPlanner.chooseApproach(5, true, true, true, 100.0, true, 1)
		);
	}

	@Test
	void primitiveWatchingCoordinatesMatchTheVectorEntryPoint() {
		Vec3d look = new Vec3d(-0.4, 0.7, 0.8);
		Vec3d toward = new Vec3d(-3.0, 2.0, 6.0);
		assertEquals(
			CreeperTacticalPlanner.isTargetWatching(look, toward),
			CreeperTacticalPlanner.isTargetWatching(look.x(), look.z(), toward.x(), toward.z())
		);
	}

	@Test
	void flankDestinationIsBehindAndBesideTarget() {
		Vec3d destination = CreeperTacticalPlanner.approachDestination(
			CreeperTacticalPlanner.ApproachMode.FLANK_RIGHT,
			new Vec3d(10.0, 4.0, 10.0),
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 1.0),
			10
		);
		assertTrue(destination.z() < 10.0);
		assertTrue(Math.abs(destination.x() - 10.0) >= 2.0);
		assertEquals(4.0, destination.y());
	}

	@Test
	void primitiveDestinationInputsMatchVectorEntryPoints() {
		Vec3d target = new Vec3d(10.0, 4.0, -6.0);
		Vec3d velocity = new Vec3d(1.4, -0.2, -0.7);
		Vec3d look = new Vec3d(-0.3, 0.8, 0.9);
		for (CreeperTacticalPlanner.ApproachMode mode : CreeperTacticalPlanner.ApproachMode.values()) {
			assertEquals(
				CreeperTacticalPlanner.approachDestination(mode, target, velocity, look, 8),
				CreeperTacticalPlanner.approachDestination(
					mode,
					target.x(),
					target.y(),
					target.z(),
					velocity.x(),
					velocity.z(),
					look.x(),
					look.z(),
					8
				)
			);
		}
		assertEquals(
			CreeperTacticalPlanner.fuseDestination(target, velocity, 0.35, 8),
			CreeperTacticalPlanner.fuseDestination(
				target.x(),
				target.y(),
				target.z(),
				velocity.x(),
				velocity.z(),
				0.35,
				8
			)
		);
	}

	@Test
	void difficultyRaisesThreatWithoutBreakingConfiguredCap() {
		double easy = CreeperTacticalPlanner.movingFuseSpeed(1.25, 6, DifficultyTier.EASY);
		double normal = CreeperTacticalPlanner.movingFuseSpeed(1.25, 6, DifficultyTier.NORMAL);
		double hard = CreeperTacticalPlanner.movingFuseSpeed(1.25, 6, DifficultyTier.HARD);
		assertTrue(easy < normal);
		assertTrue(normal < hard);
		assertEquals(1.25, CreeperTacticalPlanner.movingFuseSpeed(1.25, 10, DifficultyTier.HARD));
	}

	@Test
	void runawayTargetAbortsButLateCloseFuseCommits() {
		assertFalse(CreeperTacticalPlanner.shouldContinueFuse(100.0, 4.0, true, false, 0.8, 10));
		assertTrue(CreeperTacticalPlanner.shouldContinueFuse(16.0, 4.0, false, true, 0.7, 10));
	}

	@Test
	void fusePredictionUsesVelocityAndHardHorizontalCap() {
		Vec3d destination = CreeperTacticalPlanner.fuseDestination(
			new Vec3d(10.0, 2.0, 10.0),
			new Vec3d(2.0, 1.0, 0.0),
			0.0,
			10
		);
		assertEquals(13.0, destination.x());
		assertEquals(2.0, destination.y());
		assertEquals(10.0, destination.z());
	}
}

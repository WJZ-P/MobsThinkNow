package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SkeletonCoverPlannerTest {
	@Test
	void rangeBandRejectsPointBlankAndVeryDistantPositions() {
		assertFalse(SkeletonCoverPlanner.isUsefulRange(6.9 * 6.9, 10.0));
		assertTrue(SkeletonCoverPlanner.isUsefulRange(7.0 * 7.0, 10.0));
		assertTrue(SkeletonCoverPlanner.isUsefulRange(15.5 * 15.5, 10.0));
		assertFalse(SkeletonCoverPlanner.isUsefulRange(15.6 * 15.6, 10.0));
		assertFalse(SkeletonCoverPlanner.isUsefulRange(Double.NaN, 10.0));
	}

	@Test
	void scorePrefersShortTravelAndPreferredFiringRange() {
		Vec3 skeleton = new Vec3(0.5, 2.0, 0.5);
		Vec3 target = new Vec3(10.5, 2.0, 0.5);
		double nearbyPreferred = SkeletonCoverPlanner.planScore(
			skeleton,
			target,
			new BlockPos(1, 2, 0),
			new BlockPos(0, 2, 0),
			10.0
		);
		double distantOffRange = SkeletonCoverPlanner.planScore(
			skeleton,
			target,
			new BlockPos(4, 3, 0),
			new BlockPos(5, 3, 0),
			10.0
		);
		assertTrue(nearbyPreferred < distantOffRange);
	}
}

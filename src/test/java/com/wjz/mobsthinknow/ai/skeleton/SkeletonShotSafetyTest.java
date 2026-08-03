package com.wjz.mobsthinknow.ai.skeleton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SkeletonShotSafetyTest {
	private static final double EPSILON = 1.0E-6;

	@Test
	void samplesABoundedArcAndLeadsOnlyObservedHorizontalVelocity() {
		Vec3 start = new Vec3(0.0, 1.5, 0.0);
		Vec3 targetEye = new Vec3(12.0, 1.5, 0.0);
		List<Vec3> samples = SkeletonShotSafety.trajectorySamples(
			start,
			targetEye,
			new Vec3(0.20, 2.0, 0.10)
		);

		assertEquals(9, samples.size());
		assertEquals(start, samples.getFirst());
		assertTrue(samples.getLast().x > targetEye.x);
		assertTrue(samples.getLast().z > targetEye.z);
		assertEquals(targetEye.y, samples.getLast().y, EPSILON);
		assertTrue(samples.get(4).y > start.y);
	}

	@Test
	void excessiveTargetVelocityIsClampedBeforePrediction() {
		Vec3 start = new Vec3(0.0, 1.5, 0.0);
		Vec3 targetEye = new Vec3(12.0, 1.5, 0.0);
		Vec3 end = SkeletonShotSafety.trajectorySamples(
			start,
			targetEye,
			new Vec3(100.0, 0.0, 0.0)
		).getLast();

		// 12 格距离对应 4 tick 提前量；水平速度上限 0.35，因此最多前置 1.4 格。
		assertEquals(13.4, end.x, EPSILON);
	}
}

package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import org.junit.jupiter.api.Test;

class FiringLanePlannerTest {
	@Test
	void detectsTheNearestAllyInsideTheShotCapsule() {
		FiringLanePlanner.Result<String> result = FiringLanePlanner.check(
			new Vec3d(0.0, 1.5, 0.0),
			new Vec3d(0.0, 1.5, 12.0),
			List.of(
				new FiringLanePlanner.Ally<>("far", new Vec3d(0.1, 1.5, 8.0), 0.7),
				new FiringLanePlanner.Ally<>("near", new Vec3d(0.2, 1.5, 3.0), 0.7)
			),
			20
		);
		assertFalse(result.clear());
		assertEquals("near", result.blocker());
		assertEquals(2, result.checks());
	}

	@Test
	void ignoresAlliesOutsideTheCapsuleAndAtEndpoints() {
		FiringLanePlanner.Result<String> result = FiringLanePlanner.check(
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 10.0),
			List.of(
				new FiringLanePlanner.Ally<>("side", new Vec3d(2.0, 0.0, 5.0), 0.7),
				new FiringLanePlanner.Ally<>("shooter", new Vec3d(0.0, 0.0, 0.0), 1.0),
				new FiringLanePlanner.Ally<>("target-edge", new Vec3d(0.0, 0.0, 10.0), 1.0)
			),
			20
		);
		assertTrue(result.clear());
		assertNull(result.blocker());
	}

	@Test
	void hardCheckLimitBoundsWork() {
		FiringLanePlanner.Result<String> result = FiringLanePlanner.check(
			Vec3d.ZERO,
			new Vec3d(0.0, 0.0, 10.0),
			List.of(
				new FiringLanePlanner.Ally<>("clear", new Vec3d(3.0, 0.0, 2.0), 0.5),
				new FiringLanePlanner.Ally<>("would-block", new Vec3d(0.0, 0.0, 5.0), 0.5)
			),
			1
		);
		assertTrue(result.clear());
		assertEquals(1, result.checks());
	}

	@Test
	void lateralCandidateUsesStableOppositeSides() {
		Vec3d left = FiringLanePlanner.lateralReposition(Vec3d.ZERO, new Vec3d(0.0, 0.0, 10.0), -1, 3.0);
		Vec3d right = FiringLanePlanner.lateralReposition(Vec3d.ZERO, new Vec3d(0.0, 0.0, 10.0), 1, 3.0);
		assertEquals(-left.x(), right.x(), 1.0E-9);
		assertEquals(left.z(), right.z(), 1.0E-9);
		assertTrue(left.z() < 0.0);
	}
}

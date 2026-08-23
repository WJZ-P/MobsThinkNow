package com.wjz.mobsthinknow.shared.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.junit.jupiter.api.Test;

class MixedSquadGeometryTest {
	@Test
	void rangedRolesCreateOpposingCrossfirePositions() {
		Vec3d target = Vec3d.ZERO;
		Vec3d look = new Vec3d(0.0, 0.0, 1.0);
		Vec3d left = MixedSquadGeometry.combatPosition(
			target, look, look, MixedSquadRole.RANGED_LEFT, 0, 10.0
		);
		Vec3d right = MixedSquadGeometry.combatPosition(
			target, look, look, MixedSquadRole.RANGED_RIGHT, 1, 10.0
		);
		assertTrue(left.x() > 0.0);
		assertTrue(right.x() < 0.0);
		assertEquals(left.z(), right.z(), 1.0E-9);
	}

	@Test
	void rallyPositionsKeepLeaderAtAnchorAndFollowersSeparated() {
		Vec3d anchor = new Vec3d(4.0, 2.0, 8.0);
		Vec3d target = new Vec3d(4.0, 2.0, 20.0);
		assertEquals(anchor, MixedSquadGeometry.rallyPosition(anchor, target, MixedSquadRole.LEADER, 0));
		Vec3d left = MixedSquadGeometry.rallyPosition(anchor, target, MixedSquadRole.FLANK_LEFT, 1);
		Vec3d right = MixedSquadGeometry.rallyPosition(anchor, target, MixedSquadRole.FLANK_RIGHT, 2);
		assertTrue(left.x() > anchor.x());
		assertTrue(right.x() < anchor.x());
	}

	@Test
	void allocationReducedGeometryMatchesTheOriginalVectorFormula() {
		Vec3d leader = new Vec3d(4.25, 62.0, -8.5);
		Vec3d target = new Vec3d(-11.0, 65.0, 19.0);
		Vec3d[] looks = {
			new Vec3d(0.25, 0.8, -0.75),
			new Vec3d(0.0, 1.0, 0.0)
		};
		Vec3d fallback = target.subtract(leader);
		for (MixedSquadRole role : MixedSquadRole.values()) {
			for (int ordinal : new int[] {0, 1, 2, 17}) {
				assertVectorEquals(
					legacyRallyPosition(leader, target, role, ordinal),
					MixedSquadGeometry.rallyPosition(leader, target, role, ordinal)
				);
				for (Vec3d look : looks) {
					assertVectorEquals(
						legacyCombatPosition(target, look, fallback, role, ordinal, 10.0),
						MixedSquadGeometry.combatPosition(target, look, fallback, role, ordinal, 10.0)
					);
				}
			}
		}
	}

	private static Vec3d legacyRallyPosition(
		final Vec3d leader,
		final Vec3d target,
		final MixedSquadRole role,
		final int ordinal
	) {
		Vec3d forward = target.subtract(leader).horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		double jitter = Math.floorMod(ordinal, 3) * 0.35;
		return switch (role) {
			case LEADER -> leader;
			case FRONTLINE -> leader.add(forward.scale(2.0 + jitter));
			case FLANK_LEFT -> leader.subtract(right.scale(2.5 + jitter));
			case FLANK_RIGHT -> leader.add(right.scale(2.5 + jitter));
			case RANGED_LEFT -> leader.subtract(forward.scale(2.0)).subtract(right.scale(2.0 + jitter));
			case RANGED_RIGHT -> leader.subtract(forward.scale(2.0)).add(right.scale(2.0 + jitter));
			case BREACHER -> leader.subtract(forward.scale(1.4 + jitter));
			case CARRIER -> leader.subtract(forward.scale(2.6)).add(right.scale((ordinal & 1) == 0 ? 2.4 : -2.4));
			case SUPPORT -> leader.subtract(forward.scale(2.8 + jitter));
		};
	}

	private static Vec3d legacyCombatPosition(
		final Vec3d target,
		final Vec3d look,
		final Vec3d fallback,
		final MixedSquadRole role,
		final int ordinal,
		final double rangedDistance
	) {
		Vec3d forward = look.horizontalUnitOr(fallback);
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		double side = (ordinal & 1) == 0 ? 1.0 : -1.0;
		double range = Math.max(6.0, rangedDistance);
		return switch (role) {
			case LEADER, FRONTLINE -> target.add(forward.scale(2.2));
			case FLANK_LEFT -> target.subtract(forward.scale(1.5)).subtract(right.scale(4.0));
			case FLANK_RIGHT -> target.subtract(forward.scale(1.5)).add(right.scale(4.0));
			case RANGED_LEFT -> legacyCrossfire(target, forward, right, range, -1.0);
			case RANGED_RIGHT -> legacyCrossfire(target, forward, right, range, 1.0);
			case BREACHER -> target.subtract(forward.scale(4.5)).add(right.scale(3.2 * side));
			case CARRIER -> target.subtract(forward.scale(5.5)).add(right.scale(4.0 * side));
			case SUPPORT -> target.add(forward.scale(6.0)).add(right.scale(2.5 * side));
		};
	}

	private static Vec3d legacyCrossfire(
		final Vec3d target,
		final Vec3d forward,
		final Vec3d right,
		final double range,
		final double side
	) {
		double forwardDistance = range * 0.42;
		double lateralDistance = Math.sqrt(Math.max(0.0, range * range - forwardDistance * forwardDistance));
		return target.add(forward.scale(forwardDistance)).add(right.scale(lateralDistance * side));
	}

	private static void assertVectorEquals(final Vec3d expected, final Vec3d actual) {
		assertEquals(expected.x(), actual.x(), 1.0E-12);
		assertEquals(expected.y(), actual.y(), 1.0E-12);
		assertEquals(expected.z(), actual.z(), 1.0E-12);
	}
}

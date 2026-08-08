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
}

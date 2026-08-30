package com.wjz.mobsthinknow.paper.squad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlan;
import com.wjz.mobsthinknow.shared.squad.MixedSquadRole;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class PaperSquadDirectiveTest {
	@Test
	void exactVisibleStateCanReuseAnImmutableSnapshotAcrossTicks() {
		UUID leaderId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();
		Vec3d destination = new Vec3d(3.0, 64.0, -7.0);
		PaperSquadDirective directive = new PaperSquadDirective(
			4L,
			2,
			MixedSquadState.ENGAGING,
			MixedSquadPlan.COMBINED_ARMS,
			MixedSquadRole.RANGED_LEFT,
			destination,
			8.0,
			65.0,
			12.0,
			leaderId,
			targetId,
			true
		);

		assertTrue(directive.matches(
			4L, 2, MixedSquadState.ENGAGING, MixedSquadPlan.COMBINED_ARMS,
			MixedSquadRole.RANGED_LEFT, new Vec3d(3.0, 64.0, -7.0),
			8.0, 65.0, 12.0, leaderId, targetId, true
		));
		assertFalse(directive.matches(
			4L, 2, MixedSquadState.ENGAGING, MixedSquadPlan.COMBINED_ARMS,
			MixedSquadRole.RANGED_LEFT, destination,
			8.0, 65.0, 12.25, leaderId, targetId, true
		));
		assertFalse(directive.matches(
			4L, 2, MixedSquadState.ENGAGING, MixedSquadPlan.COMBINED_ARMS,
			MixedSquadRole.RANGED_LEFT, destination,
			8.0, 65.0, 12.0, leaderId, targetId, false
		));
	}
}

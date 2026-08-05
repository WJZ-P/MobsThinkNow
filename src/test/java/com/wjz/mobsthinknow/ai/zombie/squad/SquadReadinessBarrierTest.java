package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SquadReadinessBarrierTest {
	@Test
	void arrivedButUnreadyRoleDoesNotCountTowardCommit() {
		SquadReadinessBarrier.Result result = SquadReadinessBarrier.evaluate(List.of(
			new SquadReadinessBarrier.MemberStatus(true, true, true),
			new SquadReadinessBarrier.MemberStatus(true, true, false),
			new SquadReadinessBarrier.MemberStatus(true, false, true)
		), 0.67);

		assertEquals(3, result.assigned());
		assertEquals(2, result.arrived());
		assertEquals(1, result.ready());
		assertEquals(3, result.required());
		assertFalse(result.canCommit());
	}

	@Test
	void unassignedMembersDoNotDiluteDeploymentQuorum() {
		SquadReadinessBarrier.Result result = SquadReadinessBarrier.evaluate(List.of(
			new SquadReadinessBarrier.MemberStatus(true, true, true),
			new SquadReadinessBarrier.MemberStatus(true, true, true),
			new SquadReadinessBarrier.MemberStatus(false, false, false)
		), 1.0);

		assertEquals(2, result.assigned());
		assertEquals(1.0, result.readyFraction());
		assertTrue(result.canCommit());
	}

	@Test
	void emptyBarrierNeverCommits() {
		assertFalse(SquadReadinessBarrier.evaluate(List.of(), 0.0).canCommit());
	}
}

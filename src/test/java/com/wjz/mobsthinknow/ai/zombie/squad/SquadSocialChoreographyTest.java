package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class SquadSocialChoreographyTest {
	private static final List<SquadRole> FOLLOWER_ROLES = List.of(
		SquadRole.PRESSURER,
		SquadRole.FLANK_LEFT,
		SquadRole.FLANK_RIGHT,
		SquadRole.SUPPORT
	);

	@Test
	void formingAndLongRallyUseVisibleMeetingCallsWithoutTickSpam() {
		assertEquals(
			ZombieBodyAction.CALL_TO_MEETING,
			onlyCue(SquadState.FORMING, 0L).action()
		);
		assertTrue(cues(SquadState.FORMING, 1L).isEmpty());
		assertTrue(cues(SquadState.RALLYING, 19L).isEmpty());
		assertEquals(ZombieBodyAction.CALL_TO_MEETING, onlyCue(SquadState.RALLYING, 20L).action());
		assertTrue(cues(SquadState.RALLYING, 21L).isEmpty());
		assertEquals(ZombieBodyAction.CALL_TO_MEETING, onlyCue(SquadState.RALLYING, 56L).action());
	}

	@Test
	void oneBriefingCycleContainsSurveyDiscussionDirectionalOrdersAndResponses() {
		assertEquals(ZombieBodyAction.SURVEY_MEMBERS, onlyCue(SquadState.BRIEFING, 0L).action());
		assertEquals(ZombieBodyAction.CONFER, onlyCue(SquadState.BRIEFING, 3L).action());
		assertEquals(ZombieBodyAction.COMMAND_LEFT, onlyCue(SquadState.BRIEFING, 6L).action());

		SquadSocialChoreography.Cue leftResponse = onlyCue(SquadState.BRIEFING, 10L);
		assertEquals(1, leftResponse.followerIndex());
		assertEquals(ZombieBodyAction.NOD, leftResponse.action());
		assertEquals(ZombieBodyAction.SHAKE_HEAD, onlyCue(SquadState.BRIEFING, 13L).action());
		assertEquals(ZombieBodyAction.COMMAND_RIGHT, onlyCue(SquadState.BRIEFING, 16L).action());

		SquadSocialChoreography.Cue rightResponse = onlyCue(SquadState.BRIEFING, 20L);
		assertEquals(2, rightResponse.followerIndex());
		assertEquals(ZombieBodyAction.ACKNOWLEDGE, rightResponse.action());
	}

	@Test
	void missingFlankRolesDegradeDirectionalOrdersToGenericCommand() {
		List<SquadRole> roles = List.of(SquadRole.PRESSURER, SquadRole.SUPPORT);
		assertEquals(
			ZombieBodyAction.COMMAND,
			SquadSocialChoreography.cuesAt(SquadState.BRIEFING, 17L, 6L, roles).getFirst().action()
		);
		assertEquals(
			ZombieBodyAction.COMMAND,
			SquadSocialChoreography.cuesAt(SquadState.BRIEFING, 17L, 16L, roles).getFirst().action()
		);
	}

	@Test
	void deploymentStartsWithAdvanceOrderThenAcknowledgesEachFollowerOnce() {
		SquadSocialChoreography.Cue leader = onlyCue(SquadState.DEPLOYING, 0L);
		assertTrue(leader.leader());
		assertEquals(ZombieBodyAction.ADVANCE_ORDER, leader.action());

		for (int phase = 2; phase <= 8; phase += 2) {
			SquadSocialChoreography.Cue follower = onlyCue(SquadState.DEPLOYING, phase);
			assertTrue(!follower.leader());
			assertEquals(ZombieBodyAction.ACKNOWLEDGE, follower.action());
		}
		assertTrue(cues(SquadState.DEPLOYING, 10L).isEmpty());
	}

	private static List<SquadSocialChoreography.Cue> cues(final SquadState state, final long phase) {
		return SquadSocialChoreography.cuesAt(state, 17L, phase, FOLLOWER_ROLES);
	}

	private static SquadSocialChoreography.Cue onlyCue(final SquadState state, final long phase) {
		List<SquadSocialChoreography.Cue> cues = cues(state, phase);
		assertEquals(1, cues.size());
		return cues.getFirst();
	}
}

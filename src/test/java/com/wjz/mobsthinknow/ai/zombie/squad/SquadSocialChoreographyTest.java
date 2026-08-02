package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import java.util.List;
import org.junit.jupiter.api.Test;

class SquadSocialChoreographyTest {
	private static final List<SquadSocialChoreography.Participant> CLEAR_FOLLOWERS = List.of(
		participant(101, SquadRole.PRESSURER, 7, 2L, SquadRouteOutcome.UNASSESSED),
		participant(102, SquadRole.FLANK_LEFT, 10, 4L, SquadRouteOutcome.CLEAR),
		participant(103, SquadRole.FLANK_RIGHT, 10, 6L, SquadRouteOutcome.CLEAR),
		participant(104, SquadRole.SUPPORT, 8, 8L, SquadRouteOutcome.UNASSESSED)
	);

	@Test
	void formingAndLongRallyCallWithoutTickSpamAndIdleUsesStableSparseSlot() {
		assertEquals(ZombieBodyAction.CALL_TO_MEETING, onlyCue(SquadState.FORMING, 0L).action());
		assertTrue(scene(SquadState.FORMING, 1L).cues().isEmpty());
		assertTrue(scene(SquadState.RALLYING, 19L).cues().isEmpty());
		assertEquals(ZombieBodyAction.CALL_TO_MEETING, onlyCue(SquadState.RALLYING, 20L).action());
		assertEquals(ZombieBodyAction.CALL_TO_MEETING, onlyCue(SquadState.RALLYING, 56L).action());

		List<SquadSocialChoreography.Participant> idle = List.of(new SquadSocialChoreography.Participant(
			201,
			SquadRole.PRESSURER,
			8,
			0L,
			SquadRouteOutcome.UNASSESSED,
			SquadSocialChoreography.IdleStyle.SHIELD
		));
		assertEquals(
			ZombieBodyAction.SHIELD_TAP,
			SquadSocialChoreography.sceneAt(
				SquadState.RALLYING,
				17L,
				12L,
				idle,
				SquadSocialChoreography.Timing.DEFAULT
			).cues().getFirst().action()
		);
	}

	@Test
	void clearRoutesProduceReadableThreeSecondBriefingAndPositiveResponses() {
		assertEquals(ZombieBodyAction.SURVEY_MEMBERS, onlyCue(SquadState.BRIEFING, 0L).action());
		assertEquals(ZombieBodyAction.CONFER, onlyCue(SquadState.BRIEFING, 8L).action());
		assertEquals(ZombieBodyAction.COMMAND_LEFT, onlyCue(SquadState.BRIEFING, 16L).action());
		assertEquals(ZombieBodyAction.NOD, onlyCue(SquadState.BRIEFING, 26L).action());
		assertTrue(scene(SquadState.BRIEFING, 33L).cues().isEmpty());
		assertEquals(ZombieBodyAction.COMMAND_RIGHT, onlyCue(SquadState.BRIEFING, 38L).action());
		assertEquals(ZombieBodyAction.ACKNOWLEDGE, onlyCue(SquadState.BRIEFING, 48L).action());
		assertEquals(ZombieBodyAction.COMMAND, onlyCue(SquadState.BRIEFING, 60L).action());
	}

	@Test
	void routeObjectionsCauseHeadShakeAndARealCorrectionOrder() {
		List<SquadSocialChoreography.Participant> followers = List.of(
			participant(101, SquadRole.PRESSURER, 8, 2L, SquadRouteOutcome.UNASSESSED),
			participant(102, SquadRole.FLANK_LEFT, 10, 4L, SquadRouteOutcome.REROUTED),
			participant(103, SquadRole.FLANK_RIGHT, 10, 6L, SquadRouteOutcome.BLOCKED)
		);
		assertEquals(ZombieBodyAction.SHAKE_HEAD, cueAt(followers, SquadState.BRIEFING, 26L).action());
		assertEquals(ZombieBodyAction.COMMAND_LEFT, cueAt(followers, SquadState.BRIEFING, 33L).action());
		assertEquals(ZombieBodyAction.SHAKE_HEAD, cueAt(followers, SquadState.BRIEFING, 48L).action());
		assertEquals(ZombieBodyAction.COMMAND, cueAt(followers, SquadState.BRIEFING, 55L).action());
	}

	@Test
	void intelligenceControlsResponseDelayWithoutRandomTickState() {
		SquadSocialChoreography.Participant high = participant(
			102, SquadRole.FLANK_LEFT, 9, 1L, SquadRouteOutcome.CLEAR
		);
		SquadSocialChoreography.Participant low = participant(
			102, SquadRole.FLANK_LEFT, 4, 1L, SquadRouteOutcome.CLEAR
		);
		assertEquals(0, SquadSocialChoreography.responseDelay(high));
		assertEquals(3, SquadSocialChoreography.responseDelay(low));

		List<SquadSocialChoreography.Participant> followers = List.of(
			low,
			participant(103, SquadRole.FLANK_RIGHT, 10, 2L, SquadRouteOutcome.CLEAR)
		);
		assertTrue(sceneAt(followers, SquadState.BRIEFING, 26L).cues().isEmpty());
		assertEquals(ZombieBodyAction.NOD, cueAt(followers, SquadState.BRIEFING, 29L).action());
	}

	@Test
	void attentionMovesFromSpeakerToNamedMemberAndActualRoleDestination() {
		SquadSocialChoreography.Scene conference = scene(SquadState.BRIEFING, 9L);
		assertEquals(SquadSocialChoreography.FocusKind.ACTOR, conference.attention().audienceFocus().kind());
		assertEquals(conference.attention().audienceFocus(), conference.attention().leaderFocus());

		SquadSocialChoreography.Scene memberLook = scene(SquadState.BRIEFING, 17L);
		assertEquals(SquadSocialChoreography.FocusKind.ACTOR, memberLook.attention().leaderFocus().kind());
		assertEquals(102, memberLook.attention().leaderFocus().actorEntityId());

		SquadSocialChoreography.Scene destinationLook = scene(SquadState.BRIEFING, 23L);
		assertEquals(
			SquadSocialChoreography.FocusKind.ROLE_DESTINATION,
			destinationLook.attention().leaderFocus().kind()
		);
		assertEquals(SquadRole.FLANK_LEFT, destinationLook.attention().leaderFocus().role());
	}

	@Test
	void successionHasLookAroundSaluteAcknowledgementsAndFinalCommand() {
		assertEquals(ZombieBodyAction.SUCCESSION_LOOK_AROUND, onlyCue(SquadState.REORGANIZING, 0L).action());
		assertEquals(ZombieBodyAction.SUCCESSION_SALUTE, onlyCue(SquadState.REORGANIZING, 14L).action());
		assertEquals(ZombieBodyAction.NOD, onlyCue(SquadState.REORGANIZING, 26L).action());
		assertEquals(ZombieBodyAction.COMMAND, onlyCue(SquadState.REORGANIZING, 43L).action());
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
		assertTrue(scene(SquadState.DEPLOYING, 10L).cues().isEmpty());
	}

	private static SquadSocialChoreography.Participant participant(
		final int id,
		final SquadRole role,
		final int intelligence,
		final long stableKey,
		final SquadRouteOutcome outcome
	) {
		return new SquadSocialChoreography.Participant(
			id,
			role,
			intelligence,
			stableKey,
			outcome,
			SquadSocialChoreography.IdleStyle.NONE
		);
	}

	private static SquadSocialChoreography.Scene scene(final SquadState state, final long phase) {
		return sceneAt(CLEAR_FOLLOWERS, state, phase);
	}

	private static SquadSocialChoreography.Scene sceneAt(
		final List<SquadSocialChoreography.Participant> followers,
		final SquadState state,
		final long phase
	) {
		return SquadSocialChoreography.sceneAt(
			state,
			17L,
			phase,
			followers,
			SquadSocialChoreography.Timing.DEFAULT
		);
	}

	private static SquadSocialChoreography.Cue cueAt(
		final List<SquadSocialChoreography.Participant> followers,
		final SquadState state,
		final long phase
	) {
		List<SquadSocialChoreography.Cue> cues = sceneAt(followers, state, phase).cues();
		assertEquals(1, cues.size());
		return cues.getFirst();
	}

	private static SquadSocialChoreography.Cue onlyCue(final SquadState state, final long phase) {
		List<SquadSocialChoreography.Cue> cues = scene(state, phase).cues();
		assertEquals(1, cues.size());
		return cues.getFirst();
	}
}

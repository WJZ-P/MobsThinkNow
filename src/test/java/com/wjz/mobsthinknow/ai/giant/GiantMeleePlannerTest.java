package com.wjz.mobsthinknow.ai.giant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GiantMeleePlannerTest {
	@Test
	void iqTenChoosesGroundSmashAgainstACrowdWhenBothHandsAreFree() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(5.0, 4, true, true, 10, GiantMeleeAction.NONE),
			0.999,
			0.25
		);

		assertEquals(GiantMeleeAction.GROUND_SMASH, action);
	}

	@Test
	void loadedHandsLeaveStompAsTheOnlyCloseRangeOption() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(3.0, 2, false, false, 10, GiantMeleeAction.NONE),
			0.0,
			0.75
		);

		assertEquals(GiantMeleeAction.Family.STOMP, action.family());
		assertTrue(!action.usesHand(GiantHand.RIGHT) && !action.usesHand(GiantHand.LEFT));
	}

	@Test
	void singleFreeHandIsNeverSilentlySwapped() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(6.5, 1, false, true, 10, GiantMeleeAction.NONE),
			0.0,
			0.0
		);

		assertEquals(GiantMeleeAction.SWEEP_LEFT, action);
	}

	@Test
	void singleCloseTargetPrefersReadablePalmStrike() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(3.0, 1, true, true, 10, GiantMeleeAction.NONE),
			0.0,
			0.20
		);

		assertEquals(GiantMeleeAction.Family.SLAP, action.family());
	}

	@Test
	void singleDistantTargetDoesNotWasteTheTwoHandedCrowdSmash() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(6.0, 1, true, true, 10, GiantMeleeAction.NONE),
			0.0,
			0.20
		);

		assertEquals(GiantMeleeAction.Family.SWEEP, action.family());
	}

	@Test
	void noActionStartsOutsideTheSevenBlockCombatEnvelope() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(7.26, 3, true, true, 10, GiantMeleeAction.NONE),
			0.0,
			0.0
		);

		assertEquals(GiantMeleeAction.NONE, action);
	}

	@Test
	void defendingTargetMakesTheGiantPreferAFrontKick() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(
				4.0,
				1,
				true,
				true,
				10,
				true,
				false,
				GiantMeleeAction.NONE
			),
			0.0,
			0.25
		);

		assertEquals(GiantMeleeAction.Family.KICK, action.family());
		assertTrue(!action.usesHand(GiantHand.RIGHT) && !action.usesHand(GiantHand.LEFT));
	}

	@Test
	void highIntelligenceSingleTargetCanBeGrabbedWithTheAvailableHand() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(
				3.0,
				1,
				false,
				true,
				10,
				false,
				true,
				GiantMeleeAction.NONE
			),
			0.0,
			0.75
		);

		assertEquals(GiantMeleeAction.GRAB_LEFT, action);
	}

	@Test
	void repeatingAGrabIsDownweightedInFavorOfAnotherSingleTargetAttack() {
		GiantMeleeAction action = GiantMeleePlanner.choose(
			new GiantMeleePlanner.Context(
				3.0,
				1,
				true,
				true,
				10,
				false,
				true,
				GiantMeleeAction.GRAB_RIGHT
			),
			0.0,
			0.25
		);

		assertEquals(GiantMeleeAction.Family.SLAP, action.family());
	}
}

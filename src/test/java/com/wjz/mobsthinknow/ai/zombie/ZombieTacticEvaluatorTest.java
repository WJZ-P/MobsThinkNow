package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieTacticEvaluatorTest {
	@Test
	void loneZombiePressuresUnprotectedTarget() {
		ZombieDecisionContext context = new ZombieDecisionContext(true, true, false, true, 1, 0, true);

		assertEquals(ZombieTactic.PRESSURE, ZombieTacticEvaluator.select(context, ZombieTactic.PRESSURE));
	}

	@Test
	void loneZombieFlanksFrontFacingShield() {
		ZombieDecisionContext context = new ZombieDecisionContext(true, true, true, true, 1, 0, true);
		ZombieTactic result = ZombieTacticEvaluator.select(context, ZombieTactic.PRESSURE);

		assertTrue(result == ZombieTactic.FLANK_LEFT || result == ZombieTactic.FLANK_RIGHT);
	}

	@Test
	void firstPackMemberKeepsPressure() {
		ZombieDecisionContext context = new ZombieDecisionContext(true, true, true, true, 3, 0, true);

		assertEquals(ZombieTactic.PRESSURE, ZombieTacticEvaluator.select(context, ZombieTactic.PRESSURE));
	}

	@Test
	void additionalPackMemberSurroundsTarget() {
		ZombieDecisionContext context = new ZombieDecisionContext(true, true, false, true, 3, 1, true);

		assertEquals(ZombieTactic.SURROUND, ZombieTacticEvaluator.select(context, ZombieTactic.PRESSURE));
	}

	@Test
	void additionalPackMemberFlanksShieldInsteadOfUsingFormationSlot() {
		ZombieDecisionContext context = new ZombieDecisionContext(true, true, true, true, 3, 1, false);

		assertEquals(ZombieTactic.FLANK_RIGHT, ZombieTacticEvaluator.select(context, ZombieTactic.SURROUND));
	}

	@Test
	void stopsFlankingAfterLeavingShieldFrontArc() {
		ZombieDecisionContext context = new ZombieDecisionContext(true, true, true, false, 1, 0, true);

		assertEquals(ZombieTactic.PRESSURE, ZombieTacticEvaluator.select(context, ZombieTactic.FLANK_LEFT));
	}

	@Test
	void searchesLastSeenPositionWithoutLineOfSight() {
		ZombieDecisionContext context = new ZombieDecisionContext(false, true, false, false, 1, 0, true);

		assertEquals(ZombieTactic.SEARCH_LAST_SEEN, ZombieTacticEvaluator.select(context, ZombieTactic.PRESSURE));
	}
}

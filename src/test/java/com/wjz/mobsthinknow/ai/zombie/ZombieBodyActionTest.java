package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZombieBodyActionTest {
	@Test
	void idsAreStableAndUnknownValuesFallBackToNone() {
		assertEquals(ZombieBodyAction.NONE, ZombieBodyAction.fromId(-1));
		assertEquals(ZombieBodyAction.NONE, ZombieBodyAction.fromId(127));
		for (ZombieBodyAction action : ZombieBodyAction.values()) {
			assertEquals(action, ZombieBodyAction.fromId(action.id()));
		}
	}

	@Test
	void transientActionsExpireWhileRetreatRemainsGoalOwned() {
		assertTrue(ZombieBodyAction.COMMAND.isActiveAt(0.0F));
		assertTrue(ZombieBodyAction.COMMAND.isActiveAt(17.99F));
		assertFalse(ZombieBodyAction.COMMAND.isActiveAt(18.0F));
		assertTrue(ZombieBodyAction.RETREAT.isActiveAt(10_000.0F));
		assertTrue(ZombieBodyAction.AXE_LEAP.isActiveAt(10_000.0F));
		assertTrue(ZombieBodyAction.ENGINEER_WORK.isActiveAt(10_000.0F));
		assertFalse(ZombieBodyAction.SWORD_FEINT.isActiveAt(18.0F));
		assertTrue(ZombieBodyAction.CALL_TO_MEETING.isActiveAt(23.99F));
		assertFalse(ZombieBodyAction.CALL_TO_MEETING.isActiveAt(24.0F));
		assertFalse(ZombieBodyAction.SHAKE_HEAD.isActiveAt(16.0F));
		assertFalse(ZombieBodyAction.ADVANCE_ORDER.isActiveAt(18.0F));
		assertFalse(ZombieBodyAction.NONE.isActiveAt(0.0F));
	}

	@Test
	void emergencyActionsHaveHigherPriorityThanConversation() {
		assertTrue(ZombieBodyAction.RETREAT.priority() > ZombieBodyAction.WAR_CRY.priority());
		assertTrue(ZombieBodyAction.RETREAT.priority() > ZombieBodyAction.SHIELD_BASH.priority());
		assertTrue(ZombieBodyAction.SHIELD_BASH.priority() > ZombieBodyAction.AXE_LEAP.priority());
		assertTrue(ZombieBodyAction.AXE_LEAP.priority() > ZombieBodyAction.AXE_WINDUP.priority());
		assertTrue(ZombieBodyAction.AXE_WINDUP.priority() > ZombieBodyAction.SWORD_FEINT.priority());
		assertTrue(ZombieBodyAction.SWORD_FEINT.priority() > ZombieBodyAction.ENGINEER_WORK.priority());
		assertTrue(ZombieBodyAction.ENGINEER_WORK.priority() > ZombieBodyAction.WAR_CRY.priority());
		assertTrue(ZombieBodyAction.WAR_CRY.priority() > ZombieBodyAction.COMMAND.priority());
		assertTrue(ZombieBodyAction.COMMAND.priority() > ZombieBodyAction.ACKNOWLEDGE.priority());
		assertTrue(ZombieBodyAction.ADVANCE_ORDER.priority() > ZombieBodyAction.CALL_TO_MEETING.priority());
		assertEquals(ZombieBodyAction.CALL_TO_MEETING.priority(), ZombieBodyAction.COMMAND_LEFT.priority());
		assertEquals(ZombieBodyAction.COMMAND_RIGHT.priority(), ZombieBodyAction.SURVEY_MEMBERS.priority());
		assertEquals(ZombieBodyAction.NOD.priority(), ZombieBodyAction.SHAKE_HEAD.priority());
		assertTrue(ZombieBodyAction.NOD.priority() > ZombieBodyAction.CONFER.priority());
	}
}

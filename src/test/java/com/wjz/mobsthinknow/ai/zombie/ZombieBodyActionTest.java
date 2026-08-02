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
		assertEquals(18, ZombieBodyAction.SHIELD_TAP.id());
		assertEquals(19, ZombieBodyAction.SWORD_INSPECT.id());
		assertEquals(20, ZombieBodyAction.AXE_SHOULDER.id());
		assertEquals(21, ZombieBodyAction.ENGINEER_CHECK.id());
		assertEquals(22, ZombieBodyAction.CONFUSED_TILT.id());
		assertEquals(23, ZombieBodyAction.SUCCESSION_LOOK_AROUND.id());
		assertEquals(24, ZombieBodyAction.SUCCESSION_SALUTE.id());
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
		assertFalse(ZombieBodyAction.SHIELD_TAP.isActiveAt(20.0F));
		assertFalse(ZombieBodyAction.SWORD_INSPECT.isActiveAt(24.0F));
		assertFalse(ZombieBodyAction.SUCCESSION_SALUTE.isActiveAt(24.0F));
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
		assertTrue(ZombieBodyAction.SUCCESSION_SALUTE.priority() > ZombieBodyAction.ADVANCE_ORDER.priority());
		assertTrue(ZombieBodyAction.SUCCESSION_LOOK_AROUND.priority() > ZombieBodyAction.NOD.priority());
		assertEquals(ZombieBodyAction.SHIELD_TAP.priority(), ZombieBodyAction.CONFUSED_TILT.priority());
	}
}

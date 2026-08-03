package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class SquadTacticMemoryTest {
	@Test
	void requiresRepeatedVisibleEvidenceAndUsesRetentionHysteresis() {
		SquadTacticMemory memory = new SquadTacticMemory();

		SquadTacticMemory.Update first = memory.observe(EnumSet.of(ObservedTargetTactic.SHIELDING), 10L);
		assertEquals(ObservedTargetTactic.NONE, first.primary());
		assertFalse(first.changed());

		SquadTacticMemory.Update activated = memory.observe(EnumSet.of(ObservedTargetTactic.SHIELDING), 11L);
		assertEquals(ObservedTargetTactic.SHIELDING, activated.primary());
		assertEquals(4, activated.score());
		assertTrue(activated.changed());

		assertEquals(
			ObservedTargetTactic.SHIELDING,
			memory.observe(EnumSet.noneOf(ObservedTargetTactic.class), 12L).primary()
		);
		assertEquals(
			ObservedTargetTactic.SHIELDING,
			memory.observe(EnumSet.noneOf(ObservedTargetTactic.class), 13L).primary()
		);
		assertEquals(
			ObservedTargetTactic.NONE,
			memory.observe(EnumSet.noneOf(ObservedTargetTactic.class), 14L).primary()
		);
	}

	@Test
	void lossOfSightAgesEvidenceWithoutTreatingUnknownAsCounterEvidence() {
		SquadTacticMemory memory = new SquadTacticMemory();
		memory.observe(EnumSet.of(ObservedTargetTactic.HIGH_GROUND), 20L);
		memory.observe(EnumSet.of(ObservedTargetTactic.HIGH_GROUND), 21L);

		SquadTacticMemory.Update remembered = memory.age(100L);
		assertEquals(ObservedTargetTactic.HIGH_GROUND, remembered.primary());
		assertEquals(4, remembered.score());
		assertEquals(ObservedTargetTactic.NONE, memory.age(122L).primary());
	}

	@Test
	void strongerFreshEvidenceCanReplaceTheRetainedPrimary() {
		SquadTacticMemory memory = new SquadTacticMemory();
		memory.observe(EnumSet.of(ObservedTargetTactic.SHIELDING), 1L);
		memory.observe(EnumSet.of(ObservedTargetTactic.SHIELDING), 2L);

		memory.observe(EnumSet.of(ObservedTargetTactic.HIGH_GROUND), 3L);
		SquadTacticMemory.Update replacement = memory.observe(
			EnumSet.of(ObservedTargetTactic.HIGH_GROUND),
			4L
		);

		assertEquals(ObservedTargetTactic.HIGH_GROUND, replacement.primary());
		assertTrue(replacement.changed());
	}
}

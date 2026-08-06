package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SquadCohesionPacingTest {
	private static final double EPSILON = 1.0E-9;

	@Test
	void inactiveOrderKeepsOnlyTheConfiguredSquadBonus() {
		assertEquals(0.10, SquadCohesionPacing.speedBonus(0.10, false, 10_000.0), EPSILON);
	}

	@Test
	void catchUpBonusUsesThreeDiscreteDistanceBands() {
		assertEquals(0.10, SquadCohesionPacing.speedBonus(0.10, true, 7.99 * 7.99), EPSILON);
		assertEquals(0.15, SquadCohesionPacing.speedBonus(0.10, true, 8.0 * 8.0), EPSILON);
		assertEquals(0.20, SquadCohesionPacing.speedBonus(0.10, true, 14.0 * 14.0), EPSILON);
		assertEquals(0.25, SquadCohesionPacing.speedBonus(0.10, true, 20.0 * 20.0), EPSILON);
	}

	@Test
	void zeroBaseBonusAlsoDisablesCatchUpPacing() {
		assertEquals(0.0, SquadCohesionPacing.speedBonus(0.0, true, 10_000.0), EPSILON);
		assertEquals(0.0, SquadCohesionPacing.speedBonus(-1.0, true, 10_000.0), EPSILON);
	}

	@Test
	void combinedBonusNeverExceedsTheExistingConfigurationCeiling() {
		assertEquals(0.50, SquadCohesionPacing.speedBonus(0.48, true, 10_000.0), EPSILON);
		assertEquals(0.50, SquadCohesionPacing.speedBonus(2.0, true, 10_000.0), EPSILON);
	}

	@Test
	void invalidInputsDoNotCreateAnInvalidAttributeModifier() {
		assertEquals(0.0, SquadCohesionPacing.speedBonus(Double.NaN, true, 10_000.0), EPSILON);
		assertEquals(0.10, SquadCohesionPacing.speedBonus(0.10, true, Double.NaN), EPSILON);
	}
}

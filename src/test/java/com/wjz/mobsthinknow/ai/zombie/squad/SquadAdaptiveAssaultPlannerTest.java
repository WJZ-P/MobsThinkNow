package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SquadAdaptiveAssaultPlannerTest {
	@Test
	void lowIntelligenceNeverAdaptsBeyondTheFrozenBasePlan() {
		assertEquals(
			SquadAssaultPlan.SWARM,
			SquadAdaptiveAssaultPlanner.adapt(
				SquadAssaultPlan.SWARM,
				ObservedTargetTactic.SHIELDING,
				new SquadComposition(2, 2, 0, 0, 0, 0),
				4
			)
		);
	}

	@Test
	void shieldAndKitingEvidencePromoteMixedForcesToPinAndFlank() {
		SquadComposition mixed = new SquadComposition(2, 1, 0, 0, 0, 0);
		assertEquals(
			SquadAssaultPlan.PIN_AND_FLANK,
			SquadAdaptiveAssaultPlanner.adapt(
				SquadAssaultPlan.SWARM,
				ObservedTargetTactic.KITING,
				mixed,
				7
			)
		);
	}

	@Test
	void highGroundPromotesTwoShootersToCrossfire() {
		assertEquals(
			SquadAssaultPlan.CROSSFIRE,
			SquadAdaptiveAssaultPlanner.adapt(
				SquadAssaultPlan.SHIELD_WEDGE,
				ObservedTargetTactic.HIGH_GROUND,
				new SquadComposition(1, 2, 0, 0, 1, 0),
				8
			)
		);
	}

	@Test
	void chokePointPromotesSpiderCreeperPairToMountedBreach() {
		assertEquals(
			SquadAssaultPlan.MOUNTED_BREACH,
			SquadAdaptiveAssaultPlanner.adapt(
				SquadAssaultPlan.SWARM,
				ObservedTargetTactic.CHOKEPOINT,
				new SquadComposition(1, 0, 1, 1, 0, 0),
				7
			)
		);
	}

	@Test
	void unsupportedCounterTacticKeepsTheBasePlan() {
		assertEquals(
			SquadAssaultPlan.SWARM,
			SquadAdaptiveAssaultPlanner.adapt(
				SquadAssaultPlan.SWARM,
				ObservedTargetTactic.WATER_DEFENSE,
				new SquadComposition(3, 0, 0, 0, 0, 0),
				10
			)
		);
	}
}

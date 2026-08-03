package com.wjz.mobsthinknow.ai.zombie.squad;

/** 根据已确认的可观察目标战术，在阵容确实支持时调整总攻方案。 */
public final class SquadAdaptiveAssaultPlanner {
	private SquadAdaptiveAssaultPlanner() {
	}

	public static SquadAssaultPlan adapt(
		final SquadAssaultPlan basePlan,
		final ObservedTargetTactic tactic,
		final SquadComposition composition,
		final int leaderIntelligence
	) {
		if (tactic == ObservedTargetTactic.NONE || leaderIntelligence < 5) {
			return basePlan;
		}
		return switch (tactic) {
			case SHIELDING, KITING -> composition.rangedMembers() > 0 && composition.meleeMembers() > 0
				? SquadAssaultPlan.PIN_AND_FLANK
				: basePlan;
			case HIGH_GROUND, WATER_DEFENSE -> composition.rangedMembers() >= 2
				? SquadAssaultPlan.CROSSFIRE
				: composition.rangedMembers() > 0 && composition.meleeMembers() > 0
					? SquadAssaultPlan.PIN_AND_FLANK
					: basePlan;
			case CHOKEPOINT -> {
				if (leaderIntelligence >= 7 && composition.spiders() > 0 && composition.creepers() > 0) {
					yield SquadAssaultPlan.MOUNTED_BREACH;
				}
				if (composition.rangedMembers() >= 2) {
					yield SquadAssaultPlan.CROSSFIRE;
				}
				yield composition.shieldFrontliners() > 0 && composition.rangedMembers() > 0
					? SquadAssaultPlan.SHIELD_WEDGE
					: basePlan;
			}
			case NONE -> basePlan;
		};
	}
}

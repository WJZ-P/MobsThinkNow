package com.wjz.mobsthinknow.ai.zombie.squad;

/** 用纯数据选择总攻方案，便于单元测试且不会在决策时触碰世界对象。 */
public final class SquadAssaultPlanner {
	private SquadAssaultPlanner() {
	}

	public static SquadAssaultPlan choose(
		final SquadComposition composition,
		final int leaderIntelligence
	) {
		int intelligence = Math.max(1, Math.min(10, leaderIntelligence));
		if (intelligence >= 8 && composition.hasFourCoreSpecies()) {
			return SquadAssaultPlan.COMBINED_ARMS;
		}
		if (intelligence >= 7 && composition.spiders() > 0 && composition.creepers() > 0) {
			return SquadAssaultPlan.MOUNTED_BREACH;
		}
		if (intelligence >= 7 && composition.rangedMembers() >= 2 && composition.meleeMembers() > 0) {
			return SquadAssaultPlan.CROSSFIRE;
		}
		if (intelligence >= 6
			&& composition.shieldFrontliners() > 0
			&& (composition.rangedMembers() > 0 || composition.creepers() > 0)) {
			return SquadAssaultPlan.SHIELD_WEDGE;
		}
		if (intelligence >= 5 && composition.rangedMembers() > 0 && composition.meleeMembers() > 0) {
			return SquadAssaultPlan.PIN_AND_FLANK;
		}
		return SquadAssaultPlan.SWARM;
	}
}

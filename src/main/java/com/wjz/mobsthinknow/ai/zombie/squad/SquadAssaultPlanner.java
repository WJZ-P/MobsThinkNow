package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.shared.squad.MixedSquadPlan;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlanner;

/** 用纯数据选择总攻方案，便于单元测试且不会在决策时触碰世界对象。 */
public final class SquadAssaultPlanner {
	private SquadAssaultPlanner() {
	}

	public static SquadAssaultPlan choose(
		final SquadComposition composition,
		final int leaderIntelligence
	) {
		MixedSquadPlanner.Composition shared = new MixedSquadPlanner.Composition(
			composition.meleeMembers(),
			composition.rangedMembers(),
			composition.creepers(),
			composition.spiders(),
			composition.shieldFrontliners(),
			composition.supportMembers()
		);
		MixedSquadPlan plan = MixedSquadPlanner.choosePlan(shared, leaderIntelligence);
		return SquadAssaultPlan.valueOf(plan.name());
	}
}

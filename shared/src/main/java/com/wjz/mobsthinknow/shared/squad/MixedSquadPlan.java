package com.wjz.mobsthinknow.shared.squad;

/** 按阵容与首领智力冻结的一轮跨物种总攻方案。 */
public enum MixedSquadPlan {
	SWARM,
	SHIELD_WEDGE,
	PIN_AND_FLANK,
	CROSSFIRE,
	MOUNTED_BREACH,
	COMBINED_ARMS;

	public boolean usesCrossfire() {
		return this == CROSSFIRE || this == COMBINED_ARMS;
	}

	public boolean usesCarrier() {
		return this == MOUNTED_BREACH || this == COMBINED_ARMS;
	}
}

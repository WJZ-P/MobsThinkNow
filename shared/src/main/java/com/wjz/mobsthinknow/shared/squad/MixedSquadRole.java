package com.wjz.mobsthinknow.shared.squad;

/** 小队黑板下发的职责；平台适配器再把职责翻译成导航、攻击或表现。 */
public enum MixedSquadRole {
	LEADER,
	FRONTLINE,
	FLANK_LEFT,
	FLANK_RIGHT,
	RANGED_LEFT,
	RANGED_RIGHT,
	BREACHER,
	CARRIER,
	SUPPORT;

	public boolean isRanged() {
		return this == RANGED_LEFT || this == RANGED_RIGHT;
	}

	public boolean isFlanker() {
		return this == FLANK_LEFT || this == FLANK_RIGHT;
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

/** 小队从发现同伴到正式交战的可观察状态。 */
public enum SquadState {
	FORMING,
	RALLYING,
	BRIEFING,
	DEPLOYING,
	ENGAGING,
	REORGANIZING
}

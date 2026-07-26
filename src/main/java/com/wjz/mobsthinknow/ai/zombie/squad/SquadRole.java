package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 首领下发的战斗职位。职位只描述职责，具体目的地由协调器根据目标朝向统一计算。
 */
public enum SquadRole {
	LEADER,
	PRESSURER,
	FLANK_LEFT,
	FLANK_RIGHT,
	CUTOFF
}

package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 首领下发的战斗职位。职位只描述职责，具体目的地由协调器根据目标朝向统一计算。
 */
public enum SquadRole {
	LEADER,
	PRESSURER,
	/** 诱饵：在目标正面保持距离横向游走叫嚣，吸引注意力给侧翼创造偷袭窗口。 */
	BAIT,
	FLANK_LEFT,
	FLANK_RIGHT,
	CUTOFF
}

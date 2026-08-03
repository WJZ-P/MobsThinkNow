package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 首领从当前可见目标身上逐拍确认的战术倾向；NONE 表示证据不足，不代表读取了目标的隐藏状态。
 */
public enum ObservedTargetTactic {
	NONE,
	HIGH_GROUND,
	SHIELDING,
	KITING,
	CHOKEPOINT,
	WATER_DEFENSE
}

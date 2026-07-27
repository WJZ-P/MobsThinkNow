package com.wjz.mobsthinknow.ai.zombie.squad;

/** 小队成员携带的战场工具类别；与主手武器类别分开建模。 */
public enum UtilityClass {
	NONE,
	WATER,
	LAVA;

	public static UtilityClass fromId(final int id) {
		return id >= 0 && id < values().length ? values()[id] : NONE;
	}
}

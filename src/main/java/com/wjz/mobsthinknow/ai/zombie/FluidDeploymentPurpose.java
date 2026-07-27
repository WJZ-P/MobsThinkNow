package com.wjz.mobsthinknow.ai.zombie;

/** 区分同一个流体事务由战斗战术还是日光自救发起。 */
public enum FluidDeploymentPurpose {
	COMBAT,
	SUN_PROTECTION;

	public static FluidDeploymentPurpose fromId(final int id) {
		FluidDeploymentPurpose[] values = values();
		return id >= 0 && id < values.length ? values[id] : COMBAT;
	}
}

package com.wjz.mobsthinknow.ai.zombie;

/** 区分同一个流体事务由战斗战术还是着火/日光生存行为发起。 */
public enum FluidDeploymentPurpose {
	COMBAT,
	/** 自身着火或日晒时用于保命；危险日光尚未解除前不主动回收。 */
	SURVIVAL,
	/** 工程兵随机技能投放；不依赖真实手持桶，并由工程技能状态机负责回收。 */
	ENGINEER;

	public static FluidDeploymentPurpose fromId(final int id) {
		FluidDeploymentPurpose[] values = values();
		return id >= 0 && id < values.length ? values[id] : COMBAT;
	}
}

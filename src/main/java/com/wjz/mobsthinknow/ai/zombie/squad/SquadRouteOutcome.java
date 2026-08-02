package com.wjz.mobsthinknow.ai.zombie.squad;

/** 会议前对成员部署路线做一次有界真实寻路后得到的语义结果。 */
public enum SquadRouteOutcome {
	/** 当前成员没有需要在会议中汇报的侧翼路线。 */
	UNASSESSED(false, false),
	/** 原始战术阵位可以被原版导航精确抵达。 */
	CLEAR(true, false),
	/** 原始阵位不可达，但首领找到了一条可达替代路线并重新分配职位。 */
	REROUTED(true, true),
	/** 原始路线与所有有界替代路线都不可达，成员被降级为直接施压。 */
	BLOCKED(false, true);

	private final boolean resolvedReachable;
	private final boolean objection;

	SquadRouteOutcome(final boolean resolvedReachable, final boolean objection) {
		this.resolvedReachable = resolvedReachable;
		this.objection = objection;
	}

	public boolean resolvedReachable() {
		return this.resolvedReachable;
	}

	public boolean isObjection() {
		return this.objection;
	}
}

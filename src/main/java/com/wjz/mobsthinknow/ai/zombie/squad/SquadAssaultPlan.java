package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 混编小队按阵容与首领智力冻结的一轮总攻方案。
 *
 * <p>枚举顺序不写入实体存档，只存在于服务端协调器的瞬时小队状态中。</p>
 */
public enum SquadAssaultPlan {
	/** 低智力或阵容单一：沿用普通职位，直接压上。 */
	SWARM,
	/** 持盾近战顶住正面，远程或爆破单位在其后完成准备。 */
	SHIELD_WEDGE,
	/** 远程正面牵制，近战从两翼和退路位切入。 */
	PIN_AND_FLANK,
	/** 两名以上射手分居目标两侧，制造交叉射界。 */
	CROSSFIRE,
	/** 蜘蛛先把苦力怕带到侧后 staging point，再开始最终冲锋。 */
	MOUNTED_BREACH,
	/** 僵尸、骷髅、苦力怕和蜘蛛齐备时的完整联合兵种合围。 */
	COMBINED_ARMS;

	public boolean usesCrossfire() {
		return this == CROSSFIRE || this == COMBINED_ARMS;
	}

	public boolean usesMountedBreach() {
		return this == MOUNTED_BREACH || this == COMBINED_ARMS;
	}
}

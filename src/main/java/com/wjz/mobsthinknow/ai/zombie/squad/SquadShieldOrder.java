package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 盾阵在当前 tick 交给单名盾卫的协同职责。
 *
 * <p>{@link #GUARD} 成员持续维持正面防线，{@link #STRIKE} 成员短暂放盾出手；
 * 非盾阵成员保持 {@link #NONE}，继续使用原本的单兵盾牌博弈状态机。</p>
 */
public enum SquadShieldOrder {
	NONE,
	GUARD,
	STRIKE
}

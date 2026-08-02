package com.wjz.mobsthinknow.ai.zombie;

/**
 * 普通僵尸的短时身体语言。
 *
 * <p>这里只同步动作编号和开始 tick，不同步逐帧骨骼角度。客户端用同一套确定性关键帧采样器
 * 还原动作，因此一次状态变化只产生一次实体数据更新，不会为附近每只僵尸每 tick 发包。</p>
 */
public enum ZombieBodyAction {
	NONE(0, 0, 0),
	ACKNOWLEDGE(1, 12, 10),
	COMMAND(2, 18, 20),
	WAR_CRY(3, 20, 30),
	RETREAT(4, -1, 50),
	/** 剑士只做动作、不结算伤害的假挥前摇，用来试探正在格挡的目标。 */
	SWORD_FEINT(5, 18, 33),
	/** 斧手起跳前明确下蹲、把斧举过头顶，给玩家留下可读的闪避窗口。 */
	AXE_WINDUP(6, 8, 34),
	/** 从双脚离地到命中或落地之间保持的空中跳劈姿态。 */
	AXE_LEAP(7, -1, 36),
	/** 盾卫放下防御后，用副手盾牌向前撞击。 */
	SHIELD_BASH(8, 14, 38),
	/** 工程兵放置并点燃 TNT 时，由技能状态机显式持有的蹲姿。 */
	ENGINEER_WORK(9, -1, 32);

	private final byte id;
	private final int durationTicks;
	private final int priority;

	ZombieBodyAction(final int id, final int durationTicks, final int priority) {
		this.id = (byte)id;
		this.durationTicks = durationTicks;
		this.priority = priority;
	}

	public byte id() {
		return this.id;
	}

	public int durationTicks() {
		return this.durationTicks;
	}

	public int priority() {
		return this.priority;
	}

	public boolean isTransient() {
		return this.durationTicks > 0;
	}

	public boolean isActiveAt(final float elapsedTicks) {
		return this != NONE && (!this.isTransient() || elapsedTicks < this.durationTicks);
	}

	public static ZombieBodyAction fromId(final int id) {
		for (ZombieBodyAction action : values()) {
			if (action.id == id) {
				return action;
			}
		}
		return NONE;
	}
}

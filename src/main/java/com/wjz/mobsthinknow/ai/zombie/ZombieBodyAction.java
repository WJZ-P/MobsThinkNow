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
	ENGINEER_WORK(9, -1, 32),
	/** 首领抬高手臂反复招手，把散开的成员召向集结点。 */
	CALL_TO_MEETING(10, 24, 20),
	/** 首领在开会前依次巡视左右成员，确认队伍已经到齐。 */
	SURVEY_MEMBERS(11, 12, 20),
	/** 首领明确指向左翼阵位。 */
	COMMAND_LEFT(12, 18, 20),
	/** 首领明确指向右翼阵位。 */
	COMMAND_RIGHT(13, 18, 20),
	/** 成员连续点头，表示理解当前命令。 */
	NOD(14, 12, 10),
	/** 成员左右摇头，像是在报告路线或位置存在问题。 */
	SHAKE_HEAD(15, 16, 10),
	/** 成员侧身做一次短促手势，像在与身旁同伴交换意见。 */
	CONFER(16, 18, 9),
	/** 部署开始时，首领先举臂蓄势，再向目标方向猛然挥下。 */
	ADVANCE_ORDER(17, 18, 22);

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

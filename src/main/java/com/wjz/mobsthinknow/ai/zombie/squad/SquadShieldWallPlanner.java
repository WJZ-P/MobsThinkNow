package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 盾阵的纯数学规划器：把稳定排序后的盾卫编号映射为多排阵位和轮换职责。
 *
 * <p>每排最多五只，既避免旧版所有盾卫堆在同一个坐标，也限制大队伍的横向宽度。
 * 进攻窗口每 16 tick 只放行一名盾卫，其余成员继续举盾，因此玩家能清楚看到
 * “盾墙开一道缝、攻击、再合拢”的轮换节奏。</p>
 */
public final class SquadShieldWallPlanner {
	static final int MAXIMUM_COLUMNS = 5;
	static final int ROTATION_TICKS = 16;
	static final double LATERAL_SPACING = 1.25;
	static final double ROW_SPACING = 1.15;

	private SquadShieldWallPlanner() {
	}

	public static boolean supports(final SquadAssaultPlan plan) {
		return plan == SquadAssaultPlan.SHIELD_WEDGE || plan == SquadAssaultPlan.COMBINED_ARMS;
	}

	/** 返回相对于盾阵中心的横向和纵深偏移；rank 必须来自同一份稳定成员顺序。 */
	public static Slot slotFor(final int rank, final int memberCount) {
		if (memberCount <= 0 || rank < 0 || rank >= memberCount) {
			throw new IllegalArgumentException("Shield-wall rank must reference an existing member");
		}
		int row = rank / MAXIMUM_COLUMNS;
		int column = rank % MAXIMUM_COLUMNS;
		int rowStart = row * MAXIMUM_COLUMNS;
		int rowCount = Math.min(MAXIMUM_COLUMNS, memberCount - rowStart);
		double lateral = (column - (rowCount - 1) * 0.5) * LATERAL_SPACING;
		return new Slot(lateral, row * ROW_SPACING);
	}

	/**
	 * 返回当前唯一可以放盾出击的 rank。准备、压制和重整阶段没有出击者。
	 * COMMIT 与 EXPLOIT 共享连续攻击时间，跨节拍边界不会无故跳号。
	 */
	public static int strikerRank(
		final SquadCombatBeat beat,
		final long combatCycle,
		final long elapsedInBeat,
		final int memberCount
	) {
		if (memberCount < 2 || !beat.allowsMeleeAttack()) {
			return -1;
		}
		long clampedElapsed = Math.max(0L, elapsedInBeat);
		long attackElapsed = beat == SquadCombatBeat.EXPLOIT
			? SquadCombatCadence.COMMIT_TICKS + clampedElapsed
			: clampedElapsed;
		long rotation = Math.floorDiv(attackElapsed, ROTATION_TICKS);
		return Math.floorMod(combatCycle + rotation, memberCount);
	}

	public static SquadShieldOrder orderFor(final int rank, final int strikerRank, final int memberCount) {
		if (memberCount < 2 || rank < 0 || rank >= memberCount) {
			return SquadShieldOrder.NONE;
		}
		return rank == strikerRank ? SquadShieldOrder.STRIKE : SquadShieldOrder.GUARD;
	}

	public record Slot(double lateralOffset, double depthOffset) {
	}
}

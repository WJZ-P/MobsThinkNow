package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 把一个共享执行 tick 映射为确定性的战斗节拍，不读取世界或实体。
 *
 * <p>首次部署先留出约一秒半给射手装填和成员观察首领；进入交战后循环执行
 * 突击、追击、重整、准备、压制。所有成员读取同一个时间轴，因此不会因各自 Goal 的启动 tick
 * 不同而逐个零散冲锋。</p>
 */
public final class SquadCombatCadence {
	public static final int SUPPRESS_TICKS = 8;
	public static final int COMMIT_TICKS = 24;
	public static final int EXPLOIT_TICKS = 40;
	public static final int RESET_TICKS = 16;
	public static final int PREPARE_TICKS = 24;
	public static final int CYCLE_TICKS = COMMIT_TICKS
		+ EXPLOIT_TICKS
		+ RESET_TICKS
		+ PREPARE_TICKS
		+ SUPPRESS_TICKS;
	private static final int MINIMUM_INITIAL_COMMIT_DELAY_TICKS = 28;
	private static final int INITIAL_COMMIT_DELAY_VARIANCE_TICKS = 5;
	private static final int FORCED_COMMIT_DELAY_TICKS = 4;

	private SquadCombatCadence() {
	}

	/** 高智力首领少等待几 tick，但仍给弓手留下完整拉弓时间；抖动只由小队 ID 决定。 */
	public static int initialCommitDelay(final int leaderIntelligence, final long squadId) {
		int intelligence = Math.clamp(leaderIntelligence, 1, 10);
		int coordinationBonus = (intelligence - 1) / 3;
		int stableJitter = Math.floorMod(Long.hashCode(squadId * 0x9E3779B97F4A7C15L), INITIAL_COMMIT_DELAY_VARIANCE_TICKS);
		return MINIMUM_INITIAL_COMMIT_DELAY_TICKS + stableJitter - coordinationBonus;
	}

	/** 部署超时后仍保留一个短促、可观察的口令间隙，而不是无限等待卡住的成员。 */
	public static int forcedCommitDelay() {
		return FORCED_COMMIT_DELAY_TICKS;
	}

	public static Window waiting() {
		return new Window(SquadCombatBeat.PREPARE, 0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L);
	}

	/** 首次总攻尚未执行时，在最后八 tick 切到远程压制窗口。 */
	public static Window deploymentWindow(final long armedAt, final long commitAt, final long now) {
		if (armedAt < 0L || commitAt < armedAt || now < armedAt) {
			return waiting();
		}
		long suppressAt = Math.max(armedAt, commitAt - SUPPRESS_TICKS);
		if (now >= suppressAt) {
			return new Window(SquadCombatBeat.SUPPRESS, suppressAt, commitAt, commitAt, 0L);
		}
		return new Window(SquadCombatBeat.PREPARE, armedAt, suppressAt, commitAt, 0L);
	}

	/** 交战从 COMMIT 开始；后续每个周期都在 SUPPRESS 结束的下一 tick 重新同步突击。 */
	public static Window combatWindow(final long firstCommitAt, final long now) {
		if (firstCommitAt < 0L || now < firstCommitAt) {
			return waiting();
		}

		long elapsed = now - firstCommitAt;
		long cycle = elapsed / CYCLE_TICKS;
		long cycleStart = firstCommitAt + cycle * CYCLE_TICKS;
		long offset = elapsed % CYCLE_TICKS;
		long commitEndsAt = cycleStart + COMMIT_TICKS;
		long exploitEndsAt = commitEndsAt + EXPLOIT_TICKS;
		long resetEndsAt = exploitEndsAt + RESET_TICKS;
		long prepareEndsAt = resetEndsAt + PREPARE_TICKS;
		long nextCommitAt = cycleStart + CYCLE_TICKS;

		if (offset < COMMIT_TICKS) {
			return new Window(SquadCombatBeat.COMMIT, cycleStart, commitEndsAt, cycleStart, cycle);
		}
		if (offset < COMMIT_TICKS + EXPLOIT_TICKS) {
			return new Window(SquadCombatBeat.EXPLOIT, commitEndsAt, exploitEndsAt, cycleStart, cycle);
		}
		if (offset < COMMIT_TICKS + EXPLOIT_TICKS + RESET_TICKS) {
			return new Window(SquadCombatBeat.RESET, exploitEndsAt, resetEndsAt, nextCommitAt, cycle + 1L);
		}
		if (offset < COMMIT_TICKS + EXPLOIT_TICKS + RESET_TICKS + PREPARE_TICKS) {
			return new Window(SquadCombatBeat.PREPARE, resetEndsAt, prepareEndsAt, nextCommitAt, cycle + 1L);
		}
		return new Window(SquadCombatBeat.SUPPRESS, prepareEndsAt, nextCommitAt, nextCommitAt, cycle + 1L);
	}

	public record Window(
		SquadCombatBeat beat,
		long startedAt,
		long endsAt,
		long executeAt,
		long cycle
	) {
	}
}

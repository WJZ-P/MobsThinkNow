package com.wjz.mobsthinknow.ai.zombie.squad;

import org.jspecify.annotations.Nullable;

/**
 * 把“目标踩中本队临时蛛网”映射成一次短促、可反制的齐射与冲锋窗口。
 * 纯时间逻辑不读取实体，便于穷举挣脱边界并保证不会永久覆盖正常战斗节拍。
 */
public final class SquadWebAmbushTiming {
	public static final int SUPPRESS_TICKS = 6;
	public static final int COMMIT_TICKS = 24;
	public static final int PRECOMMIT_ESCAPE_GRACE_TICKS = 2;
	public static final int POSTCOMMIT_ESCAPE_GRACE_TICKS = 8;

	private SquadWebAmbushTiming() {
	}

	/**
	 * @return 当前伏击窗口；目标过早脱困或硬时限结束时返回 {@code null}
	 */
	public static SquadCombatCadence.@Nullable Window window(
		final long startedAt,
		final long lastConfirmedInWebAt,
		final long now,
		final long combatCycle
	) {
		if (startedAt < 0L || lastConfirmedInWebAt < startedAt || now < startedAt) {
			return null;
		}
		long commitAt = startedAt + SUPPRESS_TICKS;
		long hardEndsAt = commitAt + COMMIT_TICKS;
		if (now < commitAt) {
			if (now - lastConfirmedInWebAt > PRECOMMIT_ESCAPE_GRACE_TICKS) {
				return null;
			}
			return new SquadCombatCadence.Window(
				SquadCombatBeat.SUPPRESS,
				startedAt,
				commitAt,
				commitAt,
				combatCycle
			);
		}

		// 口令执行前至少在最近两 tick 内仍被控住；否则玩家成功挣脱应当取消冲锋。
		if (lastConfirmedInWebAt < commitAt - PRECOMMIT_ESCAPE_GRACE_TICKS) {
			return null;
		}
		long escapeEndsAt = lastConfirmedInWebAt + POSTCOMMIT_ESCAPE_GRACE_TICKS + 1L;
		long effectiveEndsAt = Math.min(hardEndsAt, escapeEndsAt);
		if (now >= effectiveEndsAt) {
			return null;
		}
		return new SquadCombatCadence.Window(
			SquadCombatBeat.COMMIT,
			commitAt,
			effectiveEndsAt,
			commitAt,
			combatCycle
		);
	}
}

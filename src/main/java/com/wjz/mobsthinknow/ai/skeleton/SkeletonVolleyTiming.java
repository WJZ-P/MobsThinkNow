package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadCombatBeat;
import java.util.UUID;

/** 把共享总攻 tick 确定性地摊成四拍短齐射；不读取实体，便于穷举边界与多人服复现。 */
public final class SkeletonVolleyTiming {
	private static final int VOLLEY_SPREAD_TICKS = 4;

	private SkeletonVolleyTiming() {
	}

	public static long stableShooterKey(final UUID uuid) {
		return uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 17);
	}

	/** 每名射手稳定落在 {@code executeAt - 3 ... executeAt}，避免同 tick 生成弹射物尖峰。 */
	public static long releaseAt(final long executeAt, final long stableShooterKey) {
		if (executeAt == Long.MAX_VALUE) {
			return Long.MAX_VALUE;
		}
		long mixed = stableShooterKey ^ Long.rotateLeft(stableShooterKey, 21) ^ 0x9E3779B97F4A7C15L;
		int lane = Math.floorMod(Long.hashCode(mixed), VOLLEY_SPREAD_TICKS);
		return executeAt - (VOLLEY_SPREAD_TICKS - 1L) + lane;
	}

	public static boolean mayRelease(
		final SquadCombatBeat beat,
		final long executeAt,
		final long now,
		final long stableShooterKey,
		final boolean urgent
	) {
		if (urgent) {
			return true;
		}
		return switch (beat) {
			case COMMIT, EXPLOIT -> true;
			case SUPPRESS -> now >= releaseAt(executeAt, stableShooterKey) && now <= executeAt;
			case PREPARE, RESET -> false;
		};
	}
}

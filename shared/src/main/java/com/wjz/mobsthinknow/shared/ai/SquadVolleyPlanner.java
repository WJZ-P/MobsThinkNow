package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.squad.MixedSquadRole;
import java.util.Objects;

/** 让左右射手使用错开的确定性射击时隙，而不是同一 tick 一起放箭。 */
public final class SquadVolleyPlanner {
	private SquadVolleyPlanner() {
	}

	public static int shotIntervalTicks(final int configuredMinimum, final int intelligence) {
		int minimum = Math.clamp(configuredMinimum, 20, 80);
		return Math.max(minimum, 52 - IntelligenceDistribution.clamp(intelligence) * 2);
	}

	public static int chargeTicks(final int configuredMaximum, final int intelligence) {
		int maximum = Math.clamp(configuredMaximum, 8, 30);
		return Math.max(8, maximum - (IntelligenceDistribution.clamp(intelligence) - 1) / 3);
	}

	public static int releaseOffset(
		final MixedSquadRole role,
		final int stableOrder,
		final int intervalTicks
	) {
		Objects.requireNonNull(role, "role");
		int interval = Math.max(2, intervalTicks);
		int wingBase = role == MixedSquadRole.RANGED_RIGHT ? interval / 2 : 0;
		int jitterWindow = Math.max(1, interval / 8);
		return Math.floorMod(wingBase + Math.floorMod(stableOrder, jitterWindow), interval);
	}

	/** 返回不早于 now 的下一个绝对释放 tick。 */
	public static long nextReleaseTick(
		final long now,
		final MixedSquadRole role,
		final int stableOrder,
		final int intervalTicks
	) {
		int interval = Math.max(2, intervalTicks);
		int offset = releaseOffset(role, stableOrder, interval);
		long cycle = Math.floorDiv(now, interval);
		long candidate = cycle * interval + offset;
		return candidate >= now ? candidate : candidate + interval;
	}
}

package com.wjz.mobsthinknow.shared.ai;

/** Fabric 与 Paper 共用的盾卫观察、格挡反击延迟和随机窗口判定。 */
public final class ShieldCombatPlanner {
	private ShieldCombatPlanner() {
	}

	public static int guardDurationTicks(final int minimum, final int maximum, final int zeroBasedRoll) {
		return inclusiveDuration(minimum, maximum, zeroBasedRoll, "guard");
	}

	public static int counterDelayTicks(final int minimum, final int maximum, final int zeroBasedRoll) {
		return inclusiveDuration(minimum, maximum, zeroBasedRoll, "counter");
	}

	/** 武器冷却拥有最终否决权；有真实格挡时等待反击 tick，否则等待观察窗口结束。 */
	public static boolean shouldOpenStrike(
		final boolean counterPending,
		final long now,
		final long counterStrikeAt,
		final long guardDeadline,
		final boolean attackReady
	) {
		if (!attackReady) {
			return false;
		}
		return counterPending ? now >= counterStrikeAt : now >= guardDeadline;
	}

	public static boolean isFreshSignal(final long now, final long signalTime, final long maximumAgeTicks) {
		if (maximumAgeTicks < 0L) {
			return false;
		}
		long age = now - signalTime;
		return age >= 0L && age <= maximumAgeTicks;
	}

	/** 使用水平视线与“防御者到伤害源”方向的归一化点积判定攻击是否落在盾牌正面。 */
	public static boolean canGuardDirection(
		final double facingX,
		final double facingZ,
		final double sourceX,
		final double sourceZ,
		final double minimumDot
	) {
		if (!Double.isFinite(facingX)
			|| !Double.isFinite(facingZ)
			|| !Double.isFinite(sourceX)
			|| !Double.isFinite(sourceZ)
			|| !Double.isFinite(minimumDot)
			|| minimumDot < -1.0
			|| minimumDot > 1.0) {
			return false;
		}
		double facingLengthSquared = facingX * facingX + facingZ * facingZ;
		double sourceLengthSquared = sourceX * sourceX + sourceZ * sourceZ;
		if (facingLengthSquared <= 1.0E-12 || sourceLengthSquared <= 1.0E-12) {
			return false;
		}
		double dot = (facingX * sourceX + facingZ * sourceZ)
			/ Math.sqrt(facingLengthSquared * sourceLengthSquared);
		return dot >= minimumDot;
	}

	public static long disabledUntil(final long now, final long durationTicks) {
		if (durationTicks < 0L) {
			throw new IllegalArgumentException("shield disable duration must be non-negative");
		}
		if (Long.MAX_VALUE - now < durationTicks) {
			return Long.MAX_VALUE;
		}
		return now + durationTicks;
	}

	public static boolean isDisabled(final long now, final long disabledUntil) {
		return now < disabledUntil;
	}

	public static boolean shouldScheduleBash(
		final boolean enabled,
		final int intelligence,
		final int minimumIntelligence,
		final double randomRoll,
		final double chance
	) {
		return enabled
			&& intelligence >= minimumIntelligence
			&& Double.isFinite(randomRoll)
			&& Double.isFinite(chance)
			&& randomRoll >= 0.0
			&& randomRoll < chance;
	}

	private static int inclusiveDuration(
		final int minimum,
		final int maximum,
		final int zeroBasedRoll,
		final String label
	) {
		if (minimum < 0 || maximum < minimum || zeroBasedRoll < 0 || zeroBasedRoll > maximum - minimum) {
			throw new IllegalArgumentException(label + " duration roll is outside the configured range");
		}
		return minimum + zeroBasedRoll;
	}
}

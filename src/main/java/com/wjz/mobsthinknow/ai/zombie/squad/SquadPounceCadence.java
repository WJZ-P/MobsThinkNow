package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 单支混编小队的蜘蛛跳扑令牌。
 *
 * <p>协调器只持有一个定长状态，不扫描同伴；取得令牌、释放令牌和超时恢复均为 O(1)。
 * {@code nextAvailableAt} 与当前空中所有者分离：前者确保两次起跳至少错开若干 tick，后者确保
 * 前一只蜘蛛仍在空中时不会放行下一只。异常未调用 {@code stop} 时，短租约也会自行失效。</p>
 */
final class SquadPounceCadence {
	private int ownerId;
	private int targetId;
	private long reservationEndsAt = Long.MIN_VALUE;
	private long nextAvailableAt = Long.MIN_VALUE;

	public boolean canReserve(final int candidateId, final int requestedTargetId, final long now) {
		this.expire(now);
		if (candidateId <= 0 || requestedTargetId <= 0) {
			return false;
		}
		return (this.ownerId == candidateId && this.targetId == requestedTargetId)
			|| (this.ownerId == 0 && now >= this.nextAvailableAt);
	}

	public boolean tryReserve(
		final int candidateId,
		final int requestedTargetId,
		final long now,
		final int minimumIntervalTicks,
		final int maximumAirborneTicks
	) {
		if (!this.canReserve(candidateId, requestedTargetId, now)) {
			return false;
		}
		if (this.ownerId == candidateId && this.targetId == requestedTargetId) {
			return true;
		}
		this.ownerId = candidateId;
		this.targetId = requestedTargetId;
		this.reservationEndsAt = saturatingAdd(now, Math.max(1, maximumAirborneTicks));
		this.nextAvailableAt = saturatingAdd(now, Math.max(1, minimumIntervalTicks));
		return true;
	}

	/** 释放空中所有权，但保留已经写入的最小起跳间隔。 */
	public void release(final int candidateId) {
		if (this.ownerId != candidateId) {
			return;
		}
		this.ownerId = 0;
		this.targetId = 0;
		this.reservationEndsAt = Long.MIN_VALUE;
	}

	public boolean isActive(final long now) {
		this.expire(now);
		return this.ownerId != 0;
	}

	public int ownerId(final long now) {
		this.expire(now);
		return this.ownerId;
	}

	public long nextAvailableAt() {
		return this.nextAvailableAt;
	}

	private void expire(final long now) {
		if (this.ownerId != 0 && now >= this.reservationEndsAt) {
			this.ownerId = 0;
			this.targetId = 0;
			this.reservationEndsAt = Long.MIN_VALUE;
		}
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}
}

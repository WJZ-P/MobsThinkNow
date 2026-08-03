package com.wjz.mobsthinknow.ai.zombie.squad;

import net.minecraft.world.phys.Vec3;

/** 把同一命令的偶发寻路失败折叠成一次有冷却的战中改令请求。 */
public final class SquadRouteFailureTracker {
	private static final int REQUIRED_CONSECUTIVE_FAILURES = 2;
	private static final long SAME_FAILURE_WINDOW_TICKS = 24L;
	private static final double SAME_DESTINATION_DISTANCE_SQUARED = 1.0;

	private int planEpoch = Integer.MIN_VALUE;
	private Vec3 destination = Vec3.ZERO;
	private int consecutiveFailures;
	private long lastFailureAt = Long.MIN_VALUE;
	private long nextReplanAt = Long.MIN_VALUE;

	public Decision recordFailure(
		final int observedPlanEpoch,
		final Vec3 failedDestination,
		final long now,
		final int cooldownTicks
	) {
		if (now < this.nextReplanAt) {
			return Decision.COOLDOWN;
		}
		boolean sameCommand = observedPlanEpoch == this.planEpoch
			&& failedDestination.distanceToSqr(this.destination) <= SAME_DESTINATION_DISTANCE_SQUARED
			&& now - this.lastFailureAt <= SAME_FAILURE_WINDOW_TICKS;
		if (!sameCommand) {
			this.planEpoch = observedPlanEpoch;
			this.destination = failedDestination;
			this.consecutiveFailures = 1;
			this.lastFailureAt = now;
			return Decision.WAITING_FOR_CONFIRMATION;
		}

		this.lastFailureAt = now;
		if (++this.consecutiveFailures < REQUIRED_CONSECUTIVE_FAILURES) {
			return Decision.WAITING_FOR_CONFIRMATION;
		}
		this.consecutiveFailures = 0;
		this.nextReplanAt = now + Math.max(1, cooldownTicks);
		return Decision.REPLAN;
	}

	public void reset() {
		this.planEpoch = Integer.MIN_VALUE;
		this.consecutiveFailures = 0;
		this.lastFailureAt = Long.MIN_VALUE;
	}

	public enum Decision {
		WAITING_FOR_CONFIRMATION,
		COOLDOWN,
		REPLAN
	}
}

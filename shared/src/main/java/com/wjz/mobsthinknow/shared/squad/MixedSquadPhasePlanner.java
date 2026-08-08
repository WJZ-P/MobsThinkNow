package com.wjz.mobsthinknow.shared.squad;

/** 阶段转换只读取快照，使 Paper/Fabric 的会议节奏可以独立回归。 */
public final class MixedSquadPhasePlanner {
	private MixedSquadPhasePlanner() {
	}

	public static MixedSquadState next(
		final MixedSquadState current,
		final long elapsedTicks,
		final boolean quorumReached,
		final boolean emergency,
		final boolean leaderChanged,
		final Timings timings
	) {
		if (leaderChanged) {
			return MixedSquadState.REORGANIZING;
		}
		if (emergency) {
			return MixedSquadState.ENGAGING;
		}
		long elapsed = Math.max(0L, elapsedTicks);
		return switch (current) {
			case FORMING -> quorumReached || elapsed >= timings.formingTimeoutTicks()
				? MixedSquadState.BRIEFING : current;
			case BRIEFING -> elapsed >= timings.briefingTicks() ? MixedSquadState.DEPLOYING : current;
			case DEPLOYING -> quorumReached || elapsed >= timings.deploymentTimeoutTicks()
				? MixedSquadState.ENGAGING : current;
			case REORGANIZING -> elapsed >= timings.reorganizingTicks() ? MixedSquadState.FORMING : current;
			case ENGAGING -> current;
		};
	}

	public record Timings(
		int formingTimeoutTicks,
		int briefingTicks,
		int deploymentTimeoutTicks,
		int reorganizingTicks
	) {
		public Timings {
			formingTimeoutTicks = Math.max(1, formingTimeoutTicks);
			briefingTicks = Math.max(1, briefingTicks);
			deploymentTimeoutTicks = Math.max(1, deploymentTimeoutTicks);
			reorganizingTicks = Math.max(1, reorganizingTicks);
		}
	}
}

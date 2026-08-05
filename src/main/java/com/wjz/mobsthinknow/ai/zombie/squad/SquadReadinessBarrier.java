package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.List;

/** 把位置到达和职业准备合并成统一部署屏障；纯数据实现便于穷举边界测试。 */
public final class SquadReadinessBarrier {
	private SquadReadinessBarrier() {
	}

	public static Result evaluate(final List<MemberStatus> members, final double requiredFraction) {
		double quorum = Double.isFinite(requiredFraction)
			? Math.clamp(requiredFraction, 0.0, 1.0)
			: 1.0;
		int assigned = 0;
		int arrived = 0;
		int ready = 0;
		for (MemberStatus member : members) {
			if (!member.hasAssignedPosition()) {
				continue;
			}
			assigned++;
			if (member.arrived()) {
				arrived++;
				if (member.roleReady()) {
					ready++;
				}
			}
		}

		int required = assigned == 0 ? 0 : Math.max(1, (int)Math.ceil(assigned * quorum));
		return new Result(assigned, arrived, ready, required, assigned > 0 && ready >= required);
	}

	public record MemberStatus(boolean hasAssignedPosition, boolean arrived, boolean roleReady) {
	}

	public record Result(int assigned, int arrived, int ready, int required, boolean canCommit) {
		public double readyFraction() {
			return this.assigned == 0 ? 0.0 : this.ready / (double)this.assigned;
		}
	}
}

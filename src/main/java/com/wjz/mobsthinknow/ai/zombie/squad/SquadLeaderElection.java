package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.Collection;
import java.util.List;
import java.util.OptionalInt;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlanner;
import com.wjz.mobsthinknow.shared.squad.MixedSquadSpecies;

/** 跨物种首领选举：只比较智力；并列最高智力者再按出生时已经随机化的 UUID 票抽签。 */
public final class SquadLeaderElection {
	private SquadLeaderElection() {
	}

	public static OptionalInt elect(final Collection<SquadLeaderCandidate> candidates) {
		List<MixedSquadPlanner.Member<Integer>> snapshots = candidates.stream()
			.map(candidate -> new MixedSquadPlanner.Member<>(
				candidate.entityId(),
				candidate.intelligence(),
				candidate.randomTicket(),
				candidate.entityId(),
				MixedSquadSpecies.ZOMBIE,
				false,
				false
			))
			.toList();
		return MixedSquadPlanner.electLeader(snapshots)
			.map(OptionalInt::of)
			.orElseGet(OptionalInt::empty);
	}
}

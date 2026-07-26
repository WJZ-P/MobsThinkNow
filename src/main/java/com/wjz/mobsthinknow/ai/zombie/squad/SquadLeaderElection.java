package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.Collection;
import java.util.OptionalInt;

/** 确定性首领选举：智力优先，其次当前生命值，最后选择实体 ID 更小者。 */
public final class SquadLeaderElection {
	private SquadLeaderElection() {
	}

	public static OptionalInt elect(final Collection<SquadLeaderCandidate> candidates) {
		SquadLeaderCandidate winner = null;
		for (SquadLeaderCandidate candidate : candidates) {
			if (winner == null || isBetter(candidate, winner)) {
				winner = candidate;
			}
		}

		return winner == null ? OptionalInt.empty() : OptionalInt.of(winner.entityId());
	}

	private static boolean isBetter(final SquadLeaderCandidate challenger, final SquadLeaderCandidate incumbent) {
		if (challenger.intelligence() != incumbent.intelligence()) {
			return challenger.intelligence() > incumbent.intelligence();
		}
		if (Float.compare(challenger.health(), incumbent.health()) != 0) {
			return challenger.health() > incumbent.health();
		}
		return challenger.entityId() < incumbent.entityId();
	}
}

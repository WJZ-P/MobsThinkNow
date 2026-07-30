package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.Collection;
import java.util.OptionalInt;

/** 跨物种首领选举：只比较智力；并列最高智力者再按出生时已经随机化的 UUID 票抽签。 */
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
		int ticketComparison = Long.compareUnsigned(challenger.randomTicket(), incumbent.randomTicket());
		if (ticketComparison != 0) {
			return ticketComparison < 0;
		}
		return challenger.entityId() < incumbent.entityId();
	}
}

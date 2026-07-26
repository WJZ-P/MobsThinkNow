package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把首领智力转化成可见的战术复杂度。低智力首领会倾向于一拥而上；更聪明的首领才会逐步
 * 解锁单侧包抄、双侧包抄和截断退路。
 */
public final class SquadRolePlanner {
	private SquadRolePlanner() {
	}

	public static Map<Integer, SquadRole> plan(
		final List<Integer> orderedMemberIds,
		final int leaderId,
		final int leaderIntelligence
	) {
		Map<Integer, SquadRole> roles = new LinkedHashMap<>();
		roles.put(leaderId, SquadRole.LEADER);

		int tacticalIndex = 0;
		for (int memberId : orderedMemberIds) {
			if (memberId == leaderId) {
				continue;
			}

			roles.put(memberId, roleFor(tacticalIndex, leaderIntelligence));
			tacticalIndex++;
		}
		return roles;
	}

	private static SquadRole roleFor(final int index, final int intelligence) {
		if (intelligence <= 3 || index == 0) {
			return SquadRole.PRESSURER;
		}
		if (index == 1 && intelligence >= 4) {
			return SquadRole.FLANK_LEFT;
		}
		if (index == 2 && intelligence >= 7) {
			return SquadRole.FLANK_RIGHT;
		}
		if (index == 3 && intelligence >= 9) {
			return SquadRole.CUTOFF;
		}

		if (intelligence >= 7) {
			return (index & 1) == 0 ? SquadRole.FLANK_RIGHT : SquadRole.FLANK_LEFT;
		}
		return SquadRole.PRESSURER;
	}
}

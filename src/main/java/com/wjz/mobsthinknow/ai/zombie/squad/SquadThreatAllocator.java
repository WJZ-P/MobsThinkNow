package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 把有限的高可信次要威胁分给合适成员，同时保证至少 60% 成员继续压制主目标。 */
public final class SquadThreatAllocator {
	private static final double MINIMUM_SECONDARY_SCORE = 60.0;
	private static final double MINIMUM_PRIMARY_FRACTION = 0.60;

	private SquadThreatAllocator() {
	}

	public static Map<Integer, Integer> assign(
		final List<Member> members,
		final int primaryTargetId,
		final List<Threat> observedThreats
	) {
		Map<Integer, Integer> assignments = new LinkedHashMap<>();
		for (Member member : members) {
			assignments.put(member.entityId, primaryTargetId);
		}
		if (members.size() < 3) {
			return Map.copyOf(assignments);
		}

		List<Threat> secondary = observedThreats.stream()
			.filter(threat -> threat.entityId != primaryTargetId && threat.score >= MINIMUM_SECONDARY_SCORE)
			.sorted(Comparator.comparingDouble(Threat::score).reversed().thenComparingInt(Threat::entityId))
			.limit(2)
			.toList();
		if (secondary.isEmpty()) {
			return Map.copyOf(assignments);
		}

		int minimumPrimary = Math.max(2, (int)Math.ceil(members.size() * MINIMUM_PRIMARY_FRACTION));
		int responseBudget = members.size() - minimumPrimary;
		if (responseBudget <= 0) {
			return Map.copyOf(assignments);
		}

		List<Member> eligible = new ArrayList<>();
		for (Member member : members) {
			if (member.eligibleForSecondary && responseAffinity(member.role) > 0) {
				eligible.add(member);
			}
		}
		for (int slot = 0; slot < responseBudget && !eligible.isEmpty(); slot++) {
			Threat threat = secondary.get(slot % secondary.size());
			Member selected = eligible.stream()
				.min(Comparator
					.comparingInt((Member member) -> -responseAffinity(member.role))
					.thenComparingDouble(member -> member.distanceSquaredTo(threat.entityId))
					.thenComparingInt(Member::entityId))
				.orElse(null);
			if (selected == null) {
				break;
			}
			assignments.put(selected.entityId, threat.entityId);
			eligible.remove(selected);
		}
		return Map.copyOf(assignments);
	}

	private static int responseAffinity(final SquadRole role) {
		return switch (role) {
			case CUTOFF -> 5;
			case PRESSURER -> 4;
			case RANGED -> 3;
			case FLANK_LEFT, FLANK_RIGHT -> 2;
			case BREACHER -> 1;
			case LEADER, SUPPORT, CARRIER -> 0;
		};
	}

	public record Member(
		int entityId,
		SquadRole role,
		boolean eligibleForSecondary,
		Map<Integer, Double> targetDistancesSquared
	) {
		double distanceSquaredTo(final int targetId) {
			return this.targetDistancesSquared.getOrDefault(targetId, Double.POSITIVE_INFINITY);
		}
	}

	public record Threat(int entityId, double score) {
	}
}

package com.wjz.mobsthinknow.shared.squad;

import com.wjz.mobsthinknow.shared.ai.IntelligenceDistribution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 跨平台的蜘蛛运载配对器。它只处理稳定、互斥的一对一分配，不接触实体、寻路或骑乘 API。
 */
public final class MixedSquadTransportPlanner {
	private MixedSquadTransportPlanner() {
	}

	/**
	 * 为启用载具战术的阵容生成 carrier -> payload 映射。
	 * 显式 CARRIER/BREACHER 优先；若蜘蛛恰好当选首领，也能作为后备载具参与配对。
	 */
	public static <K> Map<K, K> pairCreeperCarriers(
		final MixedSquadPlan plan,
		final Collection<Member<K>> members
	) {
		Objects.requireNonNull(plan, "plan");
		Objects.requireNonNull(members, "members");
		if (!plan.usesCarrier()) {
			return Map.of();
		}

		List<Member<K>> carriers = new ArrayList<>();
		List<Member<K>> payloads = new ArrayList<>();
		for (Member<K> member : members) {
			if (!member.available()) {
				continue;
			}
			if (member.species() == MixedSquadSpecies.SPIDER) {
				carriers.add(member);
			} else if (member.species() == MixedSquadSpecies.CREEPER) {
				payloads.add(member);
			}
		}
		carriers.sort(carrierOrder());
		payloads.sort(payloadOrder());

		int pairCount = Math.min(carriers.size(), payloads.size());
		Map<K, K> result = new LinkedHashMap<>(pairCount);
		for (int index = 0; index < pairCount; index++) {
			result.put(carriers.get(index).id(), payloads.get(index).id());
		}
		return Map.copyOf(result);
	}

	private static <K> Comparator<Member<K>> carrierOrder() {
		return Comparator.<Member<K>>comparingInt(member -> carrierPriority(member.role()))
			.thenComparing(Comparator.comparingInt(Member<K>::intelligence).reversed())
			.thenComparingInt(Member::stableOrder);
	}

	private static <K> Comparator<Member<K>> payloadOrder() {
		return Comparator.<Member<K>>comparingInt(member -> payloadPriority(member.role()))
			.thenComparing(Comparator.comparingInt(Member<K>::intelligence).reversed())
			.thenComparingInt(Member::stableOrder);
	}

	private static int carrierPriority(final MixedSquadRole role) {
		if (role == MixedSquadRole.CARRIER) {
			return 0;
		}
		if (role == MixedSquadRole.LEADER) {
			return 1;
		}
		return role.isFlanker() ? 2 : 3;
	}

	private static int payloadPriority(final MixedSquadRole role) {
		if (role == MixedSquadRole.BREACHER) {
			return 0;
		}
		return role == MixedSquadRole.LEADER ? 1 : 2;
	}

	public record Member<K>(
		K id,
		MixedSquadSpecies species,
		MixedSquadRole role,
		int intelligence,
		int stableOrder,
		boolean available
	) {
		public Member {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(species, "species");
			Objects.requireNonNull(role, "role");
			intelligence = IntelligenceDistribution.clamp(intelligence);
		}
	}
}

package com.wjz.mobsthinknow.shared.squad;

import com.wjz.mobsthinknow.shared.ai.IntelligenceDistribution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 纯 Java 的跨物种首领选举、阵容识别、方案选择与职责分配。 */
public final class MixedSquadPlanner {
	private MixedSquadPlanner() {
	}

	/** 只比较智力；并列时用实体固有随机票抽签，最后才使用稳定序号。 */
	public static <K> Optional<K> electLeader(final Collection<Member<K>> members) {
		return members.stream().min(
			Comparator.<Member<K>>comparingInt(Member::intelligence).reversed()
				.thenComparing((first, second) -> Long.compareUnsigned(first.electionTicket(), second.electionTicket()))
				.thenComparingInt(Member::stableOrder)
		).map(Member::id);
	}

	public static <K> Composition composition(final Collection<Member<K>> members) {
		int zombies = 0;
		int skeletons = 0;
		int creepers = 0;
		int spiders = 0;
		int shields = 0;
		int utilities = 0;
		for (Member<K> member : members) {
			switch (member.species()) {
				case ZOMBIE -> zombies++;
				case SKELETON -> skeletons++;
				case CREEPER -> creepers++;
				case SPIDER -> spiders++;
			}
			if (member.shield()) {
				shields++;
			}
			if (member.utility()) {
				utilities++;
			}
		}
		return new Composition(zombies, skeletons, creepers, spiders, shields, utilities);
	}

	public static MixedSquadPlan choosePlan(final Composition composition, final int leaderIntelligence) {
		int intelligence = IntelligenceDistribution.clamp(leaderIntelligence);
		if (intelligence >= 8 && composition.hasFourCoreSpecies()) {
			return MixedSquadPlan.COMBINED_ARMS;
		}
		if (intelligence >= 7 && composition.spiders() > 0 && composition.creepers() > 0) {
			return MixedSquadPlan.MOUNTED_BREACH;
		}
		if (intelligence >= 7 && composition.skeletons() >= 2 && composition.zombies() > 0) {
			return MixedSquadPlan.CROSSFIRE;
		}
		if (intelligence >= 6
			&& composition.shields() > 0
			&& (composition.skeletons() > 0 || composition.creepers() > 0)) {
			return MixedSquadPlan.SHIELD_WEDGE;
		}
		if (intelligence >= 5 && composition.skeletons() > 0 && composition.zombies() > 0) {
			return MixedSquadPlan.PIN_AND_FLANK;
		}
		return MixedSquadPlan.SWARM;
	}

	/**
	 * 专职兵种先领取固定职责，普通僵尸再按首领智力填充正面与两翼。返回顺序稳定，便于两端复现。
	 */
	public static <K> Map<K, MixedSquadRole> assignRoles(
		final Collection<Member<K>> members,
		final K leaderId,
		final MixedSquadPlan plan
	) {
		Objects.requireNonNull(leaderId, "leaderId");
		Objects.requireNonNull(plan, "plan");
		List<Member<K>> ordered = new ArrayList<>(members);
		ordered.sort(Comparator.comparingInt(Member::stableOrder));
		Member<K> leader = ordered.stream()
			.filter(member -> member.id().equals(leaderId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("leader must be a squad member"));
		Map<K, MixedSquadRole> roles = new LinkedHashMap<>();
		roles.put(leaderId, MixedSquadRole.LEADER);

		boolean carrierAssigned = false;
		int rangedIndex = 0;
		int flankIndex = 0;
		boolean frontlineAssigned = false;
		for (Member<K> member : ordered) {
			if (member.id().equals(leaderId)) {
				continue;
			}
			MixedSquadRole role;
			if (member.utility()) {
				role = MixedSquadRole.SUPPORT;
			} else if (member.species() == MixedSquadSpecies.SKELETON) {
				role = (rangedIndex++ & 1) == 0 ? MixedSquadRole.RANGED_LEFT : MixedSquadRole.RANGED_RIGHT;
			} else if (member.species() == MixedSquadSpecies.CREEPER) {
				role = MixedSquadRole.BREACHER;
			} else if (member.species() == MixedSquadSpecies.SPIDER) {
				if (plan.usesCarrier() && !carrierAssigned) {
					role = MixedSquadRole.CARRIER;
					carrierAssigned = true;
				} else {
					role = (flankIndex++ & 1) == 0 ? MixedSquadRole.FLANK_LEFT : MixedSquadRole.FLANK_RIGHT;
				}
			} else if (member.shield() || !frontlineAssigned || leader.intelligence() <= 3) {
				role = MixedSquadRole.FRONTLINE;
				frontlineAssigned = true;
			} else {
				role = (flankIndex++ & 1) == 0 ? MixedSquadRole.FLANK_LEFT : MixedSquadRole.FLANK_RIGHT;
			}
			roles.put(member.id(), role);
		}
		return Map.copyOf(roles);
	}

	public record Member<K>(
		K id,
		int intelligence,
		long electionTicket,
		int stableOrder,
		MixedSquadSpecies species,
		boolean shield,
		boolean utility
	) {
		public Member {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(species, "species");
			intelligence = IntelligenceDistribution.clamp(intelligence);
		}
	}

	public record Composition(
		int zombies,
		int skeletons,
		int creepers,
		int spiders,
		int shields,
		int utilities
	) {
		public Composition {
			if (zombies < 0 || skeletons < 0 || creepers < 0 || spiders < 0 || shields < 0 || utilities < 0) {
				throw new IllegalArgumentException("composition counts must be non-negative");
			}
		}

		public boolean hasFourCoreSpecies() {
			return this.zombies > 0 && this.skeletons > 0 && this.creepers > 0 && this.spiders > 0;
		}
	}
}

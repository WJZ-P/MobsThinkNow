package com.wjz.mobsthinknow.shared.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MixedSquadPlannerTest {
	@Test
	void highestIntelligenceWinsAndUnsignedTicketBreaksTies() {
		List<MixedSquadPlanner.Member<String>> members = List.of(
			member("zombie", 8, -1L, 0, MixedSquadSpecies.ZOMBIE, false, false),
			member("skeleton", 9, 20L, 1, MixedSquadSpecies.SKELETON, false, false),
			member("spider", 9, 5L, 2, MixedSquadSpecies.SPIDER, false, false)
		);
		assertEquals("spider", MixedSquadPlanner.electLeader(members).orElseThrow());
	}

	@Test
	void fullCoreCompositionUnlocksCombinedArmsAndDedicatedRoles() {
		List<MixedSquadPlanner.Member<String>> members = List.of(
			member("leader", 10, 0L, 0, MixedSquadSpecies.ZOMBIE, true, false),
			member("archer-a", 7, 1L, 1, MixedSquadSpecies.SKELETON, false, false),
			member("archer-b", 7, 2L, 2, MixedSquadSpecies.SKELETON, false, false),
			member("creeper", 6, 3L, 3, MixedSquadSpecies.CREEPER, false, false),
			member("spider", 8, 4L, 4, MixedSquadSpecies.SPIDER, false, false)
		);
		MixedSquadPlanner.Composition composition = MixedSquadPlanner.composition(members);
		assertTrue(composition.hasFourCoreSpecies());
		MixedSquadPlan plan = MixedSquadPlanner.choosePlan(composition, 10);
		assertEquals(MixedSquadPlan.COMBINED_ARMS, plan);
		Map<String, MixedSquadRole> roles = MixedSquadPlanner.assignRoles(members, "leader", plan);
		assertEquals(MixedSquadRole.RANGED_LEFT, roles.get("archer-a"));
		assertEquals(MixedSquadRole.RANGED_RIGHT, roles.get("archer-b"));
		assertEquals(MixedSquadRole.BREACHER, roles.get("creeper"));
		assertEquals(MixedSquadRole.CARRIER, roles.get("spider"));
	}

	@Test
	void lowIntelligenceHomogeneousGroupRemainsSwarm() {
		MixedSquadPlanner.Composition composition = new MixedSquadPlanner.Composition(5, 0, 0, 0, 0, 0);
		assertEquals(MixedSquadPlan.SWARM, MixedSquadPlanner.choosePlan(composition, 3));
	}

	private static MixedSquadPlanner.Member<String> member(
		final String id,
		final int intelligence,
		final long ticket,
		final int order,
		final MixedSquadSpecies species,
		final boolean shield,
		final boolean utility
	) {
		return new MixedSquadPlanner.Member<>(id, intelligence, ticket, order, species, shield, utility);
	}
}

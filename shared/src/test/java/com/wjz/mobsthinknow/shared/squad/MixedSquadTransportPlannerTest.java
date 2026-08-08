package com.wjz.mobsthinknow.shared.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MixedSquadTransportPlannerTest {
	@Test
	void doesNotPairWhenPlanHasNoCarrierTactic() {
		Map<String, String> result = MixedSquadTransportPlanner.pairCreeperCarriers(
			MixedSquadPlan.SWARM,
			List.of(spider("spider", MixedSquadRole.CARRIER, 10, 1, true), creeper("creeper", 10, 2, true))
		);
		assertTrue(result.isEmpty());
	}

	@Test
	void prefersExplicitCarrierAndBreacherRoles() {
		Map<String, String> result = MixedSquadTransportPlanner.pairCreeperCarriers(
			MixedSquadPlan.COMBINED_ARMS,
			List.of(
				spider("leader-spider", MixedSquadRole.LEADER, 10, 1, true),
				spider("carrier", MixedSquadRole.CARRIER, 6, 2, true),
				creeper("leader-creeper", MixedSquadRole.LEADER, 10, 3, true),
				creeper("breacher", MixedSquadRole.BREACHER, 5, 4, true)
			)
		);
		assertEquals(Map.of("carrier", "breacher", "leader-spider", "leader-creeper"), result);
	}

	@Test
	void leaderSpiderRemainsAUsableFallbackCarrier() {
		Map<String, String> result = MixedSquadTransportPlanner.pairCreeperCarriers(
			MixedSquadPlan.MOUNTED_BREACH,
			List.of(
				spider("leader", MixedSquadRole.LEADER, 9, 4, true),
				creeper("payload", 8, 2, true)
			)
		);
		assertEquals(Map.of("leader", "payload"), result);
	}

	@Test
	void unavailableMembersNeverConsumeAPair() {
		Map<String, String> result = MixedSquadTransportPlanner.pairCreeperCarriers(
			MixedSquadPlan.MOUNTED_BREACH,
			List.of(
				spider("busy", MixedSquadRole.CARRIER, 10, 1, false),
				spider("ready", MixedSquadRole.FLANK_LEFT, 5, 2, true),
				creeper("spent", 10, 3, false),
				creeper("ready-payload", 4, 4, true)
			)
		);
		assertEquals(Map.of("ready", "ready-payload"), result);
	}

	@Test
	void pairingIsOneToOneAndStable() {
		List<MixedSquadTransportPlanner.Member<String>> members = List.of(
			spider("s-low-order", MixedSquadRole.CARRIER, 8, 1, true),
			spider("s-high-order", MixedSquadRole.CARRIER, 8, 9, true),
			creeper("c-smart", 9, 7, true),
			creeper("c-less-smart", 5, 1, true),
			creeper("unused", 1, 2, true)
		);
		Map<String, String> first = MixedSquadTransportPlanner.pairCreeperCarriers(
			MixedSquadPlan.COMBINED_ARMS,
			members
		);
		Map<String, String> second = MixedSquadTransportPlanner.pairCreeperCarriers(
			MixedSquadPlan.COMBINED_ARMS,
			members.reversed()
		);
		assertEquals(Map.of("s-low-order", "c-smart", "s-high-order", "c-less-smart"), first);
		assertEquals(first, second);
	}

	private static MixedSquadTransportPlanner.Member<String> spider(
		final String id,
		final MixedSquadRole role,
		final int intelligence,
		final int order,
		final boolean available
	) {
		return new MixedSquadTransportPlanner.Member<>(
			id,
			MixedSquadSpecies.SPIDER,
			role,
			intelligence,
			order,
			available
		);
	}

	private static MixedSquadTransportPlanner.Member<String> creeper(
		final String id,
		final int intelligence,
		final int order,
		final boolean available
	) {
		return creeper(id, MixedSquadRole.BREACHER, intelligence, order, available);
	}

	private static MixedSquadTransportPlanner.Member<String> creeper(
		final String id,
		final MixedSquadRole role,
		final int intelligence,
		final int order,
		final boolean available
	) {
		return new MixedSquadTransportPlanner.Member<>(
			id,
			MixedSquadSpecies.CREEPER,
			role,
			intelligence,
			order,
			available
		);
	}
}

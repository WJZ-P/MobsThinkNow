package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SquadThreatAllocatorTest {
	@Test
	void preservesSixtyPercentOnPrimaryAndUsesRoleAffinity() {
		List<SquadThreatAllocator.Member> members = List.of(
			member(1, SquadRole.CUTOFF, 10, 1.0, 11, 9.0),
			member(2, SquadRole.PRESSURER, 10, 9.0, 11, 1.0),
			member(3, SquadRole.RANGED, 10, 2.0, 11, 2.0),
			member(4, SquadRole.LEADER, 10, 1.0, 11, 1.0),
			member(5, SquadRole.SUPPORT, 10, 1.0, 11, 1.0)
		);

		Map<Integer, Integer> assignments = SquadThreatAllocator.assign(
			members,
			99,
			List.of(
				new SquadThreatAllocator.Threat(10, 90.0),
				new SquadThreatAllocator.Threat(11, 80.0)
			)
		);

		assertEquals(10, assignments.get(1));
		assertEquals(11, assignments.get(2));
		assertEquals(3, assignments.values().stream().filter(target -> target == 99).count());
		assertEquals(99, assignments.get(4));
		assertEquals(99, assignments.get(5));
	}

	@Test
	void ignoresLowConfidenceThreatAndTinySquads() {
		List<SquadThreatAllocator.Member> members = List.of(
			member(1, SquadRole.CUTOFF, 10, 1.0),
			member(2, SquadRole.PRESSURER, 10, 2.0)
		);

		Map<Integer, Integer> assignments = SquadThreatAllocator.assign(
			members,
			99,
			List.of(new SquadThreatAllocator.Threat(10, 59.9))
		);
		assertEquals(Map.of(1, 99, 2, 99), assignments);
	}

	private static SquadThreatAllocator.Member member(
		final int id,
		final SquadRole role,
		final Object... targetDistancePairs
	) {
		Map<Integer, Double> distances = new java.util.HashMap<>();
		for (int index = 0; index < targetDistancePairs.length; index += 2) {
			distances.put((Integer)targetDistancePairs[index], (Double)targetDistancePairs[index + 1]);
		}
		return new SquadThreatAllocator.Member(id, role, true, Map.copyOf(distances));
	}
}

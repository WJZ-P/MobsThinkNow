package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SquadRolePlannerTest {
	@Test
	void lowIntelligenceLeaderOrdersAFrontalRush() {
		Map<Integer, SquadRole> roles = SquadRolePlanner.plan(List.of(1, 2, 3, 4), 1, 3);

		assertEquals(SquadRole.LEADER, roles.get(1));
		assertEquals(SquadRole.PRESSURER, roles.get(2));
		assertEquals(SquadRole.PRESSURER, roles.get(3));
		assertEquals(SquadRole.PRESSURER, roles.get(4));
	}

	@Test
	void brilliantLeaderUnlocksTwoFlanksAndCutoff() {
		Map<Integer, SquadRole> roles = SquadRolePlanner.plan(List.of(1, 2, 3, 4, 5), 1, 10);

		assertEquals(SquadRole.LEADER, roles.get(1));
		assertEquals(SquadRole.PRESSURER, roles.get(2));
		assertEquals(SquadRole.FLANK_LEFT, roles.get(3));
		assertEquals(SquadRole.FLANK_RIGHT, roles.get(4));
		assertEquals(SquadRole.CUTOFF, roles.get(5));
	}
}

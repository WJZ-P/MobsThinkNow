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

	@Test
	void rightFlankUnlocksAtSeven() {
		Map<Integer, SquadRole> seven = SquadRolePlanner.plan(List.of(1, 2, 3, 4), 1, 7);
		Map<Integer, SquadRole> six = SquadRolePlanner.plan(List.of(1, 2, 3, 4), 1, 6);

		assertEquals(SquadRole.FLANK_RIGHT, seven.get(4));
		assertEquals(SquadRole.PRESSURER, six.get(4));
	}

	@Test
	void shieldBearersAnchorPressureSlots() {
		Map<Integer, SquadRole> roles = SquadRolePlanner.planLoadouts(
			List.of(1, 2, 3, 4, 5),
			1,
			10,
			Map.of(
				2, new SquadLoadout(WeaponClass.SWORD, false),
				3, new SquadLoadout(WeaponClass.AXE, true),
				4, new SquadLoadout(WeaponClass.SPEAR, false),
				5, new SquadLoadout(WeaponClass.NONE, true)
			)
		);

		assertEquals(SquadRole.PRESSURER, roles.get(3));
		assertEquals(SquadRole.FLANK_LEFT, roles.get(2));
		assertEquals(SquadRole.FLANK_RIGHT, roles.get(5));
		assertEquals(SquadRole.CUTOFF, roles.get(4));
	}

	@Test
	void weaponsBiasRoleAssignmentTowardTheirSpeciality() {
		Map<Integer, SquadRole> roles = SquadRolePlanner.plan(
			List.of(1, 2, 3, 4, 5),
			1,
			10,
			Map.of(2, WeaponClass.SWORD, 3, WeaponClass.AXE, 4, WeaponClass.SPEAR, 5, WeaponClass.NONE)
		);

		assertEquals(SquadRole.LEADER, roles.get(1));
		assertEquals(SquadRole.PRESSURER, roles.get(3));
		assertEquals(SquadRole.FLANK_LEFT, roles.get(2));
		assertEquals(SquadRole.FLANK_RIGHT, roles.get(5));
		assertEquals(SquadRole.CUTOFF, roles.get(4));
	}

	@Test
	void unarmedWeaponMapKeepsLegacyOrderExactly() {
		Map<Integer, SquadRole> legacy = SquadRolePlanner.plan(List.of(1, 2, 3, 4, 5), 1, 10);
		Map<Integer, SquadRole> unarmed = SquadRolePlanner.plan(
			List.of(1, 2, 3, 4, 5),
			1,
			10,
			Map.of(2, WeaponClass.NONE, 3, WeaponClass.NONE, 4, WeaponClass.NONE, 5, WeaponClass.NONE)
		);

		assertEquals(legacy, unarmed);
	}

	@Test
	void utilityCarrierReceivesDedicatedSupportRole() {
		Map<Integer, SquadRole> roles = SquadRolePlanner.planLoadouts(
			List.of(1, 2, 3, 4, 5),
			1,
			10,
			Map.of(
				2, new SquadLoadout(WeaponClass.NONE, false, UtilityClass.WATER),
				3, new SquadLoadout(WeaponClass.AXE, false),
				4, new SquadLoadout(WeaponClass.SWORD, false),
				5, SquadLoadout.UNARMED
			)
		);

		assertEquals(SquadRole.SUPPORT, roles.get(2));
		assertEquals(SquadRole.PRESSURER, roles.get(3));
		assertEquals(SquadRole.FLANK_LEFT, roles.get(4));
		assertEquals(SquadRole.FLANK_RIGHT, roles.get(5));
	}
}

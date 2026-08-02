package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

class SquadMeetingFormationTest {
	private static final Vec3 CENTER = Vec3.ZERO;
	private static final Vec3 FORWARD = new Vec3(0.0, 0.0, 1.0);
	private static final Vec3 LEFT = new Vec3(-1.0, 0.0, 0.0);

	@Test
	void flanksTakeReadableSidesOfTheSemicircle() {
		List<SquadRole> roles = List.of(
			SquadRole.PRESSURER,
			SquadRole.FLANK_LEFT,
			SquadRole.FLANK_RIGHT,
			SquadRole.SUPPORT
		);

		List<Vec3> positions = SquadMeetingFormation.arrange(CENTER, FORWARD, roles, 3.0);

		assertTrue(positions.get(1).dot(LEFT) > 0.0, "Left flanker should stand on the leader's left.");
		assertTrue(positions.get(2).dot(LEFT) < 0.0, "Right flanker should stand on the leader's right.");
		for (Vec3 position : positions) {
			assertTrue(position.dot(FORWARD) > 0.0, "Every follower should remain in the forward semicircle.");
		}
	}

	@Test
	void rangedMembersPreferABackRowWhenOneExists() {
		List<SquadRole> roles = new ArrayList<>();
		roles.add(SquadRole.RANGED);
		for (int index = 1; index < 12; index++) {
			roles.add(SquadRole.PRESSURER);
		}

		List<Vec3> positions = SquadMeetingFormation.arrange(CENTER, FORWARD, roles, 3.0);
		double nearestPressurerRadius = positions.stream()
			.skip(1)
			.mapToDouble(Vec3::horizontalDistance)
			.min()
			.orElseThrow();

		assertTrue(
			positions.getFirst().horizontalDistance() > nearestPressurerRadius + 1.0,
			"Ranged member should claim an outer-row slot before melee members."
		);
	}

	@Test
	void maximumSquadProducesUniqueSpacedMeetingSlots() {
		List<SquadRole> roles = new ArrayList<>();
		SquadRole[] cycle = {
			SquadRole.PRESSURER,
			SquadRole.FLANK_LEFT,
			SquadRole.FLANK_RIGHT,
			SquadRole.CUTOFF,
			SquadRole.SUPPORT,
			SquadRole.RANGED
		};
		for (int index = 0; index < 19; index++) {
			roles.add(cycle[index % cycle.length]);
		}

		List<Vec3> positions = SquadMeetingFormation.arrange(CENTER, FORWARD, roles, 3.0);
		Set<Vec3> unique = new HashSet<>(positions);

		assertEquals(19, positions.size());
		assertEquals(19, unique.size(), "No two followers may receive the same meeting slot.");
		for (int first = 0; first < positions.size(); first++) {
			assertTrue(positions.get(first).dot(FORWARD) > 0.0);
			for (int second = first + 1; second < positions.size(); second++) {
				double horizontalDistance = positions.get(first)
					.subtract(positions.get(second))
					.horizontalDistance();
				assertTrue(horizontalDistance > 0.75, "Meeting slots overlap at " + first + " and " + second);
			}
		}
	}
}

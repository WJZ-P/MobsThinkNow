package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadCasualtyPlannerTest {
	@Test
	void selectsLowestHealthCasualtyAndPrefersHealthyShieldEscort() {
		Vec3 threat = Vec3.ZERO;
		SquadCasualtyPlanner.Response response = SquadCasualtyPlanner.select(List.of(
			member(1, 4.0, 0.28, 8, true, true, false),
			member(2, 5.0, 0.18, 7, true, true, false),
			member(3, 6.0, 1.00, 5, true, true, false),
			member(4, 8.0, 0.80, 4, true, true, true)
		), threat, 0.30);

		assertTrue(response != null);
		assertEquals(2, response.casualtyId());
		assertEquals(4, response.escortId(), "Shield support should outrank a closer unshielded escort.");
	}

	@Test
	void neverUsesIneligibleCreeperLikeMemberAsCasualtyOrEscort() {
		SquadCasualtyPlanner.Response response = SquadCasualtyPlanner.select(List.of(
			new SquadCasualtyPlanner.MemberSnapshot(1, new Vec3(3.0, 0.0, 0.0), 0.10, 10, false, false, false, false),
			member(2, 4.0, 0.20, 8, true, false, false),
			member(3, 5.0, 0.90, 7, true, true, false)
		), Vec3.ZERO, 0.30);

		assertTrue(response != null);
		assertEquals(2, response.casualtyId());
		assertEquals(3, response.escortId());
	}

	@Test
	void prefersMobileSpiderCarrierWhenNoHealthyShieldEscortExists() {
		SquadCasualtyPlanner.Response response = SquadCasualtyPlanner.select(List.of(
			member(1, 5.0, 0.18, 9, true, false, false),
			member(2, 5.8, 0.95, 8, true, true, false),
			member(3, 8.5, 0.90, 7, true, true, false, true)
		), Vec3.ZERO, 0.30);

		assertTrue(response != null);
		assertEquals(3, response.escortId(), "A qualified casualty carrier should outrank a closer unshielded walker.");
	}

	@Test
	void refusesResponseWithoutHealthyEscortOrAtLongRange() {
		assertNull(SquadCasualtyPlanner.select(List.of(
			member(1, 4.0, 0.20, 8, true, true, false),
			member(2, 5.0, 0.50, 9, true, true, true)
		), Vec3.ZERO, 0.30));

		assertNull(SquadCasualtyPlanner.select(List.of(
			member(1, 20.0, 0.20, 8, true, true, false),
			member(2, 19.0, 1.00, 9, true, true, true)
		), Vec3.ZERO, 0.30));
	}

	@Test
	void escortPointStaysBetweenThreatAndCasualtyWhileCasualtyMovesAway() {
		SquadCasualtyPlanner.MemberSnapshot casualty = member(2, 5.0, 0.20, 8, true, true, false);
		SquadCasualtyPlanner.MemberSnapshot escort = member(4, 7.0, 0.90, 9, true, true, true);
		SquadCasualtyPlanner.Response response = SquadCasualtyPlanner.responseForPair(casualty, escort, Vec3.ZERO);

		assertTrue(response.casualtyDestination().x > casualty.position().x);
		assertTrue(response.escortDestination().x > 0.0);
		assertTrue(response.escortDestination().x < casualty.position().x);
		assertTrue(
			SquadCasualtyPlanner.horizontalDistanceSquared(response.casualtyDestination(), Vec3.ZERO)
				> SquadCasualtyPlanner.horizontalDistanceSquared(casualty.position(), Vec3.ZERO)
		);
	}

	@Test
	void safeDistanceUsesHorizontalGeometry() {
		assertTrue(SquadCasualtyPlanner.isSafe(new Vec3(10.0, 0.0, 0.0), Vec3.ZERO));
		assertTrue(!SquadCasualtyPlanner.isSafe(new Vec3(0.0, 100.0, 9.9), Vec3.ZERO));
	}

	private static SquadCasualtyPlanner.MemberSnapshot member(
		final int id,
		final double x,
		final double health,
		final int intelligence,
		final boolean casualtyEligible,
		final boolean escortEligible,
		final boolean shield
	) {
		return member(id, x, health, intelligence, casualtyEligible, escortEligible, shield, false);
	}

	private static SquadCasualtyPlanner.MemberSnapshot member(
		final int id,
		final double x,
		final double health,
		final int intelligence,
		final boolean casualtyEligible,
		final boolean escortEligible,
		final boolean shield,
		final boolean mobileCarrier
	) {
		return new SquadCasualtyPlanner.MemberSnapshot(
			id,
			new Vec3(x, 0.0, 0.0),
			health,
			intelligence,
			casualtyEligible,
			escortEligible,
			shield,
			mobileCarrier
		);
	}
}

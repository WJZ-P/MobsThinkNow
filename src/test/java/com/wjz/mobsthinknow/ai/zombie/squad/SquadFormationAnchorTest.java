package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadFormationAnchorTest {
	private static final double EPSILON = 1.0E-6;

	@Test
	void missingOrStationaryVelocityKeepsTheLastReliablePosition() {
		Vec3 lastSeen = new Vec3(8.0, 3.0, -4.0);

		assertEquals(lastSeen, SquadFormationAnchor.predict(lastSeen, null, 10, 4L));
		assertEquals(lastSeen, SquadFormationAnchor.predict(lastSeen, Vec3.ZERO, 10, 4L));
	}

	@Test
	void smarterLeaderLeadsAMovingTargetFurtherWithoutChangingHeight() {
		Vec3 lastSeen = new Vec3(5.0, 12.0, 5.0);
		Vec3 velocity = new Vec3(0.2, 0.8, 0.0);

		Vec3 lowIntelligence = SquadFormationAnchor.predict(lastSeen, velocity, 1, 0L);
		Vec3 highIntelligence = SquadFormationAnchor.predict(lastSeen, velocity, 10, 0L);

		assertTrue(highIntelligence.x > lowIntelligence.x);
		assertEquals(lastSeen.y, lowIntelligence.y, EPSILON);
		assertEquals(lastSeen.y, highIntelligence.y, EPSILON);
	}

	@Test
	void extremeVelocityAndOldObservationStayInsideTheBoundedLeadRadius() {
		Vec3 lastSeen = new Vec3(-2.0, 7.0, 11.0);
		Vec3 predicted = SquadFormationAnchor.predict(
			lastSeen,
			new Vec3(100.0, -40.0, 100.0),
			100,
			Long.MAX_VALUE
		);

		assertTrue(horizontalDistance(lastSeen, predicted) <= 3.5 + EPSILON);
		assertEquals(lastSeen.y, predicted.y, EPSILON);
	}

	@Test
	void invalidVelocityDoesNotPoisonFormationCoordinates() {
		Vec3 lastSeen = new Vec3(1.0, 2.0, 3.0);

		assertEquals(
			lastSeen,
			SquadFormationAnchor.predict(lastSeen, new Vec3(Double.NaN, 0.0, 1.0), 8, 0L)
		);
	}

	private static double horizontalDistance(final Vec3 first, final Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}

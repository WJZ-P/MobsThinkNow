package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadFiringLaneRegistryTest {
	@Test
	void detectsAHitOnTheSampledArcAboveTheDirectLine() {
		SquadFiringLaneRegistry registry = new SquadFiringLaneRegistry();
		List<Vec3> arc = List.of(
			new Vec3(0.0, 2.0, 0.0),
			new Vec3(4.0, 5.0, 0.0),
			new Vec3(8.0, 2.0, 0.0)
		);
		assertTrue(registry.reserve(10, 99, arc, 0.15, false, 20L, 5L));

		SquadFiringLaneRegistry.Reservation reservation = registry.blockingLane(
			20,
			new AABB(3.8, 4.7, -0.2, 4.2, 5.3, 0.2),
			20L
		);

		assertSame(arc.getFirst(), reservation.start());
		assertSame(arc.getLast(), reservation.end());
		assertEquals(3, reservation.trajectory().size());
		assertNull(registry.blockingLane(10, new AABB(3.8, 4.7, -0.2, 4.2, 5.3, 0.2), 20L));
		assertNull(registry.blockingLane(20, new AABB(3.8, 4.7, -0.2, 4.2, 5.3, 0.2), 26L));
	}

	@Test
	void explosiveLaneAlsoClearsTheTargetBlastZone() {
		SquadFiringLaneRegistry registry = new SquadFiringLaneRegistry();
		registry.reserve(
			4,
			99,
			new Vec3(0.0, 2.0, 0.0),
			new Vec3(10.0, 2.0, 0.0),
			0.2,
			true,
			0L,
			5L
		);

		assertEquals(
			4,
			registry.blockingLane(8, new AABB(9.5, -0.5, 2.5, 10.5, 0.5, 3.5), 0L).shooterId()
		);
	}
}

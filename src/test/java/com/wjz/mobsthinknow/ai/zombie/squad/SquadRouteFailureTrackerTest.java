package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadRouteFailureTrackerTest {
	@Test
	void requiresTwoMatchingFailuresThenEnforcesCooldown() {
		SquadRouteFailureTracker tracker = new SquadRouteFailureTracker();
		Vec3 destination = new Vec3(8.0, 64.0, 3.0);

		assertEquals(
			SquadRouteFailureTracker.Decision.WAITING_FOR_CONFIRMATION,
			tracker.recordFailure(4, destination, 100L, 40)
		);
		assertEquals(
			SquadRouteFailureTracker.Decision.REPLAN,
			tracker.recordFailure(4, destination.add(0.25, 0.0, 0.0), 112L, 40)
		);
		assertEquals(
			SquadRouteFailureTracker.Decision.COOLDOWN,
			tracker.recordFailure(4, destination, 130L, 40)
		);
		assertEquals(
			SquadRouteFailureTracker.Decision.WAITING_FOR_CONFIRMATION,
			tracker.recordFailure(4, destination, 152L, 40)
		);
	}

	@Test
	void differentPlanOrDestinationRestartsConfirmation() {
		SquadRouteFailureTracker tracker = new SquadRouteFailureTracker();

		assertEquals(
			SquadRouteFailureTracker.Decision.WAITING_FOR_CONFIRMATION,
			tracker.recordFailure(1, new Vec3(1.0, 2.0, 3.0), 20L, 60)
		);
		assertEquals(
			SquadRouteFailureTracker.Decision.WAITING_FOR_CONFIRMATION,
			tracker.recordFailure(2, new Vec3(1.0, 2.0, 3.0), 21L, 60)
		);
		assertEquals(
			SquadRouteFailureTracker.Decision.WAITING_FOR_CONFIRMATION,
			tracker.recordFailure(2, new Vec3(5.0, 2.0, 3.0), 22L, 60)
		);
	}
}

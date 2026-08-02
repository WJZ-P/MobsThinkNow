package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class SquadBriefingRoutePlannerTest {
	private static final Vec3 REQUESTED = new Vec3(8.0, 0.0, 4.0);
	private static final Vec3 SHORT_FLANK = new Vec3(6.0, 0.0, 3.0);
	private static final Vec3 CUTOFF = new Vec3(5.0, 0.0, 0.0);
	private static final Vec3 PRESSURE = new Vec3(3.0, 0.0, 0.0);
	private static final List<SquadBriefingRoutePlanner.Candidate> FALLBACKS = List.of(
		new SquadBriefingRoutePlanner.Candidate(SquadRole.FLANK_LEFT, SHORT_FLANK),
		new SquadBriefingRoutePlanner.Candidate(SquadRole.CUTOFF, CUTOFF),
		new SquadBriefingRoutePlanner.Candidate(SquadRole.PRESSURER, PRESSURE)
	);

	@Test
	void reachableOriginalRouteIsAcceptedWithoutTryingFallbacks() {
		SquadBriefingRoutePlanner.Result result = SquadBriefingRoutePlanner.resolve(
			SquadRole.FLANK_LEFT,
			REQUESTED,
			FALLBACKS,
			REQUESTED::equals
		);
		assertEquals(SquadRouteOutcome.CLEAR, result.outcome());
		assertEquals(SquadRole.FLANK_LEFT, result.assignedRole());
		assertEquals(REQUESTED, result.resolvedDestination());
		assertEquals(1, result.pathChecks());
	}

	@Test
	void firstReachableFallbackBecomesRealReplannedOrder() {
		Set<Vec3> reachable = Set.of(CUTOFF, PRESSURE);
		SquadBriefingRoutePlanner.Result result = SquadBriefingRoutePlanner.resolve(
			SquadRole.FLANK_LEFT,
			REQUESTED,
			FALLBACKS,
			reachable::contains
		);
		assertEquals(SquadRouteOutcome.REROUTED, result.outcome());
		assertEquals(SquadRole.CUTOFF, result.assignedRole());
		assertEquals(CUTOFF, result.resolvedDestination());
		assertEquals(3, result.pathChecks());
	}

	@Test
	void noReachableCandidateDegradesToDirectPressureAndReportsObjection() {
		SquadBriefingRoutePlanner.Result result = SquadBriefingRoutePlanner.resolve(
			SquadRole.FLANK_RIGHT,
			REQUESTED,
			FALLBACKS,
			ignored -> false
		);
		assertEquals(SquadRouteOutcome.BLOCKED, result.outcome());
		assertEquals(SquadRole.PRESSURER, result.assignedRole());
		assertNull(result.resolvedDestination());
		assertEquals(4, result.pathChecks());
	}
}

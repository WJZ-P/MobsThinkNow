package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedSpatialIndexTest {
	@Test
	void denseBucketNeverExceedsRawScanBudget() {
		BoundedSpatialIndex<Candidate> index = newIndex(12.0);
		List<Candidate> candidates = new ArrayList<>();
		for (int id = 0; id < 200; id++) {
			Candidate candidate = new Candidate(id, 1, 1.0, 1.0, true);
			candidates.add(candidate);
			index.add(candidate);
		}

		BoundedSpatialIndex.ScanResult<Candidate> result = index.collectNearby(
			candidates.getFirst(),
			Candidate::available,
			BoundedSpatialIndexTest::distanceSquared,
			144.0,
			100,
			16
		);

		assertEquals(16, result.rawChecks());
		assertTrue(result.candidates().size() <= 17, "Accepted candidates escaped the raw scan budget.");
	}

	@Test
	void queryRespectsTargetGroupDistanceAndAvailability() {
		BoundedSpatialIndex<Candidate> index = newIndex(10.0);
		Candidate seed = new Candidate(1, 7, 0.0, 0.0, true);
		Candidate nearby = new Candidate(2, 7, 2.0, 0.0, true);
		Candidate unavailable = new Candidate(3, 7, 1.0, 0.0, false);
		Candidate otherTarget = new Candidate(4, 8, 1.0, 0.0, true);
		Candidate tooFar = new Candidate(5, 7, 9.0, 0.0, true);
		for (Candidate candidate : List.of(seed, nearby, unavailable, otherTarget, tooFar)) {
			index.add(candidate);
		}

		BoundedSpatialIndex.ScanResult<Candidate> result = index.collectNearby(
			seed,
			Candidate::available,
			BoundedSpatialIndexTest::distanceSquared,
			25.0,
			10,
			20
		);

		assertEquals(List.of(seed, nearby), result.candidates());
	}

	@Test
	void rejectsInvalidCellSize() {
		assertThrows(IllegalArgumentException.class, () -> newIndex(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> newIndex(0.0));
	}

	private static BoundedSpatialIndex<Candidate> newIndex(final double cellSize) {
		return new BoundedSpatialIndex<>(cellSize, Candidate::group, Candidate::x, Candidate::z);
	}

	private static double distanceSquared(final Candidate first, final Candidate second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	private record Candidate(int id, int group, double x, double z, boolean available) {
	}
}

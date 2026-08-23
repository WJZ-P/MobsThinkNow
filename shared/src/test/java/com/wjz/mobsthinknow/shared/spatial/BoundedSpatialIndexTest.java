package com.wjz.mobsthinknow.shared.spatial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BoundedSpatialIndexTest {
	@Test
	void denseBucketNeverExceedsRawScanBudget() {
		BoundedSpatialIndex<Integer, Candidate> index = newIndex(12.0);
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
	void queryRespectsGroupDistanceAndAvailability() {
		BoundedSpatialIndex<Integer, Candidate> index = newIndex(10.0);
		Candidate seed = new Candidate(1, 7, 0.0, 0.0, true);
		Candidate nearby = new Candidate(2, 7, 2.0, 0.0, true);
		Candidate unavailable = new Candidate(3, 7, 1.0, 0.0, false);
		Candidate otherGroup = new Candidate(4, 8, 1.0, 0.0, true);
		Candidate tooFar = new Candidate(5, 7, 9.0, 0.0, true);
		for (Candidate candidate : List.of(seed, nearby, unavailable, otherGroup, tooFar)) {
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
	void upsertMovesCandidateWithoutLeavingGhostInOldBucket() {
		BoundedSpatialIndex<Integer, MutableCandidate> index = newMutableIndex(10.0);
		MutableCandidate seed = new MutableCandidate(1, 4, 0.0, 0.0);
		MutableCandidate moving = new MutableCandidate(2, 4, 2.0, 0.0);
		index.add(seed);
		index.add(moving);

		assertEquals(List.of(seed, moving), collect(index, seed));
		moving.x = 40.0;
		assertTrue(index.upsert(moving));
		assertEquals(List.of(seed), collect(index, seed));
		assertEquals(2, index.size());
	}

	@Test
	void upsertMovesCandidateBetweenGroups() {
		BoundedSpatialIndex<Integer, MutableCandidate> index = newMutableIndex(10.0);
		MutableCandidate seed = new MutableCandidate(1, 4, 0.0, 0.0);
		MutableCandidate moving = new MutableCandidate(2, 4, 2.0, 0.0);
		index.add(seed);
		index.add(moving);

		moving.group = 9;
		assertTrue(index.upsert(moving));
		assertEquals(List.of(seed), collect(index, seed));
	}

	@Test
	void repeatedUpsertAndRemoveAreIdempotent() {
		BoundedSpatialIndex<Integer, MutableCandidate> index = newMutableIndex(10.0);
		MutableCandidate candidate = new MutableCandidate(1, 4, 0.0, 0.0);

		assertTrue(index.upsert(candidate));
		assertFalse(index.upsert(candidate));
		assertEquals(1, index.size());
		assertTrue(index.remove(candidate));
		assertFalse(index.remove(candidate));
		assertEquals(0, index.size());
	}

	@Test
	void identityKeepsEqualValueObjectsSeparate() {
		BoundedSpatialIndex<Integer, Candidate> index = newIndex(10.0);
		Candidate first = new Candidate(1, 4, 0.0, 0.0, true);
		Candidate equalButDistinct = new Candidate(1, 4, 0.0, 0.0, true);
		index.add(first);
		index.add(equalButDistinct);

		assertEquals(2, index.size());
		assertEquals(List.of(first, equalButDistinct), index.collectNearby(
			first,
			Candidate::available,
			BoundedSpatialIndexTest::distanceSquared,
			100.0,
			4,
			4
		).candidates());
	}

	@Test
	void callerCanExcludeSeedAndResultIsImmutable() {
		BoundedSpatialIndex<Integer, Candidate> index = newIndex(10.0);
		Candidate seed = new Candidate(1, 4, 0.0, 0.0, true);
		Candidate nearby = new Candidate(2, 4, 1.0, 0.0, true);
		index.add(seed);
		index.add(nearby);

		List<Candidate> result = index.collectNearby(
			seed,
			Candidate::available,
			BoundedSpatialIndexTest::distanceSquared,
			100.0,
			4,
			4,
			false
		).candidates();

		assertEquals(List.of(nearby), result);
		assertThrows(UnsupportedOperationException.class, () -> result.add(seed));
	}

	@Test
	void rejectsInvalidCellSizeCoordinatesAndOversizedRadius() {
		assertThrows(IllegalArgumentException.class, () -> newIndex(Double.NaN));
		assertThrows(IllegalArgumentException.class, () -> newIndex(0.0));

		BoundedSpatialIndex<Integer, MutableCandidate> index = newMutableIndex(10.0);
		MutableCandidate invalid = new MutableCandidate(1, 1, Double.NaN, 0.0);
		assertThrows(IllegalArgumentException.class, () -> index.add(invalid));

		MutableCandidate valid = new MutableCandidate(2, 1, 0.0, 0.0);
		index.add(valid);
		assertThrows(IllegalArgumentException.class, () -> index.collectNearby(
			valid,
			ignored -> true,
			BoundedSpatialIndexTest::distanceSquared,
			100.01,
			4,
			4
		));
	}

	@Test
	void directResultConstructionDefensivelyCopiesAndValidates() {
		List<String> mutable = new ArrayList<>(List.of("first"));
		BoundedSpatialIndex.ScanResult<String> result = new BoundedSpatialIndex.ScanResult<>(mutable, 1);
		mutable.add("second");
		assertEquals(List.of("first"), result.candidates());
		assertThrows(UnsupportedOperationException.class, () -> result.candidates().add("third"));
		assertThrows(IllegalArgumentException.class, () -> new BoundedSpatialIndex.ScanResult<>(List.of(), -1));
		assertThrows(NullPointerException.class, () -> new BoundedSpatialIndex.ScanResult<>(null, 0));
	}

	@Test
	void primitiveCellTableMatchesBruteForceAcrossRehashesMovesAndTombstones() {
		Random random = new Random(0x4D544E5F53504143L);
		BoundedSpatialIndex<Integer, MutableCandidate> index = newMutableIndex(10.0);
		List<MutableCandidate> candidates = new ArrayList<>();
		Map<MutableCandidate, Boolean> registered = new IdentityHashMap<>();
		for (int id = 0; id < 240; id++) {
			MutableCandidate candidate = new MutableCandidate(
				id,
				random.nextInt(4),
				random.nextDouble(-800.0, 800.0),
				random.nextDouble(-800.0, 800.0)
			);
			candidates.add(candidate);
			index.add(candidate);
			registered.put(candidate, Boolean.TRUE);
		}

		for (int step = 0; step < 600; step++) {
			MutableCandidate changed = candidates.get(random.nextInt(candidates.size()));
			switch (random.nextInt(4)) {
				case 0, 1 -> {
					changed.group = random.nextInt(4);
					changed.x = random.nextDouble(-800.0, 800.0);
					changed.z = random.nextDouble(-800.0, 800.0);
					index.upsert(changed);
					registered.put(changed, Boolean.TRUE);
				}
				case 2 -> {
					boolean expected = registered.remove(changed) != null;
					assertEquals(expected, index.remove(changed));
				}
				default -> {
					if (!registered.containsKey(changed)) {
						index.add(changed);
						registered.put(changed, Boolean.TRUE);
					}
				}
			}

			MutableCandidate seed = candidates.get(random.nextInt(candidates.size()));
			List<MutableCandidate> actual = index.collectNearby(
				seed,
				ignored -> true,
				BoundedSpatialIndexTest::distanceSquared,
				100.0,
				1_000,
				1_000
			).candidates();
			List<MutableCandidate> expected = new ArrayList<>();
			expected.add(seed);
			for (MutableCandidate candidate : registered.keySet()) {
				if (candidate != seed && candidate.group == seed.group
					&& distanceSquared(seed, candidate) <= 100.0) {
					expected.add(candidate);
				}
			}
			assertSameIdentities(expected, actual);
			assertEquals(registered.size(), index.size());
		}
	}

	@Test
	void linkedIdentityBucketPreservesInsertionOrderAfterRemovalAndReentry() {
		BoundedSpatialIndex<Integer, MutableCandidate> index = newMutableIndex(10.0);
		MutableCandidate seed = new MutableCandidate(1, 4, 0.0, 0.0);
		MutableCandidate first = new MutableCandidate(2, 4, 1.0, 0.0);
		MutableCandidate moving = new MutableCandidate(3, 4, 2.0, 0.0);
		MutableCandidate last = new MutableCandidate(4, 4, 3.0, 0.0);
		for (MutableCandidate candidate : List.of(seed, first, moving, last)) {
			index.add(candidate);
		}

		assertEquals(List.of(seed, first, moving, last), collect(index, seed));
		assertTrue(index.remove(moving));
		index.add(moving);
		assertEquals(List.of(seed, first, last, moving), collect(index, seed));
	}

	private static BoundedSpatialIndex<Integer, Candidate> newIndex(final double cellSize) {
		return new BoundedSpatialIndex<>(cellSize, Candidate::group, Candidate::x, Candidate::z);
	}

	private static BoundedSpatialIndex<Integer, MutableCandidate> newMutableIndex(final double cellSize) {
		return new BoundedSpatialIndex<>(
			cellSize,
			candidate -> candidate.group,
			candidate -> candidate.x,
			candidate -> candidate.z
		);
	}

	private static List<MutableCandidate> collect(
		final BoundedSpatialIndex<Integer, MutableCandidate> index,
		final MutableCandidate seed
	) {
		return index.collectNearby(
			seed,
			ignored -> true,
			BoundedSpatialIndexTest::distanceSquared,
			100.0,
			10,
			20
		).candidates();
	}

	private static double distanceSquared(final Candidate first, final Candidate second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	private static double distanceSquared(final MutableCandidate first, final MutableCandidate second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	private static <T> void assertSameIdentities(final List<T> expected, final List<T> actual) {
		assertEquals(expected.size(), actual.size());
		for (T candidate : expected) {
			assertTrue(actual.stream().anyMatch(actualCandidate -> actualCandidate == candidate));
		}
	}

	private record Candidate(int id, int group, double x, double z, boolean available) {
	}

	private static final class MutableCandidate {
		private final int id;
		private int group;
		private double x;
		private double z;

		private MutableCandidate(final int id, final int group, final double x, final double z) {
			this.id = id;
			this.group = group;
			this.x = x;
			this.z = z;
		}
	}
}

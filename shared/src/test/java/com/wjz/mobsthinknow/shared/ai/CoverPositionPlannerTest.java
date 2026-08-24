package com.wjz.mobsthinknow.shared.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.GridPosition;
import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.SearchLimits;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CoverPositionPlannerTest {
	@Test
	void selectsAHiddenCellWithAnAdjacentClearPeekAndScoresNearestFirst() {
		GridPosition origin = new GridPosition(0, 0, 0);
		Set<GridPosition> hidden = Set.of(new GridPosition(1, 0, 0), new GridPosition(3, 0, 0));
		Set<GridPosition> clear = Set.of(new GridPosition(1, 0, 1), new GridPosition(3, 0, 1));
		Set<GridPosition> standable = new HashSet<>(hidden);
		standable.addAll(clear);
		var result = CoverPositionPlanner.findPlans(
			origin,
			origin.center(),
			new Vec3d(1.5, 0.0, 10.5),
			10.0,
			0,
			new SearchLimits(4, 0, 96, 4, 0.50, 2.0),
			new SetProbe(standable, hidden, clear)
		);

		assertEquals(2, result.plans().size());
		assertEquals(new GridPosition(1, 0, 0), result.plans().getFirst().hide());
		assertEquals(new GridPosition(1, 0, 1), result.plans().getFirst().peek());
		assertTrue(result.plans().getFirst().score() < result.plans().getLast().score());
	}

	@Test
	void rawCandidateAndResultBudgetsRemainHardBounds() {
		AtomicInteger standabilityChecks = new AtomicInteger();
		SearchLimits limits = new SearchLimits(8, 3, 7, 2, 0.10, 3.0);
		var result = CoverPositionPlanner.findPlans(
			new GridPosition(0, 0, 0),
			new Vec3d(0.5, 0.0, 0.5),
			new Vec3d(0.5, 0.0, 10.5),
			10.0,
			0,
			limits,
			(x, y, z) -> {
				standabilityChecks.incrementAndGet();
				return false;
			}
		);

		assertEquals(7, result.rawChecks());
		assertEquals(7, standabilityChecks.get());
		assertTrue(result.plans().isEmpty());
	}

	@Test
	void rangeBandAndInvalidSettingsAreNormalized() {
		SearchLimits limits = new SearchLimits(0, -5, 0, 99, Double.NaN, Double.NaN);
		assertEquals(1, limits.horizontalRadius());
		assertEquals(0, limits.verticalRadius());
		assertEquals(1, limits.maximumRawCandidates());
		assertEquals(16, limits.maximumPlans());
		assertFalse(CoverPositionPlanner.isUsefulRange(6.9 * 6.9, 10.0, SearchLimits.defaults()));
		assertTrue(CoverPositionPlanner.isUsefulRange(7.0 * 7.0, 10.0, SearchLimits.defaults()));
		assertTrue(CoverPositionPlanner.isUsefulRange(15.5 * 15.5, 10.0, SearchLimits.defaults()));
		assertFalse(CoverPositionPlanner.isUsefulRange(Double.NaN, 10.0, SearchLimits.defaults()));
	}

	@Test
	void scalarScoringMatchesTheOriginalCenterVectorFormula() {
		Vec3d actor = new Vec3d(-3.25, 63.0, 8.75);
		Vec3d target = new Vec3d(12.5, 65.0, -4.5);
		for (GridPosition hide : List.of(
			new GridPosition(0, 64, 0),
			new GridPosition(-7, 62, 9)
		)) {
			for (GridPosition peek : List.of(
				hide.offset(1, 0, 0),
				hide.offset(0, 0, -1)
			)) {
				double preferredRange = 10.0;
				double travelCost = hide.center().distanceSquared(actor);
				double rangeError = Math.sqrt(peek.center().distanceSquared(target)) - preferredRange;
				double verticalCost = Math.abs(hide.y() - actor.y()) * 2.0;
				double expected = travelCost + rangeError * rangeError * 1.5 + verticalCost;
				assertEquals(
					expected,
					CoverPositionPlanner.score(actor, target, hide, peek, preferredRange),
					1.0E-12
				);
			}
		}
	}

	private record SetProbe(
		Set<GridPosition> standable,
		Set<GridPosition> hidden,
		Set<GridPosition> clear
	) implements CoverPositionPlanner.Probe {
		@Override
		public boolean isStandable(final int x, final int y, final int z) {
			return this.standable.contains(new GridPosition(x, y, z));
		}

		@Override
		public boolean isHidden(final int x, final int y, final int z) {
			return this.hidden.contains(new GridPosition(x, y, z));
		}

		@Override
		public boolean hasClearShot(final int x, final int y, final int z) {
			return this.clear.contains(new GridPosition(x, y, z));
		}
	}
}

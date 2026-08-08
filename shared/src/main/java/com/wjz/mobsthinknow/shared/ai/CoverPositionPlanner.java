package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-neutral bounded cover search.
 *
 * <p>The planner owns only deterministic candidate ordering, range filtering and scoring. A platform adapter
 * supplies collision and ray tests through {@link Probe}; therefore neither Bukkit nor Minecraft classes leak into
 * the shared tactical kernel. Every invocation has a strict raw-candidate budget and returns at most the configured
 * number of plans.</p>
 */
public final class CoverPositionPlanner {
	private static final Direction[] HORIZONTAL_DIRECTIONS = {
		new Direction(0, -1),
		new Direction(0, 1),
		new Direction(-1, 0),
		new Direction(1, 0)
	};
	private static final Map<OffsetShape, List<Offset>> OFFSETS = new ConcurrentHashMap<>();

	private CoverPositionPlanner() {
	}

	public static SearchResult findPlans(
		final GridPosition origin,
		final Vec3d actorPosition,
		final Vec3d targetPosition,
		final double configuredPreferredRange,
		final int stableRotation,
		final SearchLimits limits,
		final Probe probe
	) {
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(actorPosition, "actorPosition");
		Objects.requireNonNull(targetPosition, "targetPosition");
		Objects.requireNonNull(limits, "limits");
		Objects.requireNonNull(probe, "probe");
		double preferredRange = validPreferredRange(configuredPreferredRange);
		List<ScoredPlan> candidates = new ArrayList<>();
		int rawChecks = 0;
		int directionOffset = Math.floorMod(stableRotation, HORIZONTAL_DIRECTIONS.length);

		for (Offset offset : offsetsFor(limits)) {
			if (rawChecks >= limits.maximumRawCandidates()) {
				break;
			}
			rawChecks++;
			GridPosition hide = origin.offset(offset.x(), offset.y(), offset.z());
			if (!probe.isStandable(hide)
				|| !isUsefulRange(hide.center().distanceSquared(targetPosition), preferredRange, limits)
				|| !probe.isHidden(hide)) {
				continue;
			}

			Plan bestAtHide = null;
			double bestScore = Double.POSITIVE_INFINITY;
			for (int index = 0; index < HORIZONTAL_DIRECTIONS.length; index++) {
				Direction direction = HORIZONTAL_DIRECTIONS[
					(index + directionOffset) % HORIZONTAL_DIRECTIONS.length
				];
				GridPosition peek = hide.offset(direction.x(), 0, direction.z());
				if (!probe.isStandable(peek)
					|| !isUsefulRange(peek.center().distanceSquared(targetPosition), preferredRange, limits)
					|| !probe.hasClearShot(peek)) {
					continue;
				}
				double score = score(actorPosition, targetPosition, hide, peek, preferredRange);
				if (score < bestScore) {
					bestScore = score;
					bestAtHide = new Plan(hide, peek, score);
				}
			}
			if (bestAtHide != null) {
				candidates.add(new ScoredPlan(bestAtHide));
			}
		}

		candidates.sort(Comparator.comparingDouble(candidate -> candidate.plan().score()));
		int resultSize = Math.min(limits.maximumPlans(), candidates.size());
		List<Plan> plans = new ArrayList<>(resultSize);
		for (int index = 0; index < resultSize; index++) {
			plans.add(candidates.get(index).plan());
		}
		return new SearchResult(plans, rawChecks);
	}

	public static boolean isUsefulRange(
		final double distanceSquared,
		final double configuredPreferredRange,
		final SearchLimits limits
	) {
		Objects.requireNonNull(limits, "limits");
		if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
			return false;
		}
		double preferredRange = validPreferredRange(configuredPreferredRange);
		double minimum = preferredRange * limits.minimumRangeRatio();
		double maximum = preferredRange * limits.maximumRangeRatio();
		return distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum;
	}

	public static double score(
		final Vec3d actorPosition,
		final Vec3d targetPosition,
		final GridPosition hide,
		final GridPosition peek,
		final double configuredPreferredRange
	) {
		Objects.requireNonNull(actorPosition, "actorPosition");
		Objects.requireNonNull(targetPosition, "targetPosition");
		Objects.requireNonNull(hide, "hide");
		Objects.requireNonNull(peek, "peek");
		double preferredRange = validPreferredRange(configuredPreferredRange);
		double travelCost = hide.center().distanceSquared(actorPosition);
		double peekRangeError = Math.sqrt(peek.center().distanceSquared(targetPosition)) - preferredRange;
		double verticalCost = Math.abs(hide.y() - actorPosition.y()) * 2.0;
		return travelCost + peekRangeError * peekRangeError * 1.5 + verticalCost;
	}

	private static List<Offset> offsetsFor(final SearchLimits limits) {
		OffsetShape shape = new OffsetShape(limits.horizontalRadius(), limits.verticalRadius());
		return OFFSETS.computeIfAbsent(shape, CoverPositionPlanner::createOffsets);
	}

	private static List<Offset> createOffsets(final OffsetShape shape) {
		List<Offset> offsets = new ArrayList<>();
		for (int y = -shape.verticalRadius(); y <= shape.verticalRadius(); y++) {
			for (int z = -shape.horizontalRadius(); z <= shape.horizontalRadius(); z++) {
				for (int x = -shape.horizontalRadius(); x <= shape.horizontalRadius(); x++) {
					int horizontalSquared = x * x + z * z;
					if (horizontalSquared <= shape.horizontalRadius() * shape.horizontalRadius()) {
						offsets.add(new Offset(x, y, z, horizontalSquared));
					}
				}
			}
		}
		offsets.sort(
			Comparator.comparingInt((Offset offset) -> Math.abs(offset.y()))
				.thenComparingInt(Offset::horizontalSquared)
		);
		return List.copyOf(offsets);
	}

	private static double validPreferredRange(final double configuredPreferredRange) {
		return Double.isFinite(configuredPreferredRange) && configuredPreferredRange > 0.0
			? configuredPreferredRange
			: 10.0;
	}

	@FunctionalInterface
	public interface Probe {
		boolean isStandable(GridPosition position);

		default boolean isHidden(final GridPosition position) {
			return false;
		}

		default boolean hasClearShot(final GridPosition position) {
			return false;
		}
	}

	public record SearchLimits(
		int horizontalRadius,
		int verticalRadius,
		int maximumRawCandidates,
		int maximumPlans,
		double minimumRangeRatio,
		double maximumRangeRatio
	) {
		public SearchLimits {
			horizontalRadius = Math.clamp(horizontalRadius, 1, 8);
			verticalRadius = Math.clamp(verticalRadius, 0, 3);
			maximumRawCandidates = Math.clamp(maximumRawCandidates, 1, 2048);
			maximumPlans = Math.clamp(maximumPlans, 1, 16);
			minimumRangeRatio = finiteClamp(minimumRangeRatio, 0.10, 1.0, 0.70);
			maximumRangeRatio = finiteClamp(maximumRangeRatio, minimumRangeRatio, 3.0, 1.55);
		}

		public static SearchLimits defaults() {
			return new SearchLimits(4, 1, 96, 4, 0.70, 1.55);
		}
	}

	public record GridPosition(int x, int y, int z) {
		public GridPosition offset(final int deltaX, final int deltaY, final int deltaZ) {
			return new GridPosition(this.x + deltaX, this.y + deltaY, this.z + deltaZ);
		}

		public Vec3d center() {
			return new Vec3d(this.x + 0.5, this.y, this.z + 0.5);
		}
	}

	public record Plan(GridPosition hide, GridPosition peek, double score) {
		public Plan {
			Objects.requireNonNull(hide, "hide");
			Objects.requireNonNull(peek, "peek");
			if (!Double.isFinite(score) || score < 0.0) {
				throw new IllegalArgumentException("score must be finite and non-negative");
			}
		}
	}

	public record SearchResult(List<Plan> plans, int rawChecks) {
		public SearchResult {
			plans = List.copyOf(Objects.requireNonNull(plans, "plans"));
			if (rawChecks < 0) {
				throw new IllegalArgumentException("rawChecks must be non-negative");
			}
		}
	}

	private static double finiteClamp(
		final double value,
		final double minimum,
		final double maximum,
		final double fallback
	) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
	}

	private record Direction(int x, int z) {
	}

	private record Offset(int x, int y, int z, int horizontalSquared) {
	}

	private record OffsetShape(int horizontalRadius, int verticalRadius) {
	}

	private record ScoredPlan(Plan plan) {
	}
}

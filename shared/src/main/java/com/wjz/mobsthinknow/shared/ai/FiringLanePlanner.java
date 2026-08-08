package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.Collection;
import java.util.Objects;

/** 纯数学的友军射线胶囊检查；平台层只需提供同队成员的有限快照。 */
public final class FiringLanePlanner {
	private static final double ENDPOINT_MARGIN = 0.02;

	private FiringLanePlanner() {
	}

	public static <K> Result<K> check(
		final Vec3d origin,
		final Vec3d target,
		final Collection<Ally<K>> allies,
		final int maximumChecks
	) {
		Objects.requireNonNull(origin, "origin");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(allies, "allies");
		int limit = Math.max(0, maximumChecks);
		Vec3d segment = target.subtract(origin);
		double lengthSquared = squaredLength(segment);
		if (lengthSquared < 1.0E-9 || limit == 0) {
			return new Result<>(true, null, 0);
		}

		K blocker = null;
		double nearestProjection = Double.POSITIVE_INFINITY;
		int checks = 0;
		for (Ally<K> ally : allies) {
			if (checks >= limit) {
				break;
			}
			checks++;
			Vec3d relative = ally.position().subtract(origin);
			double projection = dot(relative, segment) / lengthSquared;
			if (projection <= ENDPOINT_MARGIN || projection >= 1.0 - ENDPOINT_MARGIN) {
				continue;
			}
			Vec3d closest = origin.add(segment.scale(projection));
			if (closest.distanceSquared(ally.position()) <= ally.radius() * ally.radius()
				&& projection < nearestProjection) {
				blocker = ally.id();
				nearestProjection = projection;
			}
		}
		return new Result<>(blocker == null, blocker, checks);
	}

	/** 射界受阻时先横移、再略微后撤，给下一次公共 Pathfinder 查询一个稳定候选。 */
	public static Vec3d lateralReposition(
		final Vec3d shooter,
		final Vec3d target,
		final int stableSide,
		final double configuredDistance
	) {
		Vec3d forward = target.subtract(shooter).horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		double side = stableSide < 0 ? -1.0 : 1.0;
		double distance = Double.isFinite(configuredDistance)
			? Math.clamp(configuredDistance, 1.0, 6.0)
			: 3.0;
		return shooter
			.add(right.scale(side * distance))
			.subtract(forward.scale(Math.min(1.5, distance * 0.30)));
	}

	private static double dot(final Vec3d first, final Vec3d second) {
		return first.x() * second.x() + first.y() * second.y() + first.z() * second.z();
	}

	private static double squaredLength(final Vec3d vector) {
		return dot(vector, vector);
	}

	public record Ally<K>(K id, Vec3d position, double radius) {
		public Ally {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(position, "position");
			radius = Double.isFinite(radius) ? Math.clamp(radius, 0.05, 4.0) : 0.75;
		}
	}

	public record Result<K>(boolean clear, K blocker, int checks) {
		public Result {
			if (checks < 0) {
				throw new IllegalArgumentException("checks must be non-negative");
			}
		}
	}
}

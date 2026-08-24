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
		double segmentX = target.x() - origin.x();
		double segmentY = target.y() - origin.y();
		double segmentZ = target.z() - origin.z();
		double lengthSquared = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
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
			double relativeX = ally.x() - origin.x();
			double relativeY = ally.y() - origin.y();
			double relativeZ = ally.z() - origin.z();
			double projection = (
				relativeX * segmentX + relativeY * segmentY + relativeZ * segmentZ
			) / lengthSquared;
			if (projection <= ENDPOINT_MARGIN || projection >= 1.0 - ENDPOINT_MARGIN) {
				continue;
			}
			double closestX = origin.x() + segmentX * projection;
			double closestY = origin.y() + segmentY * projection;
			double closestZ = origin.z() + segmentZ * projection;
			double separationX = closestX - ally.x();
			double separationY = closestY - ally.y();
			double separationZ = closestZ - ally.z();
			double separationSquared = separationX * separationX
				+ separationY * separationY
				+ separationZ * separationZ;
			if (separationSquared <= ally.radius() * ally.radius()
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
		double forwardX = target.x() - shooter.x();
		double forwardZ = target.z() - shooter.z();
		double lengthSquared = forwardX * forwardX + forwardZ * forwardZ;
		if (lengthSquared < 1.0E-9) {
			forwardX = 0.0;
			forwardZ = 1.0;
		} else {
			double inverseLength = 1.0 / Math.sqrt(lengthSquared);
			forwardX *= inverseLength;
			forwardZ *= inverseLength;
		}
		double side = stableSide < 0 ? -1.0 : 1.0;
		double distance = Double.isFinite(configuredDistance)
			? Math.clamp(configuredDistance, 1.0, 6.0)
			: 3.0;
		double retreat = Math.min(1.5, distance * 0.30);
		return new Vec3d(
			shooter.x() - forwardZ * side * distance - forwardX * retreat,
			shooter.y(),
			shooter.z() + forwardX * side * distance - forwardZ * retreat
		);
	}

	public record Ally<K>(K id, double x, double y, double z, double radius) {
		public Ally(final K id, final Vec3d position, final double radius) {
			this(id, position.x(), position.y(), position.z(), radius);
		}

		public Ally {
			Objects.requireNonNull(id, "id");
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("ally position must be finite");
			}
			radius = Double.isFinite(radius) ? Math.clamp(radius, 0.05, 4.0) : 0.75;
		}

		public Vec3d position() {
			return new Vec3d(this.x, this.y, this.z);
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

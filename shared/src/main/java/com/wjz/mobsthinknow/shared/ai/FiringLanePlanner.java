package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** 纯数学的友军射线胶囊检查；平台层只需提供同队成员的有限快照。 */
public final class FiringLanePlanner {
	private static final double ENDPOINT_MARGIN = 0.02;
	private static final int MAXIMUM_CACHED_CLEAR_CHECKS = 100;
	private static final Result<?>[] CLEAR_RESULTS = createClearResults();

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
			return clearResult(0);
		}

		K blocker = null;
		double nearestProjection = Double.POSITIVE_INFINITY;
		int checks = 0;
		@SuppressWarnings("unchecked")
		List<Ally<K>> list = allies instanceof List<?> ? (List<Ally<K>>)allies : null;
		Iterator<Ally<K>> iterator = list == null ? allies.iterator() : null;
		int index = 0;
		while (checks < limit && (list != null ? index < list.size() : iterator.hasNext())) {
			Ally<K> ally = list != null ? list.get(index++) : iterator.next();
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
		return blocker == null ? clearResult(checks) : new Result<>(false, blocker, checks);
	}

	/** Allocation-free hot-path overload for platform adapters that retain one buffer per goal instance. */
	public static <K> Result<K> check(
		final Vec3d origin,
		final Vec3d target,
		final AllyBuffer<K> allies,
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
			return clearResult(0);
		}

		K blocker = null;
		double nearestProjection = Double.POSITIVE_INFINITY;
		int checks = 0;
		for (int index = 0; index < allies.size && checks < limit; index++) {
			int offset = index * AllyBuffer.VALUE_STRIDE;
			double allyX = allies.values[offset];
			double allyY = allies.values[offset + 1];
			double allyZ = allies.values[offset + 2];
			double radius = allies.values[offset + 3];
			checks++;
			double relativeX = allyX - origin.x();
			double relativeY = allyY - origin.y();
			double relativeZ = allyZ - origin.z();
			double projection = (
				relativeX * segmentX + relativeY * segmentY + relativeZ * segmentZ
			) / lengthSquared;
			if (projection <= ENDPOINT_MARGIN || projection >= 1.0 - ENDPOINT_MARGIN) {
				continue;
			}
			double closestX = origin.x() + segmentX * projection;
			double closestY = origin.y() + segmentY * projection;
			double closestZ = origin.z() + segmentZ * projection;
			double separationX = closestX - allyX;
			double separationY = closestY - allyY;
			double separationZ = closestZ - allyZ;
			double separationSquared = separationX * separationX
				+ separationY * separationY
				+ separationZ * separationZ;
			if (separationSquared <= radius * radius && projection < nearestProjection) {
				blocker = allies.idAt(index);
				nearestProjection = projection;
			}
		}
		return blocker == null ? clearResult(checks) : new Result<>(false, blocker, checks);
	}

	private static Result<?>[] createClearResults() {
		Result<?>[] results = new Result<?>[MAXIMUM_CACHED_CLEAR_CHECKS + 1];
		for (int checks = 0; checks < results.length; checks++) {
			results[checks] = new Result<>(true, null, checks);
		}
		return results;
	}

	@SuppressWarnings("unchecked")
	private static <K> Result<K> clearResult(final int checks) {
		return checks >= 0 && checks < CLEAR_RESULTS.length
			? (Result<K>)CLEAR_RESULTS[checks]
			: new Result<>(true, null, checks);
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

	public static final class AllyBuffer<K> {
		private static final int INITIAL_CAPACITY = 8;
		private static final int VALUE_STRIDE = 4;

		private Object[] ids = new Object[INITIAL_CAPACITY];
		private double[] values = new double[INITIAL_CAPACITY * VALUE_STRIDE];
		private int size;

		public void add(final K id, final double x, final double y, final double z, final double radius) {
			Objects.requireNonNull(id, "id");
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("ally position must be finite");
			}
			this.ensureCapacity(this.size + 1);
			this.ids[this.size] = id;
			int offset = this.size * VALUE_STRIDE;
			this.values[offset] = x;
			this.values[offset + 1] = y;
			this.values[offset + 2] = z;
			this.values[offset + 3] = Double.isFinite(radius) ? Math.clamp(radius, 0.05, 4.0) : 0.75;
			this.size++;
		}

		public void clear() {
			Arrays.fill(this.ids, 0, this.size, null);
			this.size = 0;
		}

		public int size() {
			return this.size;
		}

		@SuppressWarnings("unchecked")
		private K idAt(final int index) {
			return (K)this.ids[index];
		}

		private void ensureCapacity(final int required) {
			if (required <= this.ids.length) {
				return;
			}
			int capacity = Math.max(required, this.ids.length * 2);
			this.ids = Arrays.copyOf(this.ids, capacity);
			this.values = Arrays.copyOf(this.values, capacity * VALUE_STRIDE);
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

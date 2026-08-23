package com.wjz.mobsthinknow.shared.spatial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * 与游戏平台无关的二维空间桶，用固定九桶窗口和原始候选预算约束附近查询。
 *
 * <p>索引按 {@code group} 完全隔离世界、维度或目标组。查询半径不得大于桶边长，因而只需访问
 * 种子所在桶及周围八桶；即使某个桶被塞入大量候选，也会在 {@code rawScanLimit} 处停止。
 * 候选以对象身份登记，因此值相等但并非同一对象的两个候选仍可同时存在。</p>
 *
 * @param <G> 隔离组键，例如世界 UUID 或目标实体 ID
 * @param <T> 候选快照类型
 */
public final class BoundedSpatialIndex<G, T> {
	private static final double RADIUS_EPSILON = 1.0E-9;

	private final double cellSize;
	private final Function<T, G> groupId;
	private final ToDoubleFunction<T> xCoordinate;
	private final ToDoubleFunction<T> zCoordinate;
	private final Map<G, Map<CellKey, LinkedHashMap<IdentityKey<T>, T>>> groups = new HashMap<>();
	private final IdentityHashMap<T, Membership<G, T>> memberships = new IdentityHashMap<>();

	public BoundedSpatialIndex(
		final double cellSize,
		final Function<T, G> groupId,
		final ToDoubleFunction<T> xCoordinate,
		final ToDoubleFunction<T> zCoordinate
	) {
		if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
			throw new IllegalArgumentException("cellSize must be finite and positive");
		}
		this.cellSize = cellSize;
		this.groupId = Objects.requireNonNull(groupId, "groupId");
		this.xCoordinate = Objects.requireNonNull(xCoordinate, "xCoordinate");
		this.zCoordinate = Objects.requireNonNull(zCoordinate, "zCoordinate");
	}

	/** 新增候选；同一对象已经存在时等同于 {@link #upsert(Object)}。 */
	public void add(final T candidate) {
		this.upsert(candidate);
	}

	/**
	 * 新增候选，或在其组/坐标改变后以 O(1) 桶定位更新登记。
	 *
	 * @return 候选是否首次加入或移动到了不同桶
	 */
	public boolean upsert(final T candidate) {
		Objects.requireNonNull(candidate, "candidate");
		G group = Objects.requireNonNull(this.groupId.apply(candidate), "candidate group");
		CellKey cell = this.cellFor(candidate);
		Membership<G, T> current = this.memberships.get(candidate);
		if (current != null && current.group().equals(group) && current.cell().equals(cell)) {
			return false;
		}
		if (current != null) {
			this.removeMembership(current);
		}

		IdentityKey<T> identity = current == null ? new IdentityKey<>(candidate) : current.identity();
		this.groups
			.computeIfAbsent(group, ignored -> new HashMap<>())
			.computeIfAbsent(cell, ignored -> new LinkedHashMap<>())
			.put(identity, candidate);
		this.memberships.put(candidate, new Membership<>(group, cell, identity));
		return true;
	}

	/** @return 候选原先是否登记在索引中。 */
	public boolean remove(final T candidate) {
		Membership<G, T> removed = this.memberships.remove(candidate);
		if (removed == null) {
			return false;
		}
		this.removeMembership(removed);
		return true;
	}

	public void clear() {
		this.groups.clear();
		this.memberships.clear();
	}

	public int size() {
		return this.memberships.size();
	}

	/** 保留 Fabric 既有语义：结果第一项为种子。 */
	public ScanResult<T> collectNearby(
		final T seed,
		final Predicate<T> isAvailable,
		final SquaredDistance<T> squaredDistance,
		final double radiusSquared,
		final int acceptedLimit,
		final int rawScanLimit
	) {
		return this.collectNearby(
			seed,
			isAvailable,
			squaredDistance,
			radiusSquared,
			acceptedLimit,
			rawScanLimit,
			true
		);
	}

	/**
	 * 查询同组九桶中的邻居。{@code rawChecks} 包含扫描到种子本身及随后被谓词拒绝的候选，便于平台端
	 * 精确观测本次查询成本。
	 */
	public ScanResult<T> collectNearby(
		final T seed,
		final Predicate<T> isAvailable,
		final SquaredDistance<T> squaredDistance,
		final double radiusSquared,
		final int acceptedLimit,
		final int rawScanLimit,
		final boolean includeSeed
	) {
		Objects.requireNonNull(seed, "seed");
		Objects.requireNonNull(isAvailable, "isAvailable");
		Objects.requireNonNull(squaredDistance, "squaredDistance");
		this.validateRadius(radiusSquared);

		List<T> accepted = new ArrayList<>();
		if (includeSeed && acceptedLimit > 0) {
			accepted.add(seed);
		}
		if (accepted.size() >= acceptedLimit || acceptedLimit <= 0 || rawScanLimit <= 0) {
			return new ScanResult<>(List.copyOf(accepted), 0);
		}

		G group = Objects.requireNonNull(this.groupId.apply(seed), "seed group");
		Map<CellKey, LinkedHashMap<IdentityKey<T>, T>> cells = this.groups.get(group);
		if (cells == null) {
			return new ScanResult<>(List.copyOf(accepted), 0);
		}

		CellKey center = this.cellFor(seed);
		int rawChecks = 0;
		outer:
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				Map<IdentityKey<T>, T> bucket = cells.get(new CellKey(center.x() + dx, center.z() + dz));
				if (bucket == null) {
					continue;
				}
				for (T candidate : bucket.values()) {
					if (rawChecks >= rawScanLimit) {
						break outer;
					}
					rawChecks++;
					if (candidate == seed || !isAvailable.test(candidate)) {
						continue;
					}
					if (squaredDistance.between(seed, candidate) <= radiusSquared) {
						accepted.add(candidate);
						if (accepted.size() >= acceptedLimit) {
							break outer;
						}
					}
				}
			}
		}
		return new ScanResult<>(List.copyOf(accepted), rawChecks);
	}

	private void validateRadius(final double radiusSquared) {
		double maximum = this.cellSize * this.cellSize;
		if (!Double.isFinite(radiusSquared) || radiusSquared < 0.0
			|| radiusSquared > maximum + RADIUS_EPSILON) {
			throw new IllegalArgumentException("radius must be finite and no larger than cellSize");
		}
	}

	private CellKey cellFor(final T candidate) {
		double x = this.xCoordinate.applyAsDouble(candidate);
		double z = this.zCoordinate.applyAsDouble(candidate);
		if (!Double.isFinite(x) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("candidate coordinates must be finite");
		}
		return new CellKey((int)Math.floor(x / this.cellSize), (int)Math.floor(z / this.cellSize));
	}

	private void removeMembership(final Membership<G, T> membership) {
		Map<CellKey, LinkedHashMap<IdentityKey<T>, T>> cells = this.groups.get(membership.group());
		if (cells == null) {
			return;
		}
		Map<IdentityKey<T>, T> bucket = cells.get(membership.cell());
		if (bucket != null) {
			bucket.remove(membership.identity());
			if (bucket.isEmpty()) {
				cells.remove(membership.cell());
			}
		}
		if (cells.isEmpty()) {
			this.groups.remove(membership.group());
		}
	}

	@FunctionalInterface
	public interface SquaredDistance<T> {
		double between(T first, T second);
	}

	public record ScanResult<T>(List<T> candidates, int rawChecks) {
		public ScanResult {
			candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
			if (rawChecks < 0) {
				throw new IllegalArgumentException("rawChecks must be non-negative");
			}
		}
	}

	private record CellKey(int x, int z) {
	}

	private record Membership<G, T>(G group, CellKey cell, IdentityKey<T> identity) {
	}

	private static final class IdentityKey<T> {
		private final T candidate;
		private final int hash;

		private IdentityKey(final T candidate) {
			this.candidate = candidate;
			this.hash = System.identityHashCode(candidate);
		}

		@Override
		public boolean equals(final Object other) {
			return this == other || (other instanceof IdentityKey<?> key && this.candidate == key.candidate);
		}

		@Override
		public int hashCode() {
			return this.hash;
		}
	}
}

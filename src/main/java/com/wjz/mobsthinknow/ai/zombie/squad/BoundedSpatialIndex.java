package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * 按“目标组 + 二维空间格”组织候选，并对单次查询设置硬扫描预算。
 *
 * <p>这个类完全不依赖 Minecraft，可直接做单元测试。查询只访问种子所在格及周围八格；即使某格
 * 塞入大量实体，也会在 {@code rawScanLimit} 处停止，从数据结构层保证不会做一次无界全量扫描。</p>
 */
final class BoundedSpatialIndex<T> {
	private final double cellSize;
	private final ToIntFunction<T> groupId;
	private final ToDoubleFunction<T> xCoordinate;
	private final ToDoubleFunction<T> zCoordinate;
	private final Map<Integer, Map<CellKey, List<T>>> groups = new HashMap<>();

	BoundedSpatialIndex(
		final double cellSize,
		final ToIntFunction<T> groupId,
		final ToDoubleFunction<T> xCoordinate,
		final ToDoubleFunction<T> zCoordinate
	) {
		if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
			throw new IllegalArgumentException("cellSize must be finite and positive");
		}
		this.cellSize = cellSize;
		this.groupId = groupId;
		this.xCoordinate = xCoordinate;
		this.zCoordinate = zCoordinate;
	}

	void add(final T candidate) {
		this.groups
			.computeIfAbsent(this.groupId.applyAsInt(candidate), ignored -> new HashMap<>())
			.computeIfAbsent(this.cellFor(candidate), ignored -> new ArrayList<>())
			.add(candidate);
	}

	ScanResult<T> collectNearby(
		final T seed,
		final Predicate<T> isAvailable,
		final SquaredDistance<T> squaredDistance,
		final double radiusSquared,
		final int acceptedLimit,
		final int rawScanLimit
	) {
		List<T> accepted = new ArrayList<>();
		accepted.add(seed);
		if (acceptedLimit <= 1 || rawScanLimit <= 0) {
			return new ScanResult<>(accepted, 0);
		}

		Map<CellKey, List<T>> cells = this.groups.get(this.groupId.applyAsInt(seed));
		if (cells == null) {
			return new ScanResult<>(accepted, 0);
		}

		CellKey center = this.cellFor(seed);
		int rawChecks = 0;
		outer:
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				List<T> bucket = cells.get(new CellKey(center.x + dx, center.z + dz));
				if (bucket == null) {
					continue;
				}
				for (T candidate : bucket) {
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
		return new ScanResult<>(accepted, rawChecks);
	}

	private CellKey cellFor(final T candidate) {
		return new CellKey(
			(int)Math.floor(this.xCoordinate.applyAsDouble(candidate) / this.cellSize),
			(int)Math.floor(this.zCoordinate.applyAsDouble(candidate) / this.cellSize)
		);
	}

	@FunctionalInterface
	interface SquaredDistance<T> {
		double between(T first, T second);
	}

	record ScanResult<T>(List<T> candidates, int rawChecks) {
	}

	private record CellKey(int x, int z) {
	}
}

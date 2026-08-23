package com.wjz.mobsthinknow.shared.spatial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
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
	private final Map<G, LongBucketMap<IdentityBucket<T>>> groups = new HashMap<>();
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
		long cell = this.cellFor(candidate);
		Membership<G, T> current = this.memberships.get(candidate);
		if (current != null && current.group().equals(group) && current.cell() == cell) {
			return false;
		}
		if (current != null) {
			this.removeMembership(current);
		}

		BucketNode<T> node = current == null ? new BucketNode<>(candidate) : current.node();
		LongBucketMap<IdentityBucket<T>> cells = this.groups.computeIfAbsent(
			group,
			ignored -> new LongBucketMap<>()
		);
		IdentityBucket<T> bucket = cells.get(cell);
		if (bucket == null) {
			bucket = new IdentityBucket<>();
			cells.put(cell, bucket);
		}
		bucket.add(node);
		this.memberships.put(candidate, new Membership<>(group, cell, node));
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
		LongBucketMap<IdentityBucket<T>> cells = this.groups.get(group);
		if (cells == null) {
			return new ScanResult<>(List.copyOf(accepted), 0);
		}

		long center = this.cellFor(seed);
		int centerX = unpackX(center);
		int centerZ = unpackZ(center);
		int rawChecks = 0;
		outer:
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				IdentityBucket<T> bucket = cells.get(packCell(centerX + dx, centerZ + dz));
				if (bucket == null) {
					continue;
				}
				for (BucketNode<T> node = bucket.first(); node != null; node = node.next()) {
					T candidate = node.candidate();
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

	private long cellFor(final T candidate) {
		double x = this.xCoordinate.applyAsDouble(candidate);
		double z = this.zCoordinate.applyAsDouble(candidate);
		if (!Double.isFinite(x) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("candidate coordinates must be finite");
		}
		return packCell((int)Math.floor(x / this.cellSize), (int)Math.floor(z / this.cellSize));
	}

	private void removeMembership(final Membership<G, T> membership) {
		LongBucketMap<IdentityBucket<T>> cells = this.groups.get(membership.group());
		if (cells == null) {
			return;
		}
		IdentityBucket<T> bucket = cells.get(membership.cell());
		if (bucket != null) {
			bucket.remove(membership.node());
			if (bucket.isEmpty()) {
				cells.remove(membership.cell());
			}
		}
		if (cells.isEmpty()) {
			this.groups.remove(membership.group());
		}
	}

	private static long packCell(final int x, final int z) {
		return ((long)x << Integer.SIZE) ^ (z & 0xFFFFFFFFL);
	}

	private static int unpackX(final long cell) {
		return (int)(cell >> Integer.SIZE);
	}

	private static int unpackZ(final long cell) {
		return (int)cell;
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

	private record Membership<G, T>(G group, long cell, BucketNode<T> node) {
	}

	/** 成员身份已由外层 memberships 保证；链表节点让热查询无需 Iterator 对象。 */
	private static final class IdentityBucket<T> {
		private BucketNode<T> first;
		private BucketNode<T> last;
		private int size;

		private void add(final BucketNode<T> node) {
			node.previous = this.last;
			node.next = null;
			if (this.last == null) {
				this.first = node;
			} else {
				this.last.next = node;
			}
			this.last = node;
			this.size++;
		}

		private void remove(final BucketNode<T> node) {
			if (node.previous == null) {
				this.first = node.next;
			} else {
				node.previous.next = node.next;
			}
			if (node.next == null) {
				this.last = node.previous;
			} else {
				node.next.previous = node.previous;
			}
			node.previous = null;
			node.next = null;
			this.size--;
		}

		private BucketNode<T> first() {
			return this.first;
		}

		private boolean isEmpty() {
			return this.size == 0;
		}
	}

	private static final class BucketNode<T> {
		private final T candidate;
		private BucketNode<T> previous;
		private BucketNode<T> next;

		private BucketNode(final T candidate) {
			this.candidate = candidate;
		}

		private T candidate() {
			return this.candidate;
		}

		private BucketNode<T> next() {
			return this.next;
		}
	}

	/** 共享模块不引入平台集合依赖；这个小型开放寻址表让九桶查询保持 primitive long 查找。 */
	private static final class LongBucketMap<V> {
		private static final int INITIAL_CAPACITY = 16;
		private static final int LOAD_NUMERATOR = 3;
		private static final int LOAD_DENOMINATOR = 5;
		private static final byte OCCUPIED = 1;
		private static final byte DELETED = 2;

		private long[] keys = new long[INITIAL_CAPACITY];
		private Object[] values = new Object[INITIAL_CAPACITY];
		private byte[] states = new byte[INITIAL_CAPACITY];
		private int size;
		private int deleted;

		private V get(final long key) {
			int mask = this.keys.length - 1;
			int slot = mix(key) & mask;
			while (this.states[slot] != 0) {
				if (this.states[slot] == OCCUPIED && this.keys[slot] == key) {
					return this.valueAt(slot);
				}
				slot = slot + 1 & mask;
			}
			return null;
		}

		private void put(final long key, final V value) {
			Objects.requireNonNull(value, "value");
			if ((this.size + this.deleted + 1) * LOAD_DENOMINATOR
				>= this.keys.length * LOAD_NUMERATOR) {
				this.rehash(this.keys.length << 1);
			}
			this.insert(key, value);
		}

		private V remove(final long key) {
			int mask = this.keys.length - 1;
			int slot = mix(key) & mask;
			while (this.states[slot] != 0) {
				if (this.states[slot] == OCCUPIED && this.keys[slot] == key) {
					V removed = this.valueAt(slot);
					this.values[slot] = null;
					this.states[slot] = DELETED;
					this.size--;
					this.deleted++;
					if (this.size > 0 && this.deleted > this.size) {
						this.rehash(this.keys.length);
					}
					return removed;
				}
				slot = slot + 1 & mask;
			}
			return null;
		}

		private boolean isEmpty() {
			return this.size == 0;
		}

		private void insert(final long key, final V value) {
			int mask = this.keys.length - 1;
			int slot = mix(key) & mask;
			int firstDeleted = -1;
			while (this.states[slot] != 0) {
				if (this.states[slot] == OCCUPIED && this.keys[slot] == key) {
					this.values[slot] = value;
					return;
				}
				if (firstDeleted < 0 && this.states[slot] == DELETED) {
					firstDeleted = slot;
				}
				slot = slot + 1 & mask;
			}
			if (firstDeleted >= 0) {
				slot = firstDeleted;
				this.deleted--;
			}
			this.keys[slot] = key;
			this.values[slot] = value;
			this.states[slot] = OCCUPIED;
			this.size++;
		}

		private void rehash(final int capacity) {
			long[] previousKeys = this.keys;
			Object[] previousValues = this.values;
			byte[] previousStates = this.states;
			this.keys = new long[capacity];
			this.values = new Object[capacity];
			this.states = new byte[capacity];
			this.size = 0;
			this.deleted = 0;
			for (int index = 0; index < previousKeys.length; index++) {
				if (previousStates[index] == OCCUPIED) {
					this.insert(previousKeys[index], valueAt(previousValues, index));
				}
			}
		}

		@SuppressWarnings("unchecked")
		private V valueAt(final int slot) {
			return (V)this.values[slot];
		}

		@SuppressWarnings("unchecked")
		private static <V> V valueAt(final Object[] values, final int slot) {
			return (V)values[slot];
		}

		private static int mix(final long key) {
			long value = key;
			value ^= value >>> 33;
			value *= 0xFF51AFD7ED558CCDL;
			value ^= value >>> 33;
			value *= 0xC4CEB9FE1A85EC53L;
			value ^= value >>> 33;
			return (int)(value ^ value >>> Integer.SIZE);
		}
	}

}

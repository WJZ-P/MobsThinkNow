package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * 单支小队共享的有界局部危险记忆。
 *
 * <p>成员只上报自己真实遇到的失败位置；每条记录带过期时间，容量满时优先移除最早过期的条目。
 * 默认容量 32，因此路径检查即使逐条比较也具有固定上限，不会随世界实体数量增长。</p>
 */
public final class SquadDangerMemory {
	public static final int DEFAULT_CAPACITY = 32;

	private final int capacity;
	private final Map<BlockPos, Entry> entries = new LinkedHashMap<>();

	public SquadDangerMemory() {
		this(DEFAULT_CAPACITY);
	}

	SquadDangerMemory(final int capacity) {
		this.capacity = Math.max(1, capacity);
	}

	public void report(
		final BlockPos position,
		final SquadDangerKind kind,
		final int severity,
		final long now,
		final long lifetimeTicks
	) {
		this.prune(now);
		BlockPos key = position.immutable();
		Entry previous = this.entries.get(key);
		int boundedSeverity = Math.clamp(severity, 1, 3);
		long expiresAt = now + Math.max(1L, lifetimeTicks);
		if (previous != null) {
			this.entries.put(key, new Entry(
				key,
				boundedSeverity >= previous.severity ? kind : previous.kind,
				Math.max(previous.severity, boundedSeverity),
				now,
				Math.max(previous.expiresAt, expiresAt)
			));
			return;
		}

		if (this.entries.size() >= this.capacity) {
			BlockPos eviction = this.entries.entrySet().stream()
				.min(Comparator.comparingLong(entry -> entry.getValue().expiresAt))
				.map(Map.Entry::getKey)
				.orElse(null);
			if (eviction != null) {
				this.entries.remove(eviction);
			}
		}
		this.entries.put(key, new Entry(key, kind, boundedSeverity, now, expiresAt));
	}

	public boolean isDangerousNear(
		final BlockPos position,
		final int horizontalRadius,
		final int verticalRadius,
		final long now
	) {
		this.prune(now);
		int horizontal = Math.max(0, horizontalRadius);
		int vertical = Math.max(0, verticalRadius);
		for (Entry entry : this.entries.values()) {
			BlockPos danger = entry.position;
			if (Math.abs(danger.getX() - position.getX()) <= horizontal
				&& Math.abs(danger.getY() - position.getY()) <= vertical
				&& Math.abs(danger.getZ() - position.getZ()) <= horizontal) {
				return true;
			}
		}
		return false;
	}

	public int activeEntryCount(final long now) {
		this.prune(now);
		return this.entries.size();
	}

	public List<Entry> snapshot(final long now) {
		this.prune(now);
		return List.copyOf(new ArrayList<>(this.entries.values()));
	}

	public void clear() {
		this.entries.clear();
	}

	private void prune(final long now) {
		this.entries.values().removeIf(entry -> entry.expiresAt < now);
	}

	public record Entry(
		BlockPos position,
		SquadDangerKind kind,
		int severity,
		long reportedAt,
		long expiresAt
	) {
	}
}

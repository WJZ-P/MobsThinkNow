package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 只保存可见目标和真实攻击者证据的有界威胁记忆，不读取玩家隐藏数据。 */
public final class SquadThreatMemory {
	public static final int MAXIMUM_THREATS = 8;
	private static final double DECAY_PER_TICK = 0.5;

	private final Map<Integer, Entry> entries = new HashMap<>();

	public ObservationResult observe(final int targetId, final Evidence evidence, final long now) {
		this.prune(now);
		Entry previous = this.entries.get(targetId);
		double previousScore = previous == null ? 0.0 : decayedScore(previous, now);
		double score = Math.min(100.0, Math.max(previousScore, evidence.minimumScore) + evidence.increment);
		this.entries.put(targetId, new Entry(targetId, score, now));
		int evictedTargetId = -1;
		if (this.entries.size() > MAXIMUM_THREATS) {
			Integer weakest = this.entries.values().stream()
				.min(Comparator.comparingDouble(entry -> decayedScore(entry, now)))
				.map(Entry::targetId)
				.orElse(null);
			if (weakest != null) {
				this.entries.remove(weakest);
				evictedTargetId = weakest;
			}
		}
		return new ObservationResult(this.entries.containsKey(targetId), evictedTargetId);
	}

	public List<ThreatScore> snapshot(final long now) {
		this.prune(now);
		List<ThreatScore> result = new ArrayList<>(this.entries.size());
		for (Entry entry : this.entries.values()) {
			result.add(new ThreatScore(entry.targetId, decayedScore(entry, now)));
		}
		result.sort(Comparator.comparingDouble(ThreatScore::score).reversed().thenComparingInt(ThreatScore::targetId));
		return List.copyOf(result);
	}

	public void remove(final int targetId) {
		this.entries.remove(targetId);
	}

	public void clear() {
		this.entries.clear();
	}

	private void prune(final long now) {
		this.entries.values().removeIf(entry -> decayedScore(entry, now) < 1.0);
	}

	private static double decayedScore(final Entry entry, final long now) {
		return Math.max(0.0, entry.score - Math.max(0L, now - entry.observedAt) * DECAY_PER_TICK);
	}

	public enum Evidence {
		VISIBLE_TARGET(30.0, 5.0),
		DIRECT_ATTACK(65.0, 15.0);

		private final double minimumScore;
		private final double increment;

		Evidence(final double minimumScore, final double increment) {
			this.minimumScore = minimumScore;
			this.increment = increment;
		}
	}

	private record Entry(int targetId, double score, long observedAt) {
	}

	public record ThreatScore(int targetId, double score) {
	}

	public record ObservationResult(boolean retained, int evictedTargetId) {
	}
}

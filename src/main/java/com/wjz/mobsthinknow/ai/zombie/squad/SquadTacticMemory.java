package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * 只接收“本拍确实被队员看见”的证据，并用迟滞分数过滤玩家一帧举盾、跳跃或入水造成的抖动。
 */
public final class SquadTacticMemory {
	private static final int ACTIVATION_SCORE = 4;
	private static final int RETENTION_SCORE = 2;
	private static final int MAXIMUM_SCORE = 8;
	private static final int OBSERVATION_GAIN = 2;
	private static final int PASSIVE_DECAY = 1;
	private static final int MEMORY_TICKS = 100;

	private final EnumMap<ObservedTargetTactic, Evidence> evidence =
		new EnumMap<>(ObservedTargetTactic.class);
	private ObservedTargetTactic primary = ObservedTargetTactic.NONE;

	public Update observe(final EnumSet<ObservedTargetTactic> visibleSignals, final long now) {
		for (ObservedTargetTactic tactic : ObservedTargetTactic.values()) {
			if (tactic == ObservedTargetTactic.NONE) {
				continue;
			}
			Evidence current = this.evidence.computeIfAbsent(tactic, ignored -> new Evidence());
			if (visibleSignals.contains(tactic)) {
				current.score = Math.min(MAXIMUM_SCORE, current.score + OBSERVATION_GAIN);
				current.lastObservedAt = now;
			} else {
				current.score = Math.max(0, current.score - PASSIVE_DECAY);
			}
		}

		ObservedTargetTactic previous = this.primary;
		this.primary = this.choosePrimary(now);
		return new Update(this.primary, this.scoreOf(this.primary), previous != this.primary);
	}

	/** 没有队员看见目标时只让既有证据自然老化，不把未知状态当作反证。 */
	public Update age(final long now) {
		ObservedTargetTactic previous = this.primary;
		this.primary = this.choosePrimary(now);
		return new Update(this.primary, this.scoreOf(this.primary), previous != this.primary);
	}

	public ObservedTargetTactic primary(final long now) {
		this.primary = this.choosePrimary(now);
		return this.primary;
	}

	public void clear() {
		this.evidence.clear();
		this.primary = ObservedTargetTactic.NONE;
	}

	private ObservedTargetTactic choosePrimary(final long now) {
		ObservedTargetTactic selected = ObservedTargetTactic.NONE;
		int selectedScore = 0;
		for (ObservedTargetTactic tactic : ObservedTargetTactic.values()) {
			if (tactic == ObservedTargetTactic.NONE) {
				continue;
			}
			Evidence current = this.evidence.get(tactic);
			int requiredScore = tactic == this.primary ? RETENTION_SCORE : ACTIVATION_SCORE;
			if (current == null
				|| current.score < requiredScore
				|| now - current.lastObservedAt > MEMORY_TICKS) {
				continue;
			}
			if (current.score > selectedScore) {
				selected = tactic;
				selectedScore = current.score;
			}
		}
		return selected;
	}

	private int scoreOf(final ObservedTargetTactic tactic) {
		Evidence current = this.evidence.get(tactic);
		return current == null ? 0 : current.score;
	}

	public record Update(ObservedTargetTactic primary, int score, boolean changed) {
	}

	private static final class Evidence {
		private int score;
		private long lastObservedAt = Long.MIN_VALUE;
	}
}

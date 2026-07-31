package com.wjz.mobsthinknow.ai.giant;

/** 射手登顶时可见的接取、举升、肩部停顿和登顶阶段。 */
public enum GiantBoardingPhase {
	NONE(1),
	CATCHING(12),
	LIFTING(16),
	SHOULDER(6),
	TO_HEAD(10);

	private final int durationTicks;

	GiantBoardingPhase(final int durationTicks) {
		this.durationTicks = durationTicks;
	}

	public int durationTicks() {
		return this.durationTicks;
	}

	public static GiantBoardingPhase fromId(final int id) {
		GiantBoardingPhase[] values = values();
		return id >= 0 && id < values.length ? values[id] : NONE;
	}
}

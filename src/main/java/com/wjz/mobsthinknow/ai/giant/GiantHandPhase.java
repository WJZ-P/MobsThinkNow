package com.wjz.mobsthinknow.ai.giant;

/**
 * 单只手的独立工作阶段。
 *
 * <p>阶段和起始 tick 会同步到客户端；候选人的 UUID 只在服务端持久化，运行时实体 ID
 * 负责客户端渲染。这样左右手即使有一侧候选死亡、掉队或提前抛出，另一侧也不会换手。</p>
 */
public enum GiantHandPhase {
	EMPTY(1),
	RENDEZVOUS(10),
	PICKUP(8),
	HOLDING(5),
	AIMING(12),
	THROWING(8),
	COOLDOWN(12);

	private final int nominalDurationTicks;

	GiantHandPhase(final int nominalDurationTicks) {
		this.nominalDurationTicks = nominalDurationTicks;
	}

	public int nominalDurationTicks() {
		return this.nominalDurationTicks;
	}

	public static GiantHandPhase fromId(final int id) {
		GiantHandPhase[] values = values();
		return id >= 0 && id < values.length ? values[id] : EMPTY;
	}
}

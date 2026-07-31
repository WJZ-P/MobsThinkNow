package com.wjz.mobsthinknow.ai.giant;

import org.jspecify.annotations.Nullable;

/**
 * 巨人可同步给客户端的近身格斗动作。
 *
 * <p>持续时间和命中帧属于服务端与客户端共同遵守的动作契约：服务端只在
 * {@link #impactTick()} 结算一次伤害，客户端则用同一时间轴播放前摇、命中和后摇。
 * 左右动作拆成独立枚举值，避免依赖一个额外的“当前手”同步字段。</p>
 */
public enum GiantMeleeAction {
	NONE(Family.NONE, 1, -1, 0.0F, 0.0, 0.0, null, false),
	SWEEP_RIGHT(Family.SWEEP, 24, 11, 0.72F, 1.15, 0.16, GiantHand.RIGHT, false),
	SWEEP_LEFT(Family.SWEEP, 24, 11, 0.72F, 1.15, 0.16, GiantHand.LEFT, false),
	SLAP_RIGHT(Family.SLAP, 18, 8, 0.92F, 1.65, 0.24, GiantHand.RIGHT, false),
	SLAP_LEFT(Family.SLAP, 18, 8, 0.92F, 1.65, 0.24, GiantHand.LEFT, false),
	STOMP_RIGHT(Family.STOMP, 28, 14, 0.76F, 0.95, 0.42, null, false),
	STOMP_LEFT(Family.STOMP, 28, 14, 0.76F, 0.95, 0.42, null, false),
	GROUND_SMASH(Family.GROUND_SMASH, 36, 20, 1.28F, 1.35, 0.62, null, true);

	private final Family family;
	private final int durationTicks;
	private final int impactTick;
	private final float damageMultiplier;
	private final double knockback;
	private final double verticalLaunch;
	private final @Nullable GiantHand actionHand;
	private final boolean requiresBothHands;

	GiantMeleeAction(
		final Family family,
		final int durationTicks,
		final int impactTick,
		final float damageMultiplier,
		final double knockback,
		final double verticalLaunch,
		final @Nullable GiantHand actionHand,
		final boolean requiresBothHands
	) {
		this.family = family;
		this.durationTicks = durationTicks;
		this.impactTick = impactTick;
		this.damageMultiplier = damageMultiplier;
		this.knockback = knockback;
		this.verticalLaunch = verticalLaunch;
		this.actionHand = actionHand;
		this.requiresBothHands = requiresBothHands;
	}

	public Family family() {
		return this.family;
	}

	public int durationTicks() {
		return this.durationTicks;
	}

	public int impactTick() {
		return this.impactTick;
	}

	public float damageMultiplier() {
		return this.damageMultiplier;
	}

	public double knockback() {
		return this.knockback;
	}

	public double verticalLaunch() {
		return this.verticalLaunch;
	}

	public @Nullable GiantHand actionHand() {
		return this.actionHand;
	}

	public boolean requiresBothHands() {
		return this.requiresBothHands;
	}

	public boolean usesHand(final GiantHand hand) {
		return this.requiresBothHands || this.actionHand == hand;
	}

	public boolean isActive() {
		return this != NONE;
	}

	public static GiantMeleeAction fromId(final int id) {
		GiantMeleeAction[] values = values();
		return id >= 0 && id < values.length ? values[id] : NONE;
	}

	public enum Family {
		NONE,
		SWEEP,
		SLAP,
		STOMP,
		GROUND_SMASH
	}
}

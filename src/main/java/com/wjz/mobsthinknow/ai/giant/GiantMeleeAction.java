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
	NONE(Family.NONE, 1, -1, 0.0F, 0.0, 0.0, null, false, 0, -1),
	SWEEP_RIGHT(Family.SWEEP, 24, 11, 0.72F, 1.15, 0.16, GiantHand.RIGHT, false, 4, -1),
	SWEEP_LEFT(Family.SWEEP, 24, 11, 0.72F, 1.15, 0.16, GiantHand.LEFT, false, 4, -1),
	SLAP_RIGHT(Family.SLAP, 18, 8, 0.92F, 1.65, 0.24, GiantHand.RIGHT, false, 3, -1),
	SLAP_LEFT(Family.SLAP, 18, 8, 0.92F, 1.65, 0.24, GiantHand.LEFT, false, 3, -1),
	STOMP_RIGHT(Family.STOMP, 28, 14, 0.76F, 0.95, 0.42, null, false, 5, -1),
	STOMP_LEFT(Family.STOMP, 28, 14, 0.76F, 0.95, 0.42, null, false, 5, -1),
	GROUND_SMASH(Family.GROUND_SMASH, 36, 20, 1.28F, 1.35, 0.62, null, true, 6, -1),
	KICK_RIGHT(Family.KICK, 24, 12, 0.66F, 2.25, 0.30, null, false, 4, -1),
	KICK_LEFT(Family.KICK, 24, 12, 0.66F, 2.25, 0.30, null, false, 4, -1),
	GRAB_RIGHT(Family.GRAB, 42, 12, 0.34F, 0.35, 0.08, GiantHand.RIGHT, false, 5, 28),
	GRAB_LEFT(Family.GRAB, 42, 12, 0.34F, 0.35, 0.08, GiantHand.LEFT, false, 5, 28);

	private final Family family;
	private final int durationTicks;
	private final int impactTick;
	private final float damageMultiplier;
	private final double knockback;
	private final double verticalLaunch;
	private final @Nullable GiantHand actionHand;
	private final boolean requiresBothHands;
	private final int aimLockLeadTicks;
	private final int releaseTick;

	GiantMeleeAction(
		final Family family,
		final int durationTicks,
		final int impactTick,
		final float damageMultiplier,
		final double knockback,
		final double verticalLaunch,
		final @Nullable GiantHand actionHand,
		final boolean requiresBothHands,
		final int aimLockLeadTicks,
		final int releaseTick
	) {
		this.family = family;
		this.durationTicks = durationTicks;
		this.impactTick = impactTick;
		this.damageMultiplier = damageMultiplier;
		this.knockback = knockback;
		this.verticalLaunch = verticalLaunch;
		this.actionHand = actionHand;
		this.requiresBothHands = requiresBothHands;
		this.aimLockLeadTicks = aimLockLeadTicks;
		this.releaseTick = releaseTick;
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

	/** 命中前预留给玩家侧闪的锁向窗口起点。 */
	public int aimLockTick() {
		return Math.max(0, this.impactTick - this.aimLockLeadTicks);
	}

	/** 抓取动作把命中帧用于接住目标，再在该帧真正将其抛出。 */
	public int releaseTick() {
		return this.releaseTick;
	}

	public boolean hasReleaseTick() {
		return this.releaseTick >= 0;
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
		GROUND_SMASH,
		KICK,
		GRAB
	}
}

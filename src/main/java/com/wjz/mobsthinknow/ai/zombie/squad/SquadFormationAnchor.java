package com.wjz.mobsthinknow.ai.zombie.squad;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 为后续进攻波次计算一个有界的动态阵型锚点。
 *
 * <p>它只处理不可变的向量快照，不读取世界与实体，因此不会额外制造实体扫描或寻路开销。
 * 预测只使用水平方向，并同时限制速度、观测年龄与最终偏移，避免瞬移或击退把整支小队带离战场。</p>
 */
public final class SquadFormationAnchor {
	private static final double MAXIMUM_TRACKED_SPEED = 0.45;
	private static final long MAXIMUM_OBSERVATION_AGE_TICKS = 6L;
	private static final double BASE_LOOKAHEAD_TICKS = 2.0;
	private static final double LOOKAHEAD_PER_INTELLIGENCE = 0.35;
	private static final double MAXIMUM_LEAD_DISTANCE = 3.5;
	private static final double MINIMUM_HORIZONTAL_SPEED_SQUARED = 1.0E-6;

	private SquadFormationAnchor() {
	}

	/**
	 * 根据最后可见位置与速度预测下一轮整队中心；智力越高，合理预判的时间窗越长。
	 *
	 * @param lastSeenPosition 最近一次可靠目标位置
	 * @param velocity 最近一次有直接视线时的目标速度
	 * @param leaderIntelligence 当前首领智力，按 1..10 截断
	 * @param observationAgeTicks 该目标位置距离当前 tick 的年龄
	 */
	public static Vec3 predict(
		final Vec3 lastSeenPosition,
		final @Nullable Vec3 velocity,
		final int leaderIntelligence,
		final long observationAgeTicks
	) {
		if (velocity == null
			|| !Double.isFinite(velocity.x)
			|| !Double.isFinite(velocity.z)) {
			return lastSeenPosition;
		}

		Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0, velocity.z);
		double speedSquared = horizontalVelocity.lengthSqr();
		if (speedSquared < MINIMUM_HORIZONTAL_SPEED_SQUARED) {
			return lastSeenPosition;
		}
		double speed = Math.sqrt(speedSquared);
		if (speed > MAXIMUM_TRACKED_SPEED) {
			horizontalVelocity = horizontalVelocity.scale(MAXIMUM_TRACKED_SPEED / speed);
		}

		int intelligence = Math.clamp(leaderIntelligence, 1, 10);
		long observationAge = Math.clamp(observationAgeTicks, 0L, MAXIMUM_OBSERVATION_AGE_TICKS);
		double lookaheadTicks = observationAge
			+ BASE_LOOKAHEAD_TICKS
			+ intelligence * LOOKAHEAD_PER_INTELLIGENCE;
		Vec3 lead = horizontalVelocity.scale(lookaheadTicks);
		double leadLength = lead.length();
		if (leadLength > MAXIMUM_LEAD_DISTANCE) {
			lead = lead.scale(MAXIMUM_LEAD_DISTANCE / leadLength);
		}
		return lastSeenPosition.add(lead);
	}
}

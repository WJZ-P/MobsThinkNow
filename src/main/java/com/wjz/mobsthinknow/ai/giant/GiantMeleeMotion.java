package com.wjz.mobsthinknow.ai.giant;

import net.minecraft.world.phys.Vec3;

/**
 * 巨人格斗的纯数学锁向与根运动曲线。
 *
 * <p>每段根运动在锁向帧之前完成，并用 smoothstep 分配到多个 tick；服务端随后仍会对
 * 每个小位移执行实体碰撞与落脚面检查，所以动画可以带重量地向前踏步，又不会穿墙或冲下悬崖。</p>
 */
public final class GiantMeleeMotion {
	private static final double EPSILON = 1.0E-8;

	private GiantMeleeMotion() {
	}

	public static boolean tracksTarget(final GiantMeleeAction action, final int actionTick) {
		return action.isActive() && actionTick < action.aimLockTick();
	}

	/** 将当前水平朝向以有限角速度转向目标，避免前摇期间瞬间旋转 180 度。 */
	public static Vec3 turnToward(
		final Vec3 currentForward,
		final Vec3 desiredForward,
		final double maximumRadians
	) {
		Vec3 current = horizontalUnit(currentForward, new Vec3(0.0, 0.0, 1.0));
		Vec3 desired = horizontalUnit(desiredForward, current);
		double currentAngle = Math.atan2(current.x, current.z);
		double desiredAngle = Math.atan2(desired.x, desired.z);
		double delta = wrapRadians(desiredAngle - currentAngle);
		double limited = Math.max(-Math.abs(maximumRadians), Math.min(Math.abs(maximumRadians), delta));
		double result = currentAngle + limited;
		return new Vec3(Math.sin(result), 0.0, Math.cos(result));
	}

	/** 返回当前 tick 应前移的距离；完整动作曲线求和等于该动作的设计踏步距离。 */
	public static double forwardStep(final GiantMeleeAction action, final int actionTick) {
		return switch (action.family()) {
			case SWEEP -> smoothStepDistance(actionTick, 2, action.aimLockTick() - 1, 0.56);
			case SLAP -> smoothStepDistance(actionTick, 1, action.aimLockTick() - 1, 0.42);
			case GROUND_SMASH -> smoothStepDistance(actionTick, 4, action.aimLockTick() - 1, 0.30);
			case KICK -> smoothStepDistance(actionTick, 2, action.aimLockTick() - 1, 0.82);
			case GRAB -> smoothStepDistance(actionTick, 2, action.aimLockTick() - 1, 0.92);
			case NONE, STOMP -> 0.0;
		};
	}

	private static double smoothStepDistance(
		final int tick,
		final int firstTick,
		final int lastTick,
		final double totalDistance
	) {
		if (tick < firstTick || tick > lastTick || lastTick < firstTick) {
			return 0.0;
		}
		int duration = lastTick - firstTick + 1;
		double before = (double)(tick - firstTick) / duration;
		double after = (double)(tick - firstTick + 1) / duration;
		return totalDistance * (smooth(after) - smooth(before));
	}

	private static Vec3 horizontalUnit(final Vec3 value, final Vec3 fallback) {
		Vec3 horizontal = value.multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() <= EPSILON) {
			return fallback;
		}
		return horizontal.normalize();
	}

	private static double smooth(final double value) {
		double clamped = Math.max(0.0, Math.min(1.0, value));
		return clamped * clamped * (3.0 - 2.0 * clamped);
	}

	private static double wrapRadians(final double value) {
		double wrapped = value % (Math.PI * 2.0);
		if (wrapped >= Math.PI) {
			wrapped -= Math.PI * 2.0;
		}
		if (wrapped < -Math.PI) {
			wrapped += Math.PI * 2.0;
		}
		return wrapped;
	}
}

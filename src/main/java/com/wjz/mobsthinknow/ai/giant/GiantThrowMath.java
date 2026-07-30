package com.wjz.mobsthinknow.ai.giant;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 只处理快照数值的巨人抛投弹道，方便脱离实体线程做单元测试。 */
public final class GiantThrowMath {
	private static final double MAXIMUM_HORIZONTAL_SPEED = 1.30;
	private static final double MINIMUM_FLIGHT_TICKS = 8.0;
	private static final double MAXIMUM_FLIGHT_TICKS = 22.0;
	private static final double GRAVITY_PER_TICK = 0.08;

	private GiantThrowMath() {
	}

	/**
	 * 以目标当前速度做有限提前量，并补偿实体重力。结果刻意低于鞘翅突击速度，给玩家留下可读反应窗口。
	 */
	public static Vec3 launchVelocity(
		final Vec3 origin,
		final Vec3 targetPosition,
		final Vec3 targetVelocity
	) {
		Vec3 initialDelta = targetPosition.subtract(origin);
		double horizontalDistance = Math.sqrt(initialDelta.x * initialDelta.x + initialDelta.z * initialDelta.z);
		double flightTicks = Mth.clamp(horizontalDistance / 0.92, MINIMUM_FLIGHT_TICKS, MAXIMUM_FLIGHT_TICKS);
		Vec3 predictedTarget = targetPosition.add(
			Mth.clamp(targetVelocity.x, -0.55, 0.55) * flightTicks * 0.72,
			0.0,
			Mth.clamp(targetVelocity.z, -0.55, 0.55) * flightTicks * 0.72
		);
		Vec3 delta = predictedTarget.subtract(origin);
		double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		double horizontalSpeed = Mth.clamp(horizontalLength / flightTicks, 0.62, MAXIMUM_HORIZONTAL_SPEED);
		double scale = horizontalLength < 1.0E-6 ? 0.0 : horizontalSpeed / horizontalLength;
		double verticalSpeed = delta.y / flightTicks + GRAVITY_PER_TICK * flightTicks * 0.50;
		return new Vec3(
			delta.x * scale,
			Mth.clamp(verticalSpeed, 0.28, 0.96),
			delta.z * scale
		);
	}

	public static double maximumHorizontalSpeed() {
		return MAXIMUM_HORIZONTAL_SPEED;
	}
}

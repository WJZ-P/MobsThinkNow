package com.wjz.mobsthinknow.ai.nether;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 下界战术共享的纯几何函数；不读取世界，也不在这里执行实体查询。 */
public final class NetherCombatMath {
	private static final double EPSILON = 1.0E-8;

	private NetherCombatMath() {
	}

	/**
	 * 根据目标速度和弹体速度计算有限提前量。
	 *
	 * <p>这里刻意不解完整弹道方程：恶魂火球与烈焰弹仍保留原版加速、散布和碰撞，
	 * 本函数只消除“永远瞄准目标旧位置”的低级误差。</p>
	 */
	public static Vec3 predictedPoint(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final double distance,
		final double projectileSpeed,
		final double strength,
		final double maximumLeadTicks
	) {
		double safeSpeed = Math.max(0.05, projectileSpeed);
		double leadTicks = Mth.clamp(distance / safeSpeed, 0.0, Math.max(0.0, maximumLeadTicks));
		double boundedStrength = Mth.clamp(strength, 0.0, 1.5);
		return targetPosition.add(targetVelocity.scale(leadTicks * boundedStrength));
	}

	/** 将水平向量旋转指定角度，长度保持不变。 */
	public static Vec3 rotateHorizontal(final Vec3 vector, final double radians) {
		double cosine = Math.cos(radians);
		double sine = Math.sin(radians);
		return new Vec3(
			vector.x * cosine - vector.z * sine,
			0.0,
			vector.x * sine + vector.z * cosine
		);
	}

	/** 只取水平单位向量；零向量使用稳定的实体散列方向，避免所有单位挤到同一边。 */
	public static Vec3 horizontalUnitOrEntityFallback(final Vec3 vector, final int entityId) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		if (horizontal.lengthSqr() >= EPSILON) {
			return horizontal.normalize();
		}
		double angle = Math.floorMod(entityId * 73, 360) * Mth.DEG_TO_RAD;
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	/**
	 * 生成带有限目标提前量的水平冲锋方向。
	 * 垂直速度由各生物自己的跳跃系统负责，避免几何层伪造飞行。
	 */
	public static Vec3 predictiveHorizontalDirection(
		final Vec3 attackerPosition,
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final double leadTicks,
		final int entityId
	) {
		Vec3 predicted = targetPosition.add(targetVelocity.scale(Mth.clamp(leadTicks, 0.0, 8.0)));
		return horizontalUnitOrEntityFallback(predicted.subtract(attackerPosition), entityId);
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

import net.minecraft.world.phys.Vec3;

/** 总攻阵位的纯向量数学；调用者仍须让原版导航判断真实地形可达性。 */
public final class SquadAssaultGeometry {
	private static final double MINIMUM_HORIZONTAL_LENGTH_SQUARED = 1.0E-6;

	private SquadAssaultGeometry() {
	}

	/** 把射手交替放到目标左右两侧，同时保留少量正面分量形成真正交叉射界。 */
	public static Vec3 crossfirePosition(
		final Vec3 targetPosition,
		final Vec3 targetFacing,
		final Vec3 fallbackFacing,
		final double preferredRange,
		final int rangedIndex
	) {
		Vec3 forward = horizontalUnit(targetFacing, fallbackFacing);
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		double range = Math.max(5.0, preferredRange);
		double pairStagger = Math.floorDiv(Math.max(0, rangedIndex), 2) * 0.75;
		double forwardDistance = Math.min(range * 0.52, range * 0.38 + pairStagger);
		double lateralDistance = Math.sqrt(Math.max(0.0, range * range - forwardDistance * forwardDistance));
		double side = (rangedIndex & 1) == 0 ? 1.0 : -1.0;
		return targetPosition.add(forward.scale(forwardDistance)).add(lateral.scale(lateralDistance * side));
	}

	/** 蜘蛛苦力怕组合先进入目标侧后方，再提交最终引信冲锋。 */
	public static Vec3 mountedBreachStaging(
		final Vec3 targetPosition,
		final Vec3 targetFacing,
		final Vec3 fallbackFacing,
		final int sideSeed
	) {
		Vec3 forward = horizontalUnit(targetFacing, fallbackFacing);
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		double side = (sideSeed & 1) == 0 ? 1.0 : -1.0;
		return targetPosition.subtract(forward.scale(5.5)).add(lateral.scale(4.0 * side));
	}

	/** 载着骷髅的蜘蛛停在侧向射界上，维持射程而不是把远程乘员送去贴脸。 */
	public static Vec3 mobileFireSupportStaging(
		final Vec3 targetPosition,
		final Vec3 targetFacing,
		final Vec3 fallbackFacing,
		final double preferredRange,
		final int sideSeed
	) {
		return crossfirePosition(targetPosition, targetFacing, fallbackFacing, preferredRange, sideSeed & 1);
	}

	static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = preferred.multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			horizontal = fallback.multiply(1.0, 0.0, 1.0);
		}
		return horizontal.lengthSqr() < MINIMUM_HORIZONTAL_LENGTH_SQUARED
			? new Vec3(0.0, 0.0, 1.0)
			: horizontal.normalize();
	}
}

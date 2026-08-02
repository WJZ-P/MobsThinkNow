package com.wjz.mobsthinknow.ai.creeper;

import net.minecraft.util.Mth;

/** 已引信苦力怕的小队疏散边界与速度计算；不读取世界或实体。 */
public final class CreeperBlastEvacuationMath {
	private static final double VANILLA_EXPLOSION_RADIUS = 3.0;
	private static final double POWERED_MULTIPLIER = 2.0;
	private static final double DAMAGE_DIAMETER_MULTIPLIER = 2.0;
	private static final double SAFETY_MARGIN = 0.75;
	private static final double RELEASE_HYSTERESIS = 1.0;

	private CreeperBlastEvacuationMath() {
	}

	/** 原版伤害候选范围约为爆炸强度的两倍；额外留 0.75 格处理实体碰撞箱。 */
	public static double dangerRadius(final boolean powered) {
		double explosionRadius = VANILLA_EXPLOSION_RADIUS * (powered ? POWERED_MULTIPLIER : 1.0);
		return explosionRadius * DAMAGE_DIAMETER_MULTIPLIER + SAFETY_MARGIN;
	}

	/** 结束线比触发线多一格，避免队员在边界上每 tick 启停 Goal。 */
	public static double releaseRadius(final boolean powered) {
		return dangerRadius(powered) + RELEASE_HYSTERESIS;
	}

	public static boolean isInsideDanger(final double distanceSquared, final boolean powered) {
		double radius = dangerRadius(powered);
		return Double.isFinite(distanceSquared)
			&& distanceSquared >= 0.0
			&& distanceSquared < radius * radius;
	}

	public static boolean shouldContinue(final double distanceSquared, final boolean powered) {
		double radius = releaseRadius(powered);
		return Double.isFinite(distanceSquared)
			&& distanceSquared >= 0.0
			&& distanceSquared < radius * radius;
	}

	/** 引信越接近结束，导航倍率从 1.30 平滑提高到 1.55。 */
	public static double evacuationSpeed(final float fuseProgress) {
		return Mth.lerp(Mth.clamp(fuseProgress, 0.0F, 1.0F), 1.30, 1.55);
	}

	/** 每次重寻路只要求一个可实际抵达的中程落点，超大带电半径通过连续重规划逐步退出。 */
	public static double pathStep(final double currentDistance, final boolean powered) {
		double missing = releaseRadius(powered) - Math.max(0.0, currentDistance);
		return Mth.clamp(missing + 2.0, 5.0, 10.0);
	}
}

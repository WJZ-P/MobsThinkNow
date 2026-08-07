package com.wjz.mobsthinknow.ai.creeper;

import net.minecraft.world.phys.Vec3;

/** 苦力怕佯爆的纯决策与几何；实体感知、导航和引信写入仍只发生在服务器主线程。 */
public final class CreeperFuseFeintPlanner {
	private static final double MINIMUM_STAGING_DISTANCE = 5.0;

	private CreeperFuseFeintPlanner() {
	}

	/**
	 * 佯爆只发生在真实起爆圈之外：玩家能听见嘶声并看到闪烁，但苦力怕最多蓄势八 tick，
	 * 与原版 30 tick 爆炸线保留了很大的硬安全余量。
	 */
	public static boolean shouldFeint(
		final int intelligence,
		final boolean enabled,
		final boolean hasLineOfSight,
		final boolean targetWatching,
		final boolean targetBlocking,
		final boolean powered,
		final double fuseProgress,
		final double distanceSquared,
		final double configuredFuseStartDistance
	) {
		int iq = CreeperIntelligence.clamp(intelligence);
		if (!enabled
			|| iq < 8
			|| !hasLineOfSight
			|| (!targetWatching && !targetBlocking)
			|| powered
			|| fuseProgress > 0.01) {
			return false;
		}
		double minimum = Math.max(MINIMUM_STAGING_DISTANCE, configuredFuseStartDistance + 0.75);
		double maximum = 7.0 + (iq - 8) * 0.5;
		return distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum;
	}

	/** 把后撤点放到目标当前视线的侧后方，并给移动目标加入有限提前量。 */
	public static Vec3 repositionDestination(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final Vec3 targetLook,
		final int stableSide,
		final int intelligence
	) {
		int iq = CreeperIntelligence.clamp(intelligence);
		Vec3 facing = horizontalUnit(targetLook, new Vec3(0.0, 0.0, 1.0));
		Vec3 lateral = new Vec3(-facing.z, 0.0, facing.x);
		Vec3 prediction = cappedHorizontal(targetVelocity.scale(3.0 + iq * 0.25), 2.5);
		double rearOffset = 2.4 + iq * 0.08;
		double sideOffset = 3.3 + iq * 0.09;
		return targetPosition
			.add(prediction)
			.add(facing.scale(-rearOffset))
			.add(lateral.scale(stableSide < 0 ? -sideOffset : sideOffset));
	}

	public static int primeTicks(final double unitRandom) {
		return 6 + (int)Math.floor(clampUnit(unitRandom) * 3.0); // 6～8 tick，永远远低于原版 30 tick。
	}

	public static int repositionTicks(final double unitRandom) {
		return 26 + (int)Math.floor(clampUnit(unitRandom) * 15.0); // 26～40 tick。
	}

	public static int cooldownTicks(final int configuredBaseTicks, final double unitRandom) {
		double factor = 0.80 + clampUnit(unitRandom) * 0.40;
		return Math.max(1, (int)Math.round(configuredBaseTicks * factor));
	}

	private static Vec3 cappedHorizontal(final Vec3 value, final double maximumLength) {
		Vec3 horizontal = new Vec3(value.x, 0.0, value.z);
		double lengthSquared = horizontal.horizontalDistanceSqr();
		return lengthSquared <= maximumLength * maximumLength
			? horizontal
			: horizontal.normalize().scale(maximumLength);
	}

	private static Vec3 horizontalUnit(final Vec3 value, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(value.x, 0.0, value.z);
		return horizontal.horizontalDistanceSqr() < 1.0E-6 ? fallback : horizontal.normalize();
	}

	private static double clampUnit(final double value) {
		return Math.max(0.0, Math.min(Math.nextDown(1.0), value));
	}
}

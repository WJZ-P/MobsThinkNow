package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/**
 * 苦力怕假引爆的纯决策与几何内核。
 *
 * <p>平台层只负责采集视线、格挡、位置和速度快照，并在主线程执行引信、寻路与声效。
 * 本类不持有任何实体或世界对象，因此 Fabric 与 Paper 可以共享完全相同的心理战规则。</p>
 */
public final class CreeperFeintPlanner {
	private static final double MINIMUM_STAGING_DISTANCE = 5.0;
	private static final double REPOSITION_RADIUS = 9.0;

	private CreeperFeintPlanner() {
	}

	/** 假爆只发生在真实起爆圈之外，并为默认三十 tick 爆炸线保留硬余量。 */
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
		int iq = Math.clamp(intelligence, 1, 10);
		if (!enabled
			|| iq < 8
			|| !hasLineOfSight
			|| (!targetWatching && !targetBlocking)
			|| powered
			|| !Double.isFinite(fuseProgress)
			|| fuseProgress > 0.01
			|| !Double.isFinite(distanceSquared)
			|| distanceSquared < 0.0
			|| !Double.isFinite(configuredFuseStartDistance)) {
			return false;
		}
		double minimum = Math.max(MINIMUM_STAGING_DISTANCE, configuredFuseStartDistance + 0.75);
		double maximum = 7.0 + (iq - 8) * 0.5;
		return distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum;
	}

	/** 把退火后的移动点放在目标视线侧后方，并为移动目标加入有界提前量。 */
	public static Vec3d repositionDestination(
		final Vec3d targetPosition,
		final Vec3d targetVelocity,
		final Vec3d targetLook,
		final int stableSide,
		final int intelligence
	) {
		int iq = Math.clamp(intelligence, 1, 10);
		Vec3d facing = targetLook.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d lateral = new Vec3d(-facing.z(), 0.0, facing.x());
		Vec3d prediction = capHorizontal(targetVelocity.scale(3.0 + iq * 0.25), 2.5);
		double rearOffset = 2.4 + iq * 0.08;
		double sideOffset = 3.3 + iq * 0.09;
		Vec3d rawOffset = facing.scale(-rearOffset)
			.add(lateral.scale(stableSide < 0 ? -sideOffset : sideOffset));
		Vec3d safeOffset = rawOffset.horizontalUnitOr(facing.scale(-1.0)).scale(REPOSITION_RADIUS);
		return targetPosition.add(prediction).add(safeOffset);
	}

	public static int primeTicks(final double unitRandom) {
		return 6 + (int)Math.floor(clampUnit(unitRandom) * 3.0);
	}

	public static int repositionTicks(final double unitRandom) {
		return 26 + (int)Math.floor(clampUnit(unitRandom) * 15.0);
	}

	public static int cooldownTicks(final int configuredBaseTicks, final double unitRandom) {
		double factor = 0.80 + clampUnit(unitRandom) * 0.40;
		return Math.max(1, (int)Math.round(Math.max(1, configuredBaseTicks) * factor));
	}

	private static Vec3d capHorizontal(final Vec3d value, final double maximumLength) {
		Vec3d horizontal = value.horizontal();
		double lengthSquared = horizontal.horizontalLengthSquared();
		return lengthSquared <= maximumLength * maximumLength
			? horizontal
			: horizontal.scale(maximumLength / Math.sqrt(lengthSquared));
	}

	private static double clampUnit(final double value) {
		return Double.isFinite(value) ? Math.clamp(value, 0.0, Math.nextDown(1.0)) : 0.0;
	}
}

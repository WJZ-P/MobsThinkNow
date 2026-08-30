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
		return repositionDestination(
			targetPosition.x(),
			targetPosition.y(),
			targetPosition.z(),
			targetVelocity.x(),
			targetVelocity.z(),
			targetLook.x(),
			targetLook.z(),
			stableSide,
			intelligence
		);
	}

	/** Primitive platform entry point for the once-per-feint reposition snapshot. */
	public static Vec3d repositionDestination(
		final double targetX,
		final double targetY,
		final double targetZ,
		final double targetVelocityX,
		final double targetVelocityZ,
		double targetLookX,
		double targetLookZ,
		final int stableSide,
		final int intelligence
	) {
		int iq = Math.clamp(intelligence, 1, 10);
		double lookLengthSquared = targetLookX * targetLookX + targetLookZ * targetLookZ;
		if (lookLengthSquared < 1.0E-9) {
			targetLookX = 0.0;
			targetLookZ = 1.0;
		} else {
			double inverseLookLength = 1.0 / Math.sqrt(lookLengthSquared);
			targetLookX *= inverseLookLength;
			targetLookZ *= inverseLookLength;
		}
		double leadTicks = 3.0 + iq * 0.25;
		double predictionX = targetVelocityX * leadTicks;
		double predictionZ = targetVelocityZ * leadTicks;
		double predictionLengthSquared = predictionX * predictionX + predictionZ * predictionZ;
		if (predictionLengthSquared > 2.5 * 2.5) {
			double predictionScale = 2.5 / Math.sqrt(predictionLengthSquared);
			predictionX *= predictionScale;
			predictionZ *= predictionScale;
		}
		double rearOffset = 2.4 + iq * 0.08;
		double sideOffset = (stableSide < 0 ? -1.0 : 1.0) * (3.3 + iq * 0.09);
		double rawX = -targetLookX * rearOffset - targetLookZ * sideOffset;
		double rawZ = -targetLookZ * rearOffset + targetLookX * sideOffset;
		double rawLengthSquared = rawX * rawX + rawZ * rawZ;
		if (rawLengthSquared < 1.0E-9) {
			rawX = -targetLookX;
			rawZ = -targetLookZ;
			rawLengthSquared = rawX * rawX + rawZ * rawZ;
		}
		double repositionScale = rawLengthSquared < 1.0E-9
			? REPOSITION_RADIUS
			: REPOSITION_RADIUS / Math.sqrt(rawLengthSquared);
		return new Vec3d(
			targetX + predictionX + rawX * repositionScale,
			targetY,
			targetZ + predictionZ + rawZ * repositionScale
		);
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

	private static double clampUnit(final double value) {
		return Double.isFinite(value) ? Math.clamp(value, 0.0, Math.nextDown(1.0)) : 0.0;
	}
}

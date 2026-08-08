package com.wjz.mobsthinknow.ai.creeper;

import com.wjz.mobsthinknow.shared.ai.CreeperFeintPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import net.minecraft.world.phys.Vec3;

/** Fabric 的轻量类型适配器；实际假引爆决策由共享内核统一维护。 */
public final class CreeperFuseFeintPlanner {
	private CreeperFuseFeintPlanner() {
	}

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
		return CreeperFeintPlanner.shouldFeint(
			intelligence,
			enabled,
			hasLineOfSight,
			targetWatching,
			targetBlocking,
			powered,
			fuseProgress,
			distanceSquared,
			configuredFuseStartDistance
		);
	}

	public static Vec3 repositionDestination(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final Vec3 targetLook,
		final int stableSide,
		final int intelligence
	) {
		Vec3d destination = CreeperFeintPlanner.repositionDestination(
			toShared(targetPosition),
			toShared(targetVelocity),
			toShared(targetLook),
			stableSide,
			intelligence
		);
		return new Vec3(destination.x(), destination.y(), destination.z());
	}

	public static int primeTicks(final double unitRandom) {
		return CreeperFeintPlanner.primeTicks(unitRandom);
	}

	public static int repositionTicks(final double unitRandom) {
		return CreeperFeintPlanner.repositionTicks(unitRandom);
	}

	public static int cooldownTicks(final int configuredBaseTicks, final double unitRandom) {
		return CreeperFeintPlanner.cooldownTicks(configuredBaseTicks, unitRandom);
	}

	private static Vec3d toShared(final Vec3 vector) {
		return new Vec3d(vector.x, vector.y, vector.z);
	}
}

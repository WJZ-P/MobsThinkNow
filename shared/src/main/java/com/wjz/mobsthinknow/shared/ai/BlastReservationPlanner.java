package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/** 爆点预约的纯数学冲突判定与候场位置生成。 */
public final class BlastReservationPlanner {
	private BlastReservationPlanner() {
	}

	public static boolean conflicts(
		final Vec3d firstCenter,
		final long firstDetonationTick,
		final Vec3d secondCenter,
		final long secondDetonationTick,
		final double conflictRadius,
		final int separationTicks
	) {
		double radius = Math.max(0.0, conflictRadius);
		long timeDifference = absoluteDifference(firstDetonationTick, secondDetonationTick);
		if (timeDifference >= Math.max(0, separationTicks)) {
			return false;
		}
		double x = firstCenter.x() - secondCenter.x();
		double z = firstCenter.z() - secondCenter.z();
		return x * x + z * z < radius * radius;
	}

	/** 在目标后侧偏左/偏右生成可读候场点，避免等待者仍堵在首爆中心。 */
	public static Vec3d stagingPoint(
		final Vec3d targetPosition,
		final Vec3d targetLook,
		final int stableSide,
		final double stagingDistance
	) {
		Vec3d forward = targetLook.horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d lateral = new Vec3d(-forward.z(), 0.0, forward.x())
			.scale(stableSide < 0 ? -1.0 : 1.0);
		double distance = Math.max(3.0, stagingDistance);
		return targetPosition
			.add(forward.scale(-distance * 0.72))
			.add(lateral.scale(distance * 0.70));
	}

	public static int cellCoordinate(final double coordinate, final double cellSize) {
		double size = Double.isFinite(cellSize) && cellSize > 0.0 ? cellSize : 8.0;
		return (int)Math.floor(coordinate / size);
	}

	private static long absoluteDifference(final long first, final long second) {
		long difference;
		try {
			difference = Math.subtractExact(first, second);
		} catch (ArithmeticException exception) {
			return Long.MAX_VALUE;
		}
		return difference == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(difference);
	}
}

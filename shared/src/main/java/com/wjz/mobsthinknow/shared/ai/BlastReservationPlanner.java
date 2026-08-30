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
		return conflicts(
			firstCenter.x(),
			firstCenter.z(),
			firstDetonationTick,
			secondCenter.x(),
			secondCenter.z(),
			secondDetonationTick,
			conflictRadius,
			separationTicks
		);
	}

	public static boolean conflicts(
		final double firstX,
		final double firstZ,
		final long firstDetonationTick,
		final double secondX,
		final double secondZ,
		final long secondDetonationTick,
		final double conflictRadius,
		final int separationTicks
	) {
		double radius = Math.max(0.0, conflictRadius);
		long timeDifference = absoluteDifference(firstDetonationTick, secondDetonationTick);
		if (timeDifference >= Math.max(0, separationTicks)) {
			return false;
		}
		double x = firstX - secondX;
		double z = firstZ - secondZ;
		return x * x + z * z < radius * radius;
	}

	/** 在目标后侧偏左/偏右生成可读候场点，避免等待者仍堵在首爆中心。 */
	public static Vec3d stagingPoint(
		final Vec3d targetPosition,
		final Vec3d targetLook,
		final int stableSide,
		final double stagingDistance
	) {
		return stagingPoint(
			targetPosition.x(),
			targetPosition.y(),
			targetPosition.z(),
			targetLook.x(),
			targetLook.z(),
			stableSide,
			stagingDistance
		);
	}

	public static Vec3d stagingPoint(
		final double targetX,
		final double targetY,
		final double targetZ,
		double lookX,
		double lookZ,
		final int stableSide,
		final double stagingDistance
	) {
		double lengthSquared = lookX * lookX + lookZ * lookZ;
		if (lengthSquared < 1.0E-9) {
			lookX = 0.0;
			lookZ = 1.0;
		} else {
			double inverseLength = 1.0 / Math.sqrt(lengthSquared);
			lookX *= inverseLength;
			lookZ *= inverseLength;
		}
		double side = stableSide < 0 ? -1.0 : 1.0;
		double lateralX = -lookZ * side;
		double lateralZ = lookX * side;
		double distance = Math.max(3.0, stagingDistance);
		return new Vec3d(
			targetX - lookX * distance * 0.72 + lateralX * distance * 0.70,
			targetY,
			targetZ - lookZ * distance * 0.72 + lateralZ * distance * 0.70
		);
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

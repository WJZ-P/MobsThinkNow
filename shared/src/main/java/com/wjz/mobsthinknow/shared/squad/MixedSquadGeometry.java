package com.wjz.mobsthinknow.shared.squad;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/** 跨端集结圆阵与交战阵位的纯向量数学；真实可达性仍由各平台导航验证。 */
public final class MixedSquadGeometry {
	private static final double MINIMUM_HORIZONTAL_LENGTH_SQUARED = 1.0E-9;

	private MixedSquadGeometry() {
	}

	public static Vec3d rallyPosition(
		final Vec3d leaderPosition,
		final Vec3d targetPosition,
		final MixedSquadRole role,
		final int stableOrdinal
	) {
		double forwardX = targetPosition.x() - leaderPosition.x();
		double forwardZ = targetPosition.z() - leaderPosition.z();
		double lengthSquared = forwardX * forwardX + forwardZ * forwardZ;
		if (lengthSquared < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			forwardX = 0.0;
			forwardZ = 1.0;
		} else {
			double inverseLength = 1.0 / Math.sqrt(lengthSquared);
			forwardX *= inverseLength;
			forwardZ *= inverseLength;
		}
		double jitter = Math.floorMod(stableOrdinal, 3) * 0.35;
		return switch (role) {
			case LEADER -> leaderPosition;
			case FRONTLINE -> offset(leaderPosition, forwardX, forwardZ, 2.0 + jitter, 0.0);
			case FLANK_LEFT -> offset(leaderPosition, forwardX, forwardZ, 0.0, -(2.5 + jitter));
			case FLANK_RIGHT -> offset(leaderPosition, forwardX, forwardZ, 0.0, 2.5 + jitter);
			case RANGED_LEFT -> offset(leaderPosition, forwardX, forwardZ, -2.0, -(2.0 + jitter));
			case RANGED_RIGHT -> offset(leaderPosition, forwardX, forwardZ, -2.0, 2.0 + jitter);
			case BREACHER -> offset(leaderPosition, forwardX, forwardZ, -(1.4 + jitter), 0.0);
			case CARRIER -> offset(
				leaderPosition,
				forwardX,
				forwardZ,
				-2.6,
				(stableOrdinal & 1) == 0 ? 2.4 : -2.4
			);
			case SUPPORT -> offset(leaderPosition, forwardX, forwardZ, -(2.8 + jitter), 0.0);
		};
	}

	public static Vec3d combatPosition(
		final Vec3d targetPosition,
		final Vec3d targetLook,
		final Vec3d fallbackDirection,
		final MixedSquadRole role,
		final int stableOrdinal,
		final double rangedDistance
	) {
		return combatPosition(
			targetPosition,
			targetLook.x(),
			targetLook.z(),
			fallbackDirection.x(),
			fallbackDirection.z(),
			role,
			stableOrdinal,
			rangedDistance
		);
	}

	public static Vec3d combatPosition(
		final Vec3d targetPosition,
		final double targetLookX,
		final double targetLookZ,
		final double fallbackX,
		final double fallbackZ,
		final MixedSquadRole role,
		final int stableOrdinal,
		final double rangedDistance
	) {
		double forwardX = targetLookX;
		double forwardZ = targetLookZ;
		double lengthSquared = forwardX * forwardX + forwardZ * forwardZ;
		if (lengthSquared < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			forwardX = fallbackX;
			forwardZ = fallbackZ;
			lengthSquared = forwardX * forwardX + forwardZ * forwardZ;
		}
		if (lengthSquared < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			forwardX = 1.0;
			forwardZ = 0.0;
		} else {
			double inverseLength = 1.0 / Math.sqrt(lengthSquared);
			forwardX *= inverseLength;
			forwardZ *= inverseLength;
		}
		double side = (stableOrdinal & 1) == 0 ? 1.0 : -1.0;
		double range = Math.max(6.0, rangedDistance);
		return switch (role) {
			case LEADER, FRONTLINE -> offset(targetPosition, forwardX, forwardZ, 2.2, 0.0);
			case FLANK_LEFT -> offset(targetPosition, forwardX, forwardZ, -1.5, -4.0);
			case FLANK_RIGHT -> offset(targetPosition, forwardX, forwardZ, -1.5, 4.0);
			case RANGED_LEFT -> crossfire(targetPosition, forwardX, forwardZ, range, -1.0);
			case RANGED_RIGHT -> crossfire(targetPosition, forwardX, forwardZ, range, 1.0);
			case BREACHER -> offset(targetPosition, forwardX, forwardZ, -4.5, 3.2 * side);
			case CARRIER -> offset(targetPosition, forwardX, forwardZ, -5.5, 4.0 * side);
			case SUPPORT -> offset(targetPosition, forwardX, forwardZ, 6.0, 2.5 * side);
		};
	}

	private static Vec3d crossfire(
		final Vec3d target,
		final double forwardX,
		final double forwardZ,
		final double range,
		final double side
	) {
		double forwardDistance = range * 0.42;
		double lateralDistance = Math.sqrt(Math.max(0.0, range * range - forwardDistance * forwardDistance));
		return offset(target, forwardX, forwardZ, forwardDistance, lateralDistance * side);
	}

	private static Vec3d offset(
		final Vec3d origin,
		final double forwardX,
		final double forwardZ,
		final double forwardDistance,
		final double lateralDistance
	) {
		double rightX = -forwardZ;
		double rightZ = forwardX;
		return new Vec3d(
			origin.x() + forwardX * forwardDistance + rightX * lateralDistance,
			origin.y(),
			origin.z() + forwardZ * forwardDistance + rightZ * lateralDistance
		);
	}
}

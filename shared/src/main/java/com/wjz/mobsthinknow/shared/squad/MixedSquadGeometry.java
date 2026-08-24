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
		return rallyPosition(
			leaderPosition.x(),
			leaderPosition.y(),
			leaderPosition.z(),
			targetPosition.x(),
			targetPosition.z(),
			role,
			stableOrdinal
		);
	}

	public static Vec3d rallyPosition(
		final double leaderX,
		final double leaderY,
		final double leaderZ,
		final double targetX,
		final double targetZ,
		final MixedSquadRole role,
		final int stableOrdinal
	) {
		double forwardX = targetX - leaderX;
		double forwardZ = targetZ - leaderZ;
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
			case LEADER -> new Vec3d(leaderX, leaderY, leaderZ);
			case FRONTLINE -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, 2.0 + jitter, 0.0);
			case FLANK_LEFT -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, 0.0, -(2.5 + jitter));
			case FLANK_RIGHT -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, 0.0, 2.5 + jitter);
			case RANGED_LEFT -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, -2.0, -(2.0 + jitter));
			case RANGED_RIGHT -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, -2.0, 2.0 + jitter);
			case BREACHER -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, -(1.4 + jitter), 0.0);
			case CARRIER -> offset(
				leaderX,
				leaderY,
				leaderZ,
				forwardX,
				forwardZ,
				-2.6,
				(stableOrdinal & 1) == 0 ? 2.4 : -2.4
			);
			case SUPPORT -> offset(leaderX, leaderY, leaderZ, forwardX, forwardZ, -(2.8 + jitter), 0.0);
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
			targetPosition.x(),
			targetPosition.y(),
			targetPosition.z(),
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
		return combatPosition(
			targetPosition.x(),
			targetPosition.y(),
			targetPosition.z(),
			targetLookX,
			targetLookZ,
			fallbackX,
			fallbackZ,
			role,
			stableOrdinal,
			rangedDistance
		);
	}

	public static Vec3d combatPosition(
		final double targetX,
		final double targetY,
		final double targetZ,
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
			case LEADER, FRONTLINE -> offset(targetX, targetY, targetZ, forwardX, forwardZ, 2.2, 0.0);
			case FLANK_LEFT -> offset(targetX, targetY, targetZ, forwardX, forwardZ, -1.5, -4.0);
			case FLANK_RIGHT -> offset(targetX, targetY, targetZ, forwardX, forwardZ, -1.5, 4.0);
			case RANGED_LEFT -> crossfire(targetX, targetY, targetZ, forwardX, forwardZ, range, -1.0);
			case RANGED_RIGHT -> crossfire(targetX, targetY, targetZ, forwardX, forwardZ, range, 1.0);
			case BREACHER -> offset(targetX, targetY, targetZ, forwardX, forwardZ, -4.5, 3.2 * side);
			case CARRIER -> offset(targetX, targetY, targetZ, forwardX, forwardZ, -5.5, 4.0 * side);
			case SUPPORT -> offset(targetX, targetY, targetZ, forwardX, forwardZ, 6.0, 2.5 * side);
		};
	}

	private static Vec3d crossfire(
		final double targetX,
		final double targetY,
		final double targetZ,
		final double forwardX,
		final double forwardZ,
		final double range,
		final double side
	) {
		double forwardDistance = range * 0.42;
		double lateralDistance = Math.sqrt(Math.max(0.0, range * range - forwardDistance * forwardDistance));
		return offset(
			targetX,
			targetY,
			targetZ,
			forwardX,
			forwardZ,
			forwardDistance,
			lateralDistance * side
		);
	}

	private static Vec3d offset(
		final double originX,
		final double originY,
		final double originZ,
		final double forwardX,
		final double forwardZ,
		final double forwardDistance,
		final double lateralDistance
	) {
		double rightX = -forwardZ;
		double rightZ = forwardX;
		return new Vec3d(
			originX + forwardX * forwardDistance + rightX * lateralDistance,
			originY,
			originZ + forwardZ * forwardDistance + rightZ * lateralDistance
		);
	}
}

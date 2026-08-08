package com.wjz.mobsthinknow.shared.squad;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/** 跨端集结圆阵与交战阵位的纯向量数学；真实可达性仍由各平台导航验证。 */
public final class MixedSquadGeometry {
	private MixedSquadGeometry() {
	}

	public static Vec3d rallyPosition(
		final Vec3d leaderPosition,
		final Vec3d targetPosition,
		final MixedSquadRole role,
		final int stableOrdinal
	) {
		Vec3d forward = targetPosition.subtract(leaderPosition).horizontalUnitOr(new Vec3d(0.0, 0.0, 1.0));
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		double jitter = Math.floorMod(stableOrdinal, 3) * 0.35;
		return switch (role) {
			case LEADER -> leaderPosition;
			case FRONTLINE -> leaderPosition.add(forward.scale(2.0 + jitter));
			case FLANK_LEFT -> leaderPosition.subtract(right.scale(2.5 + jitter));
			case FLANK_RIGHT -> leaderPosition.add(right.scale(2.5 + jitter));
			case RANGED_LEFT -> leaderPosition.subtract(forward.scale(2.0)).subtract(right.scale(2.0 + jitter));
			case RANGED_RIGHT -> leaderPosition.subtract(forward.scale(2.0)).add(right.scale(2.0 + jitter));
			case BREACHER -> leaderPosition.subtract(forward.scale(1.4 + jitter));
			case CARRIER -> leaderPosition.subtract(forward.scale(2.6)).add(right.scale((stableOrdinal & 1) == 0 ? 2.4 : -2.4));
			case SUPPORT -> leaderPosition.subtract(forward.scale(2.8 + jitter));
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
		Vec3d forward = targetLook.horizontalUnitOr(fallbackDirection);
		Vec3d right = new Vec3d(-forward.z(), 0.0, forward.x());
		double side = (stableOrdinal & 1) == 0 ? 1.0 : -1.0;
		double range = Math.max(6.0, rangedDistance);
		return switch (role) {
			case LEADER, FRONTLINE -> targetPosition.add(forward.scale(2.2));
			case FLANK_LEFT -> targetPosition.subtract(forward.scale(1.5)).subtract(right.scale(4.0));
			case FLANK_RIGHT -> targetPosition.subtract(forward.scale(1.5)).add(right.scale(4.0));
			case RANGED_LEFT -> crossfire(targetPosition, forward, right, range, -1.0);
			case RANGED_RIGHT -> crossfire(targetPosition, forward, right, range, 1.0);
			case BREACHER -> targetPosition.subtract(forward.scale(4.5)).add(right.scale(3.2 * side));
			case CARRIER -> targetPosition.subtract(forward.scale(5.5)).add(right.scale(4.0 * side));
			case SUPPORT -> targetPosition.add(forward.scale(6.0)).add(right.scale(2.5 * side));
		};
	}

	private static Vec3d crossfire(
		final Vec3d target,
		final Vec3d forward,
		final Vec3d right,
		final double range,
		final double side
	) {
		double forwardDistance = range * 0.42;
		double lateralDistance = Math.sqrt(Math.max(0.0, range * range - forwardDistance * forwardDistance));
		return target.add(forward.scale(forwardDistance)).add(right.scale(lateralDistance * side));
	}
}

package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;

/** Fabric 与 Paper 共用的持械近战节奏、周旋点和斧手跳劈数学。 */
public final class MeleeWeaponPlanner {
	public static final int DEFAULT_ATTACK_COOLDOWN_TICKS = 20;
	public static final int MINIMUM_ATTACK_COOLDOWN_TICKS = 5;
	public static final int MAXIMUM_ATTACK_COOLDOWN_TICKS = 60;
	private static final double MINIMUM_DIRECTION_SQUARED = 1.0E-6;

	private MeleeWeaponPlanner() {
	}

	/** 把玩家式攻击速度换算为完整 tick；没有显式速度修饰时保留怪物原版 20 tick。 */
	public static int attackCooldownTicks(final double attackSpeed, final boolean hasAttackSpeedModifier) {
		if (!hasAttackSpeedModifier || !Double.isFinite(attackSpeed) || attackSpeed <= 0.0) {
			return DEFAULT_ATTACK_COOLDOWN_TICKS;
		}
		int cooldown = (int)Math.ceil(20.0 / attackSpeed);
		return Math.clamp(cooldown, MINIMUM_ATTACK_COOLDOWN_TICKS, MAXIMUM_ATTACK_COOLDOWN_TICKS);
	}

	/** 在目标周围旋转 45 度取得下一段周旋点；结果始终落在指定水平半径上。 */
	public static Vec3d spacingDestination(
		final Vec3d attacker,
		final Vec3d target,
		final double radius,
		final boolean clockwise
	) {
		requireFinitePositive(radius, "radius");
		Vec3d radial = horizontalUnit(attacker.subtract(target));
		double angle = clockwise ? Math.PI / 4.0 : -Math.PI / 4.0;
		double cosine = Math.cos(angle);
		double sine = Math.sin(angle);
		double rotatedX = radial.x() * cosine - radial.z() * sine;
		double rotatedZ = radial.x() * sine + radial.z() * cosine;
		return new Vec3d(target.x() + rotatedX * radius, target.y(), target.z() + rotatedZ * radius);
	}

	public static boolean isAxeLaunchBand(
		final Vec3d attacker,
		final Vec3d target,
		final double minimumDistance,
		final double maximumDistance,
		final double maximumVerticalDifference
	) {
		if (!Double.isFinite(minimumDistance) || !Double.isFinite(maximumDistance)
			|| !Double.isFinite(maximumVerticalDifference)
			|| minimumDistance < 0.0 || maximumDistance < minimumDistance || maximumVerticalDifference < 0.0) {
			return false;
		}
		double horizontal = horizontalDistanceSquared(attacker, target);
		return horizontal >= minimumDistance * minimumDistance
			&& horizontal <= maximumDistance * maximumDistance
			&& Math.abs(attacker.y() - target.y()) <= maximumVerticalDifference;
	}

	/** 保留当前竖直速度，仅把水平速度指向目标；真正的起跳仍由平台自己的跳跃控制器触发。 */
	public static Vec3d axeLeapVelocity(
		final Vec3d attacker,
		final Vec3d target,
		final double currentVerticalVelocity,
		final double horizontalSpeed
	) {
		requireFinitePositive(horizontalSpeed, "horizontalSpeed");
		Vec3d direction = horizontalUnit(target.subtract(attacker));
		return new Vec3d(
			direction.x() * horizontalSpeed,
			Double.isFinite(currentVerticalVelocity) ? currentVerticalVelocity : 0.0,
			direction.z() * horizontalSpeed
		);
	}

	/** 空中只做有限航向修正，避免每 tick 把真实抛物线吸回目标中心。 */
	public static Vec3d guideAxeLeap(
		final Vec3d currentVelocity,
		final Vec3d attacker,
		final Vec3d target,
		final double horizontalSpeed,
		final double steeringStrength
	) {
		requireFinitePositive(horizontalSpeed, "horizontalSpeed");
		double steering = Double.isFinite(steeringStrength) ? Math.clamp(steeringStrength, 0.0, 1.0) : 0.0;
		Vec3d desired = horizontalUnit(target.subtract(attacker)).scale(horizontalSpeed);
		return new Vec3d(
			currentVelocity.x() * (1.0 - steering) + desired.x() * steering,
			currentVelocity.y(),
			currentVelocity.z() * (1.0 - steering) + desired.z() * steering
		);
	}

	public static double horizontalDistanceSquared(final Vec3d first, final Vec3d second) {
		double x = first.x() - second.x();
		double z = first.z() - second.z();
		return x * x + z * z;
	}

	private static Vec3d horizontalUnit(final Vec3d vector) {
		double squared = vector.x() * vector.x() + vector.z() * vector.z();
		if (!Double.isFinite(squared) || squared < MINIMUM_DIRECTION_SQUARED) {
			return new Vec3d(1.0, 0.0, 0.0);
		}
		double inverseLength = 1.0 / Math.sqrt(squared);
		return new Vec3d(vector.x() * inverseLength, 0.0, vector.z() * inverseLength);
	}

	private static void requireFinitePositive(final double value, final String label) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(label + " must be finite and positive");
		}
	}
}

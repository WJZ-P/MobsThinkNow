package com.wjz.mobsthinknow.shared.ai;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import java.util.Objects;

/**
 * 弩手两端共用的纯决策与弹道数学。这里不保存实体，也不触碰世界；Fabric/Paper 只需传入同一 tick
 * 的有限数值快照，随后在各自主线程应用返回结果。
 */
public final class CrossbowCombatPlanner {
	private static final double MINIMUM_PROJECTILE_SPEED = 0.05;
	private static final double MINIMUM_DISTANCE_SQUARED = 1.0E-9;

	private CrossbowCombatPlanner() {
	}

	/** IQ 越高装填越快，但不会突破客户端弩动画仍可辨认的下限。 */
	public static int chargeTicks(final int configuredBase, final int intelligence) {
		int base = Math.clamp(configuredBase, 12, 60);
		return Math.max(10, base - (IntelligenceDistribution.clamp(intelligence) - 1) / 2);
	}

	/**
	 * 已装填后的短暂瞄准窗。stableOrder 与 shotSequence 生成可复现抖动，避免整队同 tick 机械开火。
	 */
	public static int aimDelayTicks(
		final int configuredMinimum,
		final int configuredMaximum,
		final int intelligence,
		final int stableOrder,
		final long shotSequence
	) {
		int minimum = Math.clamp(configuredMinimum, 1, 20);
		int maximum = Math.clamp(configuredMaximum, minimum, 40);
		int masteryReduction = (IntelligenceDistribution.clamp(intelligence) - 1) / 3;
		int adjustedMinimum = Math.max(1, minimum - masteryReduction);
		int adjustedMaximum = Math.max(adjustedMinimum, maximum - masteryReduction);
		int span = adjustedMaximum - adjustedMinimum + 1;
		long mixed = mix64(Integer.toUnsignedLong(stableOrder) ^ shotSequence * 0x9E3779B97F4A7C15L);
		return adjustedMinimum + Math.floorMod(mixed, span);
	}

	/**
	 * 使用两次定点迭代预判匀速目标，并补偿恒定重力下坠。返回单位方向和预计飞行 tick。
	 */
	public static AimSolution intercept(
		final Vec3d shooter,
		final Vec3d target,
		final Vec3d targetVelocityPerTick,
		final double projectileSpeedPerTick,
		final double gravityPerTickSquared,
		final double configuredMaximumLeadTicks
	) {
		Objects.requireNonNull(shooter, "shooter");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(targetVelocityPerTick, "targetVelocityPerTick");
		double speed = finiteClamp(projectileSpeedPerTick, MINIMUM_PROJECTILE_SPEED, 8.0);
		double gravity = finiteClamp(gravityPerTickSquared, 0.0, 0.20);
		double maximumLead = finiteClamp(configuredMaximumLeadTicks, 0.0, 40.0);
		double flightTicks = Math.min(maximumLead, Math.sqrt(shooter.distanceSquared(target)) / speed);
		double aimX = target.x();
		double aimY = target.y();
		double aimZ = target.z();
		for (int iteration = 0; iteration < 2; iteration++) {
			aimX = target.x() + targetVelocityPerTick.x() * flightTicks;
			aimY = target.y() + targetVelocityPerTick.y() * flightTicks;
			aimZ = target.z() + targetVelocityPerTick.z() * flightTicks;
			double deltaX = aimX - shooter.x();
			double deltaY = aimY - shooter.y();
			double deltaZ = aimZ - shooter.z();
			flightTicks = Math.min(
				maximumLead,
				Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / speed
			);
		}
		aimY += 0.5 * gravity * flightTicks * flightTicks;
		double deltaX = aimX - shooter.x();
		double deltaY = aimY - shooter.y();
		double deltaZ = aimZ - shooter.z();
		double lengthSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
		Vec3d direction;
		if (lengthSquared < MINIMUM_DISTANCE_SQUARED) {
			direction = new Vec3d(0.0, 0.0, 1.0);
		} else {
			double inverseLength = 1.0 / Math.sqrt(lengthSquared);
			direction = new Vec3d(deltaX * inverseLength, deltaY * inverseLength, deltaZ * inverseLength);
		}
		return new AimSolution(direction, new Vec3d(aimX, aimY, aimZ), flightTicks);
	}

	/** Same intercept iteration when the caller only needs the predicted impact point. */
	public static Vec3d interceptPoint(
		final Vec3d shooter,
		final Vec3d target,
		final Vec3d targetVelocityPerTick,
		final double projectileSpeedPerTick,
		final double gravityPerTickSquared,
		final double configuredMaximumLeadTicks
	) {
		Objects.requireNonNull(shooter, "shooter");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(targetVelocityPerTick, "targetVelocityPerTick");
		double speed = finiteClamp(projectileSpeedPerTick, MINIMUM_PROJECTILE_SPEED, 8.0);
		double gravity = finiteClamp(gravityPerTickSquared, 0.0, 0.20);
		double maximumLead = finiteClamp(configuredMaximumLeadTicks, 0.0, 40.0);
		double flightTicks = Math.min(maximumLead, Math.sqrt(shooter.distanceSquared(target)) / speed);
		double aimX = target.x();
		double aimY = target.y();
		double aimZ = target.z();
		for (int iteration = 0; iteration < 2; iteration++) {
			aimX = target.x() + targetVelocityPerTick.x() * flightTicks;
			aimY = target.y() + targetVelocityPerTick.y() * flightTicks;
			aimZ = target.z() + targetVelocityPerTick.z() * flightTicks;
			double deltaX = aimX - shooter.x();
			double deltaY = aimY - shooter.y();
			double deltaZ = aimZ - shooter.z();
			flightTicks = Math.min(
				maximumLead,
				Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) / speed
			);
		}
		aimY += 0.5 * gravity * flightTicks * flightTicks;
		return new Vec3d(aimX, aimY, aimZ);
	}

	/**
	 * 爆炸弹必须位于射程带内，且目标爆心附近没有队友。检查上限保证拥挤服务器中工作量恒定。
	 */
	public static <T> BlastSafety<T> assessBlast(
		final Vec3d shooter,
		final Vec3d target,
		final List<BlastAlly<T>> allies,
		final double configuredMinimumRange,
		final double configuredMaximumRange,
		final double configuredDangerRadius,
		final int configuredMaximumChecks
	) {
		Objects.requireNonNull(shooter, "shooter");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(allies, "allies");
		double minimumRange = finiteClamp(configuredMinimumRange, 2.0, 32.0);
		double maximumRange = finiteClamp(configuredMaximumRange, minimumRange, 64.0);
		double distanceSquared = shooter.distanceSquared(target);
		if (distanceSquared < minimumRange * minimumRange) {
			return new BlastSafety<>(BlastStatus.TOO_CLOSE, null, 0);
		}
		if (distanceSquared > maximumRange * maximumRange) {
			return new BlastSafety<>(BlastStatus.TOO_FAR, null, 0);
		}
		double dangerRadius = finiteClamp(configuredDangerRadius, 1.0, 12.0);
		int maximumChecks = Math.clamp(configuredMaximumChecks, 1, 128);
		int checks = 0;
		for (int index = 0; index < allies.size() && checks < maximumChecks; index++) {
			BlastAlly<T> ally = allies.get(index);
			checks++;
			double combinedRadius = dangerRadius + finiteClamp(ally.radius(), 0.0, 4.0);
			double deltaX = ally.x() - target.x();
			double deltaY = ally.y() - target.y();
			double deltaZ = ally.z() - target.z();
			if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= combinedRadius * combinedRadius) {
				return new BlastSafety<>(BlastStatus.ALLY_IN_BLAST, ally.id(), checks);
			}
		}
		return new BlastSafety<>(BlastStatus.CLEAR, null, checks);
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}

	public record AimSolution(Vec3d direction, Vec3d aimPoint, double flightTicks) {
		public AimSolution {
			Objects.requireNonNull(direction, "direction");
			Objects.requireNonNull(aimPoint, "aimPoint");
		}
	}

	public record BlastAlly<T>(T id, double x, double y, double z, double radius) {
		public BlastAlly(final T id, final Vec3d position, final double radius) {
			this(id, position.x(), position.y(), position.z(), radius);
		}

		public BlastAlly {
			Objects.requireNonNull(id, "id");
			if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
				throw new IllegalArgumentException("ally position must be finite");
			}
		}

		public Vec3d position() {
			return new Vec3d(this.x, this.y, this.z);
		}
	}

	public record BlastSafety<T>(BlastStatus status, T blocker, int checks) {
		public BlastSafety {
			Objects.requireNonNull(status, "status");
		}

		public boolean clear() {
			return this.status == BlastStatus.CLEAR;
		}
	}

	public enum BlastStatus {
		CLEAR,
		TOO_CLOSE,
		TOO_FAR,
		ALLY_IN_BLAST
	}
}

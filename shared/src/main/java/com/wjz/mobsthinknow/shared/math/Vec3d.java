package com.wjz.mobsthinknow.shared.math;

/**
 * 不绑定 Minecraft、Bukkit 或 Fabric 类型的不可变三维向量。
 *
 * <p>共享决策层只接受数值快照，平台适配器负责在边界处转换实体和坐标对象。这样纯数学逻辑既可
 * 单元测试，也不会把任何服务端对象交给异步线程。</p>
 */
public record Vec3d(double x, double y, double z) {
	public static final Vec3d ZERO = new Vec3d(0.0, 0.0, 0.0);
	private static final double MINIMUM_LENGTH_SQUARED = 1.0E-9;

	public Vec3d {
		if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
			throw new IllegalArgumentException("Vector coordinates must be finite.");
		}
	}

	public Vec3d add(final Vec3d other) {
		return new Vec3d(this.x + other.x, this.y + other.y, this.z + other.z);
	}

	public Vec3d subtract(final Vec3d other) {
		return new Vec3d(this.x - other.x, this.y - other.y, this.z - other.z);
	}

	public Vec3d scale(final double factor) {
		return new Vec3d(this.x * factor, this.y * factor, this.z * factor);
	}

	public Vec3d horizontal() {
		return new Vec3d(this.x, 0.0, this.z);
	}

	public double horizontalLengthSquared() {
		return this.x * this.x + this.z * this.z;
	}

	public double distanceSquared(final Vec3d other) {
		double dx = this.x - other.x;
		double dy = this.y - other.y;
		double dz = this.z - other.z;
		return dx * dx + dy * dy + dz * dz;
	}

	public Vec3d horizontalUnitOr(final Vec3d fallback) {
		double lengthSquared = this.horizontalLengthSquared();
		if (lengthSquared < MINIMUM_LENGTH_SQUARED) {
			Vec3d horizontalFallback = fallback.horizontal();
			double fallbackLengthSquared = horizontalFallback.horizontalLengthSquared();
			if (fallbackLengthSquared < MINIMUM_LENGTH_SQUARED) {
				return new Vec3d(1.0, 0.0, 0.0);
			}
			return horizontalFallback.scale(1.0 / Math.sqrt(fallbackLengthSquared));
		}
		return this.horizontal().scale(1.0 / Math.sqrt(lengthSquared));
	}
}

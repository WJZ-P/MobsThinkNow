package com.wjz.mobsthinknow.paper;

import com.wjz.mobsthinknow.shared.math.Vec3d;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** Paper 实体热路径的无分配坐标运算；Location 只留给确实需要它的公共 API 边界。 */
public final class PaperEntityMath {
	private PaperEntityMath() {
	}

	public static double distanceSquared(final Entity first, final Entity second) {
		requireSameWorld(first, second);
		return distanceSquared(
			first.getX(), first.getY(), first.getZ(),
			second.getX(), second.getY(), second.getZ()
		);
	}

	public static double distanceSquared(final Entity entity, final Location point) {
		if (entity.getWorld() != point.getWorld()) {
			throw new IllegalArgumentException("Cannot measure distance between different worlds");
		}
		return distanceSquared(
			entity.getX(), entity.getY(), entity.getZ(),
			point.getX(), point.getY(), point.getZ()
		);
	}

	public static double distanceSquared(final Entity entity, final Vec3d point) {
		return distanceSquared(entity, point.x(), point.y(), point.z());
	}

	public static double distanceSquared(
		final Entity entity,
		final double pointX,
		final double pointY,
		final double pointZ
	) {
		return distanceSquared(
			entity.getX(), entity.getY(), entity.getZ(),
			pointX, pointY, pointZ
		);
	}

	public static double horizontalDistanceSquared(final Entity first, final Entity second) {
		requireSameWorld(first, second);
		return horizontalDistanceSquared(first.getX(), first.getZ(), second.getX(), second.getZ());
	}

	static double distanceSquared(
		final double firstX,
		final double firstY,
		final double firstZ,
		final double secondX,
		final double secondY,
		final double secondZ
	) {
		double x = firstX - secondX;
		double y = firstY - secondY;
		double z = firstZ - secondZ;
		return x * x + y * y + z * z;
	}

	static double horizontalDistanceSquared(
		final double firstX,
		final double firstZ,
		final double secondX,
		final double secondZ
	) {
		double x = firstX - secondX;
		double z = firstZ - secondZ;
		return x * x + z * z;
	}

	private static void requireSameWorld(final Entity first, final Entity second) {
		if (first.getWorld() != second.getWorld()) {
			throw new IllegalArgumentException("Cannot measure distance between different worlds");
		}
	}
}

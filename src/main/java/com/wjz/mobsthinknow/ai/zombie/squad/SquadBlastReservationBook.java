package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 同一小队的爆点预约簿；按苦力怕实体 ID 保存，避免多枚引信对同一目标或重叠区域同时提交。
 */
public final class SquadBlastReservationBook {
	private final Map<Integer, Reservation> reservations = new LinkedHashMap<>();

	public boolean canReserve(
		final int ownerId,
		final int targetId,
		final Vec3 center,
		final double dangerRadius,
		final long now
	) {
		this.prune(now);
		if (ownerId <= 0 || targetId <= 0 || !isValid(center, dangerRadius)) {
			return false;
		}
		return this.firstConflict(ownerId, targetId, center, dangerRadius) == null;
	}

	public Decision reserve(
		final int ownerId,
		final int targetId,
		final Vec3 center,
		final double dangerRadius,
		final boolean forced,
		final long now,
		final long lifetimeTicks
	) {
		this.prune(now);
		if (ownerId <= 0 || targetId <= 0 || !isValid(center, dangerRadius)) {
			return Decision.CONFLICT;
		}
		Reservation existing = this.reservations.get(ownerId);
		if (!forced && this.firstConflict(ownerId, targetId, center, dangerRadius) != null) {
			return Decision.CONFLICT;
		}
		Reservation updated = new Reservation(
			ownerId,
			targetId,
			center,
			Math.max(0.5, dangerRadius),
			forced,
			saturatingAdd(now, Math.max(1L, lifetimeTicks))
		);
		this.reservations.put(ownerId, updated);
		return existing == null ? Decision.ACQUIRED : Decision.RENEWED;
	}

	public boolean renew(
		final int ownerId,
		final Vec3 center,
		final long now,
		final long lifetimeTicks
	) {
		this.prune(now);
		Reservation existing = this.reservations.get(ownerId);
		if (existing == null || !isValid(center, existing.dangerRadius())) {
			return false;
		}
		if (!existing.forced
			&& this.firstConflict(ownerId, existing.targetId, center, existing.dangerRadius) != null) {
			return false;
		}
		this.reservations.put(ownerId, new Reservation(
			existing.ownerId,
			existing.targetId,
			center,
			existing.dangerRadius,
			existing.forced,
			saturatingAdd(now, Math.max(1L, lifetimeTicks))
		));
		return true;
	}

	public void release(final int ownerId) {
		this.reservations.remove(ownerId);
	}

	public @Nullable Reservation reservationFor(final int ownerId, final long now) {
		this.prune(now);
		return this.reservations.get(ownerId);
	}

	public @Nullable Reservation conflictingReservation(
		final int ownerId,
		final int targetId,
		final Vec3 center,
		final double dangerRadius,
		final long now
	) {
		this.prune(now);
		if (ownerId <= 0 || targetId <= 0 || !isValid(center, dangerRadius)) {
			return null;
		}
		return this.firstConflict(ownerId, targetId, center, dangerRadius);
	}

	public int activeCount(final long now) {
		this.prune(now);
		return this.reservations.size();
	}

	public void clear() {
		this.reservations.clear();
	}

	private @Nullable Reservation firstConflict(
		final int ownerId,
		final int targetId,
		final Vec3 center,
		final double dangerRadius
	) {
		for (Reservation reservation : this.reservations.values()) {
			if (reservation.ownerId == ownerId) {
				continue;
			}
			if (reservation.targetId == targetId || overlaps(reservation, center, dangerRadius)) {
				return reservation;
			}
		}
		return null;
	}

	private static boolean overlaps(
		final Reservation existing,
		final Vec3 center,
		final double dangerRadius
	) {
		double spacing = Math.max(3.0, Math.min(existing.dangerRadius, Math.max(0.5, dangerRadius)) * 0.75);
		double x = existing.center.x - center.x;
		double z = existing.center.z - center.z;
		return x * x + z * z < spacing * spacing;
	}

	private void prune(final long now) {
		this.reservations.values().removeIf(reservation -> reservation.expiresAt < now);
	}

	private static boolean isValid(final Vec3 center, final double dangerRadius) {
		return center != null
			&& Double.isFinite(center.x)
			&& Double.isFinite(center.y)
			&& Double.isFinite(center.z)
			&& Double.isFinite(dangerRadius)
			&& dangerRadius > 0.0;
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	public enum Decision {
		ACQUIRED,
		RENEWED,
		CONFLICT
	}

	public record Reservation(
		int ownerId,
		int targetId,
		Vec3 center,
		double dangerRadius,
		boolean forced,
		long expiresAt
	) {
		public Reservation {
			if (ownerId <= 0 || targetId <= 0 || !isValid(center, dangerRadius)) {
				throw new IllegalArgumentException("invalid blast reservation");
			}
		}
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 射手短期发布的弹道走廊；成员按实体 ID 查询自己是否挡线。 */
public final class SquadFiringLaneRegistry {
	private final Map<Integer, Reservation> reservations = new LinkedHashMap<>();

	public boolean reserve(
		final int shooterId,
		final int targetId,
		final Vec3 start,
		final Vec3 end,
		final double clearance,
		final boolean explosive,
		final long now,
		final long lifetimeTicks
	) {
		return this.reserve(
			shooterId,
			targetId,
			List.of(start, end),
			clearance,
			explosive,
			now,
			lifetimeTicks
		);
	}

	public boolean reserve(
		final int shooterId,
		final int targetId,
		final List<Vec3> trajectory,
		final double clearance,
		final boolean explosive,
		final long now,
		final long lifetimeTicks
	) {
		if (trajectory.size() < 2) {
			return false;
		}
		this.prune(now);
		boolean created = !this.reservations.containsKey(shooterId);
		this.reservations.put(shooterId, new Reservation(
			shooterId,
			targetId,
			List.copyOf(trajectory),
			Math.max(0.05, clearance),
			explosive,
			now + Math.max(1L, lifetimeTicks)
		));
		return created;
	}

	public void release(final int shooterId) {
		this.reservations.remove(shooterId);
	}

	public @Nullable Reservation blockingLane(
		final int memberId,
		final AABB memberBounds,
		final long now
	) {
		this.prune(now);
		Reservation selected = null;
		for (Reservation reservation : this.reservations.values()) {
			if (reservation.shooterId == memberId || reservation.targetId == memberId) {
				continue;
			}
			AABB expanded = memberBounds.inflate(reservation.clearance);
			boolean corridorHit = expanded.clip(reservation.start(), reservation.end()).isPresent();
			for (int index = 1; !corridorHit && index < reservation.trajectory.size(); index++) {
				corridorHit = expanded.clip(
					reservation.trajectory.get(index - 1),
					reservation.trajectory.get(index)
				).isPresent();
			}
			boolean explosiveZone = reservation.explosive
				&& expanded.distanceToSqr(reservation.end()) < 16.0;
			if (!corridorHit && !explosiveZone) {
				continue;
			}
			if (selected == null || (reservation.explosive && !selected.explosive)) {
				selected = reservation;
			}
		}
		return selected;
	}

	public int activeCount(final long now) {
		this.prune(now);
		return this.reservations.size();
	}

	public void clear() {
		this.reservations.clear();
	}

	private void prune(final long now) {
		this.reservations.values().removeIf(reservation -> reservation.expiresAt < now);
	}

	public record Reservation(
		int shooterId,
		int targetId,
		List<Vec3> trajectory,
		double clearance,
		boolean explosive,
		long expiresAt
	) {
		public Vec3 start() {
			return this.trajectory.getFirst();
		}

		public Vec3 end() {
			return this.trajectory.getLast();
		}
	}
}

package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.BlastReservationPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;

/**
 * 主线程爆点预约板。空间哈希每次只读 3x3 桶并受 raw-check 上限约束，不随全服苦力怕数量线性扫描。
 */
public final class PaperBlastReservationBoard {
	private static final int MAXIMUM_EXPIRY_CLEANUP_PER_OPERATION = 128;

	private final Supplier<PaperSettings> settings;
	private final PaperMetrics metrics;
	private final Map<UUID, Reservation> byOwner = new HashMap<>();
	private final Map<CellKey, Set<UUID>> cells = new HashMap<>();
	private final PriorityQueue<Expiry> expiries = new PriorityQueue<>();

	public PaperBlastReservationBoard(
		final Supplier<PaperSettings> settings,
		final PaperMetrics metrics
	) {
		this.settings = settings;
		this.metrics = metrics;
	}

	public Availability availability(
		final Creeper creeper,
		final Location predictedCenter,
		final long predictedDetonationTick
	) {
		long now = Bukkit.getCurrentTick();
		this.cleanupExpired(now);
		return this.findConflict(
			creeper.getUniqueId(),
			predictedCenter.getX(),
			predictedCenter.getZ(),
			predictedCenter.getWorld().getUID(),
			predictedDetonationTick,
			now
		);
	}

	public boolean tryReserve(
		final Creeper creeper,
		final LivingEntity target,
		final Location predictedCenter,
		final long predictedDetonationTick,
		final boolean forced
	) {
		long now = Bukkit.getCurrentTick();
		this.cleanupExpired(now);
		PaperSettings config = this.settings.get();
		if (!config.enabled()
			|| !config.creeperTacticsEnabled()
			|| !creeper.isValid()
			|| creeper.isDead()
			|| !target.isValid()
			|| target.isDead()
			|| predictedCenter.getWorld() == null
			|| predictedCenter.getWorld() != creeper.getWorld()
			|| target.getWorld() != creeper.getWorld()
			|| !isFinite(predictedCenter)) {
			this.release(creeper);
			return false;
		}
		UUID ownerId = creeper.getUniqueId();
		double centerX = predictedCenter.getX();
		double centerZ = predictedCenter.getZ();
		UUID worldId = predictedCenter.getWorld().getUID();
		Availability availability = this.findConflict(
			ownerId,
			centerX,
			centerZ,
			worldId,
			predictedDetonationTick,
			now
		);
		if (!forced && availability != Availability.AVAILABLE) {
			this.release(ownerId);
			if (availability == Availability.SATURATED) {
				this.metrics.blastReservationSaturated();
			} else {
				this.metrics.blastReservationConflict();
			}
			return false;
		}

		long expiresAt = saturatingAdd(now, config.creeperBlastReservationLeaseTicks());
		Reservation previous = this.byOwner.get(ownerId);
		if (previous != null) {
			this.removeFromCell(previous);
		}
		CellKey cell = cellFor(worldId, centerX, centerZ, config.creeperBlastConflictRadius());
		Reservation replacement = new Reservation(
			ownerId,
			target.getUniqueId(),
			worldId,
			centerX,
			centerZ,
			predictedDetonationTick,
			expiresAt,
			cell
		);
		this.byOwner.put(ownerId, replacement);
		this.cells.computeIfAbsent(cell, ignored -> new HashSet<>()).add(ownerId);
		this.expiries.add(new Expiry(ownerId, expiresAt));
		this.compactExpiriesIfNeeded();
		if (previous == null) {
			this.metrics.blastReservationAcquired();
		}
		return true;
	}

	public Location stagingPoint(
		final Creeper creeper,
		final LivingEntity target,
		final int stableSide
	) {
		double yaw = Math.toRadians(target.getYaw());
		double horizontalScale = Math.cos(Math.toRadians(target.getPitch()));
		Vec3d point = BlastReservationPlanner.stagingPoint(
			target.getX(),
			target.getY(),
			target.getZ(),
			-horizontalScale * Math.sin(yaw),
			horizontalScale * Math.cos(yaw),
			stableSide,
			Math.max(7.0, this.settings.get().creeperBlastConflictRadius() + 1.0)
		);
		return new Location(creeper.getWorld(), point.x(), point.y(), point.z());
	}

	public void release(final Creeper creeper) {
		this.release(creeper.getUniqueId());
	}

	public void release(final UUID ownerId) {
		Reservation removed = this.byOwner.remove(ownerId);
		if (removed != null) {
			this.removeFromCell(removed);
			this.metrics.blastReservationReleased();
		}
		this.compactExpiriesIfNeeded();
	}

	public int activeCount() {
		this.cleanupExpired(Bukkit.getCurrentTick());
		return this.byOwner.size();
	}

	public void clear() {
		this.byOwner.clear();
		this.cells.clear();
		this.expiries.clear();
	}

	private Availability findConflict(
		final UUID ownerId,
		final double centerX,
		final double centerZ,
		final UUID worldId,
		final long detonationTick,
		final long now
	) {
		PaperSettings config = this.settings.get();
		double cellSize = config.creeperBlastConflictRadius();
		CellKey centerCell = cellFor(worldId, centerX, centerZ, cellSize);
		int rawChecks = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				Set<UUID> bucket = this.cells.get(new CellKey(worldId, centerCell.x() + dx, centerCell.z() + dz));
				if (bucket == null) {
					continue;
				}
				for (UUID candidateId : bucket) {
					if (candidateId.equals(ownerId)) {
						continue;
					}
					Reservation candidate = this.byOwner.get(candidateId);
					if (candidate == null || candidate.expiresAt() <= now) {
						continue;
					}
					if (++rawChecks > config.creeperBlastMaximumChecks()) {
						return Availability.SATURATED;
					}
					if (BlastReservationPlanner.conflicts(
						centerX,
						centerZ,
						detonationTick,
						candidate.centerX(),
						candidate.centerZ(),
						candidate.detonationTick(),
						config.creeperBlastConflictRadius(),
						config.creeperBlastSeparationTicks()
					)) {
						return Availability.CONFLICT;
					}
				}
			}
		}
		return Availability.AVAILABLE;
	}

	private void cleanupExpired(final long now) {
		int cleaned = 0;
		while (cleaned < MAXIMUM_EXPIRY_CLEANUP_PER_OPERATION
			&& !this.expiries.isEmpty()
			&& this.expiries.peek().expiresAt() <= now) {
			Expiry expiry = this.expiries.poll();
			Reservation current = this.byOwner.get(expiry.ownerId());
			if (current != null && current.expiresAt() == expiry.expiresAt()) {
				this.byOwner.remove(expiry.ownerId());
				this.removeFromCell(current);
			}
			cleaned++;
		}
	}

	private void removeFromCell(final Reservation reservation) {
		Set<UUID> bucket = this.cells.get(reservation.cell());
		if (bucket != null) {
			bucket.remove(reservation.ownerId());
			if (bucket.isEmpty()) {
				this.cells.remove(reservation.cell());
			}
		}
	}

	private void compactExpiriesIfNeeded() {
		int maximumBacklog = Math.max(128, this.byOwner.size() * 4);
		if (this.expiries.size() <= maximumBacklog) {
			return;
		}
		this.expiries.clear();
		for (Reservation reservation : this.byOwner.values()) {
			this.expiries.add(new Expiry(reservation.ownerId(), reservation.expiresAt()));
		}
	}

	private static boolean isFinite(final Location location) {
		return Double.isFinite(location.getX())
			&& Double.isFinite(location.getY())
			&& Double.isFinite(location.getZ());
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private static CellKey cellFor(
		final UUID worldId,
		final double centerX,
		final double centerZ,
		final double cellSize
	) {
		return new CellKey(
			worldId,
			BlastReservationPlanner.cellCoordinate(centerX, cellSize),
			BlastReservationPlanner.cellCoordinate(centerZ, cellSize)
		);
	}

	public enum Availability {
		AVAILABLE,
		CONFLICT,
		SATURATED
	}

	private record CellKey(UUID worldId, int x, int z) {
	}

	private record Reservation(
		UUID ownerId,
		UUID targetId,
		UUID worldId,
		double centerX,
		double centerZ,
		long detonationTick,
		long expiresAt,
		CellKey cell
	) {
	}

	private record Expiry(UUID ownerId, long expiresAt) implements Comparable<Expiry> {
		@Override
		public int compareTo(final Expiry other) {
			return Long.compare(this.expiresAt, other.expiresAt);
		}
	}
}

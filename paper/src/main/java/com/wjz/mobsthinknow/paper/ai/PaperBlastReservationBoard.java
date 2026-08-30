package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.BlastReservationPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
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
	private final Map<UUID, Long2ObjectOpenHashMap<ReservationBucket>> cellsByWorld = new HashMap<>();
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
		long cell = packedCell(
			BlastReservationPlanner.cellCoordinate(centerX, config.creeperBlastConflictRadius()),
			BlastReservationPlanner.cellCoordinate(centerZ, config.creeperBlastConflictRadius())
		);
		Reservation replacement = new Reservation(
			ownerId,
			worldId,
			centerX,
			centerZ,
			predictedDetonationTick,
			expiresAt,
			cell
		);
		this.byOwner.put(ownerId, replacement);
		this.bucket(worldId, cell).add(replacement);
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
		this.cellsByWorld.clear();
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
		int centerCellX = BlastReservationPlanner.cellCoordinate(centerX, cellSize);
		int centerCellZ = BlastReservationPlanner.cellCoordinate(centerZ, cellSize);
		Long2ObjectOpenHashMap<ReservationBucket> worldCells = this.cellsByWorld.get(worldId);
		if (worldCells == null) {
			return Availability.AVAILABLE;
		}
		int rawChecks = 0;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				ReservationBucket bucket = worldCells.get(packedCell(centerCellX + dx, centerCellZ + dz));
				if (bucket == null) {
					continue;
				}
				for (Reservation candidate = bucket.first(); candidate != null; candidate = candidate.bucketNext) {
					if (candidate.ownerId().equals(ownerId)) {
						continue;
					}
					if (candidate.expiresAt() <= now) {
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
		Long2ObjectOpenHashMap<ReservationBucket> worldCells = this.cellsByWorld.get(reservation.worldId());
		if (worldCells == null) {
			return;
		}
		ReservationBucket bucket = worldCells.get(reservation.cell());
		if (bucket != null) {
			bucket.remove(reservation);
			if (bucket.isEmpty()) {
				worldCells.remove(reservation.cell());
			}
		}
		if (worldCells.isEmpty()) {
			this.cellsByWorld.remove(reservation.worldId());
		}
	}

	private ReservationBucket bucket(final UUID worldId, final long cell) {
		Long2ObjectOpenHashMap<ReservationBucket> worldCells = this.cellsByWorld.computeIfAbsent(
			worldId,
			ignored -> new Long2ObjectOpenHashMap<>()
		);
		ReservationBucket bucket = worldCells.get(cell);
		if (bucket == null) {
			bucket = new ReservationBucket();
			worldCells.put(cell, bucket);
		}
		return bucket;
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

	static long packedCell(final int x, final int z) {
		return ((long)x << Integer.SIZE) ^ (z & 0xFFFFFFFFL);
	}

	public enum Availability {
		AVAILABLE,
		CONFLICT,
		SATURATED
	}

	static final class Reservation {
		private final UUID ownerId;
		private final UUID worldId;
		private final double centerX;
		private final double centerZ;
		private final long detonationTick;
		private final long expiresAt;
		private final long cell;
		private Reservation bucketPrevious;
		private Reservation bucketNext;

		Reservation(
			final UUID ownerId,
			final UUID worldId,
			final double centerX,
			final double centerZ,
			final long detonationTick,
			final long expiresAt,
			final long cell
		) {
			this.ownerId = ownerId;
			this.worldId = worldId;
			this.centerX = centerX;
			this.centerZ = centerZ;
			this.detonationTick = detonationTick;
			this.expiresAt = expiresAt;
			this.cell = cell;
		}

		private UUID ownerId() {
			return this.ownerId;
		}

		private UUID worldId() {
			return this.worldId;
		}

		private double centerX() {
			return this.centerX;
		}

		private double centerZ() {
			return this.centerZ;
		}

		private long detonationTick() {
			return this.detonationTick;
		}

		private long expiresAt() {
			return this.expiresAt;
		}

		private long cell() {
			return this.cell;
		}
	}

	static final class ReservationBucket {
		private Reservation first;
		private Reservation last;
		private int size;

		void add(final Reservation reservation) {
			reservation.bucketPrevious = this.last;
			reservation.bucketNext = null;
			if (this.last == null) {
				this.first = reservation;
			} else {
				this.last.bucketNext = reservation;
			}
			this.last = reservation;
			this.size++;
		}

		void remove(final Reservation reservation) {
			if (reservation.bucketPrevious == null) {
				this.first = reservation.bucketNext;
			} else {
				reservation.bucketPrevious.bucketNext = reservation.bucketNext;
			}
			if (reservation.bucketNext == null) {
				this.last = reservation.bucketPrevious;
			} else {
				reservation.bucketNext.bucketPrevious = reservation.bucketPrevious;
			}
			reservation.bucketPrevious = null;
			reservation.bucketNext = null;
			this.size--;
		}

		Reservation first() {
			return this.first;
		}

		boolean isEmpty() {
			return this.size == 0;
		}

		int size() {
			return this.size;
		}
	}

	private record Expiry(UUID ownerId, long expiresAt) implements Comparable<Expiry> {
		@Override
		public int compareTo(final Expiry other) {
			return Long.compare(this.expiresAt, other.expiresAt);
		}
	}
}

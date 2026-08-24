package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperProjectileEvasionSettings;
import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner;
import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner.ReactionProfile;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.plugin.Plugin;

/**
 * Main-thread projectile spatial index shared by every Paper skeleton.
 *
 * <p>One bounded task relocates tracked arrows between 12-block cells. A skeleton query visits at most 27 cells
 * and stops at the configured raw-candidate limit, so work cannot degrade into every skeleton scanning every
 * projectile in the world.</p>
 */
public final class PaperProjectileThreatBoard {
	private static final double CELL_SIZE = 12.0;

	private final BooleanSupplier globallyEnabled;
	private final Supplier<PaperProjectileEvasionSettings> settings;
	private final PaperMetrics metrics;
	private final LinkedHashMap<UUID, TrackedArrow> tracked = new LinkedHashMap<>();
	private final Map<UUID, Long2ObjectOpenHashMap<ArrowBucket>> cellsByWorld = new HashMap<>();
	private Plugin plugin;
	private BukkitTask task;

	public PaperProjectileThreatBoard(
		final BooleanSupplier globallyEnabled,
		final Supplier<PaperProjectileEvasionSettings> settings,
		final PaperMetrics metrics
	) {
		this.globallyEnabled = globallyEnabled;
		this.settings = settings;
		this.metrics = metrics;
	}

	public void start(final Plugin plugin) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.stopTask();
		this.clear();
		if (!this.enabled()) {
			return;
		}
		this.trackLoadedArrows();
		this.startTask();
	}

	public void stop() {
		this.stopTask();
		this.clear();
		this.plugin = null;
	}

	public void clear() {
		this.tracked.clear();
		this.cellsByWorld.clear();
	}

	public void reconfigure() {
		PaperProjectileEvasionSettings config = this.settings.get();
		if (!this.enabled()) {
			this.stopTask();
			this.clear();
			return;
		}
		while (this.tracked.size() > config.maximumTrackedProjectiles()) {
			UUID oldest = this.tracked.keySet().iterator().next();
			this.remove(oldest);
		}
		this.trackLoadedArrows();
		this.startTask();
	}

	public void observeAdded(final Entity entity) {
		if (entity instanceof AbstractArrow arrow) {
			this.track(arrow);
		}
	}

	public void observeRemoved(final Entity entity) {
		if (entity instanceof AbstractArrow arrow) {
			this.remove(arrow.getUniqueId());
		}
	}

	public Optional<Threat> nearestIncoming(
		final AbstractSkeleton skeleton,
		final ReactionProfile reaction,
		final double scanRadius,
		final int maximumChecks
	) {
		if (!this.enabled() || !skeleton.isValid() || reaction == null || !Double.isFinite(scanRadius) || scanRadius <= 0.0) {
			return Optional.empty();
		}
		this.metrics.projectileThreatQuery();

		double centerX = skeleton.getX();
		double centerY = skeleton.getY() + skeleton.getHeight() * 0.5;
		double centerZ = skeleton.getZ();
		int centerCellX = cell(centerX);
		int centerCellY = cell(centerY);
		int centerCellZ = cell(centerZ);
		Long2ObjectOpenHashMap<ArrowBucket> worldCells = this.cellsByWorld.get(
			skeleton.getWorld().getUID()
		);
		if (worldCells == null) {
			return Optional.empty();
		}

		double radiusSquared = scanRadius * scanRadius;
		long now = Bukkit.getCurrentTick();
		Threat best = null;
		int checks = 0;
		outer:
		for (int dy = -1; dy <= 1; dy++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					ArrowBucket bucket = worldCells.get(
						packedCell(centerCellX + dx, centerCellY + dy, centerCellZ + dz)
					);
					if (bucket == null) {
						continue;
					}
					for (TrackedArrow entry = bucket.first(); entry != null; entry = entry.bucketNext()) {
						if (checks >= maximumChecks) {
							break outer;
						}
						checks++;
						if (!entry.arrow().isValid()
							|| entry.arrow().isInBlock()
							|| shotBy(entry.arrow(), skeleton)) {
							continue;
						}
						double relativeX = centerX - entry.arrow().getX();
						double relativeY = centerY - entry.arrow().getY();
						double relativeZ = centerZ - entry.arrow().getZ();
						if (relativeX * relativeX + relativeY * relativeY + relativeZ * relativeZ > radiusSquared) {
							continue;
						}
						entry.refreshVelocity(now);
						double time = ProjectileEvasionPlanner.closestApproachTicks(
							relativeX,
							relativeY,
							relativeZ,
							entry.velocityX,
							entry.velocityY,
							entry.velocityZ,
							reaction.predictionHorizonTicks()
						);
						if (!Double.isFinite(time)
							|| !ProjectileEvasionPlanner.isIncomingAtClosestApproach(
								relativeX,
								relativeY,
								relativeZ,
								entry.velocityX,
								entry.velocityY,
								entry.velocityZ,
								time,
								reaction.safetyRadius()
							)) {
							continue;
						}
						if (best == null || time < best.closestApproachTicks()) {
							best = new Threat(entry.arrow(), time);
						}
					}
				}
			}
		}
		this.metrics.projectileThreatCandidatesChecked(checks);
		if (best != null) {
			this.metrics.projectileThreatDetected();
		}
		return Optional.ofNullable(best);
	}

	public int trackedCount() {
		return this.tracked.size();
	}

	public boolean isRunning() {
		return this.task != null;
	}

	private void trackLoadedArrows() {
		for (World world : Bukkit.getWorlds()) {
			for (AbstractArrow arrow : world.getEntitiesByClass(AbstractArrow.class)) {
				this.track(arrow);
			}
		}
	}

	private void startTask() {
		if (this.task == null && this.plugin != null) {
			this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
		}
	}

	private void stopTask() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
	}

	private void track(final AbstractArrow arrow) {
		PaperProjectileEvasionSettings config = this.settings.get();
		if (!this.enabled() || arrow.isInBlock() || this.tracked.containsKey(arrow.getUniqueId())) {
			return;
		}
		if (this.tracked.size() >= config.maximumTrackedProjectiles()) {
			this.metrics.projectileTrackingCapacityRejected();
			return;
		}
		long cell = packedCell(arrow.getX(), arrow.getY(), arrow.getZ());
		TrackedArrow entry = new TrackedArrow(arrow, arrow.getWorld().getUID(), cell);
		this.tracked.put(arrow.getUniqueId(), entry);
		this.bucket(entry.worldId(), cell).add(entry);
	}

	private void tick() {
		if (!this.enabled()) {
			if (!this.tracked.isEmpty()) {
				this.clear();
			}
			return;
		}
		Iterator<Map.Entry<UUID, TrackedArrow>> iterator = this.tracked.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, TrackedArrow> mapEntry = iterator.next();
			TrackedArrow entry = mapEntry.getValue();
			AbstractArrow arrow = entry.arrow();
			if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock()) {
				this.removeFromBucket(entry);
				iterator.remove();
				continue;
			}
			UUID worldId = arrow.getWorld().getUID();
			long nextCell = packedCell(arrow.getX(), arrow.getY(), arrow.getZ());
			if (worldId.equals(entry.worldId()) && nextCell == entry.cell()) {
				continue;
			}
			this.removeFromBucket(entry);
			entry.worldId = worldId;
			entry.cell = nextCell;
			this.bucket(worldId, nextCell).add(entry);
		}
	}

	private void remove(final UUID id) {
		TrackedArrow removed = this.tracked.remove(id);
		if (removed != null) {
			this.removeFromBucket(removed);
		}
	}

	private ArrowBucket bucket(final UUID worldId, final long cell) {
		Long2ObjectOpenHashMap<ArrowBucket> worldCells = this.cellsByWorld.computeIfAbsent(
			worldId,
			ignored -> new Long2ObjectOpenHashMap<>()
		);
		ArrowBucket bucket = worldCells.get(cell);
		if (bucket == null) {
			bucket = new ArrowBucket();
			worldCells.put(cell, bucket);
		}
		return bucket;
	}

	private void removeFromBucket(final TrackedArrow entry) {
		Long2ObjectOpenHashMap<ArrowBucket> worldCells = this.cellsByWorld.get(entry.worldId);
		if (worldCells == null) {
			return;
		}
		ArrowBucket bucket = worldCells.get(entry.cell);
		if (bucket != null) {
			bucket.remove(entry);
			if (bucket.isEmpty()) {
				worldCells.remove(entry.cell);
			}
		}
		if (worldCells.isEmpty()) {
			this.cellsByWorld.remove(entry.worldId);
		}
	}

	private static boolean shotBy(final AbstractArrow arrow, final AbstractSkeleton skeleton) {
		ProjectileSource source = arrow.getShooter();
		return source instanceof Entity entity && entity.getUniqueId().equals(skeleton.getUniqueId());
	}

	private boolean enabled() {
		return this.globallyEnabled.getAsBoolean() && this.settings.get().enabled();
	}

	static final class TrackedArrow {
		private final AbstractArrow arrow;
		private UUID worldId;
		private long cell;
		private long velocityTick = Long.MIN_VALUE;
		private double velocityX;
		private double velocityY;
		private double velocityZ;
		private TrackedArrow bucketPrevious;
		private TrackedArrow bucketNext;

		TrackedArrow(final AbstractArrow arrow, final UUID worldId, final long cell) {
			this.arrow = arrow;
			this.worldId = worldId;
			this.cell = cell;
		}

		private AbstractArrow arrow() {
			return this.arrow;
		}

		private UUID worldId() {
			return this.worldId;
		}

		private long cell() {
			return this.cell;
		}

		TrackedArrow bucketNext() {
			return this.bucketNext;
		}

		private void refreshVelocity(final long now) {
			if (this.velocityTick == now) {
				return;
			}
			Vector velocity = this.arrow.getVelocity();
			this.velocityX = velocity.getX();
			this.velocityY = velocity.getY();
			this.velocityZ = velocity.getZ();
			this.velocityTick = now;
		}
	}

	static final class ArrowBucket {
		private TrackedArrow first;
		private TrackedArrow last;
		private int size;

		void add(final TrackedArrow entry) {
			entry.bucketPrevious = this.last;
			entry.bucketNext = null;
			if (this.last == null) {
				this.first = entry;
			} else {
				this.last.bucketNext = entry;
			}
			this.last = entry;
			this.size++;
		}

		void remove(final TrackedArrow entry) {
			if (entry.bucketPrevious == null) {
				this.first = entry.bucketNext;
			} else {
				entry.bucketPrevious.bucketNext = entry.bucketNext;
			}
			if (entry.bucketNext == null) {
				this.last = entry.bucketPrevious;
			} else {
				entry.bucketNext.bucketPrevious = entry.bucketPrevious;
			}
			entry.bucketPrevious = null;
			entry.bucketNext = null;
			this.size--;
		}

		TrackedArrow first() {
			return this.first;
		}

		boolean isEmpty() {
			return this.size == 0;
		}

		int size() {
			return this.size;
		}
	}

	private static long packedCell(final double x, final double y, final double z) {
		return packedCell(cell(x), cell(y), cell(z));
	}

	static long packedCell(final int x, final int y, final int z) {
		return ((long)x & 0x3FF_FFFFL) << 38
			| ((long)z & 0x3FF_FFFFL) << 12
			| ((long)y & 0xFFFL);
	}

	private static int cell(final double coordinate) {
		return (int)Math.floor(coordinate / CELL_SIZE);
	}

	public record Threat(AbstractArrow projectile, double closestApproachTicks) {
	}
}

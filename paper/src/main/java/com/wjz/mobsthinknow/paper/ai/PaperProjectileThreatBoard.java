package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperProjectileEvasionSettings;
import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner;
import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner.ReactionProfile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

	private final Supplier<PaperProjectileEvasionSettings> settings;
	private final PaperMetrics metrics;
	private final LinkedHashMap<UUID, TrackedArrow> tracked = new LinkedHashMap<>();
	private final Map<UUID, Map<CellKey, LinkedHashSet<UUID>>> cellsByWorld = new HashMap<>();
	private BukkitTask task;

	public PaperProjectileThreatBoard(
		final Supplier<PaperProjectileEvasionSettings> settings,
		final PaperMetrics metrics
	) {
		this.settings = settings;
		this.metrics = metrics;
	}

	public void start(final Plugin plugin) {
		this.stop();
		for (World world : Bukkit.getWorlds()) {
			for (AbstractArrow arrow : world.getEntitiesByClass(AbstractArrow.class)) {
				this.track(arrow);
			}
		}
		this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
	}

	public void stop() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
		this.clear();
	}

	public void clear() {
		this.tracked.clear();
		this.cellsByWorld.clear();
	}

	public void reconfigure() {
		PaperProjectileEvasionSettings config = this.settings.get();
		if (!config.enabled()) {
			this.clear();
			return;
		}
		while (this.tracked.size() > config.maximumTrackedProjectiles()) {
			UUID oldest = this.tracked.keySet().iterator().next();
			this.remove(oldest);
		}
		for (World world : Bukkit.getWorlds()) {
			for (AbstractArrow arrow : world.getEntitiesByClass(AbstractArrow.class)) {
				this.track(arrow);
			}
		}
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
		this.metrics.projectileThreatQuery();
		if (!skeleton.isValid() || reaction == null || !Double.isFinite(scanRadius) || scanRadius <= 0.0) {
			return Optional.empty();
		}

		Vector center = skeleton.getBoundingBox().getCenter();
		CellKey centerCell = CellKey.at(center);
		Map<CellKey, LinkedHashSet<UUID>> worldCells = this.cellsByWorld.get(skeleton.getWorld().getUID());
		if (worldCells == null) {
			return Optional.empty();
		}

		double radiusSquared = scanRadius * scanRadius;
		Threat best = null;
		int checks = 0;
		outer:
		for (int dy = -1; dy <= 1; dy++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int dx = -1; dx <= 1; dx++) {
					LinkedHashSet<UUID> bucket = worldCells.get(centerCell.offset(dx, dy, dz));
					if (bucket == null) {
						continue;
					}
					for (UUID id : bucket) {
						if (checks >= maximumChecks) {
							break outer;
						}
						checks++;
						TrackedArrow entry = this.tracked.get(id);
						if (entry == null
							|| !entry.arrow().isValid()
							|| entry.arrow().isInBlock()
							|| shotBy(entry.arrow(), skeleton)) {
							continue;
						}
						Location projectile = entry.arrow().getLocation();
						if (projectile.toVector().distanceSquared(center) > radiusSquared) {
							continue;
						}
						Vector velocity = entry.arrow().getVelocity();
						double time = ProjectileEvasionPlanner.closestApproachTicks(
							center.getX() - projectile.getX(),
							center.getY() - projectile.getY(),
							center.getZ() - projectile.getZ(),
							velocity.getX(),
							velocity.getY(),
							velocity.getZ(),
							reaction.predictionHorizonTicks()
						);
						if (!Double.isFinite(time)
							|| !ProjectileEvasionPlanner.isIncoming(
								center.getX() - projectile.getX(),
								center.getY() - projectile.getY(),
								center.getZ() - projectile.getZ(),
								velocity.getX(),
								velocity.getY(),
								velocity.getZ(),
								reaction.predictionHorizonTicks(),
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

	private void track(final AbstractArrow arrow) {
		PaperProjectileEvasionSettings config = this.settings.get();
		if (!config.enabled() || arrow.isInBlock() || this.tracked.containsKey(arrow.getUniqueId())) {
			return;
		}
		if (this.tracked.size() >= config.maximumTrackedProjectiles()) {
			this.metrics.projectileTrackingCapacityRejected();
			return;
		}
		CellKey cell = CellKey.at(arrow.getLocation().toVector());
		TrackedArrow entry = new TrackedArrow(arrow, arrow.getWorld().getUID(), cell);
		this.tracked.put(arrow.getUniqueId(), entry);
		this.bucket(entry.worldId(), cell).add(arrow.getUniqueId());
	}

	private void tick() {
		if (!this.settings.get().enabled()) {
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
				this.removeFromBucket(mapEntry.getKey(), entry.worldId(), entry.cell());
				iterator.remove();
				continue;
			}
			UUID worldId = arrow.getWorld().getUID();
			CellKey nextCell = CellKey.at(arrow.getLocation().toVector());
			if (worldId.equals(entry.worldId()) && nextCell.equals(entry.cell())) {
				continue;
			}
			this.removeFromBucket(mapEntry.getKey(), entry.worldId(), entry.cell());
			this.bucket(worldId, nextCell).add(mapEntry.getKey());
			mapEntry.setValue(new TrackedArrow(arrow, worldId, nextCell));
		}
	}

	private void remove(final UUID id) {
		TrackedArrow removed = this.tracked.remove(id);
		if (removed != null) {
			this.removeFromBucket(id, removed.worldId(), removed.cell());
		}
	}

	private LinkedHashSet<UUID> bucket(final UUID worldId, final CellKey cell) {
		return this.cellsByWorld
			.computeIfAbsent(worldId, ignored -> new HashMap<>())
			.computeIfAbsent(cell, ignored -> new LinkedHashSet<>());
	}

	private void removeFromBucket(final UUID id, final UUID worldId, final CellKey cell) {
		Map<CellKey, LinkedHashSet<UUID>> worldCells = this.cellsByWorld.get(worldId);
		if (worldCells == null) {
			return;
		}
		LinkedHashSet<UUID> bucket = worldCells.get(cell);
		if (bucket != null && bucket.remove(id) && bucket.isEmpty()) {
			worldCells.remove(cell);
		}
		if (worldCells.isEmpty()) {
			this.cellsByWorld.remove(worldId);
		}
	}

	private static boolean shotBy(final AbstractArrow arrow, final AbstractSkeleton skeleton) {
		ProjectileSource source = arrow.getShooter();
		return source instanceof Entity entity && entity.getUniqueId().equals(skeleton.getUniqueId());
	}

	private record TrackedArrow(AbstractArrow arrow, UUID worldId, CellKey cell) {
	}

	private record CellKey(int x, int y, int z) {
		static CellKey at(final Vector point) {
			return new CellKey(cell(point.getX()), cell(point.getY()), cell(point.getZ()));
		}

		CellKey offset(final int dx, final int dy, final int dz) {
			return new CellKey(this.x + dx, this.y + dy, this.z + dz);
		}

		private static int cell(final double coordinate) {
			return (int)Math.floor(coordinate / CELL_SIZE);
		}
	}

	public record Threat(AbstractArrow projectile, double closestApproachTicks) {
	}
}

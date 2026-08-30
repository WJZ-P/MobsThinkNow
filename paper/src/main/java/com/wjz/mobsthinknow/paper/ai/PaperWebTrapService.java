package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperWebTrapSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.SoundGroup;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Spider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns bounded Paper cobweb placement, expiry and rollback on the server thread.
 *
 * <p>Only blocks that this service successfully changed are registered. Player/plugin replacement events discard
 * ownership immediately, so an expiry never overwrites a later block edit. Chunk and world unload hooks restore
 * entries while their blocks are still available and the expiry queue never forces an unloaded chunk back into
 * memory.</p>
 */
public final class PaperWebTrapService implements Listener {
	private static final int MAXIMUM_EXPIRIES_PER_TICK = 64;
	private static final int UNLOADED_RETRY_TICKS = 20;
	private static final int MINIMUM_HORIZONTAL_SPACING_SQUARED = 2;

	private final BooleanSupplier globallyEnabled;
	private final Supplier<PaperWebTrapSettings> settings;
	private final PaperMetrics metrics;
	private final BlockData cobweb = Material.COBWEB.createBlockData();
	private final Map<BlockKey, Trap> active = new HashMap<>();
	private final Map<UUID, Integer> activeByWorld = new HashMap<>();
	private final Map<UUID, LinkedHashSet<BlockKey>> activeByOwner = new HashMap<>();
	private final ChunkIndex activeByChunk = new ChunkIndex();
	private final PriorityQueue<Expiry> expiries = new PriorityQueue<>();
	private Plugin plugin;
	private BukkitTask task;
	private boolean listenerRegistered;
	private long cleanupBudgetTick = Long.MIN_VALUE;
	private int expiriesCheckedThisTick;

	public PaperWebTrapService(
		final BooleanSupplier globallyEnabled,
		final Supplier<PaperWebTrapSettings> settings,
		final PaperMetrics metrics
	) {
		this.globallyEnabled = globallyEnabled;
		this.settings = settings;
		this.metrics = metrics;
	}

	public void start(final Plugin plugin) {
		this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
		this.stopTask();
		if (!this.listenerRegistered) {
			Bukkit.getPluginManager().registerEvents(this, plugin);
			this.listenerRegistered = true;
		}
		this.startTask();
	}

	public void stop() {
		this.stopTask();
		this.restoreAll(false);
		if (this.listenerRegistered) {
			HandlerList.unregisterAll(this);
			this.listenerRegistered = false;
		}
		this.plugin = null;
	}

	public void reconfigure() {
		if (!this.enabled()) {
			this.stopTask();
			this.restoreAll(true);
			return;
		}
		this.enforceConfiguredCapacity();
		this.startTask();
	}

	public boolean canPlace(final Spider owner, final Block block) {
		this.cleanupExpired(Bukkit.getCurrentTick());
		return this.canPlaceNow(owner, block);
	}

	public boolean tryPlace(final Spider owner, final Block block, final long now) {
		this.cleanupExpired(now);
		if (!this.canPlaceNow(owner, block)) {
			this.metrics.spiderWebTrapPlacementRejected();
			return false;
		}

		BlockData web = this.cobweb.clone();
		EntityChangeBlockEvent change = new EntityChangeBlockEvent(owner, block, web);
		Bukkit.getPluginManager().callEvent(change);
		if (change.isCancelled()) {
			this.metrics.spiderWebTrapProtectionRejected();
			return false;
		}

		BlockData previous = block.getBlockData().clone();
		block.setBlockData(web, true);
		if (block.getType() != Material.COBWEB) {
			this.metrics.spiderWebTrapPlacementRejected();
			return false;
		}

		BlockKey key = BlockKey.at(block);
		long expiresAt = saturatingAdd(now, this.settings.get().lifetimeTicks());
		Trap trap = new Trap(owner.getUniqueId(), previous, expiresAt);
		this.active.put(key, trap);
		this.activeByWorld.merge(key.worldId(), 1, Integer::sum);
		this.activeByOwner.computeIfAbsent(trap.ownerId(), ignored -> new LinkedHashSet<>()).add(key);
		this.activeByChunk.add(key);
		this.expiries.add(new Expiry(key, expiresAt));
		this.compactExpiriesIfNeeded();
		this.playPlacementFeedback(block, web);
		this.metrics.spiderWebTrapPlaced();
		return true;
	}

	public int activeCount() {
		this.cleanupExpired(Bukkit.getCurrentTick());
		this.auditLoadedOwnership();
		return this.active.size();
	}

	public Optional<Location> ownedTrap(final UUID ownerId) {
		LinkedHashSet<BlockKey> owned = this.activeByOwner.get(ownerId);
		if (owned == null) {
			return Optional.empty();
		}
		for (BlockKey key : List.copyOf(owned)) {
			World world = Bukkit.getWorld(key.worldId());
			if (world == null || !key.isChunkLoaded(world)) {
				continue;
			}
			Block block = world.getBlockAt(key.x(), key.y(), key.z());
			if (block.getType() != Material.COBWEB) {
				this.discardStaleEntry(key);
				continue;
			}
			return Optional.of(key.location(world));
		}
		return Optional.empty();
	}

	public boolean isOwned(final Block block) {
		BlockKey key = BlockKey.at(block);
		if (!this.active.containsKey(key)) {
			return false;
		}
		if (block.getType() == Material.COBWEB) {
			return true;
		}
		this.discardStaleEntry(key);
		return false;
	}

	/** Restore every trap owned by one spider without touching unrelated encounters. */
	public int releaseOwner(final UUID ownerId, final boolean feedback) {
		LinkedHashSet<BlockKey> owned = this.activeByOwner.get(ownerId);
		if (owned == null) {
			return 0;
		}
		int released = 0;
		for (BlockKey key : List.copyOf(owned)) {
			if (this.remove(key, true, feedback)) {
				released++;
			}
		}
		return released;
	}

	public int activeOwnerCount() {
		this.auditLoadedOwnership();
		return this.activeByOwner.size();
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBreak(final BlockBreakEvent event) {
		this.discardOwnership(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockPlace(final BlockPlaceEvent event) {
		this.discardOwnership(event.getBlockPlaced());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockBurn(final BlockBurnEvent event) {
		this.discardOwnership(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockFade(final BlockFadeEvent event) {
		this.discardOwnership(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityChangesBlock(final EntityChangeBlockEvent event) {
		this.discardOwnership(event.getBlock());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityExplode(final EntityExplodeEvent event) {
		for (Block block : event.blockList()) {
			this.discardOwnership(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBlockExplode(final BlockExplodeEvent event) {
		for (Block block : event.blockList()) {
			this.discardOwnership(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonExtend(final BlockPistonExtendEvent event) {
		for (Block block : event.getBlocks()) {
			this.discardOwnership(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPistonRetract(final BlockPistonRetractEvent event) {
		for (Block block : event.getBlocks()) {
			this.discardOwnership(block);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkUnload(final ChunkUnloadEvent event) {
		this.restoreChunk(event.getChunk());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWorldUnload(final WorldUnloadEvent event) {
		this.restoreWorld(event.getWorld());
	}

	private boolean canPlaceNow(final Spider owner, final Block block) {
		PaperWebTrapSettings config = this.settings.get();
		World world = block.getWorld();
		BlockKey key = BlockKey.at(block);
		if (this.active.containsKey(key) && block.getType() != Material.COBWEB) {
			this.discardStaleEntry(key);
		}
		if (this.activeByWorld.getOrDefault(key.worldId(), 0) >= config.maximumActivePerWorld()) {
			this.auditLoadedOwnership(world);
			if (this.activeByWorld.getOrDefault(key.worldId(), 0) >= config.maximumActivePerWorld()) {
				return false;
			}
		}
		Boolean mobGriefing = world.getGameRuleValue(GameRules.MOB_GRIEFING);
		if (!this.globallyEnabled.getAsBoolean()
			|| !config.enabled()
			|| !owner.isValid()
			|| owner.isDead()
			|| owner.getWorld() != world
			|| !Boolean.TRUE.equals(mobGriefing)
			|| this.active.containsKey(key)
			|| !block.isEmpty()
			|| block.isLiquid()
			|| !block.canPlace(this.cobweb)
			|| !isSafeSupport(block.getRelative(BlockFace.DOWN))
			|| !world.getWorldBorder().isInside(block.getLocation().add(0.5, 0.0, 0.5))) {
			return false;
		}
		if (this.hasNearbyReservation(key) || hasNearbyCobweb(block)) {
			return false;
		}
		return true;
	}

	private boolean hasNearbyReservation(final BlockKey key) {
		for (int yOffset = -1; yOffset <= 1; yOffset++) {
			for (int zOffset = -1; zOffset <= 1; zOffset++) {
				for (int xOffset = -1; xOffset <= 1; xOffset++) {
					if (xOffset * xOffset + zOffset * zOffset > MINIMUM_HORIZONTAL_SPACING_SQUARED) {
						continue;
					}
					if (this.active.containsKey(key.offset(xOffset, yOffset, zOffset))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static boolean hasNearbyCobweb(final Block center) {
		for (int zOffset = -1; zOffset <= 1; zOffset++) {
			for (int xOffset = -1; xOffset <= 1; xOffset++) {
				if ((xOffset != 0 || zOffset != 0)
					&& center.getRelative(xOffset, 0, zOffset).getType() == Material.COBWEB) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isSafeSupport(final Block support) {
		Material material = support.getType();
		return support.isSolid()
			&& material != Material.CACTUS
			&& material != Material.MAGMA_BLOCK
			&& material != Material.CAMPFIRE
			&& material != Material.SOUL_CAMPFIRE
			&& material != Material.POWDER_SNOW;
	}

	private void tick() {
		if (!this.enabled()) {
			if (!this.active.isEmpty()) {
				this.restoreAll(true);
			}
			return;
		}
		if (this.active.isEmpty()) {
			this.activeByWorld.clear();
			this.expiries.clear();
			return;
		}
		for (World world : Bukkit.getWorlds()) {
			if (this.activeByWorld.containsKey(world.getUID())
				&& !Boolean.TRUE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING))) {
				this.restoreLoadedWorld(world);
			}
		}
		this.cleanupExpired(Bukkit.getCurrentTick());
	}

	private void cleanupExpired(final long now) {
		if (this.cleanupBudgetTick != now) {
			this.cleanupBudgetTick = now;
			this.expiriesCheckedThisTick = 0;
		}
		int remainingBudget = MAXIMUM_EXPIRIES_PER_TICK - this.expiriesCheckedThisTick;
		if (remainingBudget <= 0) {
			return;
		}
		int checked = 0;
		while (checked < remainingBudget
			&& !this.expiries.isEmpty()
			&& this.expiries.peek().expiresAt() <= now) {
			Expiry expiry = this.expiries.poll();
			Trap current = this.active.get(expiry.key());
			if (current == null || current.expiresAt() != expiry.expiresAt()) {
				checked++;
				continue;
			}
			World world = Bukkit.getWorld(expiry.key().worldId());
			if (world != null && !expiry.key().isChunkLoaded(world)) {
				long retryAt = saturatingAdd(now, UNLOADED_RETRY_TICKS);
				this.expiries.add(new Expiry(expiry.key(), retryAt));
				this.active.put(expiry.key(), current.withExpiresAt(retryAt));
				checked++;
				continue;
			}
			this.remove(expiry.key(), true, true);
			checked++;
		}
		this.expiriesCheckedThisTick += checked;
	}

	private void restoreChunk(final Chunk chunk) {
		UUID worldId = chunk.getWorld().getUID();
		for (BlockKey key : this.activeByChunk.snapshot(worldId, chunk.getX(), chunk.getZ())) {
			this.remove(key, true, false, chunk.getWorld());
		}
	}

	private void restoreWorld(final World world) {
		UUID worldId = world.getUID();
		for (BlockKey key : new ArrayList<>(this.active.keySet())) {
			if (key.worldId().equals(worldId)) {
				this.remove(key, true, false, world);
			}
		}
	}

	private void restoreLoadedWorld(final World world) {
		UUID worldId = world.getUID();
		for (BlockKey key : new ArrayList<>(this.active.keySet())) {
			if (key.worldId().equals(worldId) && key.isChunkLoaded(world)) {
				this.remove(key, true, false);
			}
		}
	}

	private void enforceConfiguredCapacity() {
		int maximum = this.settings.get().maximumActivePerWorld();
		Map<UUID, List<Map.Entry<BlockKey, Trap>>> byWorld = new HashMap<>();
		for (Map.Entry<BlockKey, Trap> entry : this.active.entrySet()) {
			byWorld.computeIfAbsent(entry.getKey().worldId(), ignored -> new ArrayList<>()).add(entry);
		}
		for (List<Map.Entry<BlockKey, Trap>> entries : byWorld.values()) {
			int excess = entries.size() - maximum;
			if (excess <= 0) {
				continue;
			}
			entries.sort(Comparator.comparingLong(entry -> entry.getValue().expiresAt()));
			for (int index = 0; index < excess; index++) {
				this.remove(entries.get(index).getKey(), true, true);
			}
		}
	}

	private void restoreAll(final boolean feedback) {
		for (BlockKey key : new ArrayList<>(this.active.keySet())) {
			this.remove(key, true, feedback);
		}
		this.active.clear();
		this.activeByWorld.clear();
		this.activeByOwner.clear();
		this.activeByChunk.clear();
		this.expiries.clear();
		this.cleanupBudgetTick = Long.MIN_VALUE;
		this.expiriesCheckedThisTick = 0;
	}

	private void discardOwnership(final Block block) {
		if (this.remove(BlockKey.at(block), false, false)) {
			this.metrics.spiderWebTrapOwnershipLost();
		}
	}

	private void discardStaleEntry(final BlockKey key) {
		if (this.remove(key, false, false)) {
			this.metrics.spiderWebTrapOwnershipLost();
		}
	}

	private void auditLoadedOwnership() {
		for (World world : Bukkit.getWorlds()) {
			this.auditLoadedOwnership(world);
		}
	}

	private void auditLoadedOwnership(final World world) {
		for (BlockKey key : new ArrayList<>(this.active.keySet())) {
			if (key.worldId().equals(world.getUID())
				&& key.isChunkLoaded(world)
				&& world.getBlockAt(key.x(), key.y(), key.z()).getType() != Material.COBWEB) {
				this.discardStaleEntry(key);
			}
		}
	}

	private boolean remove(final BlockKey key, final boolean restore, final boolean feedback) {
		return this.remove(key, restore, feedback, null);
	}

	private boolean remove(
		final BlockKey key,
		final boolean restore,
		final boolean feedback,
		final World accessibleWorld
	) {
		Trap trap = this.active.remove(key);
		if (trap == null) {
			return false;
		}
		this.activeByWorld.computeIfPresent(key.worldId(), (ignored, count) -> count <= 1 ? null : count - 1);
		this.activeByOwner.computeIfPresent(trap.ownerId(), (ignored, keys) -> {
			keys.remove(key);
			return keys.isEmpty() ? null : keys;
		});
		this.activeByChunk.remove(key);
		this.compactExpiriesIfNeeded();
		if (!restore) {
			return true;
		}
		World world = accessibleWorld != null ? accessibleWorld : Bukkit.getWorld(key.worldId());
		if (world == null || (accessibleWorld == null && !key.isChunkLoaded(world))) {
			return true;
		}
		Block block = world.getBlockAt(key.x(), key.y(), key.z());
		if (block.getType() != Material.COBWEB) {
			return true;
		}
		BlockData web = block.getBlockData();
		SoundGroup sounds = block.getBlockSoundGroup();
		Location center = block.getLocation().add(0.5, 0.45, 0.5);
		block.setBlockData(trap.previous(), true);
		this.metrics.spiderWebTrapRestored();
		if (feedback) {
			world.playSound(center, sounds.getBreakSound(), SoundCategory.BLOCKS, 0.55F, sounds.getPitch() * 1.15F);
			world.spawnParticle(Particle.BLOCK, center, 8, 0.25, 0.25, 0.25, 0.02, web);
		}
		return true;
	}

	private void playPlacementFeedback(final Block block, final BlockData web) {
		World world = block.getWorld();
		Location center = block.getLocation().add(0.5, 0.5, 0.5);
		SoundGroup sounds = block.getBlockSoundGroup();
		world.playSound(center, sounds.getPlaceSound(), SoundCategory.HOSTILE, 0.85F, sounds.getPitch() * 1.20F);
		world.spawnParticle(Particle.BLOCK, center, 14, 0.30, 0.30, 0.30, 0.03, web);
	}

	private void stopTask() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
	}

	private void startTask() {
		if (this.task == null && this.plugin != null && this.enabled()) {
			this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
		}
	}

	public boolean isRunning() {
		return this.task != null;
	}

	private boolean enabled() {
		return this.globallyEnabled.getAsBoolean() && this.settings.get().enabled();
	}

	/** Stale queue nodes from player edits never exceed a small multiple of the live, hard-capped registry. */
	private void compactExpiriesIfNeeded() {
		int maximumBacklog = Math.max(128, this.active.size() * 4);
		if (this.expiries.size() <= maximumBacklog) {
			return;
		}
		this.expiries.clear();
		for (Map.Entry<BlockKey, Trap> entry : this.active.entrySet()) {
			this.expiries.add(new Expiry(entry.getKey(), entry.getValue().expiresAt()));
		}
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	record BlockKey(UUID worldId, int x, int y, int z) {
		static BlockKey at(final Block block) {
			return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
		}

		BlockKey offset(final int xOffset, final int yOffset, final int zOffset) {
			return new BlockKey(this.worldId, this.x + xOffset, this.y + yOffset, this.z + zOffset);
		}

		boolean isChunkLoaded(final World world) {
			return world.isChunkLoaded(Math.floorDiv(this.x, 16), Math.floorDiv(this.z, 16));
		}

		Location location(final World world) {
			return new Location(world, this.x + 0.5, this.y, this.z + 0.5);
		}
	}

	record ChunkKey(UUID worldId, int x, int z) {
		static ChunkKey at(final BlockKey key) {
			return new ChunkKey(key.worldId(), Math.floorDiv(key.x(), 16), Math.floorDiv(key.z(), 16));
		}
	}

	static final class ChunkIndex {
		private final Map<ChunkKey, LinkedHashSet<BlockKey>> entries = new HashMap<>();

		void add(final BlockKey key) {
			this.entries.computeIfAbsent(ChunkKey.at(key), ignored -> new LinkedHashSet<>()).add(key);
		}

		void remove(final BlockKey key) {
			this.entries.computeIfPresent(ChunkKey.at(key), (ignored, keys) -> {
				keys.remove(key);
				return keys.isEmpty() ? null : keys;
			});
		}

		List<BlockKey> snapshot(final UUID worldId, final int chunkX, final int chunkZ) {
			LinkedHashSet<BlockKey> keys = this.entries.get(new ChunkKey(worldId, chunkX, chunkZ));
			return keys == null ? List.of() : List.copyOf(keys);
		}

		void clear() {
			this.entries.clear();
		}

		int chunkCount() {
			return this.entries.size();
		}
	}

	private record Trap(UUID ownerId, BlockData previous, long expiresAt) {
		Trap withExpiresAt(final long replacement) {
			return new Trap(this.ownerId, this.previous, replacement);
		}
	}

	private record Expiry(BlockKey key, long expiresAt) implements Comparable<Expiry> {
		@Override
		public int compareTo(final Expiry other) {
			return Long.compare(this.expiresAt, other.expiresAt);
		}
	}
}

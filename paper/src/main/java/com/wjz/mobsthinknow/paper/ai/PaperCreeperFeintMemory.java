package com.wjz.mobsthinknow.paper.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Creeper;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * 相邻 Paper Goal 共享的短期假引爆所有权。
 *
 * <p>状态只在服务器主线程访问：真实引信与接敌 Goal 能无歧义地让位；插件自身触发的
 * CreeperIgniteEvent 也能与玩家用打火石进行的外部点燃区分开。</p>
 */
public final class PaperCreeperFeintMemory {
	private static final int MAXIMUM_COOLING_ENTRIES = 64;
	private static final int MAXIMUM_COMPLETION_ENTRIES = 256;
	private static final long COMPLETION_MEMORY_TICKS = 600L;

	private final Set<UUID> active = new HashSet<>();
	private final Set<UUID> ownedIgnition = new HashSet<>();
	private final Set<UUID> externallyIgnited = new HashSet<>();
	private final Map<UUID, Long> nextAllowedAt = new HashMap<>();
	private final Map<UUID, Long> completedAt = new LinkedHashMap<>();
	private final Map<UUID, CoolingEntry> cooling = new LinkedHashMap<>();
	private BukkitTask coolingTask;

	public void start(final Plugin plugin) {
		if (this.coolingTask == null) {
			this.coolingTask = Bukkit.getScheduler().runTaskTimer(
				plugin,
				() -> this.tickCooling(Bukkit.getCurrentTick()),
				1L,
				1L
			);
		}
	}

	public void stop() {
		if (this.coolingTask != null) {
			this.coolingTask.cancel();
			this.coolingTask = null;
		}
		this.clear();
	}

	public boolean canStart(final Creeper creeper, final long now) {
		UUID id = creeper.getUniqueId();
		Long nextAllowed = this.nextAllowedAt.get(id);
		if (nextAllowed != null && now >= nextAllowed) {
			this.nextAllowedAt.remove(id, nextAllowed);
			nextAllowed = null;
		}
		return !this.active.contains(id)
			&& !this.cooling.containsKey(id)
			&& this.active.size() + this.cooling.size() < MAXIMUM_COOLING_ENTRIES
			&& nextAllowed == null;
	}

	public boolean begin(final Creeper creeper, final long now) {
		if (!this.canStart(creeper, now)) {
			return false;
		}
		UUID id = creeper.getUniqueId();
		this.active.add(id);
		this.externallyIgnited.remove(id);
		return true;
	}

	public boolean isActive(final Creeper creeper) {
		return this.active.contains(creeper.getUniqueId());
	}

	public boolean blocksCombatGoals(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		CoolingEntry entry = this.cooling.get(id);
		if (entry != null && Bukkit.getCurrentTick() >= entry.combatUnlockAt()) {
			coolDown(entry.creeper());
			this.cooling.remove(id, entry);
			this.ownedIgnition.remove(id);
			entry = null;
		}
		return this.active.contains(id)
			|| entry != null && Bukkit.getCurrentTick() < entry.combatUnlockAt();
	}

	public void beginOwnedIgnition(final Creeper creeper) {
		this.ownedIgnition.add(creeper.getUniqueId());
	}

	public void endOwnedIgnition(final Creeper creeper) {
		this.ownedIgnition.remove(creeper.getUniqueId());
	}

	public void observeIgnition(final Creeper creeper, final boolean ignited) {
		UUID id = creeper.getUniqueId();
		if (ignited && this.active.contains(id) && !this.ownedIgnition.contains(id)) {
			this.externallyIgnited.add(id);
		}
	}

	/** 玩家交互在原版真正写入引信前到达，因此可无歧义地覆盖插件当前所有权。 */
	public void markExternalIgnition(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		if (this.active.contains(id) || this.cooling.containsKey(id)) {
			this.externallyIgnited.add(id);
			this.ownedIgnition.remove(id);
			this.cooling.remove(id);
		}
	}

	public boolean wasExternallyIgnited(final Creeper creeper) {
		return this.externallyIgnited.contains(creeper.getUniqueId());
	}

	public void beginPostFeintCooling(final Creeper creeper, final long now, final int durationTicks) {
		UUID id = creeper.getUniqueId();
		this.ownedIgnition.add(id);
		this.cooling.put(id, new CoolingEntry(creeper, saturatingAdd(now, Math.max(1, durationTicks))));
	}

	/** Stops synthetic fuse suppression exactly when the real tactical fuse has acquired a valid attack. */
	public void transferToRealFuse(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		this.cooling.remove(id);
		this.ownedIgnition.remove(id);
	}

	public void finish(final Creeper creeper, final long now, final int cooldownTicks) {
		UUID id = creeper.getUniqueId();
		this.active.remove(id);
		if (!this.cooling.containsKey(id)) {
			this.ownedIgnition.remove(id);
		}
		this.externallyIgnited.remove(id);
		this.nextAllowedAt.put(id, saturatingAdd(now, Math.max(1, cooldownTicks)));
	}

	public void markCompleted(final Creeper creeper, final long now) {
		this.pruneCompletions(now);
		this.completedAt.put(creeper.getUniqueId(), now);
		while (this.completedAt.size() > MAXIMUM_COMPLETION_ENTRIES) {
			this.completedAt.remove(this.completedAt.keySet().iterator().next());
		}
	}

	public boolean completedSince(final Creeper creeper, final long tick) {
		long now = Bukkit.getCurrentTick();
		this.pruneCompletions(now);
		return this.completedAt.getOrDefault(creeper.getUniqueId(), Long.MIN_VALUE) >= tick;
	}

	public void discard(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		this.active.remove(id);
		this.ownedIgnition.remove(id);
		this.externallyIgnited.remove(id);
		this.cooling.remove(id);
		this.nextAllowedAt.remove(id);
		this.completedAt.remove(id);
	}

	public int activeCount() {
		return this.active.size();
	}

	public int coolingCount() {
		return this.cooling.size();
	}

	public void clear() {
		this.active.clear();
		this.ownedIgnition.clear();
		this.externallyIgnited.clear();
		this.nextAllowedAt.clear();
		this.completedAt.clear();
		for (CoolingEntry entry : this.cooling.values()) {
			coolDown(entry.creeper());
		}
		this.cooling.clear();
	}

	void tickCooling(final long now) {
		this.pruneCompletions(now);
		var iterator = this.cooling.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, CoolingEntry> mapped = iterator.next();
			CoolingEntry entry = mapped.getValue();
			Creeper creeper = entry.creeper();
			if (!creeper.isValid() || creeper.isDead()) {
				this.ownedIgnition.remove(mapped.getKey());
				iterator.remove();
				continue;
			}
			coolDown(creeper);
			if (now >= entry.combatUnlockAt()) {
				this.ownedIgnition.remove(mapped.getKey());
				iterator.remove();
			}
		}
	}

	private void pruneCompletions(final long now) {
		this.completedAt.values().removeIf(completed -> now - completed > COMPLETION_MEMORY_TICKS);
	}

	private static void coolDown(final Creeper creeper) {
		if (creeper.isValid() && !creeper.isDead()) {
			if (creeper.isIgnited()) {
				creeper.setIgnited(false);
			}
			if (creeper.getFuseTicks() != 0) {
				creeper.setFuseTicks(0);
			}
		}
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private record CoolingEntry(Creeper creeper, long combatUnlockAt) {
	}
}

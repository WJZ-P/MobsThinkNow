package com.wjz.mobsthinknow.paper.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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

	private final Set<UUID> active = new HashSet<>();
	private final Set<UUID> ownedIgnition = new HashSet<>();
	private final Set<UUID> externallyIgnited = new HashSet<>();
	private final Map<UUID, Long> nextAllowedAt = new HashMap<>();
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
		return !this.active.contains(id) && now >= this.nextAllowedAt.getOrDefault(id, Long.MIN_VALUE);
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
		return this.active.contains(id) || this.cooling.containsKey(id);
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
		if (this.cooling.size() >= MAXIMUM_COOLING_ENTRIES && !this.cooling.containsKey(id)) {
			Iterator<Map.Entry<UUID, CoolingEntry>> iterator = this.cooling.entrySet().iterator();
			if (iterator.hasNext()) {
				Map.Entry<UUID, CoolingEntry> oldest = iterator.next();
				coolDown(oldest.getValue().creeper());
				this.ownedIgnition.remove(oldest.getKey());
				iterator.remove();
			}
		}
		this.ownedIgnition.add(id);
		this.cooling.put(id, new CoolingEntry(creeper, saturatingAdd(now, Math.max(1, durationTicks))));
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

	public void discard(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		this.active.remove(id);
		this.ownedIgnition.remove(id);
		this.externallyIgnited.remove(id);
		this.cooling.remove(id);
		this.nextAllowedAt.remove(id);
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
		for (CoolingEntry entry : this.cooling.values()) {
			coolDown(entry.creeper());
		}
		this.cooling.clear();
	}

	private void tickCooling(final long now) {
		Iterator<Map.Entry<UUID, CoolingEntry>> iterator = this.cooling.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, CoolingEntry> mapped = iterator.next();
			CoolingEntry entry = mapped.getValue();
			Creeper creeper = entry.creeper();
			if (!creeper.isValid() || creeper.isDead() || now >= entry.expiresAt()) {
				this.ownedIgnition.remove(mapped.getKey());
				iterator.remove();
				continue;
			}
			coolDown(creeper);
		}
	}

	private static void coolDown(final Creeper creeper) {
		if (creeper.isValid() && !creeper.isDead()) {
			creeper.setIgnited(false);
			creeper.setFuseTicks(0);
		}
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private record CoolingEntry(Creeper creeper, long expiresAt) {
	}
}

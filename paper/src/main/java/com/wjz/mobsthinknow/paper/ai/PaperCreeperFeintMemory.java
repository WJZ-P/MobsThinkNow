package com.wjz.mobsthinknow.paper.ai;

import java.util.HashMap;
import java.util.HashSet;
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
	private final Map<UUID, CompletionEntry> completions = new HashMap<>();
	private final Map<UUID, CoolingEntry> cooling = new HashMap<>();
	private CoolingEntry oldestCooling;
	private CoolingEntry newestCooling;
	private CompletionEntry oldestCompletion;
	private CompletionEntry newestCompletion;
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
		if (entry == null) {
			return this.active.contains(id);
		}
		long now = Bukkit.getCurrentTick();
		if (now >= entry.combatUnlockAt) {
			coolDown(entry.creeper);
			this.removeCooling(entry);
			this.ownedIgnition.remove(id);
			return this.active.contains(id);
		}
		return true;
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
			this.removeCooling(id);
		}
	}

	public boolean wasExternallyIgnited(final Creeper creeper) {
		return this.externallyIgnited.contains(creeper.getUniqueId());
	}

	public void beginPostFeintCooling(final Creeper creeper, final long now, final int durationTicks) {
		UUID id = creeper.getUniqueId();
		this.ownedIgnition.add(id);
		this.removeCooling(id);
		CoolingEntry entry = new CoolingEntry(
			id,
			creeper,
			saturatingAdd(now, Math.max(1, durationTicks))
		);
		this.cooling.put(id, entry);
		this.appendCooling(entry);
	}

	/** Stops synthetic fuse suppression exactly when the real tactical fuse has acquired a valid attack. */
	public void transferToRealFuse(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		this.removeCooling(id);
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
		UUID id = creeper.getUniqueId();
		CompletionEntry entry = this.completions.get(id);
		if (entry == null) {
			entry = new CompletionEntry(id);
			this.completions.put(id, entry);
		} else {
			this.unlinkCompletion(entry);
		}
		entry.completedAt = now;
		this.appendCompletion(entry);
		while (this.completions.size() > MAXIMUM_COMPLETION_ENTRIES) {
			this.removeOldestCompletion();
		}
	}

	public boolean completedSince(final Creeper creeper, final long tick) {
		long now = Bukkit.getCurrentTick();
		this.pruneCompletions(now);
		CompletionEntry entry = this.completions.get(creeper.getUniqueId());
		return entry != null && entry.completedAt >= tick;
	}

	public void discard(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		this.active.remove(id);
		this.ownedIgnition.remove(id);
		this.externallyIgnited.remove(id);
		this.removeCooling(id);
		this.nextAllowedAt.remove(id);
		CompletionEntry completion = this.completions.remove(id);
		if (completion != null) {
			this.unlinkCompletion(completion);
		}
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
		this.completions.clear();
		this.oldestCompletion = null;
		this.newestCompletion = null;
		for (CoolingEntry entry = this.oldestCooling; entry != null; entry = entry.next) {
			coolDown(entry.creeper);
		}
		this.cooling.clear();
		this.oldestCooling = null;
		this.newestCooling = null;
	}

	void tickCooling(final long now) {
		this.pruneCompletions(now);
		CoolingEntry entry = this.oldestCooling;
		while (entry != null) {
			CoolingEntry next = entry.next;
			Creeper creeper = entry.creeper;
			if (!creeper.isValid() || creeper.isDead()) {
				this.ownedIgnition.remove(entry.id);
				this.removeCooling(entry);
			} else {
				coolDown(creeper);
				if (now >= entry.combatUnlockAt) {
					this.ownedIgnition.remove(entry.id);
					this.removeCooling(entry);
				}
			}
			entry = next;
		}
	}

	private void appendCooling(final CoolingEntry entry) {
		entry.previous = this.newestCooling;
		entry.next = null;
		if (this.newestCooling == null) {
			this.oldestCooling = entry;
		} else {
			this.newestCooling.next = entry;
		}
		this.newestCooling = entry;
	}

	private void unlinkCooling(final CoolingEntry entry) {
		if (entry.previous == null) {
			this.oldestCooling = entry.next;
		} else {
			entry.previous.next = entry.next;
		}
		if (entry.next == null) {
			this.newestCooling = entry.previous;
		} else {
			entry.next.previous = entry.previous;
		}
		entry.previous = null;
		entry.next = null;
	}

	private CoolingEntry removeCooling(final UUID id) {
		CoolingEntry entry = this.cooling.remove(id);
		if (entry != null) {
			this.unlinkCooling(entry);
		}
		return entry;
	}

	private void removeCooling(final CoolingEntry entry) {
		if (this.cooling.remove(entry.id, entry)) {
			this.unlinkCooling(entry);
		}
	}

	private void pruneCompletions(final long now) {
		while (this.oldestCompletion != null
			&& now - this.oldestCompletion.completedAt > COMPLETION_MEMORY_TICKS) {
			this.removeOldestCompletion();
		}
	}

	int completionCount() {
		return this.completions.size();
	}

	private void appendCompletion(final CompletionEntry entry) {
		entry.previous = this.newestCompletion;
		entry.next = null;
		if (this.newestCompletion == null) {
			this.oldestCompletion = entry;
		} else {
			this.newestCompletion.next = entry;
		}
		this.newestCompletion = entry;
	}

	private void unlinkCompletion(final CompletionEntry entry) {
		if (entry.previous == null) {
			this.oldestCompletion = entry.next;
		} else {
			entry.previous.next = entry.next;
		}
		if (entry.next == null) {
			this.newestCompletion = entry.previous;
		} else {
			entry.next.previous = entry.previous;
		}
		entry.previous = null;
		entry.next = null;
	}

	private void removeOldestCompletion() {
		CompletionEntry entry = this.oldestCompletion;
		if (entry == null) {
			return;
		}
		this.unlinkCompletion(entry);
		this.completions.remove(entry.id, entry);
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

	private static final class CoolingEntry {
		private final UUID id;
		private final Creeper creeper;
		private final long combatUnlockAt;
		private CoolingEntry previous;
		private CoolingEntry next;

		private CoolingEntry(final UUID id, final Creeper creeper, final long combatUnlockAt) {
			this.id = id;
			this.creeper = creeper;
			this.combatUnlockAt = combatUnlockAt;
		}
	}

	private static final class CompletionEntry {
		private final UUID id;
		private long completedAt;
		private CompletionEntry previous;
		private CompletionEntry next;

		private CompletionEntry(final UUID id) {
			this.id = id;
		}
	}
}

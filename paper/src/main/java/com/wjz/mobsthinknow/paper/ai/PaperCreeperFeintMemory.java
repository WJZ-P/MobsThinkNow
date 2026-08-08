package com.wjz.mobsthinknow.paper.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Creeper;

/**
 * 相邻 Paper Goal 共享的短期假引爆所有权。
 *
 * <p>状态只在服务器主线程访问：真实引信与接敌 Goal 能无歧义地让位；插件自身触发的
 * CreeperIgniteEvent 也能与玩家用打火石进行的外部点燃区分开。</p>
 */
public final class PaperCreeperFeintMemory {
	private final Set<UUID> active = new HashSet<>();
	private final Set<UUID> ownedIgnitionMutation = new HashSet<>();
	private final Set<UUID> externallyIgnited = new HashSet<>();
	private final Map<UUID, Long> nextAllowedAt = new HashMap<>();

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

	public void beginOwnedIgnitionMutation(final Creeper creeper) {
		this.ownedIgnitionMutation.add(creeper.getUniqueId());
	}

	public void endOwnedIgnitionMutation(final Creeper creeper) {
		this.ownedIgnitionMutation.remove(creeper.getUniqueId());
	}

	public void observeIgnition(final Creeper creeper, final boolean ignited) {
		UUID id = creeper.getUniqueId();
		if (ignited && this.active.contains(id) && !this.ownedIgnitionMutation.contains(id)) {
			this.externallyIgnited.add(id);
		}
	}

	public boolean wasExternallyIgnited(final Creeper creeper) {
		return this.externallyIgnited.contains(creeper.getUniqueId());
	}

	public void finish(final Creeper creeper, final long now, final int cooldownTicks) {
		UUID id = creeper.getUniqueId();
		this.active.remove(id);
		this.ownedIgnitionMutation.remove(id);
		this.externallyIgnited.remove(id);
		this.nextAllowedAt.put(id, saturatingAdd(now, Math.max(1, cooldownTicks)));
	}

	public void discard(final Creeper creeper) {
		UUID id = creeper.getUniqueId();
		this.active.remove(id);
		this.ownedIgnitionMutation.remove(id);
		this.externallyIgnited.remove(id);
		this.nextAllowedAt.remove(id);
	}

	public int activeCount() {
		return this.active.size();
	}

	public void clear() {
		this.active.clear();
		this.ownedIgnitionMutation.clear();
		this.externallyIgnited.clear();
		this.nextAllowedAt.clear();
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}
}

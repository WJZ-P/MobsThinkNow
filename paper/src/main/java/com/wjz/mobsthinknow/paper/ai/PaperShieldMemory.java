package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.shared.ai.ShieldCombatPlanner;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;

/** 成功格挡事件到盾卫 Goal 的一次性主线程邮箱，只保存 UUID 与 tick。 */
public final class PaperShieldMemory {
	private final Map<UUID, BlockSignal> pending = new HashMap<>();

	public void record(final Zombie zombie, final LivingEntity attacker, final long now) {
		if (attacker != zombie) {
			this.pending.put(zombie.getUniqueId(), new BlockSignal(attacker.getUniqueId(), now));
		}
	}

	public BlockSignal consume(
		final Zombie zombie,
		final LivingEntity expectedAttacker,
		final long now,
		final int maximumAgeTicks
	) {
		BlockSignal signal = this.pending.remove(zombie.getUniqueId());
		return signal != null
			&& signal.attackerId().equals(expectedAttacker.getUniqueId())
			&& ShieldCombatPlanner.isFreshSignal(now, signal.observedAt(), maximumAgeTicks)
			? signal
			: null;
	}

	public void discard(final Zombie zombie) {
		this.pending.remove(zombie.getUniqueId());
	}

	public void clear() {
		this.pending.clear();
	}

	public int pendingCount() {
		return this.pending.size();
	}

	public record BlockSignal(UUID attackerId, long observedAt) {
	}
}

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
	private final Map<UUID, Long> disabledUntil = new HashMap<>();

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
		this.disabledUntil.remove(zombie.getUniqueId());
	}

	public void discardSignal(final Zombie zombie) {
		this.pending.remove(zombie.getUniqueId());
	}

	public void disable(final Zombie zombie, final long now, final int durationTicks) {
		this.pending.remove(zombie.getUniqueId());
		this.disabledUntil.put(
			zombie.getUniqueId(),
			ShieldCombatPlanner.disabledUntil(now, durationTicks)
		);
	}

	public boolean isDisabled(final Zombie zombie, final long now) {
		Long until = this.disabledUntil.get(zombie.getUniqueId());
		if (until == null) {
			return false;
		}
		if (!ShieldCombatPlanner.isDisabled(now, until)) {
			this.disabledUntil.remove(zombie.getUniqueId());
			return false;
		}
		return true;
	}

	public void clear() {
		this.pending.clear();
		this.disabledUntil.clear();
	}

	public int pendingCount() {
		return this.pending.size();
	}

	public int disabledCount(final long now) {
		this.disabledUntil.values().removeIf(until -> !ShieldCombatPlanner.isDisabled(now, until));
		return this.disabledUntil.size();
	}

	public record BlockSignal(UUID attackerId, long observedAt) {
	}
}

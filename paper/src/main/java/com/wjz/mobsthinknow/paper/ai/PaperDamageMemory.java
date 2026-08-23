package com.wjz.mobsthinknow.paper.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;

/**
 * Paper 伤害事件到自定义 Goal 的主线程短期邮箱。
 *
 * <p>只保存 UUID 和数值，不延长实体生命周期。同一 AI 评估前连续受击时同时保留最近攻击者和最大单次
 * 实伤来源；Goal 消费一次后即删除，旧事件不会在重载功能后突然触发。</p>
 */
public final class PaperDamageMemory {
	private final Map<UUID, DamageSnapshot> pending = new HashMap<>();

	public void record(
		final Zombie zombie,
		final LivingEntity attacker,
		final double finalDamage,
		final long now
	) {
		if (attacker == zombie || finalDamage <= 0.0 || !Double.isFinite(finalDamage)) {
			return;
		}
		this.pending.compute(
			zombie.getUniqueId(),
			(ignored, existing) -> existing == null
				? DamageSnapshot.first(attacker.getUniqueId(), finalDamage, now)
				: existing.withHit(attacker.getUniqueId(), finalDamage, now)
		);
	}

	public DamageSnapshot consume(final Zombie zombie, final long now, final int maximumAgeTicks) {
		DamageSnapshot snapshot = this.pending.remove(zombie.getUniqueId());
		int maximumAge = Math.max(1, maximumAgeTicks);
		if (snapshot == null || !isFresh(now, snapshot.latestObservedAt(), maximumAge)) {
			return null;
		}
		return isFresh(now, snapshot.largestDamageObservedAt(), maximumAge)
			? snapshot
			: snapshot.withLargestFromLatest();
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

	public record DamageSnapshot(
		UUID latestAttackerId,
		double latestDamage,
		long latestObservedAt,
		UUID largestDamageAttackerId,
		double largestDamage,
		long largestDamageObservedAt
	) {
		private static DamageSnapshot first(final UUID attackerId, final double damage, final long now) {
			return new DamageSnapshot(attackerId, damage, now, attackerId, damage, now);
		}

		private DamageSnapshot withHit(final UUID attackerId, final double damage, final long now) {
			return damage >= this.largestDamage
				? new DamageSnapshot(attackerId, damage, now, attackerId, damage, now)
				: new DamageSnapshot(
					attackerId,
					damage,
					now,
					this.largestDamageAttackerId,
					this.largestDamage,
					this.largestDamageObservedAt
				);
		}

		private DamageSnapshot withLargestFromLatest() {
			return new DamageSnapshot(
				this.latestAttackerId,
				this.latestDamage,
				this.latestObservedAt,
				this.latestAttackerId,
				this.latestDamage,
				this.latestObservedAt
			);
		}
	}

	private static boolean isFresh(final long now, final long observedAt, final int maximumAgeTicks) {
		long age = now - observedAt;
		return age >= 0L && age <= maximumAgeTicks;
	}
}

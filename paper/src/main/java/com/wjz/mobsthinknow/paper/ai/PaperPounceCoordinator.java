package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Spider;

/** 每个目标至多一个跳扑租约，查询与更新均为 O(1)。 */
public final class PaperPounceCoordinator {
	private static final int MAXIMUM_EXPIRY_CLEANUP_PER_OPERATION = 128;

	private final Supplier<PaperSettings> settings;
	private final PaperMetrics metrics;
	private final Map<UUID, Reservation> byTarget = new HashMap<>();
	private final Map<UUID, UUID> targetBySpider = new HashMap<>();
	private final Map<UUID, Long> targetCooldownUntil = new HashMap<>();
	private final PriorityQueue<ReservationExpiry> reservationExpiries = new PriorityQueue<>();
	private final PriorityQueue<CooldownExpiry> cooldownExpiries = new PriorityQueue<>();

	public PaperPounceCoordinator(final Supplier<PaperSettings> settings, final PaperMetrics metrics) {
		this.settings = settings;
		this.metrics = metrics;
	}

	public boolean tryAcquire(final Spider spider, final LivingEntity target) {
		long now = Bukkit.getCurrentTick();
		this.cleanupExpired(now);
		PaperSettings config = this.settings.get();
		if (!config.enabled()
			|| !config.spiderTacticsEnabled()
			|| !config.spiderPredictivePounceEnabled()
			|| !spider.isValid()
			|| spider.isDead()
			|| !target.isValid()
			|| target.isDead()
			|| spider.getWorld() != target.getWorld()) {
			this.release(spider, false);
			return false;
		}
		UUID targetId = target.getUniqueId();
		Long cooldown = this.targetCooldownUntil.get(targetId);
		if (cooldown != null) {
			if (cooldown > now) {
				this.metrics.spiderPounceReservationConflict();
				return false;
			}
			this.targetCooldownUntil.remove(targetId);
		}

		UUID spiderId = spider.getUniqueId();
		UUID previousTargetId = this.targetBySpider.get(spiderId);
		if (previousTargetId != null && !previousTargetId.equals(targetId)) {
			Reservation previous = this.byTarget.get(previousTargetId);
			if (previous != null && previous.spiderId().equals(spiderId)) {
				this.removeReservation(previous);
				this.metrics.spiderPounceReservationReleased();
			}
		}
		Reservation current = this.byTarget.get(targetId);
		if (current != null && !current.spiderId().equals(spiderId)) {
			this.metrics.spiderPounceReservationConflict();
			return false;
		}

		long expiresAt = saturatingAdd(now, config.spiderPounceLeaseTicks());
		Reservation replacement = new Reservation(spiderId, targetId, expiresAt);
		this.byTarget.put(targetId, replacement);
		this.targetBySpider.put(spiderId, targetId);
		this.reservationExpiries.add(new ReservationExpiry(spiderId, targetId, expiresAt));
		this.compactExpiryQueuesIfNeeded();
		if (current == null) {
			this.metrics.spiderPounceReservationAcquired();
		}
		return true;
	}

	public void release(final Spider spider, final boolean completedPounce) {
		this.release(spider.getUniqueId(), completedPounce);
	}

	public void release(final UUID spiderId, final boolean completedPounce) {
		UUID targetId = this.targetBySpider.remove(spiderId);
		if (targetId == null) {
			return;
		}
		Reservation current = this.byTarget.get(targetId);
		if (current != null && current.spiderId().equals(spiderId)) {
			this.byTarget.remove(targetId);
			if (completedPounce) {
				long cooldownUntil = saturatingAdd(
					Bukkit.getCurrentTick(),
					this.settings.get().spiderPounceStaggerTicks()
				);
				this.targetCooldownUntil.put(targetId, cooldownUntil);
				this.cooldownExpiries.add(new CooldownExpiry(targetId, cooldownUntil));
			}
			this.metrics.spiderPounceReservationReleased();
		}
		this.compactExpiryQueuesIfNeeded();
	}

	public int activeCount() {
		this.cleanupExpired(Bukkit.getCurrentTick());
		return this.byTarget.size();
	}

	public void clear() {
		this.byTarget.clear();
		this.targetBySpider.clear();
		this.targetCooldownUntil.clear();
		this.reservationExpiries.clear();
		this.cooldownExpiries.clear();
	}

	private void removeReservation(final Reservation reservation) {
		this.byTarget.remove(reservation.targetId(), reservation);
		this.targetBySpider.remove(reservation.spiderId(), reservation.targetId());
	}

	private void cleanupExpired(final long now) {
		int cleaned = 0;
		while (cleaned < MAXIMUM_EXPIRY_CLEANUP_PER_OPERATION
			&& !this.reservationExpiries.isEmpty()
			&& this.reservationExpiries.peek().expiresAt() <= now) {
			ReservationExpiry expiry = this.reservationExpiries.poll();
			Reservation current = this.byTarget.get(expiry.targetId());
			if (current != null
				&& current.spiderId().equals(expiry.spiderId())
				&& current.expiresAt() == expiry.expiresAt()) {
				this.removeReservation(current);
			}
			cleaned++;
		}
		cleaned = 0;
		while (cleaned < MAXIMUM_EXPIRY_CLEANUP_PER_OPERATION
			&& !this.cooldownExpiries.isEmpty()
			&& this.cooldownExpiries.peek().expiresAt() <= now) {
			CooldownExpiry expiry = this.cooldownExpiries.poll();
			this.targetCooldownUntil.remove(expiry.targetId(), expiry.expiresAt());
			cleaned++;
		}
	}

	private void compactExpiryQueuesIfNeeded() {
		int maximumReservations = Math.max(128, this.byTarget.size() * 4);
		if (this.reservationExpiries.size() > maximumReservations) {
			this.reservationExpiries.clear();
			for (Reservation reservation : this.byTarget.values()) {
				this.reservationExpiries.add(new ReservationExpiry(
					reservation.spiderId(),
					reservation.targetId(),
					reservation.expiresAt()
				));
			}
		}
		int maximumCooldowns = Math.max(128, this.targetCooldownUntil.size() * 4);
		if (this.cooldownExpiries.size() > maximumCooldowns) {
			this.cooldownExpiries.clear();
			for (Map.Entry<UUID, Long> entry : this.targetCooldownUntil.entrySet()) {
				this.cooldownExpiries.add(new CooldownExpiry(entry.getKey(), entry.getValue()));
			}
		}
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private record Reservation(UUID spiderId, UUID targetId, long expiresAt) {
	}

	private record ReservationExpiry(UUID spiderId, UUID targetId, long expiresAt)
		implements Comparable<ReservationExpiry> {
		@Override
		public int compareTo(final ReservationExpiry other) {
			return Long.compare(this.expiresAt, other.expiresAt);
		}
	}

	private record CooldownExpiry(UUID targetId, long expiresAt) implements Comparable<CooldownExpiry> {
		@Override
		public int compareTo(final CooldownExpiry other) {
			return Long.compare(this.expiresAt, other.expiresAt);
		}
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 僵尸觅食与武器换装共用的短期地面物品预留表。
 *
 * <p>所有调用均发生在服务端实体主线程，不需要锁。预留只保存实体 UUID 和到期 tick，
 * 不持有实体或世界强引用；即使 Goal 因卸载等异常路径没有执行 stop，三秒后也会自然失效。
 * 这样二十只僵尸不会同时为同一件掉落物创建路径，附近有多件补给时会自动向后续候选分流。</p>
 */
public final class ZombieGroundItemReservations {
	private static final int RESERVATION_TTL_TICKS = 60;
	private static final int SWEEP_INTERVAL_TICKS = 100;
	private static final int FORCE_SWEEP_SIZE = 256;
	private static final Map<UUID, Reservation> RESERVATIONS = new HashMap<>();
	private static long nextSweepAt = Long.MIN_VALUE;

	private ZombieGroundItemReservations() {
	}

	static boolean isAvailableTo(final ItemEntity item, final Zombie zombie, final long now) {
		if (item.level() != zombie.level() || item.isRemoved() || item.getItem().isEmpty()) {
			return false;
		}
		Reservation current = activeReservation(item.getUUID(), now);
		return current == null || current.zombieId().equals(zombie.getUUID());
	}

	static boolean tryReserve(final ItemEntity item, final Zombie zombie, final long now) {
		if (!isAvailableTo(item, zombie, now)) {
			return false;
		}
		sweepExpired(now);
		RESERVATIONS.put(
			item.getUUID(),
			new Reservation(zombie.getUUID(), now + RESERVATION_TTL_TICKS, zombie.level().dimension())
		);
		return true;
	}

	static boolean renew(final @Nullable ItemEntity item, final Zombie zombie, final long now) {
		return item != null && tryReserve(item, zombie, now);
	}

	static void release(final @Nullable ItemEntity item, final Zombie zombie) {
		if (item == null) {
			return;
		}
		Reservation current = RESERVATIONS.get(item.getUUID());
		if (current != null && current.zombieId().equals(zombie.getUUID())) {
			RESERVATIONS.remove(item.getUUID());
		}
	}

	/** 死亡发生在 GoalSelector 停止之前时也立刻交还补给，避免同伴等待租约自然过期。 */
	public static void releaseAll(final Zombie zombie) {
		UUID zombieId = zombie.getUUID();
		RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().zombieId().equals(zombieId));
	}

	public static void clearLevel(final ServerLevel level) {
		ResourceKey<Level> dimension = level.dimension();
		RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().dimension().equals(dimension));
	}

	public static void clear() {
		RESERVATIONS.clear();
		nextSweepAt = Long.MIN_VALUE;
	}

	private static @Nullable Reservation activeReservation(final UUID itemId, final long now) {
		Reservation current = RESERVATIONS.get(itemId);
		if (current != null && current.expiresAt() <= now) {
			RESERVATIONS.remove(itemId);
			return null;
		}
		return current;
	}

	private static void sweepExpired(final long now) {
		if (RESERVATIONS.size() < FORCE_SWEEP_SIZE && now < nextSweepAt) {
			return;
		}
		RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
		nextSweepAt = now + SWEEP_INTERVAL_TICKS;
	}

	private record Reservation(UUID zombieId, long expiresAt, ResourceKey<Level> dimension) {
	}
}

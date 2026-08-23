package com.wjz.mobsthinknow.ai.zombie;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 水桶辅助兵接收的短期求援信号。
 *
 * <p>信号只在真实攻击事件发生时写入，不做每 tick 的同伴扫描；协调器广播的上限就是单支小队人数，
 * 因而密集尸群也不会把辅助感知退化成 N²。待处理值只保存攻击者 UUID，避免区块卸载后继续强引用
 * 实体；消费时必须从 helper 当前的 ServerLevel 重新解析并复核目标。</p>
 */
public final class ZombieFluidThreatMemory {
	private static final long ALERT_LIFETIME_TICKS = 100L;
	private static final Map<Zombie, PendingAlert> ALERTS = new IdentityHashMap<>();
	private static final Map<UUID, Set<Zombie>> HELPERS_BY_ATTACKER = new HashMap<>();

	private ZombieFluidThreatMemory() {
	}

	public static void record(final Zombie helper, final LivingEntity attacker, final Vec3 defendedPosition) {
		if (!helper.isAlive()
			|| helper.isRemoved()
			|| defendedPosition == null
			|| !isUsableThreatFor(helper, attacker)) {
			return;
		}
		PendingAlert replacement = new PendingAlert(
			attacker.getUUID(),
			defendedPosition,
			saturatingAdd(helper.level().getGameTime(), ALERT_LIFETIME_TICKS)
		);
		PendingAlert previous = ALERTS.put(helper, replacement);
		if (previous != null) {
			removeReverse(helper, previous.attackerId());
		}
		HELPERS_BY_ATTACKER.computeIfAbsent(replacement.attackerId(), ignored -> newIdentitySet()).add(helper);
	}

	/** 领取一条新求援；Goal 会在接近和投放期间自行持有快照。 */
	public static @Nullable Alert consume(final Zombie helper) {
		PendingAlert alert = removeHelperAlert(helper);
		if (alert == null
			|| helper.level().getGameTime() > alert.expiresAt()
			|| !(helper.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(alert.attackerId());
		if (!(entity instanceof LivingEntity attacker) || !isUsableThreatFor(helper, attacker)) {
			return null;
		}
		return new Alert(attacker, alert.defendedPosition(), alert.expiresAt());
	}

	public static void discard(final Zombie zombie) {
		discardHelper(zombie);
		discardAttacker(zombie);
	}

	public static void discardHelper(final Zombie helper) {
		removeHelperAlert(helper);
	}

	public static void discardAttacker(final LivingEntity attacker) {
		UUID attackerId = attacker.getUUID();
		Set<Zombie> helpers = HELPERS_BY_ATTACKER.remove(attackerId);
		if (helpers == null) {
			return;
		}
		for (Zombie helper : helpers) {
			PendingAlert current = ALERTS.get(helper);
			if (current != null && current.attackerId().equals(attackerId)) {
				ALERTS.remove(helper);
			}
		}
	}

	public static void clear() {
		ALERTS.clear();
		HELPERS_BY_ATTACKER.clear();
	}

	public static void clearLevel(final ServerLevel level) {
		for (Zombie helper : ALERTS.keySet().stream().filter(zombie -> zombie.level() == level).toList()) {
			discardHelper(helper);
		}
	}

	private static boolean isUsableThreatFor(final Zombie helper, final LivingEntity attacker) {
		return attacker != null
			&& attacker != helper
			&& attacker.level() == helper.level()
			&& attacker.isAlive()
			&& !attacker.isRemoved()
			&& (!(attacker instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	public record Alert(LivingEntity attacker, Vec3 defendedPosition, long expiresAt) {
	}

	private static PendingAlert removeHelperAlert(final Zombie helper) {
		PendingAlert removed = ALERTS.remove(helper);
		if (removed != null) {
			removeReverse(helper, removed.attackerId());
		}
		return removed;
	}

	private static void removeReverse(final Zombie helper, final UUID attackerId) {
		Set<Zombie> helpers = HELPERS_BY_ATTACKER.get(attackerId);
		if (helpers != null && helpers.remove(helper) && helpers.isEmpty()) {
			HELPERS_BY_ATTACKER.remove(attackerId);
		}
	}

	private static Set<Zombie> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private record PendingAlert(UUID attackerId, Vec3 defendedPosition, long expiresAt) {
	}
}

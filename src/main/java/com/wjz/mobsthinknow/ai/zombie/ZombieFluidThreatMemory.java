package com.wjz.mobsthinknow.ai.zombie;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 水桶辅助兵接收的短期求援信号。
 *
 * <p>信号只在真实攻击事件发生时写入，不做每 tick 的同伴扫描；协调器广播的上限就是单支小队人数，
 * 因而密集尸群也不会把辅助感知退化成 N²。</p>
 */
public final class ZombieFluidThreatMemory {
	private static final long ALERT_LIFETIME_TICKS = 100L;
	private static final Map<Zombie, Alert> ALERTS = new IdentityHashMap<>();

	private ZombieFluidThreatMemory() {
	}

	public static void record(final Zombie helper, final LivingEntity attacker, final Vec3 defendedPosition) {
		if (!helper.isAlive() || !isUsableThreat(attacker)) {
			return;
		}
		ALERTS.put(helper, new Alert(
			attacker,
			defendedPosition,
			helper.level().getGameTime() + ALERT_LIFETIME_TICKS
		));
	}

	/** 领取一条新求援；Goal 会在接近和投放期间自行持有快照。 */
	public static @Nullable Alert consume(final Zombie helper) {
		Alert alert = ALERTS.remove(helper);
		if (alert == null
			|| helper.level().getGameTime() > alert.expiresAt()
			|| !isUsableThreat(alert.attacker())) {
			return null;
		}
		return alert;
	}

	public static void discard(final Zombie zombie) {
		ALERTS.remove(zombie);
	}

	public static void clear() {
		ALERTS.clear();
	}

	public static void clearLevel(final ServerLevel level) {
		ALERTS.keySet().removeIf(zombie -> zombie.level() == level);
	}

	private static boolean isUsableThreat(final LivingEntity attacker) {
		return attacker.isAlive()
			&& (!(attacker instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	public record Alert(LivingEntity attacker, Vec3 defendedPosition, long expiresAt) {
	}
}

package com.wjz.mobsthinknow.paper.ai;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Paper Goal 共用的目标存活、同世界与游戏模式过滤。 */
public final class PaperThreats {
	private PaperThreats() {
	}

	public static boolean isLiveFor(final Mob actor, final LivingEntity target) {
		if (target == null
			|| target == actor
			|| !target.isValid()
			|| target.isDead()
			|| target.getWorld() != actor.getWorld()) {
			return false;
		}
		return !(target instanceof Player player)
			|| (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR);
	}
}

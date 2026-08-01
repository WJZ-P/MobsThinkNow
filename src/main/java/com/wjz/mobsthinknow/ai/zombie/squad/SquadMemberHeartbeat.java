package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** 把未通过智能近战包装提交感知的成员转换为统一协调器心跳。 */
public final class SquadMemberHeartbeat {
	private SquadMemberHeartbeat() {
	}

	public static void tick(final ServerLevel level, final Mob mob, final boolean speciesAiEnabled) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled || !config.packSurrounding || !speciesAiEnabled) {
			return;
		}
		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		boolean visible = mob.getSensing().hasLineOfSight(target);
		long now = level.getGameTime();
		ZombieSquadCoordinator.forLevel(level).heartbeat(
			mob,
			target,
			visible,
			visible ? target.position() : null,
			visible ? now : Long.MIN_VALUE
		);
	}
}

package com.wjz.mobsthinknow;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.command.MtnCommands;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobsThinkNow implements ModInitializer {
	public static final String MOD_ID = "mobsthinknow";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();
		MtnCommands.register();
		// 协调器统一在每个维度 tick 的末尾做一次决策，保证本 tick 的所有僵尸心跳已经收齐。
		ServerTickEvents.END_LEVEL_TICK.register(ZombieSquadCoordinator::tickLevel);
		ServerLevelEvents.UNLOAD.register((server, level) -> ZombieSquadCoordinator.unloadLevel(level));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ZombieSquadCoordinator.clearAll());
		// 在 die() 记录“Named entity died”日志之前恢复职业名牌；只做表现清理，不改变死亡结果。
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
			if (entity instanceof Zombie zombie) {
				ZombieSquadCoordinator.onZombieDying(zombie);
			}
			return true;
		});
		LOGGER.info("Mobs Think Now initialized for Minecraft 26.1.2.");
	}
}

package com.wjz.mobsthinknow;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.command.MtnCommands;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
		LOGGER.info("Mobs Think Now initialized for Minecraft 26.1.2.");
	}
}

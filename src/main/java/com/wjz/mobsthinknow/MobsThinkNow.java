package com.wjz.mobsthinknow;

import com.wjz.mobsthinknow.command.MtnCommands;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MobsThinkNow implements ModInitializer {
	public static final String MOD_ID = "mobsthinknow";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.load();
		MtnCommands.register();
		LOGGER.info("Mobs Think Now initialized for Minecraft 26.1.2.");
	}
}

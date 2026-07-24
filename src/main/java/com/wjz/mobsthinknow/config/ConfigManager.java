package com.wjz.mobsthinknow.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.wjz.mobsthinknow.MobsThinkNow;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MobsThinkNow.MOD_ID + ".json");
	private static volatile MobsThinkNowConfig current = new MobsThinkNowConfig();

	private ConfigManager() {
	}

	public static MobsThinkNowConfig get() {
		return current;
	}

	public static boolean load() {
		MobsThinkNowConfig loaded = new MobsThinkNowConfig();

		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (Files.exists(CONFIG_PATH)) {
				try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
					MobsThinkNowConfig parsed = GSON.fromJson(reader, MobsThinkNowConfig.class);
					if (parsed != null) {
						loaded = parsed;
					}
				}
			}

			loaded.validate();
			current = loaded;
			save(loaded);
			return true;
		} catch (IOException | RuntimeException exception) {
			MobsThinkNow.LOGGER.error("Unable to load {}. Keeping the previous valid configuration.", CONFIG_PATH, exception);
			return false;
		}
	}

	private static void save(final MobsThinkNowConfig config) throws IOException {
		try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
	}
}

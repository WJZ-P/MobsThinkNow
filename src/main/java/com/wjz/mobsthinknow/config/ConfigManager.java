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
import java.util.Objects;
import java.util.function.Consumer;
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

	public static synchronized boolean load() {
		MobsThinkNowConfig loaded = new MobsThinkNowConfig();

		try {
			if (Files.exists(CONFIG_PATH)) {
				try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
					MobsThinkNowConfig parsed = GSON.fromJson(reader, MobsThinkNowConfig.class);
					if (parsed != null) {
						loaded = parsed;
					}
				}
			}

			loaded.validate();
			save(loaded);
			current = loaded;
			return true;
		} catch (IOException | RuntimeException exception) {
			MobsThinkNow.LOGGER.error("Unable to load {}. Keeping the previous valid configuration.", CONFIG_PATH, exception);
			return false;
		}
	}

	/**
	 * 在当前有效配置的副本上执行修改，校验并保存成功后再一次性发布新快照。
	 * 这样服务端 tick 不会观察到修改到一半的配置，也不会在写盘失败时使用未保存的值。
	 *
	 * @param updater 只修改传入配置副本的操作
	 * @return 配置是否成功保存并生效
	 */
	public static synchronized boolean update(final Consumer<MobsThinkNowConfig> updater) {
		Objects.requireNonNull(updater, "updater");

		try {
			MobsThinkNowConfig updated = GSON.fromJson(GSON.toJson(current), MobsThinkNowConfig.class);
			updater.accept(updated);
			updated.validate();
			save(updated);
			current = updated;
			return true;
		} catch (IOException | RuntimeException exception) {
			MobsThinkNow.LOGGER.error("Unable to save {}. Keeping the previous valid configuration.", CONFIG_PATH, exception);
			return false;
		}
	}

	private static void save(final MobsThinkNowConfig config) throws IOException {
		Files.createDirectories(CONFIG_PATH.getParent());
		try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
			GSON.toJson(config, writer);
		}
	}
}

package com.wjz.mobsthinknow.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.wjz.mobsthinknow.MobsThinkNow;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;

public final class ConfigManager {
	private static final int MAXIMUM_CONFIG_CHARACTERS = 1_000_000;
	private static final int MAXIMUM_CONFIG_NESTING = 64;
	private static final int FILE_REPLACEMENT_ATTEMPTS = 6;
	private static final long FILE_REPLACEMENT_RETRY_MILLIS = 25L;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(MobsThinkNow.MOD_ID + ".json");
	private static volatile MobsThinkNowConfig current = new MobsThinkNowConfig();

	private ConfigManager() {
	}

	public static MobsThinkNowConfig get() {
		return current;
	}

	/** Client editors work on a detached draft so cancelling a screen cannot mutate the live server snapshot. */
	public static synchronized MobsThinkNowConfig editableCopy() {
		return copyOf(current);
	}

	public static synchronized boolean load() {
		MobsThinkNowConfig loaded = new MobsThinkNowConfig();

		try {
			if (Files.exists(CONFIG_PATH)) {
				try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
					MobsThinkNowConfig parsed = parseStrict(reader);
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
			MobsThinkNowConfig updated = copyOf(current);
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

	/** Validate and atomically publish one complete editor draft with exactly one filesystem replacement. */
	public static synchronized boolean replace(final MobsThinkNowConfig replacement) {
		Objects.requireNonNull(replacement, "replacement");
		try {
			MobsThinkNowConfig updated = copyOf(replacement);
			updated.validate();
			save(updated);
			current = updated;
			return true;
		} catch (IOException | RuntimeException exception) {
			MobsThinkNow.LOGGER.error("Unable to save {}. Keeping the previous valid configuration.", CONFIG_PATH, exception);
			return false;
		}
	}

	static MobsThinkNowConfig copyOf(final MobsThinkNowConfig source) {
		return Objects.requireNonNull(
			GSON.fromJson(GSON.toJson(Objects.requireNonNull(source, "source")), MobsThinkNowConfig.class),
			"serialized configuration"
		);
	}

	/** Parse one bounded strict JSON document and reject duplicate object keys before Gson performs binding. */
	static MobsThinkNowConfig parseStrict(final Reader source) throws IOException {
		Objects.requireNonNull(source, "source");
		StringBuilder json = new StringBuilder();
		char[] buffer = new char[4096];
		for (int read = source.read(buffer); read >= 0; read = source.read(buffer)) {
			if (read == 0) {
				continue;
			}
			if (json.length() > MAXIMUM_CONFIG_CHARACTERS - read) {
				throw new IOException("Configuration exceeds " + MAXIMUM_CONFIG_CHARACTERS + " characters.");
			}
			json.append(buffer, 0, read);
		}

		String document = json.toString();
		try (JsonReader strictReader = new JsonReader(new StringReader(document))) {
			strictReader.setStrictness(Strictness.STRICT);
			strictReader.setNestingLimit(MAXIMUM_CONFIG_NESTING);
			verifyJsonValue(strictReader);
			if (strictReader.peek() != JsonToken.END_DOCUMENT) {
				throw new JsonParseException("Configuration contains trailing JSON content at " + strictReader.getPath());
			}
		}
		return GSON.fromJson(document, MobsThinkNowConfig.class);
	}

	private static void verifyJsonValue(final JsonReader reader) throws IOException {
		switch (reader.peek()) {
			case BEGIN_OBJECT -> {
				reader.beginObject();
				Set<String> keys = new HashSet<>();
				while (reader.hasNext()) {
					String name = reader.nextName();
					if (!keys.add(name)) {
						throw new JsonParseException("Duplicate configuration key '" + name + "' at " + reader.getPath());
					}
					verifyJsonValue(reader);
				}
				reader.endObject();
			}
			case BEGIN_ARRAY -> {
				reader.beginArray();
				while (reader.hasNext()) {
					verifyJsonValue(reader);
				}
				reader.endArray();
			}
			case STRING, NUMBER -> reader.nextString();
			case BOOLEAN -> reader.nextBoolean();
			case NULL -> reader.nextNull();
			default -> throw new JsonParseException("Expected a JSON value at " + reader.getPath());
		}
	}

	private static void save(final MobsThinkNowConfig config) throws IOException {
		saveAtomically(CONFIG_PATH, config);
	}

	/** Write beside the destination first so a crash or serialization failure cannot truncate the last valid file. */
	static void saveAtomically(final Path destination, final MobsThinkNowConfig config) throws IOException {
		Path parent = Objects.requireNonNull(destination.getParent(), "configuration path must have a parent");
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, destination.getFileName().toString() + ".", ".tmp");
		try {
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
			replaceFileWithRetry(temporary, destination);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	/** Windows virus scanners and indexers can briefly deny a replace; keep the old snapshot and retry in place. */
	private static void replaceFileWithRetry(final Path source, final Path destination) throws IOException {
		IOException lastFailure = null;
		for (int attempt = 1; attempt <= FILE_REPLACEMENT_ATTEMPTS; attempt++) {
			try {
				try {
					Files.move(
						source,
						destination,
						StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING
					);
				} catch (AtomicMoveNotSupportedException ignored) {
					Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
				}
				return;
			} catch (IOException exception) {
				lastFailure = exception;
				if (attempt == FILE_REPLACEMENT_ATTEMPTS) {
					break;
				}
				try {
					Thread.sleep(FILE_REPLACEMENT_RETRY_MILLIS);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IOException("Interrupted while retrying configuration replacement.", interrupted);
				}
			}
		}
		throw Objects.requireNonNull(lastFailure, "replacement failure");
	}
}

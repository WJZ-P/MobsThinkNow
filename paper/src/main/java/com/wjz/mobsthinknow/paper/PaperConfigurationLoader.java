package com.wjz.mobsthinknow.paper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Strict UTF-8 YAML loading that reports syntax failures instead of silently substituting an empty config. */
final class PaperConfigurationLoader {
	private static final long MAXIMUM_CONFIG_BYTES = 1_000_000L;

	private PaperConfigurationLoader() {
	}

	static YamlConfiguration load(final Path path) throws IOException, InvalidConfigurationException {
		Objects.requireNonNull(path, "path");
		long size = Files.size(path);
		if (size > MAXIMUM_CONFIG_BYTES) {
			throw new InvalidConfigurationException(
				"config.yml exceeds the " + MAXIMUM_CONFIG_BYTES + " byte limit: " + size
			);
		}
		String source = Files.readString(path, StandardCharsets.UTF_8);
		LoaderOptions options = new LoaderOptions();
		options.setAllowDuplicateKeys(false);
		options.setAllowRecursiveKeys(false);
		options.setMaxAliasesForCollections(50);
		options.setNestingDepthLimit(64);
		options.setCodePointLimit(1_000_000);
		try {
			new Yaml(new SafeConstructor(options)).load(source);
		} catch (YAMLException exception) {
			throw new InvalidConfigurationException("Strict YAML validation failed", exception);
		}
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.loadFromString(source);
		return configuration;
	}
}

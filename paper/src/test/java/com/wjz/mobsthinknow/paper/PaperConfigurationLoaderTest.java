package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PaperConfigurationLoaderTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void readsUtf8AndNestedValues() throws Exception {
		Path file = this.temporaryDirectory.resolve("config.yml");
		Files.writeString(
			file,
			"enabled: false\nidentity:\n  label: '聪明怪物'\n",
			StandardCharsets.UTF_8
		);
		var loaded = PaperConfigurationLoader.load(file);
		assertFalse(loaded.getBoolean("enabled", true));
		assertEquals("聪明怪物", loaded.getString("identity.label"));
	}

	@Test
	void malformedYamlIsReportedToTheTransactionalCaller() throws Exception {
		Path file = this.temporaryDirectory.resolve("config.yml");
		Files.writeString(file, "enabled: true\nspider: [unterminated\n", StandardCharsets.UTF_8);
		assertThrows(InvalidConfigurationException.class, () -> PaperConfigurationLoader.load(file));
	}

	@Test
	void duplicateKeysAreRejectedRatherThanSilentlyTakingTheLastValue() throws Exception {
		Path file = this.temporaryDirectory.resolve("duplicates.yml");
		Files.writeString(file, "enabled: true\nenabled: false\n", StandardCharsets.UTF_8);
		assertThrows(InvalidConfigurationException.class, () -> PaperConfigurationLoader.load(file));
	}

	@Test
	void oversizedYamlIsRejectedBeforeItIsReadIntoMemory() throws Exception {
		Path file = this.temporaryDirectory.resolve("oversized.yml");
		Files.write(file, new byte[1_000_001]);

		assertThrows(InvalidConfigurationException.class, () -> PaperConfigurationLoader.load(file));
	}
}

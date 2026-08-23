package com.wjz.mobsthinknow.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ConfigManagerTest {
	@TempDir
	Path temporaryDirectory;

	@Test
	void copiedEditorDraftIsDetachedFromThePublishedSnapshot() {
		MobsThinkNowConfig published = new MobsThinkNowConfig();
		published.enabled = false;
		published.skeletonCrossbowChance = 0.25;

		MobsThinkNowConfig draft = ConfigManager.copyOf(published);

		assertNotSame(published, draft);
		assertFalse(draft.enabled);
		assertEquals(0.25, draft.skeletonCrossbowChance);
		draft.enabled = true;
		draft.skeletonCrossbowChance = 0.75;
		assertFalse(published.enabled);
		assertEquals(0.25, published.skeletonCrossbowChance);
	}

	@Test
	void strictParserAcceptsOneCompleteConfigurationObject() throws Exception {
		MobsThinkNowConfig parsed = ConfigManager.parseStrict(new StringReader("{\"enabled\":false}"));

		assertFalse(parsed.enabled);
	}

	@Test
	void strictParserRejectsDuplicateKeysAndLenientJsonSyntax() {
		assertThrows(
			JsonParseException.class,
			() -> ConfigManager.parseStrict(new StringReader("{\"enabled\":true,\"enabled\":false}"))
		);
		assertThrows(
			JsonParseException.class,
			() -> ConfigManager.parseStrict(new StringReader("{\"future\":{\"value\":1,\"value\":2}}"))
		);
		assertThrows(
			IOException.class,
			() -> ConfigManager.parseStrict(new StringReader("{/* comment */\"enabled\":true}"))
		);
		assertThrows(
			IOException.class,
			() -> ConfigManager.parseStrict(new StringReader("{\"enabled\":true} trailing"))
		);
		String tooDeep = "[".repeat(65) + "0" + "]".repeat(65);
		assertThrows(IOException.class, () -> ConfigManager.parseStrict(new StringReader(tooDeep)));
	}

	@Test
	void strictParserRejectsOversizedInputBeforeBinding() {
		assertThrows(
			IOException.class,
			() -> ConfigManager.parseStrict(new StringReader(" ".repeat(1_000_001)))
		);
	}

	@Test
	void atomicSaveCreatesParentsAndLeavesOnlyACompleteDestination() throws Exception {
		Path destination = this.temporaryDirectory.resolve("nested/mobsthinknow.json");
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.enabled = false;

		ConfigManager.saveAtomically(destination, config);

		assertTrue(Files.isRegularFile(destination));
		assertTrue(Files.readString(destination, StandardCharsets.UTF_8).contains("\"enabled\": false"));
		try (var children = Files.list(destination.getParent())) {
			assertEquals(List.of(destination), children.toList());
		}
	}

	@Test
	void atomicSaveReplacesThePreviousSnapshotWithoutTemporaryDebris() throws Exception {
		Path destination = this.temporaryDirectory.resolve("mobsthinknow.json");
		Files.writeString(destination, "truncated-old-value", StandardCharsets.UTF_8);
		MobsThinkNowConfig config = new MobsThinkNowConfig();

		for (int replacement = 0; replacement < 32; replacement++) {
			config.enabled = replacement % 2 == 0;
			ConfigManager.saveAtomically(destination, config);
		}

		String saved = Files.readString(destination, StandardCharsets.UTF_8);
		assertFalse(saved.contains("truncated-old-value"));
		assertTrue(JsonParser.parseString(saved).isJsonObject());
		try (var children = Files.list(this.temporaryDirectory)) {
			assertEquals(List.of(destination), children.toList());
		}
	}
}

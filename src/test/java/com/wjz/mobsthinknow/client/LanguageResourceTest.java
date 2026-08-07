package com.wjz.mobsthinknow.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** 资源级回归测试：防止 Windows 工具链把新增中文静默替换成问号。 */
class LanguageResourceTest {
	@Test
	void chineseAndEnglishLanguageFilesHaveMatchingKeysAndIntactUnicode() throws IOException {
		JsonObject chinese = readLanguage("zh_cn.json");
		JsonObject english = readLanguage("en_us.json");

		assertEquals(english.keySet(), chinese.keySet(), "Chinese and English translation keys drifted apart.");
		assertTrue(
			chinese.entrySet().stream().anyMatch(entry -> entry.getValue().getAsString().matches(".*[\\u4E00-\\u9FFF].*")),
			"Chinese resource contained no CJK text; it may have been transcoded incorrectly."
		);
		chinese.entrySet().forEach(entry -> assertFalse(
			entry.getValue().getAsString().contains("??"),
			() -> "Translation was replaced by question marks: " + entry.getKey()
		));
		assertEquals("预判式临时蛛网", chinese.get("mobsthinknow.config.spider_web_traps").getAsString());
		assertEquals("战地伤员掩护撤离", chinese.get("mobsthinknow.config.squad_casualty_extraction").getAsString());
	}

	private static JsonObject readLanguage(final String fileName) throws IOException {
		String path = "/assets/mobsthinknow/lang/" + fileName;
		try (InputStream stream = LanguageResourceTest.class.getResourceAsStream(path)) {
			assertNotNull(stream, "Missing language resource: " + path);
			try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}
}

package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PaperDefaultConfigTest {
	@Test
	void bundledConfigurationParsesAndKeepsSkeletonSectionsAtTheRightDepth() throws Exception {
		InputStream stream = PaperDefaultConfigTest.class.getResourceAsStream("/config.yml");
		assertNotNull(stream);
		YamlConfiguration configuration = new YamlConfiguration();
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			configuration.load(reader);
		}
		assertTrue(configuration.getBoolean("skeleton.spacing.enabled"));
		assertEquals(20, configuration.getInt("skeleton.spacing.timeout-cooldown-ticks"));
		assertTrue(configuration.getBoolean("skeleton.projectile-evasion.enabled"));
		assertEquals(256, configuration.getInt("skeleton.projectile-evasion.maximum-tracked-projectiles"));
		assertTrue(configuration.getBoolean("skeleton.cover-peeking.enabled"));
		assertEquals(96, configuration.getInt("skeleton.cover-peeking.maximum-candidate-checks"));
		assertEquals(4, configuration.getInt("skeleton.cover-peeking.maximum-path-checks"));
		assertTrue(configuration.getBoolean("skeleton.coordinated-fire.enabled"));
	}
}

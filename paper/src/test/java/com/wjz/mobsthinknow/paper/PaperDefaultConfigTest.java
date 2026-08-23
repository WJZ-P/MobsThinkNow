package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wjz.mobsthinknow.shared.ai.SpiderWebTrapPlanner;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PaperDefaultConfigTest {
	@Test
	void bundledConfigurationParsesAndKeepsNestedFeatureSectionsAtTheRightDepth() throws Exception {
		InputStream stream = PaperDefaultConfigTest.class.getResourceAsStream("/config.yml");
		assertNotNull(stream);
		YamlConfiguration configuration = new YamlConfiguration();
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			configuration.load(reader);
		}
		assertTrue(configuration.getBoolean("skeleton.spacing.enabled"));
		assertEquals(10, configuration.getInt("zombie.shield-tactics.strike-window-ticks"));
		assertEquals(20, configuration.getInt("zombie.shield-tactics.block-signal-memory-ticks"));
		assertEquals(5, configuration.getInt("zombie.shield-tactics.block.minimum-use-ticks"));
		assertEquals(0.0, configuration.getDouble("zombie.shield-tactics.block.minimum-facing-dot"));
		assertEquals(3.0, configuration.getDouble("zombie.shield-tactics.axe-disable-seconds"));
		assertFalse(configuration.contains("zombie.shield-tactics.counter.strike-window-ticks"));
		assertEquals(20, configuration.getInt("skeleton.spacing.timeout-cooldown-ticks"));
		assertTrue(configuration.getBoolean("skeleton.projectile-evasion.enabled"));
		assertEquals(256, configuration.getInt("skeleton.projectile-evasion.maximum-tracked-projectiles"));
		assertTrue(configuration.getBoolean("skeleton.cover-peeking.enabled"));
		assertEquals(96, configuration.getInt("skeleton.cover-peeking.maximum-candidate-checks"));
		assertEquals(4, configuration.getInt("skeleton.cover-peeking.maximum-path-checks"));
		assertTrue(configuration.getBoolean("skeleton.coordinated-fire.enabled"));
		assertTrue(configuration.getBoolean("spider.tactics.web-traps.enabled"));
		assertEquals(7, configuration.getInt("spider.tactics.web-traps.minimum-intelligence"));
		assertEquals(
			SpiderWebTrapPlanner.DEFAULT_COOLDOWN_TICKS,
			configuration.getInt("spider.tactics.web-traps.cooldown-ticks")
		);
		assertEquals(
			SpiderWebTrapPlanner.DEFAULT_LIFETIME_TICKS,
			configuration.getInt("spider.tactics.web-traps.lifetime-ticks")
		);
		assertEquals(128, configuration.getInt("spider.tactics.web-traps.maximum-active-per-world"));
		assertTrue(configuration.getBoolean("spider.tactics.web-traps.blast-containment"));
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class ZombieIntelligenceNameTest {
	@Test
	void structuredMarkerPreservesTheOriginalName() {
		Component decorated = ZombieIntelligenceName.decorate(Component.literal("Bob"), 8);
		ZombieIntelligenceName.DecoratedName parsed = ZombieIntelligenceName.decoratedName(decorated);

		assertNotNull(parsed);
		assertEquals("Bob", parsed.base().getString());
		assertEquals(8, parsed.intelligence());
		assertEquals("Bob [8]", decorated.getString());
	}

	@Test
	void ordinaryBracketedNamesAreNotMistakenForOurMarker() {
		assertNull(ZombieIntelligenceName.decoratedName(Component.literal("Bob [8]")));
		assertEquals(0, ZombieIntelligenceName.parseMarker(" [11]"));
		assertEquals(0, ZombieIntelligenceName.parseMarker("[8]"));
	}
}

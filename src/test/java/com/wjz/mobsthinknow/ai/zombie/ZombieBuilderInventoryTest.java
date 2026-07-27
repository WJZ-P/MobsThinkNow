package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ZombieBuilderInventoryTest {
	@Test
	void emptyHandBreakTimeTracksSoftBlockHardness() {
		assertEquals(15, ZombieBuilderInventory.emptyHandBreakTicks(0.5F));
		assertEquals(18, ZombieBuilderInventory.emptyHandBreakTicks(0.6F));
	}

	@Test
	void emptyHandBreakTimeHasDefensiveBounds() {
		assertEquals(5, ZombieBuilderInventory.emptyHandBreakTicks(0.0F));
		assertEquals(40, ZombieBuilderInventory.emptyHandBreakTicks(100.0F));
	}
}

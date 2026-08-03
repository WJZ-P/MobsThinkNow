package com.wjz.mobsthinknow.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EndermanHeldItemPlacementTest {
	@Test
	void weaponAnchorReachesEndOfThirtyPixelArm() {
		assertEquals(18.0F / 16.0F, EndermanHeldItemPlacement.localArmYOffset(false));
	}

	@Test
	void shieldKeepsForearmMountedAnchor() {
		assertEquals(0.0F, EndermanHeldItemPlacement.localArmYOffset(true));
	}
}

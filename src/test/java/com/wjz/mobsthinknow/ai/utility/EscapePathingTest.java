package com.wjz.mobsthinknow.ai.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EscapePathingTest {
	@Test
	void horizontalDistanceIgnoresVerticalSeparation() {
		assertEquals(
			25.0,
			EscapePathing.horizontalDistanceSquared(new Vec3(3.0, 100.0, 4.0), Vec3.ZERO)
		);
	}

	@Test
	void awayDirectionUsesThreatVectorThenStableFallback() {
		assertEquals(
			new Vec3(1.0, 0.0, 0.0),
			EscapePathing.horizontalAwayDirection(new Vec3(2.0, 3.0, 0.0), Vec3.ZERO, Vec3.ZERO)
		);
		assertEquals(
			new Vec3(0.0, 0.0, -1.0),
			EscapePathing.horizontalAwayDirection(Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 2.0, -4.0))
		);
		assertEquals(
			new Vec3(0.0, 0.0, 1.0),
			EscapePathing.horizontalAwayDirection(Vec3.ZERO, Vec3.ZERO, Vec3.ZERO)
		);
	}
}

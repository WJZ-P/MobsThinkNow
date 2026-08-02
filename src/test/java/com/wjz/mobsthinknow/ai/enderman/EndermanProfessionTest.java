package com.wjz.mobsthinknow.ai.enderman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EndermanProfessionTest {
	@Test
	void idsRemainContiguousForSaveAndNetworkCompatibility() {
		assertEquals(
			Arrays.asList(0, 1, 2, 3, 4),
			Arrays.stream(EndermanProfession.values()).map(value -> (int)value.id()).toList()
		);
		for (EndermanProfession profession : EndermanProfession.values()) {
			assertEquals(profession, EndermanProfession.fromId(profession.id()));
		}
		assertEquals(EndermanProfession.NONE, EndermanProfession.fromId(99));
	}

	@Test
	void normalizedEliteRollSelectsEveryProfession() {
		assertEquals(
			EndermanProfession.CREEPER_HERALD,
			EndermanProfessionProfile.choose(Difficulty.EASY, 5, false, 0.02)
		);
		assertEquals(
			EndermanProfession.VOID_LANCER,
			EndermanProfessionProfile.choose(Difficulty.EASY, 5, false, 0.12)
		);
		assertEquals(
			EndermanProfession.VOID_GUARD,
			EndermanProfessionProfile.choose(Difficulty.EASY, 5, false, 0.30)
		);
		assertEquals(
			EndermanProfession.RIFTBLADE,
			EndermanProfessionProfile.choose(Difficulty.EASY, 5, false, 0.50)
		);
	}

	@Test
	void difficultyIntelligenceAndEndDimensionRaiseEliteAccess() {
		EndermanProfession easyLow = EndermanProfessionProfile.choose(Difficulty.EASY, 1, false, 0.35);
		EndermanProfession hardHigh = EndermanProfessionProfile.choose(Difficulty.HARD, 10, false, 0.35);
		EndermanProfession overworldHighRoll = EndermanProfessionProfile.choose(Difficulty.HARD, 10, false, 0.82);
		EndermanProfession endHighRoll = EndermanProfessionProfile.choose(Difficulty.HARD, 10, true, 0.82);

		assertEquals(EndermanProfession.RIFTBLADE, easyLow);
		assertNotEquals(EndermanProfession.RIFTBLADE, hardHigh);
		assertEquals(EndermanProfession.RIFTBLADE, overworldHighRoll);
		assertNotEquals(EndermanProfession.RIFTBLADE, endHighRoll);
	}

	@Test
	void horizontalTeleportDirectionFallsBackWithoutNaN() {
		Vec3 direction = EndermanCombatTeleport.horizontalUnit(Vec3.ZERO, new Vec3(3.0, 7.0, 4.0));
		assertEquals(0.6, direction.x, 1.0E-9);
		assertEquals(0.0, direction.y, 1.0E-9);
		assertEquals(0.8, direction.z, 1.0E-9);
		assertTrue(Double.isFinite(direction.lengthSqr()));
	}
}

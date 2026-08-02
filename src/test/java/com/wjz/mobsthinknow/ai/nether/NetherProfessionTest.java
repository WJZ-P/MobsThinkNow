package com.wjz.mobsthinknow.ai.nether;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetherProfessionTest {
	@Test
	void stableIdsRoundTripWithoutCrossingFamilies() {
		for (NetherProfession profession : NetherProfession.values()) {
			assertEquals(profession, NetherProfession.fromId(profession.id()));
		}
		assertEquals(NetherProfession.NONE, NetherProfession.fromId(-1));
		assertEquals(NetherProfession.NONE, NetherProfession.fromId(99));
	}

	@Test
	void crossbowPiglinsAlwaysBecomeMarksmen() {
		for (int difficulty = 1; difficulty <= 3; difficulty++) {
			assertEquals(
				NetherProfession.PIGLIN_MARKSMAN,
				NetherProfessionProfile.choose(
					NetherProfessionFamily.PIGLIN,
					true,
					false,
					difficulty,
					0.99
				)
			);
		}
	}

	@Test
	void hardDifficultyPromotesTheSameRollToElite() {
		assertEquals(
			NetherProfession.BLAZE_CINDER_GUARD,
			NetherProfessionProfile.choose(NetherProfessionFamily.BLAZE, false, false, 1, 0.10)
		);
		assertEquals(
			NetherProfession.BLAZE_VOLLEYMASTER,
			NetherProfessionProfile.choose(NetherProfessionFamily.BLAZE, false, false, 3, 0.10)
		);
		assertEquals(
			NetherProfession.HOGLIN_RAVAGER,
			NetherProfessionProfile.choose(NetherProfessionFamily.HOGLIN, false, false, 3, 0.31)
		);
		assertEquals(
			NetherProfession.WITHER_SKELETON_DUELIST,
			NetherProfessionProfile.choose(NetherProfessionFamily.WITHER_SKELETON, false, false, 1, 0.10)
		);
		assertEquals(
			NetherProfession.WITHER_SKELETON_HEXER,
			NetherProfessionProfile.choose(NetherProfessionFamily.WITHER_SKELETON, false, false, 3, 0.10)
		);
	}

	@Test
	void existingSpecialistWeaponsKeepTheirDedicatedProfession() {
		for (int difficulty = 1; difficulty <= 3; difficulty++) {
			assertEquals(
				NetherProfession.ZOMBIFIED_PIGLIN_LANCER,
				NetherProfessionProfile.choose(
					NetherProfessionFamily.ZOMBIFIED_PIGLIN,
					true,
					false,
					difficulty,
					0.99
				)
			);
			assertEquals(
				NetherProfession.WITHER_SKELETON_HEXER,
				NetherProfessionProfile.choose(
					NetherProfessionFamily.WITHER_SKELETON,
					true,
					false,
					difficulty,
					0.99
				)
			);
		}
	}

	@Test
	void everyFamilyReturnsOnlyCompatibleProfessions() {
		for (NetherProfessionFamily family : NetherProfessionFamily.values()) {
			for (int difficulty = 1; difficulty <= 3; difficulty++) {
				for (double roll : new double[]{0.0, 0.1, 0.4, 0.7, 1.0}) {
					NetherProfession profession = NetherProfessionProfile.choose(
						family,
						false,
						false,
						difficulty,
						roll
					);
					assertTrue(
						profession == NetherProfession.NONE || profession.belongsTo(family),
						() -> profession + " escaped " + family
					);
				}
			}
		}
	}

	@Test
	void tacticsGiveEachProfessionARealMechanicalIdentity() {
		assertEquals(5, NetherProfessionTactics.blazeVolleySize(4, NetherProfession.BLAZE_VOLLEYMASTER));
		assertEquals(3, NetherProfessionTactics.blazeVolleySize(4, NetherProfession.BLAZE_CINDER_GUARD));
		assertEquals(2, NetherProfessionTactics.ghastExplosionPower(1, NetherProfession.GHAST_SIEGEBREAKER));
		assertTrue(
			NetherProfessionTactics.hoglinImpulseMultiplier(NetherProfession.HOGLIN_RAVAGER)
				> NetherProfessionTactics.hoglinImpulseMultiplier(NetherProfession.HOGLIN_BULWARK)
		);
		assertTrue(
			NetherProfessionTactics.magmaPounceMultiplier(NetherProfession.MAGMA_AMBUSHER)
				> NetherProfessionTactics.magmaPounceMultiplier(NetherProfession.MAGMA_TITAN)
		);
		assertTrue(
			NetherProfessionTactics.undeadMoveSpeed(NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER)
				> NetherProfessionTactics.undeadMoveSpeed(NetherProfession.ZOMBIFIED_PIGLIN_WARCALLER)
		);
		assertTrue(NetherProfessionTactics.undeadUsesLunge(NetherProfession.WITHER_SKELETON_REAPER));
		assertTrue(
			NetherProfessionTactics.undeadRecoveryTicks(NetherProfession.WITHER_SKELETON_DUELIST)
				> NetherProfessionTactics.undeadRecoveryTicks(NetherProfession.WITHER_SKELETON_REAPER)
		);
	}
}

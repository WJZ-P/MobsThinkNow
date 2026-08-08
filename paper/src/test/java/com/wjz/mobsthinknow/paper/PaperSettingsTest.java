package com.wjz.mobsthinknow.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PaperSettingsTest {
	@Test
	void validatesUnsafeConfigurationOnceAtReloadBoundary() {
		PaperSettings settings = PaperSettings.validated(
			true,
			true,
			true,
			99,
			Double.NaN,
			5.0,
			999,
			1.0,
			9.0,
			0,
			true,
			99,
			100.0,
			999,
			-10,
			true,
			99,
			99.0,
			999,
			0,
			99.0,
			999,
			99.0,
			true,
			99,
			true,
			99.0,
			true,
			99.0,
			99.0,
			999,
			0,
			999,
			true,
			99,
			true,
			true,
			999,
			0,
			999,
			true,
			99.0,
			Double.NaN,
			999,
			0
		);

		assertTrue(settings.enabled());
		assertEquals(10, settings.zombieRetreatMinimumIntelligence());
		assertEquals(0.05, settings.retreatHealthThreshold());
		assertEquals(1.0, settings.retreatHeavyHitThreshold());
		assertEquals(200, settings.retreatMaximumTicks());
		assertEquals(2.0, settings.retreatSafeDistance());
		assertEquals(2.0, settings.retreatSpeed());
		assertEquals(2, settings.damageMemoryTicks());
		assertEquals(10, settings.skeletonSpacingMinimumIntelligence());
		assertEquals(24.0, settings.skeletonPreferredRange());
		assertEquals(200, settings.skeletonDisengageMaximumTicks());
		assertEquals(0, settings.skeletonDisengageCooldownTicks());
		assertEquals(10, settings.skeletonCoordinatedFireMinimumIntelligence());
		assertEquals(40.0, settings.skeletonCoordinatedFireMaximumRange());
		assertEquals(30, settings.skeletonCoordinatedFireChargeTicks());
		assertEquals(20, settings.skeletonCoordinatedFireMinimumShotIntervalTicks());
		assertEquals(2.0, settings.skeletonFriendlyLaneRadius());
		assertEquals(100, settings.skeletonFriendlyLaneMaximumChecks());
		assertEquals(6.0, settings.skeletonLaneRepositionDistance());
		assertEquals(10, settings.creeperMinimumIntelligence());
		assertEquals(5.0, settings.creeperMaximumFuseStartDistance());
		assertEquals(1.5, settings.creeperMaximumFuseMovementSpeed());
		assertEquals(12.0, settings.creeperBlastConflictRadius());
		assertEquals(80, settings.creeperBlastSeparationTicks());
		assertEquals(10, settings.creeperBlastReservationLeaseTicks());
		assertEquals(64, settings.creeperBlastMaximumChecks());
		assertEquals(10, settings.spiderMinimumIntelligence());
		assertEquals(40, settings.spiderPounceStaggerTicks());
		assertEquals(5, settings.spiderPounceLeaseTicks());
		assertEquals(80, settings.spiderPounceMaximumAirTicks());
		assertEquals(1.6, settings.spiderMaximumCarrierSpeed());
		assertEquals(0.15, settings.spiderPayloadReleaseProgress());
		assertEquals(200, settings.spiderAssemblyTimeoutTicks());
		assertEquals(20, settings.spiderRemountCooldownTicks());
	}

	@Test
	void preservesTheFabricCompatibleRetreatDefaults() {
		PaperSettings settings = PaperSettings.validated(
			true, true, true, 1, 0.20, 0.30, 100, 5.0, 1.50, 20,
			true, 1, 10.0, 80, 20,
			true, 4, 24.0, 16, 28, 0.75, 20, 3.0,
			true, 1, true, 4.0, true, 1.25, 6.0, 24, 40, 32,
			true, 1, true, true, 10, 20, 40,
			true, 1.35, 0.35, 100, 100
		);

		assertEquals(0.20, settings.retreatHealthThreshold());
		assertEquals(0.30, settings.retreatHeavyHitThreshold());
		assertEquals(100, settings.retreatMaximumTicks());
		assertEquals(5.0, settings.retreatSafeDistance());
		assertEquals(1.50, settings.retreatSpeed());
		assertEquals(10.0, settings.skeletonPreferredRange());
		assertEquals(80, settings.skeletonDisengageMaximumTicks());
		assertEquals(20, settings.skeletonDisengageCooldownTicks());
		assertTrue(settings.skeletonCoordinatedFireEnabled());
		assertEquals(4, settings.skeletonCoordinatedFireMinimumIntelligence());
		assertEquals(24.0, settings.skeletonCoordinatedFireMaximumRange());
		assertEquals(4.0, settings.creeperMaximumFuseStartDistance());
		assertEquals(1.25, settings.creeperMaximumFuseMovementSpeed());
		assertEquals(6.0, settings.creeperBlastConflictRadius());
		assertEquals(24, settings.creeperBlastSeparationTicks());
		assertEquals(10, settings.spiderPounceStaggerTicks());
		assertEquals(20, settings.spiderPounceLeaseTicks());
		assertEquals(40, settings.spiderPounceMaximumAirTicks());
		assertTrue(settings.spiderMountedBreachEnabled());
		assertEquals(1.35, settings.spiderMaximumCarrierSpeed());
		assertEquals(0.35, settings.spiderPayloadReleaseProgress());
	}
}

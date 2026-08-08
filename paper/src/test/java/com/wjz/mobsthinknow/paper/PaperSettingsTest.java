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
			PaperWeaponSettings.validated(true, 99, 99.0, 99.0, 0, 99, 0, 999, 99.0, Double.NaN),
			PaperShieldSettings.validated(true, 99, 99.0, 0.0, 99.0, 0, 0, 999, 0, 999, 0, 999, 999, Double.NaN, 99.0),
			PaperCrossbowSettings.validated(
				true, 99, 0, 0, 999, 99.0, 99.0, 99.0, Double.NaN,
				PaperFireworkSettings.validated(true, 99, 0.0, 99.0, 99.0, 999, 99.0, 999, 999, true),
				PaperSkeletonLoadoutSettings.validated(true, 99.0, Double.NaN)
			),
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
			PaperCreeperFeintSettings.validated(true, 9999, Double.NaN),
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
		assertEquals(10, settings.zombieWeaponTactics().minimumIntelligence());
		assertEquals(5.0, settings.zombieWeaponTactics().spacingRadius());
		assertEquals(1.5, settings.zombieWeaponTactics().movementSpeed());
		assertEquals(2, settings.zombieWeaponTactics().repathTicks());
		assertEquals(10, settings.zombieWeaponTactics().axeMinimumIntelligence());
		assertEquals(4, settings.zombieWeaponTactics().axeWindupTicks());
		assertEquals(80, settings.zombieWeaponTactics().axePreparationTimeoutTicks());
		assertEquals(0.6, settings.zombieWeaponTactics().axeHorizontalSpeed());
		assertEquals(1.0, settings.zombieWeaponTactics().axeCriticalDamageMultiplier());
		assertEquals(10, settings.zombieShieldTactics().minimumIntelligence());
		assertEquals(10.0, settings.zombieShieldTactics().raiseDistance());
		assertEquals(10.0, settings.zombieShieldTactics().lowerDistance());
		assertEquals(1.5, settings.zombieShieldTactics().movementSpeed());
		assertEquals(2, settings.zombieShieldTactics().repathTicks());
		assertEquals(4, settings.zombieShieldTactics().minimumGuardTicks());
		assertEquals(100, settings.zombieShieldTactics().maximumGuardTicks());
		assertEquals(1, settings.zombieShieldTactics().minimumCounterDelayTicks());
		assertEquals(20, settings.zombieShieldTactics().maximumCounterDelayTicks());
		assertEquals(4, settings.zombieShieldTactics().strikeWindowTicks());
		assertEquals(40, settings.zombieShieldTactics().blockSignalMemoryTicks());
		assertEquals(20, settings.zombieShieldTactics().minimumBlockUseTicks());
		assertEquals(-0.5, settings.zombieShieldTactics().minimumFacingDot());
		assertEquals(200, settings.zombieShieldTactics().axeDisableTicks());
		assertEquals(10, settings.skeletonCrossbowTactics().minimumIntelligence());
		assertEquals(12, settings.skeletonCrossbowTactics().chargeTicks());
		assertEquals(1, settings.skeletonCrossbowTactics().minimumAimTicks());
		assertEquals(40, settings.skeletonCrossbowTactics().maximumAimTicks());
		assertEquals(5.0, settings.skeletonCrossbowTactics().projectileSpeed());
		assertEquals(14.0, settings.skeletonCrossbowTactics().projectileSpread());
		assertEquals(40.0, settings.skeletonCrossbowTactics().maximumLeadTicks());
		assertEquals(0.0, settings.skeletonCrossbowTactics().gravityPerTickSquared());
		assertEquals(10, settings.skeletonCrossbowTactics().firework().minimumIntelligence());
		assertEquals(4.0, settings.skeletonCrossbowTactics().firework().minimumRange());
		assertEquals(48.0, settings.skeletonCrossbowTactics().firework().maximumRange());
		assertEquals(8.0, settings.skeletonCrossbowTactics().firework().allyDangerRadius());
		assertEquals(100, settings.skeletonCrossbowTactics().firework().maximumAllyChecks());
		assertEquals(3.0, settings.skeletonCrossbowTactics().firework().projectileSpeed());
		assertEquals(100, settings.skeletonCrossbowTactics().firework().projectileLifetimeTicks());
		assertEquals(128, settings.skeletonCrossbowTactics().firework().maximumActiveProjectiles());
		assertEquals(1.0, settings.skeletonCrossbowTactics().naturalLoadout().crossbowChance());
		assertEquals(0.0, settings.skeletonCrossbowTactics().naturalLoadout().fireworkCrossbowChance());
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
		assertEquals(1200, settings.creeperFeints().cooldownTicks());
		assertEquals(1.16, settings.creeperFeints().repositionSpeed());
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
			PaperWeaponSettings.validated(true, 3, 2.8, 1.15, 6, 6, 8, 30, 0.34, 1.50),
			PaperShieldSettings.validated(true, 4, 6.0, 7.5, 1.10, 6, 12, 28, 2, 4, 10, 20, 5, 0.0, 3.0),
			PaperCrossbowSettings.validated(
				true, 3, 25, 4, 10, 3.15, 2.0, 20.0, 0.05,
				PaperFireworkSettings.validated(true, 7, 6.0, 30.0, 3.5, 20, 1.6, 40, 48, true),
				PaperSkeletonLoadoutSettings.validated(true, 0.18, 0.25)
			),
			true, 1, 10.0, 80, 20,
			true, 4, 24.0, 16, 28, 0.75, 20, 3.0,
			true, 1, true, 4.0, true, 1.25,
			PaperCreeperFeintSettings.validated(true, 240, 1.16),
			6.0, 24, 40, 32,
			true, 1, true, true, 10, 20, 40,
			true, 1.35, 0.35, 100, 100
		);

		assertEquals(0.20, settings.retreatHealthThreshold());
		assertEquals(0.30, settings.retreatHeavyHitThreshold());
		assertEquals(100, settings.retreatMaximumTicks());
		assertEquals(5.0, settings.retreatSafeDistance());
		assertEquals(1.50, settings.retreatSpeed());
		assertTrue(settings.zombieWeaponTactics().enabled());
		assertEquals(2.8, settings.zombieWeaponTactics().spacingRadius());
		assertEquals(1.50, settings.zombieWeaponTactics().axeCriticalDamageMultiplier());
		assertTrue(settings.zombieShieldTactics().enabled());
		assertEquals(12, settings.zombieShieldTactics().minimumGuardTicks());
		assertEquals(28, settings.zombieShieldTactics().maximumGuardTicks());
		assertEquals(2, settings.zombieShieldTactics().minimumCounterDelayTicks());
		assertEquals(4, settings.zombieShieldTactics().maximumCounterDelayTicks());
		assertEquals(60, settings.zombieShieldTactics().axeDisableTicks());
		assertTrue(settings.skeletonCrossbowTactics().enabled());
		assertEquals(25, settings.skeletonCrossbowTactics().chargeTicks());
		assertEquals(3.15, settings.skeletonCrossbowTactics().projectileSpeed());
		assertTrue(settings.skeletonCrossbowTactics().firework().enabled());
		assertEquals(1.6, settings.skeletonCrossbowTactics().firework().projectileSpeed());
		assertEquals(0.18, settings.skeletonCrossbowTactics().naturalLoadout().crossbowChance());
		assertEquals(10.0, settings.skeletonPreferredRange());
		assertEquals(80, settings.skeletonDisengageMaximumTicks());
		assertEquals(20, settings.skeletonDisengageCooldownTicks());
		assertTrue(settings.skeletonCoordinatedFireEnabled());
		assertEquals(4, settings.skeletonCoordinatedFireMinimumIntelligence());
		assertEquals(24.0, settings.skeletonCoordinatedFireMaximumRange());
		assertEquals(4.0, settings.creeperMaximumFuseStartDistance());
		assertEquals(1.25, settings.creeperMaximumFuseMovementSpeed());
		assertTrue(settings.creeperFeints().enabled());
		assertEquals(240, settings.creeperFeints().cooldownTicks());
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

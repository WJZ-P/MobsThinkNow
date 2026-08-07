package com.wjz.mobsthinknow.ai.zombie.squad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SquadPounceCadenceTest {
	@Test
	void oneAirborneOwnerBlocksEveryOtherSpider() {
		SquadPounceCadence cadence = new SquadPounceCadence();

		assertTrue(cadence.tryReserve(11, 90, 100L, 8, 30));
		assertTrue(cadence.canReserve(11, 90, 101L), "The current owner should be idempotent.");
		assertFalse(cadence.canReserve(12, 90, 108L));
		assertEquals(11, cadence.ownerId(108L));
	}

	@Test
	void landingReleasesOwnerButPreservesLaunchSpacing() {
		SquadPounceCadence cadence = new SquadPounceCadence();
		assertTrue(cadence.tryReserve(11, 90, 100L, 8, 30));

		cadence.release(11);

		assertFalse(cadence.isActive(101L));
		assertFalse(cadence.canReserve(12, 90, 107L));
		assertTrue(cadence.canReserve(12, 90, 108L));
		assertEquals(108L, cadence.nextAvailableAt());
	}

	@Test
	void staleAirborneOwnerSelfExpiresWithoutDeadlockingTheSquad() {
		SquadPounceCadence cadence = new SquadPounceCadence();
		assertTrue(cadence.tryReserve(11, 90, 100L, 8, 30));

		assertTrue(cadence.isActive(129L));
		assertFalse(cadence.isActive(130L));
		assertTrue(cadence.tryReserve(12, 90, 130L, 8, 30));
		assertEquals(12, cadence.ownerId(130L));
	}

	@Test
	void invalidEntityIdsNeverAcquireTheSharedToken() {
		SquadPounceCadence cadence = new SquadPounceCadence();

		assertFalse(cadence.tryReserve(0, 90, 100L, 8, 30));
		assertFalse(cadence.tryReserve(11, 0, 100L, 8, 30));
		assertFalse(cadence.isActive(100L));
	}
}

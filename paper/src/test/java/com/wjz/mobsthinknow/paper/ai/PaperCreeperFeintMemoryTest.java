package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Creeper;
import org.junit.jupiter.api.Test;

final class PaperCreeperFeintMemoryTest {
	@Test
	void coolingEntryReleasesExactlyAtItsCombatUnlockTick() {
		PaperCreeperFeintMemory memory = new PaperCreeperFeintMemory();
		AtomicBoolean ignited = new AtomicBoolean(true);
		AtomicInteger fuseTicks = new AtomicInteger(7);
		Creeper creeper = creeper(UUID.randomUUID(), ignited, fuseTicks);
		memory.beginPostFeintCooling(creeper, 100L, 10);

		memory.tickCooling(109L);
		assertEquals(1, memory.coolingCount());
		assertFalse(ignited.get());
		assertEquals(0, fuseTicks.get());

		memory.tickCooling(110L);
		assertEquals(0, memory.coolingCount());
	}

	@Test
	void invalidCreeperDoesNotRetainACoolingSlot() {
		PaperCreeperFeintMemory memory = new PaperCreeperFeintMemory();
		Creeper invalid = creeper(UUID.randomUUID(), new AtomicBoolean(), new AtomicInteger(), false);
		memory.beginPostFeintCooling(invalid, 5L, 10);

		memory.tickCooling(6L);

		assertEquals(0, memory.coolingCount());
	}

	@Test
	void coolingChainUnlinksReplacementsAndInteriorEntries() {
		PaperCreeperFeintMemory memory = new PaperCreeperFeintMemory();
		AtomicInteger validityChecks = new AtomicInteger();
		UUID replacedId = UUID.randomUUID();
		Creeper original = creeper(
			replacedId,
			new AtomicBoolean(true),
			new AtomicInteger(7),
			true,
			validityChecks
		);
		Creeper replacement = creeper(
			replacedId,
			new AtomicBoolean(true),
			new AtomicInteger(7),
			true,
			validityChecks
		);
		Creeper interior = creeper(
			UUID.randomUUID(),
			new AtomicBoolean(true),
			new AtomicInteger(7),
			true,
			validityChecks
		);
		Creeper neighbor = creeper(
			UUID.randomUUID(),
			new AtomicBoolean(true),
			new AtomicInteger(7),
			true,
			validityChecks
		);
		memory.beginPostFeintCooling(original, 100L, 5);
		memory.beginPostFeintCooling(interior, 100L, 30);
		memory.beginPostFeintCooling(neighbor, 100L, 40);
		memory.beginPostFeintCooling(replacement, 100L, 20);
		memory.transferToRealFuse(interior);

		memory.tickCooling(101L);

		assertEquals(2, memory.coolingCount());
		assertEquals(4, validityChecks.get());
		memory.tickCooling(120L);
		assertEquals(1, memory.coolingCount());
		memory.tickCooling(140L);
		assertEquals(0, memory.coolingCount());
	}

	@Test
	void elapsedPerEntityCooldownIsRemovedAndAllowsASecondFeint() {
		PaperCreeperFeintMemory memory = new PaperCreeperFeintMemory();
		Creeper creeper = creeper(UUID.randomUUID(), new AtomicBoolean(), new AtomicInteger());
		assertTrue(memory.begin(creeper, 10L));
		memory.finish(creeper, 10L, 20);

		assertFalse(memory.canStart(creeper, 29L));
		assertTrue(memory.canStart(creeper, 30L));
	}

	@Test
	void completionChainRefreshesRecencyExpiresAndEnforcesCapacity() {
		PaperCreeperFeintMemory memory = new PaperCreeperFeintMemory();
		Creeper refreshed = creeper(UUID.randomUUID(), new AtomicBoolean(), new AtomicInteger());
		memory.markCompleted(refreshed, 10L);
		memory.markCompleted(refreshed, 500L);
		memory.tickCooling(611L);
		assertEquals(1, memory.completionCount());
		memory.tickCooling(1_101L);
		assertEquals(0, memory.completionCount());

		for (int index = 0; index < 300; index++) {
			memory.markCompleted(
				creeper(UUID.randomUUID(), new AtomicBoolean(), new AtomicInteger()),
				2_000L + index
			);
		}
		assertEquals(256, memory.completionCount());
	}

	private static Creeper creeper(
		final UUID id,
		final AtomicBoolean ignited,
		final AtomicInteger fuseTicks
	) {
		return creeper(id, ignited, fuseTicks, true);
	}

	private static Creeper creeper(
		final UUID id,
		final AtomicBoolean ignited,
		final AtomicInteger fuseTicks,
		final boolean valid
	) {
		return creeper(id, ignited, fuseTicks, valid, null);
	}

	private static Creeper creeper(
		final UUID id,
		final AtomicBoolean ignited,
		final AtomicInteger fuseTicks,
		final boolean valid,
		final AtomicInteger validityChecks
	) {
		return (Creeper)Proxy.newProxyInstance(
			Creeper.class.getClassLoader(),
			new Class<?>[]{Creeper.class},
			(proxy, method, arguments) -> switch (method.getName()) {
				case "getUniqueId" -> id;
				case "isValid" -> {
					if (validityChecks != null) {
						validityChecks.incrementAndGet();
					}
					yield valid;
				}
				case "isDead" -> false;
				case "isIgnited" -> ignited.get();
				case "setIgnited" -> {
					ignited.set((boolean)arguments[0]);
					yield null;
				}
				case "getFuseTicks" -> fuseTicks.get();
				case "setFuseTicks" -> {
					fuseTicks.set((int)arguments[0]);
					yield null;
				}
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == arguments[0];
				case "toString" -> "CreeperProxy[" + id + "]";
				default -> defaultValue(method.getReturnType());
			}
		);
	}

	private static Object defaultValue(final Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == char.class) {
			return '\0';
		}
		return 0;
	}
}

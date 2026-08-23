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
	void elapsedPerEntityCooldownIsRemovedAndAllowsASecondFeint() {
		PaperCreeperFeintMemory memory = new PaperCreeperFeintMemory();
		Creeper creeper = creeper(UUID.randomUUID(), new AtomicBoolean(), new AtomicInteger());
		assertTrue(memory.begin(creeper, 10L));
		memory.finish(creeper, 10L, 20);

		assertFalse(memory.canStart(creeper, 29L));
		assertTrue(memory.canStart(creeper, 30L));
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
		return (Creeper)Proxy.newProxyInstance(
			Creeper.class.getClassLoader(),
			new Class<?>[]{Creeper.class},
			(proxy, method, arguments) -> switch (method.getName()) {
				case "getUniqueId" -> id;
				case "isValid" -> valid;
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

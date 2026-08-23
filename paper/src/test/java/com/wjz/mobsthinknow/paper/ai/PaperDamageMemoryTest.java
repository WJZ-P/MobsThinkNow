package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

final class PaperDamageMemoryTest {
	@Test
	void recentSmallHitDoesNotRefreshAnExpiredHeavyHit() {
		PaperDamageMemory memory = new PaperDamageMemory();
		Zombie zombie = entity(Zombie.class, UUID.randomUUID());
		LivingEntity heavy = entity(LivingEntity.class, UUID.randomUUID());
		LivingEntity latest = entity(LivingEntity.class, UUID.randomUUID());
		memory.record(zombie, heavy, 12.0, 10L);
		memory.record(zombie, latest, 2.0, 100L);

		PaperDamageMemory.DamageSnapshot snapshot = memory.consume(zombie, 100L, 20);

		assertEquals(latest.getUniqueId(), snapshot.latestAttackerId());
		assertEquals(latest.getUniqueId(), snapshot.largestDamageAttackerId());
		assertEquals(2.0, snapshot.largestDamage());
	}

	@Test
	void freshHeavyHitSurvivesLaterSmallerHitsWithinTheWindow() {
		PaperDamageMemory memory = new PaperDamageMemory();
		Zombie zombie = entity(Zombie.class, UUID.randomUUID());
		LivingEntity heavy = entity(LivingEntity.class, UUID.randomUUID());
		LivingEntity latest = entity(LivingEntity.class, UUID.randomUUID());
		memory.record(zombie, heavy, 12.0, 80L);
		memory.record(zombie, latest, 2.0, 95L);

		PaperDamageMemory.DamageSnapshot snapshot = memory.consume(zombie, 100L, 20);

		assertEquals(latest.getUniqueId(), snapshot.latestAttackerId());
		assertEquals(heavy.getUniqueId(), snapshot.largestDamageAttackerId());
		assertEquals(12.0, snapshot.largestDamage());
	}

	@Test
	void staleOrFutureLatestHitIsRejected() {
		PaperDamageMemory memory = new PaperDamageMemory();
		Zombie zombie = entity(Zombie.class, UUID.randomUUID());
		LivingEntity attacker = entity(LivingEntity.class, UUID.randomUUID());
		memory.record(zombie, attacker, 4.0, 10L);
		assertNull(memory.consume(zombie, 40L, 20));

		memory.record(zombie, attacker, 4.0, 50L);
		assertNull(memory.consume(zombie, 49L, 20));
	}

	@SuppressWarnings("unchecked")
	private static <T> T entity(final Class<T> type, final UUID id) {
		return (T)Proxy.newProxyInstance(
			type.getClassLoader(),
			new Class<?>[]{type},
			(proxy, method, arguments) -> switch (method.getName()) {
				case "getUniqueId" -> id;
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> proxy == arguments[0];
				case "toString" -> type.getSimpleName() + "Proxy[" + id + "]";
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

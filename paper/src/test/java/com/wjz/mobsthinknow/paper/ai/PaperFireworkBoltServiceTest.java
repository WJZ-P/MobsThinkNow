package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Firework;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

final class PaperFireworkBoltServiceTest {
	@Test
	void rejectsNullZeroAndNonFiniteDirectionsBeforeSpawning() {
		assertFalse(PaperFireworkBoltService.isUsableDirection(null));
		assertFalse(PaperFireworkBoltService.isUsableDirection(new Vector()));
		assertFalse(PaperFireworkBoltService.isUsableDirection(new Vector(Double.NaN, 0.0, 1.0)));
		assertFalse(PaperFireworkBoltService.isUsableDirection(new Vector(0.0, Double.POSITIVE_INFINITY, 1.0)));
		assertTrue(PaperFireworkBoltService.isUsableDirection(new Vector(0.1, 0.2, 1.0)));
	}

	@Test
	void activeBoltChainPreservesOrderAcrossRemovalAndReentry() {
		PaperFireworkBoltService.ActiveBoltChain chain = new PaperFireworkBoltService.ActiveBoltChain();
		PaperFireworkBoltService.ActiveBolt first = activeBolt();
		PaperFireworkBoltService.ActiveBolt moving = activeBolt();
		PaperFireworkBoltService.ActiveBolt last = activeBolt();
		chain.add(first);
		chain.add(moving);
		chain.add(last);
		assertEquals(3, chain.size());
		assertSame(first, chain.first());

		chain.remove(first);
		assertSame(moving, chain.first());
		chain.remove(moving);
		chain.add(moving);
		assertSame(last, chain.first());
		assertEquals(2, chain.size());

		chain.clear();
		assertEquals(0, chain.size());
		assertNull(chain.first());
	}

	private static PaperFireworkBoltService.ActiveBolt activeBolt() {
		return new PaperFireworkBoltService.ActiveBolt(
			UUID.randomUUID(),
			firework(),
			UUID.randomUUID(),
			UUID.randomUUID(),
			new Vector(0.0, 0.0, 1.0),
			100L
		);
	}

	private static Firework firework() {
		return (Firework)Proxy.newProxyInstance(
			Firework.class.getClassLoader(),
			new Class<?>[] {Firework.class},
			(proxy, method, arguments) -> null
		);
	}
}

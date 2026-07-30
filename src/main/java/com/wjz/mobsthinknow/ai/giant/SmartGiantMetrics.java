package com.wjz.mobsthinknow.ai.giant;

import java.util.concurrent.atomic.AtomicLong;

/** 巨人生成、乘员和双手投送的只读诊断计数器。 */
public final class SmartGiantMetrics {
	private static final AtomicLong INSTALLED_GOALS = new AtomicLong();
	private static final AtomicLong ZOMBIES_CONVERTED = new AtomicLong();
	private static final AtomicLong RIDERS_MOUNTED = new AtomicLong();
	private static final AtomicLong PAYLOADS_PICKED_UP = new AtomicLong();
	private static final AtomicLong CREEPERS_THROWN = new AtomicLong();
	private static final AtomicLong ZOMBIES_THROWN = new AtomicLong();

	private SmartGiantMetrics() {
	}

	public static void goalInstalled() {
		INSTALLED_GOALS.incrementAndGet();
	}

	public static void zombieConverted() {
		ZOMBIES_CONVERTED.incrementAndGet();
	}

	public static void riderMounted() {
		RIDERS_MOUNTED.incrementAndGet();
	}

	public static void payloadPickedUp() {
		PAYLOADS_PICKED_UP.incrementAndGet();
	}

	public static void creeperThrown() {
		CREEPERS_THROWN.incrementAndGet();
	}

	public static void zombieThrown() {
		ZOMBIES_THROWN.incrementAndGet();
	}

	public static Snapshot snapshot() {
		return new Snapshot(
			INSTALLED_GOALS.get(),
			ZOMBIES_CONVERTED.get(),
			RIDERS_MOUNTED.get(),
			PAYLOADS_PICKED_UP.get(),
			CREEPERS_THROWN.get(),
			ZOMBIES_THROWN.get()
		);
	}

	public record Snapshot(
		long installedGoals,
		long zombiesConverted,
		long ridersMounted,
		long payloadsPickedUp,
		long creepersThrown,
		long zombiesThrown
	) {
	}
}

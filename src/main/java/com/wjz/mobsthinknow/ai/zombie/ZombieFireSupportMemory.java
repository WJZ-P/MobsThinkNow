package com.wjz.mobsthinknow.ai.zombie;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/** 单次着火事件交给一只水桶队友的有界救火请求，不做每 tick 邻居扫描。 */
public final class ZombieFireSupportMemory {
	private static final long REQUEST_LIFETIME_TICKS = 120L;
	private static final Map<Zombie, Request> REQUESTS = new IdentityHashMap<>();

	private ZombieFireSupportMemory() {
	}

	public static void record(final Zombie helper, final Zombie burningMember) {
		if (!helper.isAlive()
			|| !burningMember.isAlive()
			|| !burningMember.isOnFire()
			|| helper.level() != burningMember.level()) {
			return;
		}
		REQUESTS.put(helper, new Request(
			burningMember,
			helper.level().getGameTime() + REQUEST_LIFETIME_TICKS
		));
	}

	public static @Nullable Request consume(final Zombie helper) {
		Request request = REQUESTS.remove(helper);
		if (request == null
			|| helper.level().getGameTime() > request.expiresAt()
			|| !request.burningMember().isAlive()
			|| !request.burningMember().isOnFire()
			|| request.burningMember().level() != helper.level()) {
			return null;
		}
		return request;
	}

	public static void discard(final Zombie zombie) {
		REQUESTS.remove(zombie);
		REQUESTS.values().removeIf(request -> request.burningMember() == zombie);
	}

	public static void clearLevel(final ServerLevel level) {
		REQUESTS.entrySet().removeIf(entry ->
			entry.getKey().level() == level || entry.getValue().burningMember().level() == level
		);
	}

	public static void clear() {
		REQUESTS.clear();
	}

	public record Request(Zombie burningMember, long expiresAt) {
	}
}

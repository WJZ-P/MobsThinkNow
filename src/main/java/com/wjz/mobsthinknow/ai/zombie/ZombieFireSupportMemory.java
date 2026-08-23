package com.wjz.mobsthinknow.ai.zombie;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/** 单次着火事件交给一只水桶队友的有界救火请求；值只保存 UUID，消费时才解析实体。 */
public final class ZombieFireSupportMemory {
	private static final long REQUEST_LIFETIME_TICKS = 120L;
	private static final Map<Zombie, PendingRequest> REQUESTS = new IdentityHashMap<>();
	private static final Map<UUID, Set<Zombie>> HELPERS_BY_BURNING_MEMBER = new HashMap<>();

	private ZombieFireSupportMemory() {
	}

	public static void record(final Zombie helper, final Zombie burningMember) {
		if (!helper.isAlive()
			|| !burningMember.isAlive()
			|| !burningMember.isOnFire()
			|| helper.level() != burningMember.level()) {
			return;
		}
		PendingRequest replacement = new PendingRequest(
			burningMember.getUUID(),
			saturatingAdd(helper.level().getGameTime(), REQUEST_LIFETIME_TICKS)
		);
		PendingRequest previous = REQUESTS.put(helper, replacement);
		if (previous != null) {
			removeReverse(helper, previous.burningMemberId());
		}
		HELPERS_BY_BURNING_MEMBER
			.computeIfAbsent(replacement.burningMemberId(), ignored -> newIdentitySet())
			.add(helper);
	}

	public static @Nullable Request consume(final Zombie helper) {
		PendingRequest request = removeHelperRequest(helper);
		if (request == null
			|| helper.level().getGameTime() > request.expiresAt()
			|| !(helper.level() instanceof ServerLevel level)) {
			return null;
		}
		Entity entity = level.getEntity(request.burningMemberId());
		if (!(entity instanceof Zombie burningMember)
			|| !burningMember.isAlive()
			|| burningMember.isRemoved()
			|| !burningMember.isOnFire()
			|| burningMember.level() != helper.level()) {
			return null;
		}
		return new Request(burningMember, request.expiresAt());
	}

	public static void discard(final Zombie zombie) {
		discardHelper(zombie);
		discardBurningMember(zombie);
	}

	public static void discardHelper(final Zombie helper) {
		removeHelperRequest(helper);
	}

	public static void discardBurningMember(final Zombie burningMember) {
		UUID memberId = burningMember.getUUID();
		Set<Zombie> helpers = HELPERS_BY_BURNING_MEMBER.remove(memberId);
		if (helpers == null) {
			return;
		}
		for (Zombie helper : helpers) {
			PendingRequest current = REQUESTS.get(helper);
			if (current != null && current.burningMemberId().equals(memberId)) {
				REQUESTS.remove(helper);
			}
		}
	}

	public static void clearLevel(final ServerLevel level) {
		for (Zombie helper : REQUESTS.keySet().stream().filter(zombie -> zombie.level() == level).toList()) {
			discardHelper(helper);
		}
	}

	public static void clear() {
		REQUESTS.clear();
		HELPERS_BY_BURNING_MEMBER.clear();
	}

	public record Request(Zombie burningMember, long expiresAt) {
	}

	private static PendingRequest removeHelperRequest(final Zombie helper) {
		PendingRequest removed = REQUESTS.remove(helper);
		if (removed != null) {
			removeReverse(helper, removed.burningMemberId());
		}
		return removed;
	}

	private static void removeReverse(final Zombie helper, final UUID burningMemberId) {
		Set<Zombie> helpers = HELPERS_BY_BURNING_MEMBER.get(burningMemberId);
		if (helpers != null && helpers.remove(helper) && helpers.isEmpty()) {
			HELPERS_BY_BURNING_MEMBER.remove(burningMemberId);
		}
	}

	private static Set<Zombie> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private record PendingRequest(UUID burningMemberId, long expiresAt) {
	}
}

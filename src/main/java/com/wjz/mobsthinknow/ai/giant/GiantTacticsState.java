package com.wjz.mobsthinknow.ai.giant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.jspecify.annotations.Nullable;

/**
 * 巨人战术状态的唯一读写入口。
 *
 * <p>每只手先锁定一个 UUID，再允许该实体登乘。客户端只依赖同步后的实体 ID 与阶段；
 * 服务端每 tick 对照真实乘客关系修复死亡、外部下车和旧存档的 passenger-order 数据。</p>
 */
public final class GiantTacticsState {
	private GiantTacticsState() {
	}

	public static void assignPayload(final Giant giant, final GiantHand hand, final Entity payload) {
		GiantTacticsAccess access = access(giant);
		access.mobsthinknow$setPayloadUuid(hand, payload.getUUID());
		access.mobsthinknow$setPayloadEntityId(hand, payload.getId());
	}

	public static void clearPayload(final Giant giant, final GiantHand hand) {
		GiantTacticsAccess access = access(giant);
		access.mobsthinknow$setPayloadUuid(hand, null);
		access.mobsthinknow$setPayloadEntityId(hand, 0);
	}

	public static void resetHand(final Giant giant, final GiantHand hand) {
		clearPayload(giant, hand);
		transitionHand(giant, hand, GiantHandPhase.EMPTY);
	}

	public static @Nullable Entity payloadCandidate(final Giant giant, final GiantHand hand) {
		GiantTacticsAccess access = access(giant);
		int entityId = access.mobsthinknow$getPayloadEntityId(hand);
		if (entityId != 0) {
			Entity byId = giant.level().getEntity(entityId);
			if (isMatchingPayload(access.mobsthinknow$getPayloadUuid(hand), byId)) {
				return byId;
			}
		}
		if (giant.level() instanceof ServerLevel level) {
			UUID uuid = access.mobsthinknow$getPayloadUuid(hand);
			Entity byUuid = uuid == null ? null : level.getEntity(uuid);
			if (isMatchingPayload(uuid, byUuid)) {
				access.mobsthinknow$setPayloadEntityId(hand, byUuid.getId());
				return byUuid;
			}
		}
		return null;
	}

	public static @Nullable Entity payloadForHand(final Giant giant, final GiantHand hand) {
		Entity candidate = payloadCandidate(giant, hand);
		return candidate != null && candidate.getVehicle() == giant ? candidate : null;
	}

	public static @Nullable GiantHand handFor(final Giant giant, final Entity entity) {
		GiantTacticsAccess access = access(giant);
		for (GiantHand hand : GiantHand.values()) {
			UUID expected = access.mobsthinknow$getPayloadUuid(hand);
			if ((expected != null && expected.equals(entity.getUUID()))
				|| (access.mobsthinknow$getPayloadEntityId(hand) != 0
					&& access.mobsthinknow$getPayloadEntityId(hand) == entity.getId())) {
				return hand;
			}
			Entity candidate = payloadCandidate(giant, hand);
			if (candidate == entity || (candidate != null && candidate.getUUID().equals(entity.getUUID()))) {
				return hand;
			}
		}
		return null;
	}

	public static @Nullable GiantHand firstUnreservedHand(final Giant giant) {
		for (GiantHand hand : GiantHand.values()) {
			if (payloadCandidate(giant, hand) == null
				&& access(giant).mobsthinknow$getPayloadUuid(hand) == null) {
				return hand;
			}
		}
		return null;
	}

	public static boolean hasPayloadReservation(final Giant giant, final GiantHand hand) {
		GiantTacticsAccess access = access(giant);
		return access.mobsthinknow$getPayloadUuid(hand) != null
			|| access.mobsthinknow$getPayloadEntityId(hand) != 0;
	}

	public static GiantHandPhase handPhase(final Giant giant, final GiantHand hand) {
		return access(giant).mobsthinknow$getHandPhase(hand);
	}

	public static void transitionHand(final Giant giant, final GiantHand hand, final GiantHandPhase phase) {
		GiantTacticsAccess access = access(giant);
		access.mobsthinknow$setHandPhase(hand, phase);
		access.mobsthinknow$setHandPhaseStartTick(hand, currentTick(giant));
	}

	public static int handPhaseTicks(final Giant giant, final GiantHand hand) {
		return elapsedTicks(currentTick(giant), access(giant).mobsthinknow$getHandPhaseStartTick(hand));
	}

	public static float handPhaseProgress(final Giant giant, final GiantHand hand, final float partialTicks) {
		GiantHandPhase phase = handPhase(giant, hand);
		float elapsed = elapsedTicks(currentTick(giant), access(giant).mobsthinknow$getHandPhaseStartTick(hand))
			+ partialTicks;
		return Mth.clamp(elapsed / phase.nominalDurationTicks(), 0.0F, 1.0F);
	}

	public static void beginBoarding(final Giant giant, final AbstractSkeleton rider) {
		GiantTacticsAccess access = access(giant);
		access.mobsthinknow$setBoardingRiderUuid(rider.getUUID());
		access.mobsthinknow$setBoardingRiderEntityId(rider.getId());
		transitionBoarding(giant, GiantBoardingPhase.CATCHING);
	}

	public static void clearBoarding(final Giant giant) {
		GiantTacticsAccess access = access(giant);
		access.mobsthinknow$setBoardingRiderUuid(null);
		access.mobsthinknow$setBoardingRiderEntityId(0);
		transitionBoarding(giant, GiantBoardingPhase.NONE);
	}

	public static @Nullable AbstractSkeleton boardingRider(final Giant giant) {
		GiantTacticsAccess access = access(giant);
		int entityId = access.mobsthinknow$getBoardingRiderEntityId();
		Entity entity = entityId == 0 ? null : giant.level().getEntity(entityId);
		if (entity instanceof AbstractSkeleton skeleton && matchesBoarder(access, skeleton)) {
			return skeleton;
		}
		if (giant.level() instanceof ServerLevel level) {
			UUID uuid = access.mobsthinknow$getBoardingRiderUuid();
			Entity byUuid = uuid == null ? null : level.getEntity(uuid);
			if (byUuid instanceof AbstractSkeleton skeleton && matchesBoarder(access, skeleton)) {
				access.mobsthinknow$setBoardingRiderEntityId(skeleton.getId());
				return skeleton;
			}
		}
		return null;
	}

	public static boolean isBoardingRider(final Giant giant, final Entity entity) {
		AbstractSkeleton rider = boardingRider(giant);
		return rider == entity;
	}

	public static GiantBoardingPhase boardingPhase(final Giant giant) {
		return access(giant).mobsthinknow$getBoardingPhase();
	}

	public static void transitionBoarding(final Giant giant, final GiantBoardingPhase phase) {
		GiantTacticsAccess access = access(giant);
		access.mobsthinknow$setBoardingPhase(phase);
		access.mobsthinknow$setBoardingPhaseStartTick(currentTick(giant));
	}

	public static int boardingPhaseTicks(final Giant giant) {
		return elapsedTicks(currentTick(giant), access(giant).mobsthinknow$getBoardingPhaseStartTick());
	}

	public static float boardingProgress(final Giant giant, final float partialTicks) {
		GiantBoardingPhase phase = boardingPhase(giant);
		float elapsed = elapsedTicks(currentTick(giant), access(giant).mobsthinknow$getBoardingPhaseStartTick())
			+ partialTicks;
		return Mth.clamp(elapsed / phase.durationTicks(), 0.0F, 1.0F);
	}

	/**
	 * 服务端真实性校验。固定槽优先；旧世界中尚无 UUID 的直接乘客按空闲手顺序迁移一次。
	 */
	public static void reconcile(final Giant giant) {
		if (!(giant.level() instanceof ServerLevel)) {
			return;
		}
		GiantTacticsAccess access = access(giant);
		List<Entity> assigned = new ArrayList<>(GiantPassengerLayout.MAXIMUM_PAYLOADS);
		for (GiantHand hand : GiantHand.values()) {
			Entity candidate = payloadCandidate(giant, hand);
			GiantHandPhase phase = handPhase(giant, hand);
			if (candidate == null || !candidate.isAlive() || !GiantPassengerLayout.isPayload(candidate)) {
				if (access.mobsthinknow$getPayloadUuid(hand) != null
					&& phase != GiantHandPhase.THROWING
					&& phase != GiantHandPhase.COOLDOWN) {
					resetHand(giant, hand);
				}
				continue;
			}
			assigned.add(candidate);
			if (candidate.getVehicle() == giant) {
				access.mobsthinknow$setPayloadEntityId(hand, candidate.getId());
				if (phase == GiantHandPhase.EMPTY
					|| phase == GiantHandPhase.RENDEZVOUS) {
					transitionHand(giant, hand, GiantHandPhase.HOLDING);
				}
			} else if (phase == GiantHandPhase.HOLDING || phase == GiantHandPhase.AIMING) {
				resetHand(giant, hand);
			}
		}

		for (Entity passenger : giant.getPassengers()) {
			if (!GiantPassengerLayout.isPayload(passenger) || assigned.contains(passenger)) {
				continue;
			}
			GiantHand free = firstUnreservedHand(giant);
			if (free == null) {
				break;
			}
			assignPayload(giant, free, passenger);
			transitionHand(giant, free, GiantHandPhase.HOLDING);
			assigned.add(passenger);
		}

		GiantBoardingPhase boardingPhase = boardingPhase(giant);
		AbstractSkeleton rider = boardingRider(giant);
		if (boardingPhase != GiantBoardingPhase.NONE
			&& (rider == null || !rider.isAlive()
				|| (boardingPhase != GiantBoardingPhase.CATCHING && rider.getVehicle() != giant))) {
			clearBoarding(giant);
		}
	}

	private static boolean isMatchingPayload(final @Nullable UUID expected, final @Nullable Entity entity) {
		return entity != null
			&& GiantPassengerLayout.isPayload(entity)
			&& (expected == null || expected.equals(entity.getUUID()));
	}

	private static boolean matchesBoarder(final GiantTacticsAccess access, final AbstractSkeleton skeleton) {
		UUID expected = access.mobsthinknow$getBoardingRiderUuid();
		return expected == null || expected.equals(skeleton.getUUID());
	}

	private static int currentTick(final Giant giant) {
		return (int)giant.level().getGameTime();
	}

	private static int elapsedTicks(final int current, final int start) {
		return Math.max(0, current - start);
	}

	private static GiantTacticsAccess access(final Giant giant) {
		return (GiantTacticsAccess)giant;
	}
}

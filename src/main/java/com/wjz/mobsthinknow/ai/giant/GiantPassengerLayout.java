package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 巨人挂点的唯一布局定义：头顶一名射手，固定左右手各一名投掷载荷，以及格斗期间的瞬时抓取位。
 *
 * <p>左右手优先读取同步的实体槽位，而不是 passenger list 下标。旧存档尚未完成迁移时，
 * 才会把未绑定的真实乘客临时补入空手；服务端下一次 reconcile 会将其写回固定 UUID 槽。</p>
 */
public final class GiantPassengerLayout {
	public static final int MAXIMUM_PAYLOADS = 2;
	private static final double HEAD_FEET_HEIGHT = 12.0;
	private static final double HAND_FEET_HEIGHT = 5.75;
	private static final double HAND_SIDE_OFFSET = 2.75;
	private static final double HAND_FORWARD_OFFSET = 2.05;

	private static final Vec3 BOARDING_LOW_PALM = new Vec3(-2.75, 2.25, 2.35);
	private static final Vec3 BOARDING_SHOULDER = new Vec3(-1.70, 8.35, 0.55);
	private static final Vec3 BOARDING_HEAD = new Vec3(0.0, HEAD_FEET_HEIGHT, 0.0);

	private GiantPassengerLayout() {
	}

	public static @Nullable AbstractSkeleton headRider(final Giant giant) {
		for (Entity passenger : giant.getPassengers()) {
			if (isHeadRider(passenger) && !GiantTacticsState.isGrappledTarget(giant, passenger)) {
				return (AbstractSkeleton)passenger;
			}
		}
		return null;
	}

	public static List<LivingPayload> payloads(final Giant giant) {
		List<LivingPayload> result = new ArrayList<>(MAXIMUM_PAYLOADS);
		EnumSet<GiantHand> occupied = EnumSet.noneOf(GiantHand.class);
		List<Entity> assignedEntities = new ArrayList<>(MAXIMUM_PAYLOADS);
		GiantHand grappleHand = GiantTacticsState.grappleHand(giant);
		if (GiantTacticsState.hasGrappleReservation(giant)) {
			if (grappleHand == null) {
				occupied.addAll(EnumSet.allOf(GiantHand.class));
			} else {
				occupied.add(grappleHand);
			}
		}
		if (GiantTacticsState.boardingPhase(giant) != GiantBoardingPhase.NONE) {
			occupied.add(GiantHand.RIGHT);
		}

		for (GiantHand hand : GiantHand.values()) {
			Entity entity = GiantTacticsState.payloadForHand(giant, hand);
			if (entity != null) {
				result.add(new LivingPayload(entity, hand));
				occupied.add(hand);
				assignedEntities.add(entity);
			}
		}

		// 兼容还没跑到服务端 reconcile 的旧存档以及测试直接 startRiding 的瞬间。
		for (Entity passenger : giant.getPassengers()) {
			if (!isPayload(passenger)
				|| GiantTacticsState.isGrappledTarget(giant, passenger)
				|| assignedEntities.contains(passenger)) {
				continue;
			}
			GiantHand free = firstFree(occupied);
			if (free == null) {
				break;
			}
			result.add(new LivingPayload(passenger, free));
			occupied.add(free);
		}
		result.sort(java.util.Comparator.comparingInt(payload -> payload.hand().index()));
		return List.copyOf(result);
	}

	public static @Nullable LivingPayload payload(final Giant giant, final Entity entity) {
		for (LivingPayload payload : payloads(giant)) {
			if (payload.entity() == entity) {
				return payload;
			}
		}
		return null;
	}

	public static boolean isManagedPassenger(final Entity entity) {
		return isHeadRider(entity) || isPayload(entity);
	}

	public static boolean isHeadRider(final Entity entity) {
		return entity instanceof AbstractSkeleton && OverworldUndeadFamilies.isSkeletonFamily(entity);
	}

	public static boolean isPayload(final Entity entity) {
		return entity instanceof Creeper
			|| (entity instanceof Zombie && OverworldUndeadFamilies.isZombieFamily(entity));
	}

	public static boolean hasFreeHeadSeat(final Giant giant) {
		return headRider(giant) == null && GiantTacticsState.boardingRider(giant) == null;
	}

	public static boolean canAcceptHeadRider(final Giant giant, final Entity passenger) {
		AbstractSkeleton tracked = GiantTacticsState.boardingRider(giant);
		return headRider(giant) == null && (tracked == null || tracked == passenger);
	}

	public static boolean hasFreeHand(final Giant giant) {
		return GiantTacticsState.firstUnreservedHand(giant) != null;
	}

	/** 返回传给原版 positionRider 的世界坐标挂点。 */
	public static Vec3 ridingPosition(final Giant giant, final Entity passenger) {
		if (isHeadRider(passenger)) {
			Vec3 feet = boardingFeetPosition(giant, passenger);
			return feet.add(passenger.getVehicleAttachmentPoint(giant));
		}
		LivingPayload payload = payload(giant, passenger);
		if (payload == null) {
			return giant.position();
		}
		/*
		 * positionRider 随后会减去乘员自己的 VEHICLE attachment。这里按具体实体加回该偏移，
		 * Zombie 和 Creeper 的脚底最终才会真正落在同一个掌心高度。
		 */
		return handPosition(giant, payload.hand()).add(passenger.getVehicleAttachmentPoint(giant));
	}

	/** 抓取目标从低位接触点抬到掌心，再沿锁定方向送到抛出点。 */
	public static Vec3 grappleRidingPosition(
		final Giant giant,
		final Entity passenger,
		final GiantHand hand
	) {
		double side = hand == GiantHand.RIGHT ? -2.35 : 2.35;
		Vec3 caught = new Vec3(side, 2.75, 2.85);
		Vec3 held = new Vec3(side * 1.12, 5.55, 2.10);
		Vec3 drawn = held.add(0.0, 0.35, -0.72);
		Vec3 release = new Vec3(side * 0.72, 5.15, 3.05);
		double progress = GiantTacticsState.meleeProgress(giant, 0.0F);
		Vec3 local;
		if (progress < 0.30) {
			local = caught;
		} else if (progress < 0.50) {
			local = lerp(caught, held, smooth((progress - 0.30) / 0.20));
		} else if (progress < 0.59) {
			local = lerp(held, drawn, smooth((progress - 0.50) / 0.09));
		} else {
			local = lerp(drawn, release, smooth((progress - 0.59) / 0.08));
		}
		return localToWorld(giant, local).add(passenger.getVehicleAttachmentPoint(giant));
	}

	/**
	 * 左右手载荷的脚底位置随巨人的身体朝向旋转；抛投也从完全相同的点离手。
	 */
	public static Vec3 handPosition(final Giant giant, final GiantHand hand) {
		double side = hand == GiantHand.RIGHT ? -HAND_SIDE_OFFSET : HAND_SIDE_OFFSET;
		Vec3 base = new Vec3(side, HAND_FEET_HEIGHT, HAND_FORWARD_OFFSET);
		GiantHandPhase phase = GiantTacticsState.handPhase(giant, hand);
		double progress = GiantTacticsState.handPhaseProgress(giant, hand, 0.0F);
		Vec3 local = switch (phase) {
			case PICKUP -> {
				Vec3 lowPalm = new Vec3(side, 2.25, 2.35);
				yield progress <= 0.52
					? lowPalm
					: lerp(lowPalm, base, smooth((progress - 0.52) / 0.48));
			}
			case AIMING -> lerp(base, base.add(0.0, 0.48, -0.92), smooth(progress));
			case THROWING -> {
				Vec3 aimed = base.add(0.0, 0.48, -0.92);
				Vec3 forward = base.add(0.0, 0.22, 0.78);
				yield progress < 0.55
					? lerp(aimed, forward, smooth(progress / 0.55))
					: lerp(forward, base, smooth((progress - 0.55) / 0.45));
			}
			default -> base;
		};
		return localToWorld(giant, local);
	}

	/** 兼容测试与旧调用点的数字索引。 */
	public static Vec3 handPosition(final Giant giant, final int handIndex) {
		return handPosition(giant, GiantHand.fromIndex(handIndex));
	}

	/**
	 * 射手一旦被接住就不再瞬移：先位于放低的右掌，再平滑举到肩部，短暂停顿后移到头顶。
	 */
	public static Vec3 boardingFeetPosition(final Giant giant, final Entity passenger) {
		if (!GiantTacticsState.isBoardingRider(giant, passenger)) {
			return localToWorld(giant, BOARDING_HEAD);
		}
		GiantBoardingPhase phase = GiantTacticsState.boardingPhase(giant);
		double progress = GiantTacticsState.boardingProgress(giant, 0.0F);
		Vec3 local = switch (phase) {
			case CATCHING -> BOARDING_LOW_PALM;
			case LIFTING -> lerp(BOARDING_LOW_PALM, BOARDING_SHOULDER, smooth(progress));
			case SHOULDER -> BOARDING_SHOULDER;
			case TO_HEAD -> progress < 0.65
				? lerp(BOARDING_SHOULDER, BOARDING_HEAD, smooth(progress / 0.65))
				: BOARDING_HEAD;
			case NONE -> BOARDING_HEAD;
		};
		return localToWorld(giant, local);
	}

	private static @Nullable GiantHand firstFree(final EnumSet<GiantHand> occupied) {
		for (GiantHand hand : GiantHand.values()) {
			if (!occupied.contains(hand)) {
				return hand;
			}
		}
		return null;
	}

	private static Vec3 localToWorld(final Giant giant, final Vec3 local) {
		float yawRadians = -giant.yBodyRot * Mth.DEG_TO_RAD;
		return giant.position().add(local.yRot(yawRadians));
	}

	private static Vec3 lerp(final Vec3 from, final Vec3 to, final double progress) {
		return from.add(to.subtract(from).scale(progress));
	}

	private static double smooth(final double value) {
		double clamped = Mth.clamp(value, 0.0, 1.0);
		return clamped * clamped * (3.0 - 2.0 * clamped);
	}

	public record LivingPayload(Entity entity, GiantHand hand) {
		public int handIndex() {
			return this.hand.index();
		}
	}
}

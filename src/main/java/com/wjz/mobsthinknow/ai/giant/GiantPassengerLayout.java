package com.wjz.mobsthinknow.ai.giant;

import java.util.ArrayList;
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
 * 巨人三个战术挂点的唯一布局定义：头顶一名射手，左右手各一名投掷载荷。
 *
 * <p>乘员关系本身由原版同步与存档；这里仅按实体种类解释直接乘员，避免依赖易变的
 * passenger list 下标。这样抛掉右手载荷后，左手载荷也不会突然跳到另一侧。</p>
 */
public final class GiantPassengerLayout {
	public static final int MAXIMUM_PAYLOADS = 2;
	// Skeleton 的原版 VEHICLE attachment 在脚底上方 0.7 格；加回该偏移后脚底恰好落在 12 格高的巨人头顶。
	private static final double HEAD_HEIGHT = 12.70;
	private static final double HAND_HEIGHT = 5.75;
	private static final double HAND_SIDE_OFFSET = 2.75;
	private static final double HAND_FORWARD_OFFSET = 2.05;

	private GiantPassengerLayout() {
	}

	public static @Nullable AbstractSkeleton headRider(final Giant giant) {
		for (Entity passenger : giant.getPassengers()) {
			if (isHeadRider(passenger)) {
				return (AbstractSkeleton)passenger;
			}
		}
		return null;
	}

	public static List<LivingPayload> payloads(final Giant giant) {
		List<LivingPayload> result = new ArrayList<>(MAXIMUM_PAYLOADS);
		for (Entity passenger : giant.getPassengers()) {
			if (isPayload(passenger)) {
				result.add(new LivingPayload(passenger, result.size()));
				if (result.size() == MAXIMUM_PAYLOADS) {
					break;
				}
			}
		}
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
		return entity instanceof AbstractSkeleton;
	}

	public static boolean isPayload(final Entity entity) {
		return entity instanceof Creeper || entity instanceof Zombie;
	}

	public static boolean hasFreeHeadSeat(final Giant giant) {
		return headRider(giant) == null;
	}

	public static boolean hasFreeHand(final Giant giant) {
		return payloads(giant).size() < MAXIMUM_PAYLOADS;
	}

	/** 返回传给原版 positionRider 的世界坐标挂点。 */
	public static Vec3 ridingPosition(final Giant giant, final Entity passenger) {
		if (isHeadRider(passenger)) {
			return giant.position().add(0.0, HEAD_HEIGHT, 0.0);
		}
		LivingPayload payload = payload(giant, passenger);
		if (payload == null) {
			return giant.position();
		}
		return handPosition(giant, payload.handIndex());
	}

	/**
	 * 左右手挂点随巨人的身体朝向旋转。索引 0 固定为巨人右手，索引 1 固定为左手。
	 */
	public static Vec3 handPosition(final Giant giant, final int handIndex) {
		double side = handIndex == 0 ? -HAND_SIDE_OFFSET : HAND_SIDE_OFFSET;
		float yawRadians = -giant.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 local = new Vec3(side, HAND_HEIGHT, HAND_FORWARD_OFFSET).yRot(yawRadians);
		return giant.position().add(local);
	}

	public record LivingPayload(Entity entity, int handIndex) {
	}
}

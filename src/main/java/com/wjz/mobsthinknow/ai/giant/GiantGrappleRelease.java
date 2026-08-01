package com.wjz.mobsthinknow.ai.giant;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 抓取结束时的碰撞安全释放点搜索器。
 *
 * <p>优先保留动画掌心位置，再沿攻击方向、动作手外侧及相反方向做小范围搜索；掌心完全被方块
 * 包围时，才退回原版下车点或巨人脚边的八向安全环。搜索仅在释放瞬间执行，不进入常驻 tick 热路径。</p>
 */
public final class GiantGrappleRelease {
	private static final double[] LOCAL_RADII = {0.72, 1.44, 2.16};
	private static final double[] LOCAL_HEIGHTS = {0.0, 0.72, -0.72, 1.44, -1.44};
	private static final double[] GROUND_HEIGHTS = {0.10, 0.85, 1.60, 2.35};
	private static final double EPSILON = 1.0E-8;

	private GiantGrappleRelease() {
	}

	public static Vec3 find(
		final Giant giant,
		final LivingEntity passenger,
		final Vec3 desired,
		final AABB heldBounds,
		final Vec3 attackForward,
		final @Nullable GiantHand hand
	) {
		Vec3 vanillaDismount = passenger.position();
		Vec3 forward = horizontalUnit(attackForward, bodyForward(giant));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 outward = hand == GiantHand.LEFT ? right.scale(-1.0) : right;
		List<Vec3> directions = List.of(
			forward,
			outward,
			outward.scale(-1.0),
			forward.scale(-1.0),
			horizontalUnit(forward.add(outward), forward),
			horizontalUnit(forward.subtract(outward), forward),
			horizontalUnit(forward.scale(-1.0).add(outward), outward),
			horizontalUnit(forward.scale(-1.0).subtract(outward), outward.scale(-1.0))
		);

		for (double height : LOCAL_HEIGHTS) {
			Vec3 elevated = desired.add(0.0, height, 0.0);
			if (safe(giant, passenger, desired, heldBounds, elevated)) {
				return elevated;
			}
			for (double radius : LOCAL_RADII) {
				for (Vec3 direction : directions) {
					Vec3 candidate = elevated.add(direction.scale(radius));
					if (safe(giant, passenger, desired, heldBounds, candidate)) {
						return candidate;
					}
				}
			}
		}
		if (vanillaDismount.distanceToSqr(desired) > EPSILON
			&& safe(giant, passenger, desired, heldBounds, vanillaDismount)) {
			return vanillaDismount;
		}

		double ringRadius = giant.getBbWidth() * 0.5 + passenger.getBbWidth() * 0.5 + 0.65;
		for (double height : GROUND_HEIGHTS) {
			Vec3 ringCenter = giant.position().add(0.0, height, 0.0);
			for (Vec3 direction : directions) {
				Vec3 candidate = ringCenter.add(direction.scale(ringRadius));
				if (safe(giant, passenger, desired, heldBounds, candidate)) {
					return candidate;
				}
			}
		}
		// 极端封闭空间没有严格安全点时，保留 stopRiding 已选择的位置，绝不主动塞回原掌心墙体。
		return vanillaDismount;
	}

	private static boolean safe(
		final Giant giant,
		final LivingEntity passenger,
		final Vec3 desired,
		final AABB heldBounds,
		final Vec3 candidate
	) {
		AABB dismountedDestination = passenger.getBoundingBox().move(candidate.subtract(passenger.position()));
		AABB heldDestination = heldBounds.move(candidate.subtract(desired));
		return safeBounds(giant, passenger, dismountedDestination)
			&& safeBounds(giant, passenger, heldDestination);
	}

	/**
	 * 验证同一个候选点上的单套碰撞箱。抓取目标下车时可能刷新姿态，因此调用方会同时传入
	 * 下车前的掌心碰撞箱与下车后的实体碰撞箱，避免姿态变化把真实的墙体卡位“洗掉”。
	 */
	private static boolean safeBounds(final Giant giant, final LivingEntity passenger, final AABB destination) {
		return giant.level().getWorldBorder().isWithinBounds(destination)
			&& !destination.intersects(giant.getBoundingBox().inflate(0.05))
			&& giant.level().noCollision(passenger, destination);
	}

	private static Vec3 bodyForward(final Giant giant) {
		double yaw = Math.toRadians(giant.yBodyRot);
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	private static Vec3 horizontalUnit(final Vec3 value, final Vec3 fallback) {
		Vec3 horizontal = value.multiply(1.0, 0.0, 1.0);
		return horizontal.lengthSqr() > EPSILON ? horizontal.normalize() : fallback;
	}
}

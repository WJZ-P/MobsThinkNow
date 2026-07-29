package com.wjz.mobsthinknow.ai.utility;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * 僵尸受击撤退与骷髅贴脸逃跑共享的陆地逃生规划。
 *
 * <p>这里仅负责选择“确实比当前位置更远”的落点和朝向当前路径；触发条件、持续时间、
 * 速度以及逃跑结束后的战斗恢复仍由各兵种自己的 Goal 决定。这样两种怪物复用相同的
 * 安全落点算法，又不会把骷髅的持弓拉扯与真正逃跑混成同一个状态。</p>
 */
public final class EscapePathing {
	private static final double MINIMUM_HORIZONTAL_GAIN_SQUARED = 1.0;

	private EscapePathing() {
	}

	/**
	 * 优先使用原版陆地点采样；没有合格候选时退化为严格背向威胁的直线落点。
	 */
	public static Vec3 findDestinationAwayFrom(
		final PathfinderMob mob,
		final LivingEntity threat,
		final double minimumDistance,
		final double maximumDistance,
		final int verticalSearch
	) {
		Vec3 candidate = LandRandomPos.getPosAway(
			mob,
			minimumDistance,
			maximumDistance,
			verticalSearch,
			threat.position()
		);
		double currentDistanceSquared = horizontalDistanceSquared(mob.position(), threat.position());
		if (candidate != null
			&& horizontalDistanceSquared(candidate, threat.position())
				> currentDistanceSquared + MINIMUM_HORIZONTAL_GAIN_SQUARED) {
			return candidate;
		}

		Vec3 away = horizontalAwayDirection(
			mob.position(),
			threat.position(),
			threat.getLookAngle()
		);
		return mob.position().add(away.scale(minimumDistance));
	}

	/** 让模型的头、身体和移动朝向都对准路径下一节点，而不是继续盯着身后的攻击者。 */
	public static void faceCurrentPathOrDestination(final PathfinderMob mob, final Vec3 destination) {
		Path path = mob.getNavigation().getPath();
		Vec3 focus = destination;
		if (path != null && !path.isDone()) {
			// 新路径的首节点经常就是怪物脚下；跳过零水平位移节点，确保起跑第一拍也会真正转身。
			for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
				Vec3 node = path.getEntityPosAtNode(mob, index);
				double x = node.x - mob.getX();
				double z = node.z - mob.getZ();
				if (x * x + z * z >= 1.0E-6) {
					focus = node;
					break;
				}
			}
		}
		faceTravelPoint(mob, focus);
	}

	/** 立即同步逃跑朝向；逃跑是正向奔跑，因此不保留战斗时的目标锁定姿态。 */
	public static void faceTravelPoint(final PathfinderMob mob, final Vec3 focus) {
		double x = focus.x - mob.getX();
		double z = focus.z - mob.getZ();
		if (x * x + z * z < 1.0E-6) {
			return;
		}

		float yaw = (float)(Mth.atan2(z, x) * 180.0F / Math.PI) - 90.0F;
		mob.setYRot(yaw);
		mob.setYBodyRot(yaw);
		mob.setYHeadRot(yaw);
		mob.getLookControl().setLookAt(focus.x, focus.y + 1.0, focus.z, 90.0F, 90.0F);
	}

	static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	/** 计算只含水平分量的单位逃离方向；位置重合时使用威胁视线作为稳定退化方向。 */
	public static Vec3 horizontalAwayDirection(final Vec3 origin, final Vec3 threat, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(origin.x - threat.x, 0.0, origin.z - threat.z);
		if (horizontal.horizontalDistanceSqr() < 1.0E-6) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}
		if (horizontal.horizontalDistanceSqr() < 1.0E-6) {
			return new Vec3(0.0, 0.0, 1.0);
		}
		return horizontal.normalize();
	}
}

package com.wjz.mobsthinknow.ai.spider;

import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * 高智力蜘蛛的纯几何蛛网伏击规划器。
 *
 * <p>这里只根据已知位置、速度和视线方向生成一个很短的候选序列，不查询世界、不寻路，也不修改方块。
 * Goal 因而最多检查五个落点，不会随着附近蜘蛛或玩家数量增长成平方复杂度。</p>
 */
public final class SpiderWebTrapPlanner {
	public static final int MINIMUM_INTELLIGENCE = 7;
	private static final double MINIMUM_TARGET_DISTANCE_SQUARED = 3.25 * 3.25;
	private static final double MAXIMUM_TARGET_DISTANCE_SQUARED = 9.0 * 9.0;
	private static final double MAXIMUM_HORIZONTAL_LEAD = 3.25;
	private static final double DODGE_LANE_OFFSET = 0.82;

	private SpiderWebTrapPlanner() {
	}

	public static boolean canPlan(
		final int intelligence,
		final boolean targetVisible,
		final boolean spiderOnGround,
		final boolean carryingPassenger,
		final double targetDistanceSquared
	) {
		return intelligence >= MINIMUM_INTELLIGENCE
			&& targetVisible
			&& spiderOnGround
			&& !carryingPassenger
			&& targetDistanceSquared >= MINIMUM_TARGET_DISTANCE_SQUARED
			&& targetDistanceSquared <= MAXIMUM_TARGET_DISTANCE_SQUARED;
	}

	/**
	 * 速度足够明显时追踪真实速度；目标暂时停步时则把视线方向当作下一步意图，但只前探不到一格。
	 */
	public static Vec3 predictedPosition(
		final Vec3 targetPosition,
		final Vec3 targetVelocity,
		final Vec3 targetLook,
		final int intelligence
	) {
		int clampedIntelligence = Math.max(MINIMUM_INTELLIGENCE, Math.min(10, intelligence));
		Vec3 horizontalVelocity = new Vec3(targetVelocity.x, 0.0, targetVelocity.z);
		Vec3 intent;
		if (horizontalVelocity.horizontalDistanceSqr() >= 0.0025) {
			intent = horizontalVelocity.scale(4.5 + (clampedIntelligence - MINIMUM_INTELLIGENCE) * 0.75);
		} else {
			Vec3 horizontalLook = new Vec3(targetLook.x, 0.0, targetLook.z);
			intent = horizontalLook.lengthSqr() < 1.0E-6
				? Vec3.ZERO
				: horizontalLook.normalize().scale(0.62 + (clampedIntelligence - MINIMUM_INTELLIGENCE) * 0.08);
		}
		if (intent.horizontalDistanceSqr() > MAXIMUM_HORIZONTAL_LEAD * MAXIMUM_HORIZONTAL_LEAD) {
			intent = intent.normalize().scale(MAXIMUM_HORIZONTAL_LEAD);
		}
		return targetPosition.add(intent.x, 0.0, intent.z);
	}

	/**
	 * 中线优先，其次覆盖左右闪避道，最后检查前后两格；稳定侧只改变左右候选的先后，不改变覆盖范围。
	 */
	public static List<Vec3> candidateCenters(
		final Vec3 targetPosition,
		final Vec3 predictedPosition,
		final Vec3 targetLook,
		final int stableSide
	) {
		Vec3 heading = predictedPosition.subtract(targetPosition).multiply(1.0, 0.0, 1.0);
		if (heading.horizontalDistanceSqr() < 1.0E-6) {
			heading = new Vec3(targetLook.x, 0.0, targetLook.z);
		}
		if (heading.horizontalDistanceSqr() < 1.0E-6) {
			heading = new Vec3(1.0, 0.0, 0.0);
		} else {
			heading = heading.normalize();
		}
		Vec3 side = new Vec3(-heading.z, 0.0, heading.x).scale(stableSide < 0 ? -1.0 : 1.0);
		return List.of(
			predictedPosition,
			predictedPosition.add(side.scale(DODGE_LANE_OFFSET)),
			predictedPosition.add(side.scale(-DODGE_LANE_OFFSET)),
			predictedPosition.add(heading.scale(0.78)),
			predictedPosition.add(heading.scale(-0.72))
		);
	}

	/** 智力和难度只小幅压缩冷却；配置值仍是决定世界蛛网密度的主上限。 */
	public static int cooldownTicks(
		final int configuredTicks,
		final int intelligence,
		final int difficultyId,
		final int randomExtraTicks
	) {
		int skillReduction = Math.max(0, Math.min(3, intelligence - MINIMUM_INTELLIGENCE)) * 8;
		int difficultyReduction = Math.max(0, Math.min(3, difficultyId)) * 4;
		return Math.max(60, configuredTicks - skillReduction - difficultyReduction + Math.max(0, randomExtraTicks));
	}
}

package com.wjz.mobsthinknow.ai.zombie.squad;

/**
 * 把成员到当前阵位的距离映射为离散、封顶的追赶速度加成。
 *
 * <p>离散档位让属性修饰器只在跨越阈值时变化，而不是随着每一格移动在每个 tick 重建。
 * 基础小队加速为零时，追赶也随之关闭，继续尊重原有配置开关。</p>
 */
public final class SquadCohesionPacing {
	private static final double FIRST_CATCH_UP_DISTANCE_SQUARED = 8.0 * 8.0;
	private static final double SECOND_CATCH_UP_DISTANCE_SQUARED = 14.0 * 14.0;
	private static final double MAXIMUM_CATCH_UP_DISTANCE_SQUARED = 20.0 * 20.0;
	private static final double FIRST_CATCH_UP_BONUS = 0.05;
	private static final double SECOND_CATCH_UP_BONUS = 0.10;
	private static final double MAXIMUM_CATCH_UP_BONUS = 0.15;
	private static final double MAXIMUM_TOTAL_BONUS = 0.50;

	private SquadCohesionPacing() {
	}

	/**
	 * @param configuredBaseBonus 已有的小队全员速度加成
	 * @param cohesionOrderActive 当前是否正在执行集结或持阵命令
	 * @param distanceToDestinationSquared 成员到当前阵位的距离平方
	 * @return 应写入临时移动速度修饰器的最终值
	 */
	public static double speedBonus(
		final double configuredBaseBonus,
		final boolean cohesionOrderActive,
		final double distanceToDestinationSquared
	) {
		if (!Double.isFinite(configuredBaseBonus)) {
			return 0.0;
		}
		double baseBonus = Math.clamp(configuredBaseBonus, 0.0, MAXIMUM_TOTAL_BONUS);
		if (baseBonus == 0.0
			|| !cohesionOrderActive
			|| !Double.isFinite(distanceToDestinationSquared)
			|| distanceToDestinationSquared < FIRST_CATCH_UP_DISTANCE_SQUARED) {
			return baseBonus;
		}

		double catchUpBonus;
		if (distanceToDestinationSquared >= MAXIMUM_CATCH_UP_DISTANCE_SQUARED) {
			catchUpBonus = MAXIMUM_CATCH_UP_BONUS;
		} else if (distanceToDestinationSquared >= SECOND_CATCH_UP_DISTANCE_SQUARED) {
			catchUpBonus = SECOND_CATCH_UP_BONUS;
		} else {
			catchUpBonus = FIRST_CATCH_UP_BONUS;
		}
		return Math.min(MAXIMUM_TOTAL_BONUS, baseBonus + catchUpBonus);
	}
}

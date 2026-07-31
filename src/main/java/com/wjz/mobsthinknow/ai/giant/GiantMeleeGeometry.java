package com.wjz.mobsthinknow.ai.giant;

/**
 * 巨人格斗命中区域的纯几何定义。
 *
 * <p>参数已经被调用方投影到巨人的局部水平坐标：forward 为面朝方向，side 为右侧方向，
 * vertical 为相对脚底高度。把形状从实体查询中拆出来后，边界可以用普通 JUnit 精确锁定。</p>
 */
public final class GiantMeleeGeometry {
	private GiantMeleeGeometry() {
	}

	public static boolean contains(
		final GiantMeleeAction action,
		final double forward,
		final double side,
		final double vertical
	) {
		if (!action.isActive()) {
			return false;
		}
		return switch (action.family()) {
			case NONE -> false;
			case SWEEP -> {
				double horizontalSquared = forward * forward + side * side;
				yield horizontalSquared >= 0.45 * 0.45
					&& horizontalSquared <= 7.25 * 7.25
					&& forward >= -1.20
					&& vertical >= -2.50
					&& vertical <= 4.75;
			}
			case SLAP -> forward >= 0.35
				&& forward <= 5.80
				&& Math.abs(side) <= 2.40
				&& vertical >= -2.50
				&& vertical <= 4.75;
			case STOMP -> forward * forward + side * side <= 4.20 * 4.20
				&& vertical >= -2.00
				&& vertical <= 2.80;
			case GROUND_SMASH -> {
				double fromImpactForward = forward - 3.25;
				yield fromImpactForward * fromImpactForward + side * side <= 4.40 * 4.40
					&& forward >= -0.60
					&& vertical >= -2.50
					&& vertical <= 3.50;
			}
		};
	}
}

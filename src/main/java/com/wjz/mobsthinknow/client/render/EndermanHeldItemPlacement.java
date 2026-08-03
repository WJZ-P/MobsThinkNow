package com.wjz.mobsthinknow.client.render;

/** 计算末影人长臂与原版人形手持物挂点之间的局部坐标补偿。 */
public final class EndermanHeldItemPlacement {
	private static final float MODEL_PIXELS_PER_BLOCK = 16.0F;
	private static final float HUMANOID_HAND_END_Y = 10.0F;
	private static final float ENDERMAN_HAND_END_Y = 28.0F;

	private EndermanHeldItemPlacement() {
	}

	/**
	 * 原版手持物层按普通人形手臂末端 {@code Y=10px} 放置物品；末影人手臂实际延伸到
	 * {@code Y=28px}。武器需要补足二者的 18px 差值，盾牌则继续贴在前臂上。
	 *
	 * @param forearmMounted 是否属于盾牌一类的前臂挂载装备
	 * @return 在手臂已经完成旋转后的局部 Y 轴偏移
	 */
	public static float localArmYOffset(final boolean forearmMounted) {
		return forearmMounted
			? 0.0F
			: (ENDERMAN_HAND_END_Y - HUMANOID_HAND_END_Y) / MODEL_PIXELS_PER_BLOCK;
	}
}

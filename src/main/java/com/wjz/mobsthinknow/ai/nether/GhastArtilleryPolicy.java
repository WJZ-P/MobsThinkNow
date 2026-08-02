package com.wjz.mobsthinknow.ai.nether;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;

/** 恶魂炮兵的纯策略边界；单元测试不需要伪造服务器玩家列表。 */
public final class GhastArtilleryPolicy {
	public static final double EXPANDED_VERTICAL_RANGE = 16.0;

	private GhastArtilleryPolicy() {
	}

	public static boolean enabled(final MobsThinkNowConfig config) {
		return config.enabled && config.netherAiEnabled && config.ghastArtilleryTactics;
	}

	public static boolean withinVerticalBand(final double ghastY, final double targetY) {
		return Math.abs(targetY - ghastY) <= EXPANDED_VERTICAL_RANGE;
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 僵尸专用的真实承重与跨沟几何规则。 */
public final class ZombieTraversalRules {
	private ZombieTraversalRules() {
	}

	public static boolean isEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.zombieAiEnabled && config.smartTraversal;
	}

	/**
	 * 门、栅栏门、活板门等开放后若顶面已经没有真实承重碰撞，就不能继续被当作落脚方块。
	 * 这里读取通用 OPEN 属性，因此其他 Mod 的标准可开关方块也自动受益。
	 */
	public static boolean isUnsafeOpenableSupport(final BlockGetter level, final BlockPos support) {
		BlockState state = level.getBlockState(support);
		return state.hasProperty(BlockStateProperties.OPEN)
			&& state.getValue(BlockStateProperties.OPEN)
			&& !state.isFaceSturdy(level, support, Direction.UP);
	}

	public static boolean hasStableSupport(final BlockGetter level, final BlockPos support) {
		BlockState state = level.getBlockState(support);
		return !isUnsafeOpenableSupport(level, support)
			&& state.getFluidState().isEmpty()
			&& state.isFaceSturdy(level, support, Direction.UP);
	}

	public static boolean isClearColumn(final BlockGetter level, final BlockPos feet, final int height) {
		for (int dy = 0; dy < height; dy++) {
			BlockPos checked = feet.above(dy);
			BlockState state = level.getBlockState(checked);
			if (!state.getCollisionShape(level, checked).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	public static boolean canStandAt(final BlockGetter level, final BlockPos feet) {
		return hasStableSupport(level, feet.below()) && isClearColumn(level, feet, 2);
	}
}

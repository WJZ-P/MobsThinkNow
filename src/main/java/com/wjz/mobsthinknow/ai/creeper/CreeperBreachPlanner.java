package com.wjz.mobsthinknow.ai.creeper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** 只允许把真实可炸开的第一层软障碍视为破墙目标，避免对基岩或黑曜石白白自爆。 */
public final class CreeperBreachPlanner {
	private static final float MAXIMUM_BREACH_EXPLOSION_RESISTANCE = 20.0F;

	private CreeperBreachPlanner() {
	}

	public static boolean hasBreachableBarrier(final Creeper creeper, final LivingEntity target) {
		if (!(creeper.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}

		BlockHitResult hit = level.clip(new ClipContext(
			creeper.getEyePosition(),
			target.getEyePosition(),
			ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE,
			creeper
		));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}

		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		return !state.isAir()
			&& state.getDestroySpeed(level, pos) >= 0.0F
			&& state.getBlock().getExplosionResistance() <= MAXIMUM_BREACH_EXPLOSION_RESISTANCE;
	}
}

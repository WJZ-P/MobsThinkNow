package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** 在原版陆地节点分类上只增加一条规则：开放且不承重的机关方块上方不可落脚。 */
public final class SmartZombieWalkNodeEvaluator extends WalkNodeEvaluator {
	@Override
	public PathType getPathType(
		final PathfindingContext context,
		final int x,
		final int y,
		final int z
	) {
		if (ZombieTraversalRules.isEnabled()
			&& ZombieTraversalRules.isUnsafeOpenableSupport(context.level(), new BlockPos(x, y - 1, z))) {
			return PathType.BLOCKED;
		}
		return super.getPathType(context, x, y, z);
	}

	@Override
	public PathType getPathTypeOfMob(
		final PathfindingContext context,
		final int x,
		final int y,
		final int z,
		final Mob mob
	) {
		if (ZombieTraversalRules.isEnabled()
			&& ZombieTraversalRules.isUnsafeOpenableSupport(context.level(), new BlockPos(x, y - 1, z))) {
			return PathType.BLOCKED;
		}
		return super.getPathTypeOfMob(context, x, y, z, mob);
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

/** 保留原版 GroundPathNavigation，只替换节点分类器。 */
public final class SmartZombieGroundNavigation extends GroundPathNavigation {
	public SmartZombieGroundNavigation(final Mob mob, final Level level) {
		super(mob, level);
	}

	@Override
	protected PathFinder createPathFinder(final int maxVisitedNodes) {
		this.nodeEvaluator = new SmartZombieWalkNodeEvaluator();
		return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
	}
}

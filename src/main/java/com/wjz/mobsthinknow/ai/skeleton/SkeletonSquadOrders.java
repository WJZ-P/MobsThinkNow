package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadState;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;

/** 把跨物种协调器的集结/部署命令转换成骷髅远程 Goal 可执行的移动。 */
final class SkeletonSquadOrders {
	private static final double ORDER_REACHED_DISTANCE_SQUARED = 2.25;

	private SkeletonSquadOrders() {
	}

	/**
	 * @return 当前 tick 是否应暂停个人射击，优先完成开会或远程部署命令
	 */
	static boolean obeyPreparationOrder(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final double speedModifier
	) {
		if (!(skeleton.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		SquadDirective directive = ZombieSquadCoordinator.forLevel(serverLevel).directiveFor(skeleton);
		if (directive == null
			|| (!directive.isMeetingPhase() && directive.state() != SquadState.DEPLOYING)) {
			return false;
		}

		skeleton.stopUsingItem();
		skeleton.setAggressive(false);
		Vec3 destination = directive.destination();
		if (destination != null) {
			if (skeleton.position().distanceToSqr(destination) <= ORDER_REACHED_DISTANCE_SQUARED) {
				skeleton.getNavigation().stop();
			} else if (skeleton.getNavigation().isDone() || Math.floorMod(skeleton.tickCount, 8) == 0) {
				skeleton.getNavigation().moveTo(destination.x, destination.y, destination.z, speedModifier);
			}
		}

		Vec3 focus = directive.focusPosition();
		if (focus != null) {
			skeleton.getLookControl().setLookAt(focus.x, focus.y, focus.z, 30.0F, 30.0F);
		} else {
			skeleton.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		return true;
	}
}

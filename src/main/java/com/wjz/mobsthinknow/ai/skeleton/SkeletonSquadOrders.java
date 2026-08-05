package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadCombatUrgency;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 把跨物种协调器的集结/部署命令转换成骷髅远程 Goal 可执行的移动。 */
final class SkeletonSquadOrders {
	private static final double ORDER_REACHED_DISTANCE_SQUARED = 2.25;

	private SkeletonSquadOrders() {
	}

	/**
	 * @return 当前 tick 正在执行的准备命令；{@code null} 表示个人战斗状态机接管
	 */
	static @Nullable SquadDirective obeyPreparationOrder(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final double speedModifier
	) {
		if (!(skeleton.level() instanceof ServerLevel serverLevel)) {
			return null;
		}
		SquadDirective directive = ZombieSquadCoordinator.forLevel(serverLevel).directiveFor(skeleton);
		if (directive == null
			|| (!directive.isMeetingPhase() && !directive.holdsCombatFormation())) {
			return null;
		}
		boolean urgent = directive.holdsCombatFormation()
			? SquadCombatUrgency.shouldInterruptCombatFormation(skeleton, target)
			: SquadCombatUrgency.shouldInterruptPreparation(skeleton, target);
		if (urgent) {
			return null;
		}

		if (directive.isMeetingPhase()) {
			skeleton.stopUsingItem();
			skeleton.setAggressive(false);
		} else {
			// 战斗准备/压制阶段允许弓保持满蓄、弩保持装填，只冻结移动阵位与实际发射。
			skeleton.setAggressive(true);
		}
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
		return directive;
	}

	static boolean mayReleaseShot(final AbstractSkeleton skeleton, final LivingEntity target) {
		if (!(skeleton.level() instanceof ServerLevel serverLevel)) {
			return true;
		}
		SquadDirective directive = ZombieSquadCoordinator.forLevel(serverLevel).directiveFor(skeleton);
		if (directive == null) {
			return true;
		}
		boolean urgent = directive.isCombatPhase()
			? SquadCombatUrgency.shouldInterruptCombatFormation(skeleton, target)
			: SquadCombatUrgency.shouldInterruptPreparation(skeleton, target);
		long stableKey = SkeletonVolleyTiming.stableShooterKey(skeleton.getUUID());
		return SkeletonVolleyTiming.mayRelease(
			directive.combatBeat(),
			directive.combatExecuteAt(),
			skeleton.level().getGameTime(),
			stableKey,
			urgent
		);
	}
}

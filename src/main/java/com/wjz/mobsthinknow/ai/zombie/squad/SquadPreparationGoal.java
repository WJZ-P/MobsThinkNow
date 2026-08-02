package com.wjz.mobsthinknow.ai.zombie.squad;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 让新增混编成员在开会和部署阶段真正执行协调器命令，而不是提前各自冲锋。 */
public final class SquadPreparationGoal extends Goal {
	private static final double ORDER_REACHED_DISTANCE_SQUARED = 2.25;
	private final Mob mob;
	private final double speedModifier;
	private @Nullable SquadDirective directive;

	public SquadPreparationGoal(final Mob mob, final double speedModifier) {
		this.mob = mob;
		this.speedModifier = speedModifier;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		this.directive = this.readPreparationDirective();
		return this.directive != null;
	}

	@Override
	public boolean canContinueToUse() {
		this.directive = this.readPreparationDirective();
		return this.directive != null;
	}

	@Override
	public void start() {
		this.mob.setAggressive(false);
		this.tick();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.mob.setAggressive(this.mob.getTarget() != null);
		this.directive = null;
	}

	@Override
	public void tick() {
		SquadDirective current = this.directive;
		LivingEntity target = this.mob.getTarget();
		if (current == null || target == null) {
			return;
		}
		Vec3 destination = current.destination();
		if (destination != null) {
			if (this.mob.position().distanceToSqr(destination) <= ORDER_REACHED_DISTANCE_SQUARED) {
				this.mob.getNavigation().stop();
			} else if (this.mob.getNavigation().isDone() || Math.floorMod(this.mob.tickCount, 8) == 0) {
				this.mob.getNavigation().moveTo(destination.x, destination.y, destination.z, this.speedModifier);
			}
		}
		Vec3 focus = current.focusPosition();
		if (focus != null && focus.distanceToSqr(this.mob.position()) > 0.25) {
			this.mob.getLookControl().setLookAt(focus.x, focus.y, focus.z, 35.0F, 35.0F);
		} else {
			this.mob.getLookControl().setLookAt(target, 35.0F, 35.0F);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private @Nullable SquadDirective readPreparationDirective() {
		if (!(this.mob.level() instanceof ServerLevel level)) {
			return null;
		}
		LivingEntity target = this.mob.getTarget();
		if (target == null || !target.isAlive()) {
			return null;
		}
		if (SquadCombatUrgency.shouldInterruptPreparation(this.mob, target)) {
			return null;
		}
		SquadDirective current = ZombieSquadCoordinator.forLevel(level).directiveFor(this.mob);
		return current != null
			&& (current.isMeetingPhase() || current.state() == SquadState.DEPLOYING)
			? current
			: null;
	}
}

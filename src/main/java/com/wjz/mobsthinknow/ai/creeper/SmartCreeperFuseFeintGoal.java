package com.wjz.mobsthinknow.ai.creeper;

import com.wjz.mobsthinknow.ai.spider.SpiderCreeperCarrierGoal;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 高智力苦力怕的可读心理战：在真实起爆圈外短促点燃，等目标转向或举盾后主动退火并横移。
 * 佯爆不申请爆点预约，也不会越过八 tick 的硬上限；真正提交仍完全交给 {@link SmartCreeperSwellGoal}。
 */
public final class SmartCreeperFuseFeintGoal extends Goal {
	private static final double DESTINATION_REACHED_SQUARED = 1.35 * 1.35;
	private static final double REPOSITION_SPEED = 1.16;

	private final Creeper creeper;
	private final CreeperTacticalController controller;
	private @Nullable LivingEntity target;
	private Phase phase = Phase.IDLE;
	private int phaseTicksRemaining;
	private int repathCooldown;
	private @Nullable Vec3 destination;
	private boolean completed;

	public SmartCreeperFuseFeintGoal(final Creeper creeper, final CreeperTacticalController controller) {
		this.creeper = creeper;
		this.controller = controller;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return shouldDeferApproach(this.creeper, this.controller);
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled
			&& config.creeperAiEnabled
			&& config.creeperFuseFeints
			&& this.phase != Phase.IDLE
			&& this.phaseTicksRemaining > 0
			&& this.creeper.isAlive()
			&& !this.creeper.isIgnited()
			&& !this.creeper.isPassenger()
			&& isValidTarget(this.target);
	}

	@Override
	public void start() {
		this.target = this.creeper.getTarget();
		if (!isValidTarget(this.target)) {
			return;
		}
		this.phase = Phase.PRIMING;
		this.phaseTicksRemaining = CreeperFuseFeintPlanner.primeTicks(this.creeper.getRandom().nextDouble());
		this.repathCooldown = 0;
		this.destination = null;
		this.completed = false;
		this.controller.beginFeint();
		this.creeper.getNavigation().stop();
		this.creeper.setAggressive(true);
		this.creeper.setSwellDir(1);
		SmartCreeperMetrics.feintStarted(this.target.isBlocking());
	}

	@Override
	public void tick() {
		LivingEntity currentTarget = this.target;
		if (!isValidTarget(currentTarget)) {
			return;
		}
		this.creeper.getLookControl().setLookAt(currentTarget, 55.0F, 40.0F);
		if (this.phase == Phase.PRIMING) {
			this.creeper.getNavigation().stop();
			this.creeper.setSwellDir(1);
			boolean stillVisible = this.controller.observe(currentTarget);
			if (--this.phaseTicksRemaining <= 0 || !stillVisible) {
				this.beginReposition(currentTarget);
			}
			return;
		}

		this.creeper.setSwellDir(-1);
		if (--this.phaseTicksRemaining <= 0) {
			this.completed = true;
			return;
		}
		Vec3 currentDestination = this.destination;
		if (currentDestination == null) {
			this.beginReposition(currentTarget);
			currentDestination = this.destination;
		}
		if (currentDestination == null) {
			return;
		}
		if (this.creeper.position().distanceToSqr(currentDestination) <= DESTINATION_REACHED_SQUARED) {
			this.creeper.getNavigation().stop();
			if (this.phaseTicksRemaining <= 10) {
				this.completed = true;
				this.phaseTicksRemaining = 0;
			}
			return;
		}
		if (--this.repathCooldown <= 0 || this.creeper.getNavigation().isDone()) {
			this.repathCooldown = 6;
			this.creeper.getNavigation().moveTo(
				currentDestination.x,
				currentDestination.y,
				currentDestination.z,
				REPOSITION_SPEED
			);
		}
	}

	@Override
	public void stop() {
		if (!this.creeper.isIgnited()) {
			this.creeper.setSwellDir(-1);
		}
		this.creeper.getNavigation().stop();
		MobsThinkNowConfig config = ConfigManager.get();
		int cooldown = CreeperFuseFeintPlanner.cooldownTicks(
			config.creeperFuseFeintCooldownTicks,
			this.creeper.getRandom().nextDouble()
		);
		this.controller.finishFeint(
			this.creeper.level().getGameTime(),
			this.completed ? cooldown : Math.max(40, cooldown / 2)
		);
		if (this.completed) {
			SmartCreeperMetrics.feintCompleted();
		}
		this.target = null;
		this.phase = Phase.IDLE;
		this.phaseTicksRemaining = 0;
		this.repathCooldown = 0;
		this.destination = null;
		this.completed = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public boolean isRepositioning() {
		return this.phase == Phase.REPOSITIONING;
	}

	public @Nullable Vec3 destination() {
		return this.destination;
	}

	/** 同优先级的常规接敌 Goal 用它让出本轮选择，避免依赖 GoalSelector 的插入顺序。 */
	public static boolean shouldDeferApproach(
		final Creeper creeper,
		final CreeperTacticalController controller
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled
			|| !config.creeperAiEnabled
			|| !config.creeperFuseFeints
			|| !creeper.isAlive()
			|| creeper.isIgnited()
			|| creeper.isPassenger()
			|| SpiderCreeperCarrierGoal.isTransportControlled(creeper)
			|| !controller.canStartFeint(creeper.level().getGameTime())) {
			return false;
		}
		LivingEntity target = creeper.getTarget();
		if (!isValidTarget(target)) {
			return false;
		}
		boolean visible = controller.observe(target);
		boolean watching = visible && CreeperCombatMath.isTargetWatching(
			target.getLookAngle(),
			creeper.position().subtract(target.position())
		);
		return CreeperFuseFeintPlanner.shouldFeint(
			CreeperIntelligence.get(creeper),
			true,
			visible,
			watching,
			target.isBlocking(),
			creeper.isPowered(),
			creeper.getSwelling(1.0F),
			creeper.distanceToSqr(target),
			config.creeperMaximumFuseStartDistance
		);
	}

	private void beginReposition(final LivingEntity currentTarget) {
		this.phase = Phase.REPOSITIONING;
		this.phaseTicksRemaining = CreeperFuseFeintPlanner.repositionTicks(this.creeper.getRandom().nextDouble());
		this.repathCooldown = 0;
		this.creeper.setSwellDir(-1);
		this.destination = CreeperFuseFeintPlanner.repositionDestination(
			currentTarget.position(),
			currentTarget.getDeltaMovement(),
			currentTarget.getLookAngle(),
			this.controller.stableFlankSide(),
			CreeperIntelligence.get(this.creeper)
		);
		this.controller.rememberApproach(
			this.controller.stableFlankSide() < 0
				? CreeperCombatMath.ApproachMode.FLANK_LEFT
				: CreeperCombatMath.ApproachMode.FLANK_RIGHT,
			this.destination
		);
		if (this.creeper.level() instanceof ServerLevel level) {
			level.playSound(
				null,
				this.creeper,
				SoundEvents.FIRE_EXTINGUISH,
				SoundSource.HOSTILE,
				0.45F,
				1.55F
			);
		}
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private enum Phase {
		IDLE,
		PRIMING,
		REPOSITIONING
	}
}

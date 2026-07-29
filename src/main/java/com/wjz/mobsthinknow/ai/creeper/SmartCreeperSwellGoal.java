package com.wjz.mobsthinknow.ai.creeper;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 保留原版 30 tick 引信和首次嘶声，但点火后继续向预测爆点移动；软墙后的近期目标可触发破墙提交。
 * 玩家拉出明确安全距离时会退火，避免把“聪明”实现成无条件自毁。
 */
public final class SmartCreeperSwellGoal extends SwellGoal {
	private static final int BREACH_MEMORY_TICKS = 40;

	private final Creeper creeper;
	private final CreeperTacticalController controller;
	private boolean smartMode;
	private @Nullable LivingEntity target;
	private int repathCooldown;
	private boolean movingFuse;
	private boolean breachFuse;
	private boolean abortCounted;

	public SmartCreeperSwellGoal(final Creeper creeper, final CreeperTacticalController controller) {
		super(creeper);
		this.creeper = creeper;
		this.controller = controller;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		this.smartMode = smartAiEnabled();
		if (!this.smartMode) {
			return super.canUse();
		}
		LivingEntity currentTarget = this.creeper.getTarget();
		if (this.creeper.isIgnited() || this.creeper.getSwellDir() > 0) {
			return this.creeper.isAlive();
		}
		if (!isValidTarget(currentTarget)) {
			return false;
		}
		return shouldStart(currentTarget, ConfigManager.get());
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.smartMode) {
			return !smartAiEnabled() && super.canUse();
		}
		if (!smartAiEnabled() || !this.creeper.isAlive()) {
			return false;
		}
		if (this.creeper.isIgnited()) {
			return true;
		}
		LivingEntity currentTarget = this.target;
		return isValidTarget(currentTarget)
			&& (this.creeper.getSwellDir() > 0 || shouldStart(currentTarget, ConfigManager.get()));
	}

	@Override
	public void start() {
		if (!this.smartMode) {
			super.start();
			return;
		}
		this.target = this.creeper.getTarget();
		this.repathCooldown = 0;
		this.movingFuse = false;
		this.abortCounted = false;
		this.breachFuse = this.target != null && this.isBreachCandidate(this.target, ConfigManager.get());
		if (this.breachFuse) {
			SmartCreeperMetrics.breachFuseStarted();
		}
		this.creeper.setSwellDir(1);
	}

	@Override
	public void stop() {
		if (!this.smartMode) {
			super.stop();
			return;
		}
		if (!this.creeper.isIgnited()) {
			this.creeper.setSwellDir(-1);
		}
		this.creeper.getNavigation().stop();
		this.target = null;
		this.movingFuse = false;
		this.breachFuse = false;
		this.smartMode = false;
	}

	@Override
	public void tick() {
		if (!this.smartMode) {
			super.tick();
			return;
		}
		LivingEntity currentTarget = this.target;
		if (!isValidTarget(currentTarget)) {
			if (!this.creeper.isIgnited()) {
				this.creeper.setSwellDir(-1);
			}
			return;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		int intelligence = CreeperIntelligence.get(this.creeper);
		boolean visible = this.controller.observe(currentTarget);
		boolean breachable = (!visible || this.breachFuse) && this.isBreachCandidate(currentTarget, config);
		if (breachable && !this.breachFuse) {
			this.breachFuse = true;
			SmartCreeperMetrics.breachFuseStarted();
		}
		float fuseProgress = Mth.clamp(this.creeper.getSwelling(1.0F), 0.0F, 1.0F);
		double startDistance = CreeperCombatMath.fuseStartDistance(
			config.creeperMaximumFuseStartDistance,
			intelligence,
			this.creeper.isPowered(),
			this.creeper.level().getDifficulty().getId()
		);
		boolean committed = this.creeper.isIgnited() || CreeperCombatMath.shouldContinueFuse(
			this.creeper.distanceToSqr(currentTarget),
			startDistance,
			visible,
			breachable,
			fuseProgress,
			intelligence
		);
		if (!committed) {
			this.creeper.setSwellDir(-1);
			this.creeper.getNavigation().stop();
			if (!this.abortCounted) {
				this.abortCounted = true;
				SmartCreeperMetrics.fuseAborted();
			}
			return;
		}

		this.creeper.setSwellDir(1);
		this.creeper.getLookControl().setLookAt(currentTarget, 40.0F, 35.0F);
		if (!config.creeperMovingFuse) {
			this.creeper.getNavigation().stop();
			return;
		}

		if (--this.repathCooldown > 0 && !this.creeper.getNavigation().isDone()) {
			return;
		}
		this.repathCooldown = CreeperCombatMath.repathTicks(intelligence);
		Vec3 destination = visible
			? CreeperCombatMath.fuseDestination(
				currentTarget.position(),
				currentTarget.getDeltaMovement(),
				fuseProgress,
				intelligence
			)
			: this.controller.lastSeenPosition() != null
				? this.controller.lastSeenPosition()
				: currentTarget.position();
		double speed = CreeperCombatMath.movingFuseSpeed(
			config.creeperFuseMovementSpeed,
			intelligence,
			this.creeper.level().getDifficulty().getId()
		);
		boolean moving = this.creeper.getNavigation().moveTo(
			destination.x,
			destination.y,
			destination.z,
			speed
		);
		if (moving && !this.movingFuse) {
			this.movingFuse = true;
			SmartCreeperMetrics.movingFuseStarted();
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public boolean isMovingFuse() {
		return this.movingFuse;
	}

	public boolean isBreachFuse() {
		return this.breachFuse;
	}

	private boolean shouldStart(final LivingEntity currentTarget, final MobsThinkNowConfig config) {
		int intelligence = CreeperIntelligence.get(this.creeper);
		boolean visible = this.controller.observe(currentTarget);
		boolean watching = visible && CreeperCombatMath.isTargetWatching(
			currentTarget.getLookAngle(),
			this.creeper.position().subtract(currentTarget.position())
		);
		double startDistance = CreeperCombatMath.fuseStartDistance(
			config.creeperMaximumFuseStartDistance,
			intelligence,
			this.creeper.isPowered(),
			this.creeper.level().getDifficulty().getId()
		);
		return CreeperCombatMath.shouldStartFuse(
			this.creeper.distanceToSqr(currentTarget),
			startDistance,
			visible,
			!visible && this.isBreachCandidate(currentTarget, config),
			watching,
			currentTarget.isBlocking(),
			intelligence
		);
	}

	private boolean isBreachCandidate(final LivingEntity currentTarget, final MobsThinkNowConfig config) {
		return config.creeperWallBreaching
			&& CreeperIntelligence.get(this.creeper) >= 8
			&& this.controller.hasRecentSight(BREACH_MEMORY_TICKS)
			&& CreeperBreachPlanner.hasBreachableBarrier(this.creeper, currentTarget);
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean smartAiEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.creeperAiEnabled;
	}
}

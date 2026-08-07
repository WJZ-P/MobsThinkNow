package com.wjz.mobsthinknow.ai.creeper;

import com.wjz.mobsthinknow.ai.creeper.CreeperCombatMath.ApproachMode;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import com.wjz.mobsthinknow.ai.spider.SpiderCreeperCarrierGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 苦力怕的接敌状态机：未被观察时预判截击，被正面观察或举盾针对时走稳定左右绕后点。
 *
 * <p>关闭苦力怕 AI 后，生命周期逐项委托给原版 {@link MeleeAttackGoal}，因此配置热切换不需要
 * 重建实体。猫和豹猫的回避 Goal 仍处于更高优先级 3，会照常打断本 Goal。</p>
 */
public final class SmartCreeperApproachGoal extends MeleeAttackGoal {
	private final Creeper creeper;
	private final CreeperTacticalController controller;
	private boolean smartMode;
	private int repathCooldown;
	private ApproachMode countedMode = ApproachMode.DIRECT;

	public SmartCreeperApproachGoal(final Creeper creeper, final CreeperTacticalController controller) {
		super(creeper, 1.0, false);
		this.creeper = creeper;
		this.controller = controller;
	}

	@Override
	public boolean canUse() {
		if (SpiderCreeperCarrierGoal.isTransportControlled(this.creeper)) {
			return false;
		}
		this.smartMode = smartAiEnabled();
		if (!this.smartMode) {
			return super.canUse();
		}
		if (this.controller.isFeintActive()
			|| SmartCreeperFuseFeintGoal.shouldDeferApproach(this.creeper, this.controller)) {
			return false;
		}
		return isValidTarget(this.creeper.getTarget()) && !this.creeper.isIgnited();
	}

	@Override
	public boolean canContinueToUse() {
		if (SpiderCreeperCarrierGoal.isTransportControlled(this.creeper)) {
			return false;
		}
		if (!this.smartMode) {
			return !smartAiEnabled() && super.canContinueToUse();
		}
		return smartAiEnabled()
			&& !this.controller.isFeintActive()
			&& !SmartCreeperFuseFeintGoal.shouldDeferApproach(this.creeper, this.controller)
			&& isValidTarget(this.currentTarget())
			&& !this.creeper.isIgnited();
	}

	@Override
	public void start() {
		if (!this.smartMode) {
			super.start();
			return;
		}
		this.repathCooldown = 0;
		this.countedMode = ApproachMode.DIRECT;
		this.creeper.setAggressive(true);
	}

	@Override
	public void stop() {
		if (!this.smartMode) {
			super.stop();
			return;
		}
		this.creeper.getNavigation().stop();
		this.creeper.setAggressive(false);
		this.controller.clearApproach();
		this.smartMode = false;
	}

	@Override
	public void tick() {
		if (!this.smartMode) {
			super.tick();
			return;
		}
		LivingEntity target = this.currentTarget();
		if (!isValidTarget(target)) {
			return;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		int intelligence = CreeperIntelligence.get(this.creeper);
		boolean visible = this.controller.observe(target);
		boolean watching = visible && CreeperCombatMath.isTargetWatching(
			target.getLookAngle(),
			this.creeper.position().subtract(target.position())
		);
		ApproachMode mode = CreeperCombatMath.chooseApproach(
			intelligence,
			watching,
			target.isBlocking(),
			visible,
			this.creeper.distanceToSqr(target),
			config.creeperFlanking,
			this.controller.stableFlankSide()
		);

		this.creeper.getLookControl().setLookAt(target, 35.0F, 30.0F);
		if (--this.repathCooldown > 0 && !this.creeper.getNavigation().isDone()) {
			return;
		}
		this.repathCooldown = CreeperCombatMath.repathTicks(intelligence);

		Vec3 predictedBlastCenter = CreeperCombatMath.fuseDestination(
			target.position(),
			target.getDeltaMovement(),
			0.0F,
			intelligence
		);
		Vec3 queueStaging = this.creeper.level() instanceof ServerLevel level
			? ZombieSquadCoordinator.forLevel(level).blastQueueStagingPointFor(
				this.creeper,
				target,
				predictedBlastCenter
			)
			: null;
		Vec3 destination;
		if (queueStaging != null) {
			mode = this.controller.stableFlankSide() < 0 ? ApproachMode.FLANK_LEFT : ApproachMode.FLANK_RIGHT;
			destination = queueStaging;
		} else if (!visible && this.controller.hasRecentSight(40) && this.controller.lastSeenPosition() != null) {
			mode = ApproachMode.DIRECT;
			destination = this.controller.lastSeenPosition();
		} else {
			destination = CreeperCombatMath.approachDestination(
				mode,
				target.position(),
				target.getDeltaMovement(),
				target.getLookAngle(),
				intelligence
			);
		}

		double speed = CreeperCombatMath.approachSpeed(
			intelligence,
			this.creeper.level().getDifficulty().getId()
		);
		boolean planned = this.creeper.getNavigation().moveTo(
			destination.x,
			destination.y,
			destination.z,
			speed
		);
		if (!planned && queueStaging == null && mode.isFlanking()) {
			mode = ApproachMode.INTERCEPT;
			destination = CreeperCombatMath.approachDestination(
				mode,
				target.position(),
				target.getDeltaMovement(),
				target.getLookAngle(),
				intelligence
			);
			planned = this.creeper.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
		}
		if (!planned && queueStaging == null) {
			mode = ApproachMode.DIRECT;
			destination = target.position();
			this.creeper.getNavigation().moveTo(target, speed);
		} else if (!planned) {
			this.creeper.getNavigation().stop();
		}

		this.controller.rememberApproach(mode, destination);
		this.countTransition(mode);
	}

	public ApproachMode approachMode() {
		return this.controller.approachMode();
	}

	private void countTransition(final ApproachMode mode) {
		if (mode == this.countedMode) {
			return;
		}
		this.countedMode = mode;
		if (mode.isFlanking()) {
			SmartCreeperMetrics.flankStarted();
		} else if (mode == ApproachMode.INTERCEPT) {
			SmartCreeperMetrics.interceptStarted();
		}
	}

	private @Nullable LivingEntity currentTarget() {
		return this.creeper.getTarget();
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean smartAiEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.creeperAiEnabled;
	}
}

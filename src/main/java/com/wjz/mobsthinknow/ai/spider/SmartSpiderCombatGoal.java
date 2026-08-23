package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.spider.SpiderCombatMath.ApproachMode;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 蜘蛛近战状态机：预测截击、受观察时绕侧、命中后保持面向目标撤到下一次跳扑距离。
 * 爬墙仍完全复用原版 WallClimberNavigation，因此垂直地形不会被额外的直线速度破坏。
 */
public final class SmartSpiderCombatGoal extends MeleeAttackGoal {
	private final Spider spider;
	private final int stableSide;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.MELEE);
	private boolean smartMode;
	private int repathCooldown;
	private int attackCooldown;
	private int repositionTicks;
	private ApproachMode approachMode = ApproachMode.DIRECT;
	private ApproachMode countedMode = ApproachMode.DIRECT;

	public SmartSpiderCombatGoal(final Spider spider) {
		super(spider, 1.0, true);
		this.spider = spider;
		this.stableSide = (spider.getUUID().getLeastSignificantBits() & 1L) == 0L ? -1 : 1;
	}

	@Override
	public boolean canUse() {
		this.smartMode = smartAiEnabled();
		if (!this.smartMode) {
			return !this.spider.isVehicle() && super.canUse();
		}
		return !this.spider.isVehicle()
			&& isValidTarget(this.spider.getTarget())
			&& this.activityLease.canAcquire(this.spider, this.spider.level().getGameTime());
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.smartMode) {
			if (smartAiEnabled()) {
				return false;
			}
			float brightness = this.currentBrightness();
			if (brightness >= 0.5F && this.spider.getRandom().nextInt(100) == 0) {
				this.spider.setTarget(null);
				return false;
			}
			return !this.spider.isVehicle() && super.canContinueToUse();
		}
		return smartAiEnabled()
			&& !this.spider.isVehicle()
			&& isValidTarget(this.spider.getTarget())
			&& this.activityLease.owns(this.spider, this.spider.level().getGameTime());
	}

	@Override
	public void start() {
		if (!this.smartMode) {
			super.start();
			return;
		}
		this.activityLease.acquire(this.spider, this.spider.level().getGameTime());
		this.repathCooldown = 0;
		this.attackCooldown = 0;
		this.repositionTicks = 0;
		this.approachMode = ApproachMode.DIRECT;
		this.countedMode = ApproachMode.DIRECT;
		this.spider.setAggressive(true);
	}

	@Override
	public void stop() {
		if (!this.smartMode) {
			super.stop();
			return;
		}
		this.spider.getNavigation().stop();
		this.spider.setAggressive(false);
		this.repositionTicks = 0;
		this.smartMode = false;
		this.activityLease.release(this.spider);
	}

	@Override
	public void tick() {
		if (!this.smartMode) {
			super.tick();
			return;
		}
		if (!this.activityLease.renew(this.spider, this.spider.level().getGameTime())) {
			return;
		}
		LivingEntity target = this.spider.getTarget();
		if (!isValidTarget(target)) {
			return;
		}

		this.attackCooldown = Math.max(0, this.attackCooldown - 1);
		this.repositionTicks = Math.max(0, this.repositionTicks - 1);
		this.spider.getLookControl().setLookAt(target, 45.0F, 40.0F);

		if (this.attackCooldown == 0
			&& this.spider.isWithinMeleeAttackRange(target)
			&& this.spider.getSensing().hasLineOfSight(target)
			&& this.spider.level() instanceof ServerLevel serverLevel) {
			this.spider.swing(InteractionHand.MAIN_HAND);
			boolean hit = this.spider.doHurtTarget(serverLevel, target);
			this.attackCooldown = 20;
			if (hit && ConfigManager.get().spiderHitAndRun && SpiderIntelligence.get(this.spider) >= 5) {
				this.repositionTicks = SpiderCombatMath.repositionTicks(SpiderIntelligence.get(this.spider));
				SmartSpiderMetrics.repositionStarted();
			}
		}

		// 跳扑 Goal 接管空中动量；这里继续盯住目标，但不让寻路器把刚设置的速度冲掉。
		if (!this.spider.onGround()) {
			return;
		}

		int intelligence = SpiderIntelligence.get(this.spider);
		boolean visible = this.spider.getSensing().hasLineOfSight(target);
		boolean watching = visible && SpiderCombatMath.isTargetWatching(
			target.getLookAngle(),
			this.spider.position().subtract(target.position())
		);
		this.approachMode = SpiderCombatMath.chooseApproach(
			intelligence,
			watching,
			target.isBlocking(),
			visible,
			this.repositionTicks,
			this.stableSide
		);
		this.countModeTransition();

		if (--this.repathCooldown > 0 && !this.spider.getNavigation().isDone()) {
			return;
		}
		this.repathCooldown = SpiderCombatMath.repathTicks(intelligence);
		Vec3 destination = SpiderCombatMath.approachDestination(
			this.approachMode,
			target.position(),
			target.getDeltaMovement(),
			target.getLookAngle(),
			intelligence
		);
		double speed = SpiderCombatMath.approachSpeed(
			intelligence,
			this.spider.level().getDifficulty().getId()
		);
		boolean planned = this.spider.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
		if (!planned && this.approachMode != ApproachMode.DIRECT) {
			this.approachMode = ApproachMode.DIRECT;
			this.spider.getNavigation().moveTo(target, speed);
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public ApproachMode approachMode() {
		return this.approachMode;
	}

	public int repositionTicksRemaining() {
		return this.repositionTicks;
	}

	private void countModeTransition() {
		if (this.approachMode == this.countedMode) {
			return;
		}
		this.countedMode = this.approachMode;
		if (this.approachMode.isFlank()) {
			SmartSpiderMetrics.flankStarted();
		}
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean smartAiEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.spiderAiEnabled;
	}

	/** Mirrors vanilla's deprecated entity helper through the supported level API. */
	private float currentBrightness() {
		BlockPos eye = BlockPos.containing(this.spider.getX(), this.spider.getEyeY(), this.spider.getZ());
		if (!this.spider.level().hasChunk(
			SectionPos.blockToSectionCoord(eye.getX()),
			SectionPos.blockToSectionCoord(eye.getZ())
		)) {
			return 0.0F;
		}
		float brightness = this.spider.level().getMaxLocalRawBrightness(eye) / 15.0F;
		float adjusted = brightness / (4.0F - 3.0F * brightness);
		return Mth.lerp(this.spider.level().dimensionType().ambientLight(), adjusted, 1.0F);
	}
}

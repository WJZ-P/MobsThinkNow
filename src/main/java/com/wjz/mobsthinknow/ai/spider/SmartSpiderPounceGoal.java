package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 原版跳扑的兼容包装：关闭蜘蛛 AI 时逐项委托原版，开启后扩大到 2.5～7 格并预测落点。
 * 冷却使用世界时间，Goal 等待期间也会自然流逝，不需要额外的每 tick 计数器。
 */
public final class SmartSpiderPounceGoal extends LeapAtTargetGoal {
	private final Spider spider;
	private boolean smartMode;
	private @Nullable LivingEntity target;
	private long nextPounceTick;

	public SmartSpiderPounceGoal(final Spider spider) {
		super(spider, 0.4F);
		this.spider = spider;
	}

	@Override
	public boolean canUse() {
		this.smartMode = smartAiEnabled();
		if (!this.smartMode) {
			return super.canUse();
		}
		LivingEntity currentTarget = this.spider.getTarget();
		if (this.spider.isVehicle()
			|| !isValidTarget(currentTarget)
			|| this.spider.level().getGameTime() < this.nextPounceTick) {
			return false;
		}
		this.target = currentTarget;
		return ConfigManager.get().spiderPredictivePounce
			&& SpiderCombatMath.canPredictivePounce(
				SpiderIntelligence.get(this.spider),
				this.spider.getSensing().hasLineOfSight(currentTarget),
				this.spider.onGround(),
				this.spider.distanceToSqr(currentTarget)
			);
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.smartMode) {
			return !smartAiEnabled() && super.canContinueToUse();
		}
		return smartAiEnabled() && this.spider.isAlive() && !this.spider.onGround();
	}

	@Override
	public void start() {
		if (!this.smartMode) {
			super.start();
			return;
		}
		LivingEntity currentTarget = this.target;
		if (!isValidTarget(currentTarget)) {
			return;
		}
		int intelligence = SpiderIntelligence.get(this.spider);
		Vec3 velocity = SpiderCombatMath.pounceVelocity(
			this.spider.position(),
			this.spider.getDeltaMovement(),
			currentTarget.position(),
			currentTarget.getDeltaMovement(),
			intelligence,
			this.spider.level().getDifficulty().getId()
		);
		this.spider.getNavigation().stop();
		this.spider.setDeltaMovement(velocity);
		this.nextPounceTick = this.spider.level().getGameTime()
			+ Math.max(18, 36 - intelligence)
			+ this.spider.getRandom().nextInt(9);
		SmartSpiderMetrics.pounceStarted();
	}

	@Override
	public void stop() {
		if (!this.smartMode) {
			super.stop();
		}
		this.target = null;
		this.smartMode = false;
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean smartAiEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.spiderAiEnabled;
	}
}

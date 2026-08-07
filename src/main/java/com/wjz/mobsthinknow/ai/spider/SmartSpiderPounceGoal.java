package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 原版跳扑的兼容包装：关闭蜘蛛 AI 时逐项委托原版，开启后扩大到 2.5～7 格并预测落点。
 * 冷却使用世界时间，Goal 等待期间也会自然流逝；加入混编小队后还会取得共享跳扑令牌，
 * 避免多只蜘蛛在同一帧起跳、互相碰撞或同时遮挡远程队友的射线。
 */
public final class SmartSpiderPounceGoal extends LeapAtTargetGoal {
	private final Spider spider;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.POUNCE);
	private boolean smartMode;
	private boolean squadPounceReserved;
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
		this.target = null;
		this.squadPounceReserved = false;
		LivingEntity currentTarget = this.spider.getTarget();
		if (this.spider.isVehicle()
			|| !isValidTarget(currentTarget)
			|| this.spider.level().getGameTime() < this.nextPounceTick) {
			return false;
		}
		boolean individuallyReady = ConfigManager.get().spiderPredictivePounce
			&& this.activityLease.canAcquire(this.spider, this.spider.level().getGameTime())
			&& SpiderCombatMath.canPredictivePounce(
				SpiderIntelligence.get(this.spider),
				this.spider.getSensing().hasLineOfSight(currentTarget),
				this.spider.onGround(),
				this.spider.distanceToSqr(currentTarget)
			);
		if (!individuallyReady) {
			return false;
		}
		if (this.spider.level() instanceof ServerLevel level
			&& !ZombieSquadCoordinator.forLevel(level).canStartSpiderPounce(this.spider, currentTarget)) {
			return false;
		}
		this.target = currentTarget;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.smartMode) {
			return !smartAiEnabled() && super.canContinueToUse();
		}
		return smartAiEnabled()
			&& this.spider.isAlive()
			&& !this.spider.onGround()
			&& this.activityLease.owns(this.spider, this.spider.level().getGameTime());
	}

	@Override
	public void start() {
		if (!this.smartMode) {
			super.start();
			return;
		}
		long now = this.spider.level().getGameTime();
		if (!this.activityLease.acquire(this.spider, now)) {
			this.target = null;
			return;
		}
		LivingEntity currentTarget = this.target;
		if (!isValidTarget(currentTarget)) {
			this.activityLease.release(this.spider);
			return;
		}
		if (this.spider.level() instanceof ServerLevel level) {
			ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
			if (!coordinator.tryStartSpiderPounce(this.spider, currentTarget)) {
				this.target = null;
				this.activityLease.release(this.spider);
				return;
			}
			this.squadPounceReserved = true;
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
		this.nextPounceTick = now
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
		if (this.squadPounceReserved && this.spider.level() instanceof ServerLevel level) {
			ZombieSquadCoordinator.forLevel(level).releaseSpiderPounce(this.spider);
		}
		this.squadPounceReserved = false;
		this.smartMode = false;
		this.activityLease.release(this.spider);
	}

	@Override
	public void tick() {
		if (!this.smartMode) {
			super.tick();
			return;
		}
		this.activityLease.renew(this.spider, this.spider.level().getGameTime());
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static boolean smartAiEnabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.spiderAiEnabled;
	}
}

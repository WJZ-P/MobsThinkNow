package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.spider.SmartSpiderCombatGoal;
import com.wjz.mobsthinknow.ai.spider.SmartSpiderMetrics;
import com.wjz.mobsthinknow.ai.spider.SmartSpiderPounceGoal;
import com.wjz.mobsthinknow.ai.spider.SpiderCreeperCarrierGoal;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligenceAccess;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligenceName;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只改造普通蜘蛛；洞穴蜘蛛保留毒素型原版节奏。 */
@Mixin(Spider.class)
public abstract class SpiderMixin extends Monster implements SpiderIntelligenceAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";
	@Unique
	private int mobsthinknow$spiderIntelligence;

	protected SpiderMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installSmartSpiderGoals(final CallbackInfo callbackInfo) {
		Spider spider = (Spider)(Object)this;
		if (spider.getType() != EntityType.SPIDER) {
			return;
		}
		// 私有 SpiderAttackGoal 只能按精确二进制类名识别；不会误删其他 Mod 的 MeleeAttackGoal 子类。
		this.goalSelector.removeAllGoals(goal -> goal.getClass() == LeapAtTargetGoal.class
			|| goal.getClass().getName().equals("net.minecraft.world.entity.monster.spider.Spider$SpiderAttackGoal"));
		this.goalSelector.addGoal(2, new SpiderCreeperCarrierGoal(spider));
		this.goalSelector.addGoal(3, new SmartSpiderPounceGoal(spider));
		this.goalSelector.addGoal(4, new SmartSpiderCombatGoal(spider));
		SmartSpiderMetrics.goalsInstalled();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$initializeSpiderIdentity(
		final EntityType<? extends Spider> type,
		final Level level,
		final CallbackInfo callbackInfo
	) {
		if (!level.isClientSide() && type == EntityType.SPIDER) {
			Spider spider = (Spider)(Object)this;
			SpiderIntelligenceName.apply(spider, this.mobsthinknow$getSpiderIntelligence());
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void mobsthinknow$dismountDisabledCreeperPayload(final CallbackInfo callbackInfo) {
		if (this.level().isClientSide() || this.getType() != EntityType.SPIDER) {
			return;
		}
		MobsThinkNowConfig config = ConfigManager.get();
		Entity firstPassenger = this.getFirstPassenger();
		if (firstPassenger instanceof Creeper creeper
			&& (!config.enabled || !config.spiderAiEnabled || !config.spiderCreeperCoordination)) {
			creeper.stopRiding();
		}
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.getType() == EntityType.SPIDER) {
			output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getSpiderIntelligence());
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		if (this.getType() != EntityType.SPIDER) {
			return;
		}
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$spiderIntelligence = saved == 0 ? 0 : SpiderIntelligence.clamp(saved);
		Spider spider = (Spider)(Object)this;
		SpiderIntelligenceName.apply(spider, this.mobsthinknow$getSpiderIntelligence());
	}

	/**
	 * Mob 默认把第一只 Mob 乘客视为坐骑驾驶者并暂停自身 MOVE/LOOK。
	 * 苦力怕在本组合里是载荷而非驾驶者，所以仅对此组合返回空控制者；骷髅骑士等原版关系继续走 super。
	 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		Entity firstPassenger = this.getFirstPassenger();
		if (this.getType() == EntityType.SPIDER && firstPassenger instanceof Creeper) {
			return null;
		}
		return super.getControllingPassenger();
	}

	@Override
	public int mobsthinknow$getSpiderIntelligence() {
		if (this.mobsthinknow$spiderIntelligence == 0) {
			this.mobsthinknow$spiderIntelligence = SpiderIntelligence.roll(this.level().getDifficulty(), this.random);
		}
		return this.mobsthinknow$spiderIntelligence;
	}

	@Override
	public void mobsthinknow$setSpiderIntelligence(final int intelligence) {
		this.mobsthinknow$spiderIntelligence = SpiderIntelligence.clamp(intelligence);
	}
}

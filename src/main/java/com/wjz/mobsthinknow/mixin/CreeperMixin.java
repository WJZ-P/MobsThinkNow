package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligenceAccess;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligenceName;
import com.wjz.mobsthinknow.ai.creeper.CreeperPowerAccess;
import com.wjz.mobsthinknow.ai.creeper.CreeperTacticalController;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperApproachGoal;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperMetrics;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperSwellGoal;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 普通苦力怕的智力、持久化和两个可热切换战术 Goal 接入点。 */
@Mixin(Creeper.class)
public abstract class CreeperMixin extends Monster implements CreeperIntelligenceAccess, CreeperPowerAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";

	@Shadow
	@Final
	private static EntityDataAccessor<Boolean> DATA_IS_POWERED;

	@Unique
	private int mobsthinknow$creeperIntelligence;
	@Unique
	private CreeperTacticalController mobsthinknow$tacticalController;

	protected CreeperMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installSmartCreeperGoals(final CallbackInfo callbackInfo) {
		Creeper creeper = (Creeper)(Object)this;
		// 只替换原版精确类，保留其他 Mod 注入的派生 Goal。
		this.goalSelector.removeAllGoals(goal -> goal.getClass() == SwellGoal.class || goal.getClass() == MeleeAttackGoal.class);
		this.mobsthinknow$tacticalController = new CreeperTacticalController(creeper);
		this.goalSelector.addGoal(2, new SmartCreeperSwellGoal(creeper, this.mobsthinknow$tacticalController));
		this.goalSelector.addGoal(4, new SmartCreeperApproachGoal(creeper, this.mobsthinknow$tacticalController));
		SmartCreeperMetrics.goalsInstalled();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$initializeCreeperIdentity(
		final EntityType<? extends Creeper> type,
		final Level level,
		final CallbackInfo callbackInfo
	) {
		if (!level.isClientSide()) {
			Creeper creeper = (Creeper)(Object)this;
			CreeperIntelligenceName.apply(creeper, this.mobsthinknow$getCreeperIntelligence());
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$saveCreeperIntelligence(final ValueOutput output, final CallbackInfo callbackInfo) {
		output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getCreeperIntelligence());
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadCreeperIntelligence(final ValueInput input, final CallbackInfo callbackInfo) {
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$creeperIntelligence = saved == 0 ? 0 : CreeperIntelligence.clamp(saved);
		Creeper creeper = (Creeper)(Object)this;
		CreeperIntelligenceName.apply(creeper, this.mobsthinknow$getCreeperIntelligence());
	}

	@Override
	public int mobsthinknow$getCreeperIntelligence() {
		if (this.mobsthinknow$creeperIntelligence == 0) {
			this.mobsthinknow$creeperIntelligence = CreeperIntelligence.roll(this.level().getDifficulty(), this.random);
		}
		return this.mobsthinknow$creeperIntelligence;
	}

	@Override
	public void mobsthinknow$setCreeperIntelligence(final int intelligence) {
		this.mobsthinknow$creeperIntelligence = CreeperIntelligence.clamp(intelligence);
	}

	@Override
	public void mobsthinknow$setCreeperPowered(final boolean powered) {
		this.entityData.set(DATA_IS_POWERED, powered);
	}
}

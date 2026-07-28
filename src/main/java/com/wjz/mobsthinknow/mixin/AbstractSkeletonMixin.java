package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonEmergencyDisengageGoal;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonBowAttackGoal;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** 只改造原版普通骷髅；流浪者、沼骸、凋灵骷髅等变种先保留各自原版节奏。 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonMixin extends Monster {
	@Shadow
	@Final
	private RangedBowAttackGoal<AbstractSkeleton> bowGoal;

	@Unique
	private @Nullable SmartSkeletonBowAttackGoal mobsthinknow$smartBowGoal;
	@Unique
	private boolean mobsthinknow$smartBowGoalCounted;
	@Unique
	private @Nullable SkeletonEmergencyDisengageGoal mobsthinknow$emergencyDisengageGoal;
	@Unique
	private boolean mobsthinknow$emergencyDisengageGoalCounted;

	protected AbstractSkeletonMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	/**
	 * 原版每次换装都会重建“弓或近战”Goal。等它完成判断后，仅把普通持弓骷髅的原版
	 * RangedBowAttackGoal 换成兼容包装；非弓装备继续沿用原版 meleeGoal。
	 */
	@Inject(method = "reassessWeaponGoal", at = @At("TAIL"))
	private void mobsthinknow$installSmartBowGoal(final CallbackInfo callbackInfo) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		if (skeleton.getType() != EntityType.SKELETON) {
			return;
		}

		this.goalSelector.removeGoal(this.bowGoal);
		if (this.mobsthinknow$smartBowGoal == null) {
			int vanillaInterval = skeleton.level().getDifficulty() == Difficulty.HARD ? 20 : 40;
			this.mobsthinknow$smartBowGoal = new SmartSkeletonBowAttackGoal(
				skeleton,
				1.0,
				vanillaInterval,
				15.0F
			);
		} else {
			this.goalSelector.removeGoal(this.mobsthinknow$smartBowGoal);
		}
		if (this.mobsthinknow$emergencyDisengageGoal == null) {
			this.mobsthinknow$emergencyDisengageGoal = new SkeletonEmergencyDisengageGoal(skeleton);
		} else {
			this.goalSelector.removeGoal(this.mobsthinknow$emergencyDisengageGoal);
		}

		if (skeleton.isHolding(Items.BOW)) {
			// 原版避日是优先级 2、避狼是 3、弓术是 4；贴脸脱离必须先于它们抢占 MOVE/LOOK。
			this.goalSelector.addGoal(1, this.mobsthinknow$emergencyDisengageGoal);
			this.goalSelector.addGoal(4, this.mobsthinknow$smartBowGoal);
			if (!this.mobsthinknow$emergencyDisengageGoalCounted) {
				this.mobsthinknow$emergencyDisengageGoalCounted = true;
				SmartSkeletonMetrics.emergencyGoalInstalled();
			}
			if (!this.mobsthinknow$smartBowGoalCounted) {
				this.mobsthinknow$smartBowGoalCounted = true;
				SmartSkeletonMetrics.goalInstalled();
			}
		}
	}

	/**
	 * 原版已经计算了箭速、重力抬升和难度散布；这里只在静态 spawnProjectileUsingShoot 调用前
	 * 给 X/Z 方向叠加有限提前量。原版 Y 抛物线与随机误差保持不变，因此预测不会变成必中修正。
	 */
	@ModifyArgs(
		method = "performRangedAttack",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"
		)
	)
	private void mobsthinknow$leadMovingTarget(
		final Args args,
		final LivingEntity target,
		final float power
	) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		MobsThinkNowConfig config = ConfigManager.get();
		if (skeleton.getType() != EntityType.SKELETON
			|| !config.enabled
			|| !config.skeletonAiEnabled
			|| !config.skeletonPredictiveAim) {
			return;
		}

		double originalX = (Double)args.get(3);
		double originalZ = (Double)args.get(5);
		double difficultyFactor = SkeletonCombatMath.difficultyPredictionFactor(
			skeleton.level().getDifficulty().getId()
		);
		SkeletonCombatMath.HorizontalLead lead = SkeletonCombatMath.horizontalLead(
			target.getDeltaMovement().x,
			target.getDeltaMovement().z,
			Math.sqrt(originalX * originalX + originalZ * originalZ),
			config.skeletonAimPredictionStrength * difficultyFactor
		);
		if (lead.x() == 0.0 && lead.z() == 0.0) {
			return;
		}

		args.set(3, originalX + lead.x());
		args.set(5, originalZ + lead.z());
		SmartSkeletonMetrics.predictiveShot();
	}
}

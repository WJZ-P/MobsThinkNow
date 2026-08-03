package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonBowIntervals;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCrossbowLoadout;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonEscapeSpeedAccess;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonEscapeSpeedProfile;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonEmergencyDisengageGoal;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligenceAccess;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligenceName;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonBowAttackGoal;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonCrossbowAttackGoal;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
import com.wjz.mobsthinknow.ai.skeleton.SquadSkeletonHurtByTargetGoal;
import com.wjz.mobsthinknow.ai.giant.GiantRiderBoardingGoal;
import com.wjz.mobsthinknow.ai.skeleton.MountedSkeletonCombat;
import com.wjz.mobsthinknow.ai.skeleton.MountedSkeletonTargetGoal;
import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionAccess;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionProfile;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import com.wjz.mobsthinknow.ai.nether.SmartNetherUndeadMeleeGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadTheatrics;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadCreeperEvadeGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadFiringLaneClearGoal;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** 改造主世界骷髅家族；特殊箭与各变种原版射击节奏仍由对应实体保留。 */
@Mixin(AbstractSkeleton.class)
public abstract class AbstractSkeletonMixin extends Monster implements
	SkeletonIntelligenceAccess,
	SkeletonEscapeSpeedAccess,
	NetherProfessionAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$NETHER_PROFESSION_ID =
		SynchedEntityData.defineId(AbstractSkeleton.class, EntityDataSerializers.BYTE);

	@Shadow
	@Final
	private RangedBowAttackGoal<AbstractSkeleton> bowGoal;
	@Shadow
	@Final
	private MeleeAttackGoal meleeGoal;

	@Unique
	private @Nullable SmartSkeletonBowAttackGoal mobsthinknow$smartBowGoal;
	@Unique
	private boolean mobsthinknow$smartBowGoalCounted;
	@Unique
	private @Nullable SkeletonEmergencyDisengageGoal mobsthinknow$emergencyDisengageGoal;
	@Unique
	private boolean mobsthinknow$emergencyDisengageGoalCounted;
	@Unique
	private @Nullable SmartSkeletonCrossbowAttackGoal mobsthinknow$smartCrossbowGoal;
	@Unique
	private @Nullable SmartNetherUndeadMeleeGoal mobsthinknow$smartNetherMeleeGoal;
	@Unique
	private int mobsthinknow$skeletonIntelligence;
	@Unique
	private float mobsthinknow$skeletonEscapeSpeedFactor;

	protected AbstractSkeletonMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	/** AbstractSkeleton 本身没有覆盖同步数据定义；Mixin 在这一固定版本上补一层轻量覆盖。 */
	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		// 只有凋灵骷髅会读取该槽；其他骷髅保持 NONE，避免额外客户端数据包或实体扫描。
		builder.define(mobsthinknow$NETHER_PROFESSION_ID, NetherProfession.NONE.id());
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$countWitherSkeletonController(final CallbackInfo callbackInfo) {
		if (((AbstractSkeleton)(Object)this).getType() == EntityType.WITHER_SKELETON) {
			SmartNetherMetrics.controllerInstalled();
		}
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installSquadFriendlyFireTargetGoal(final CallbackInfo callbackInfo) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		if (!OverworldUndeadFamilies.isSkeletonFamily(skeleton)) {
			return;
		}
		// 只替换 AbstractSkeleton 自己注册的精确原版类，不移除其他模组添加的 HurtByTargetGoal 子类。
		this.targetSelector.removeAllGoals(goal -> goal.getClass() == HurtByTargetGoal.class);
		// 受管理坐骑统一提供共享目标；既修复巨人头顶高度，也避免蜘蛛乘客运行自己的无效导航。
		this.targetSelector.addGoal(0, new MountedSkeletonTargetGoal(skeleton));
		this.goalSelector.addGoal(0, new SquadCreeperEvadeGoal(skeleton));
		this.targetSelector.addGoal(1, new SquadSkeletonHurtByTargetGoal(skeleton));
		this.goalSelector.addGoal(2, new GiantRiderBoardingGoal(skeleton));
		this.goalSelector.addGoal(3, new SquadFiringLaneClearGoal(skeleton, 1.12));
	}

	/**
	 * 原版 AbstractSkeleton.rideTick 会在 AI tick 结束后再次把身体朝向覆盖成载具朝向。
	 * 射手正在瞄准时把头、身体和主旋转恢复到目标方向，避免箭射向下方时模型仍直视巨人前方。
	 */
	@Inject(method = "rideTick", at = @At("TAIL"))
	private void mobsthinknow$faceSharedTargetWhileMounted(final CallbackInfo callbackInfo) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		LivingEntity target = MountedSkeletonCombat.sharedTarget(skeleton);
		if (target == null) {
			return;
		}
		skeleton.lookAt(target, 360.0F, 90.0F);
		float yaw = skeleton.getYRot();
		skeleton.setYBodyRot(yaw);
		skeleton.setYHeadRot(yaw);
	}

	/**
	 * 原版每次换装都会重建“弓或近战”Goal。等它完成判断后，把受支持持弓骷髅的原版
	 * RangedBowAttackGoal 换成兼容包装；非弓装备继续沿用原版 meleeGoal。
	 */
	@Inject(method = "reassessWeaponGoal", at = @At("TAIL"))
	private void mobsthinknow$installSmartBowGoal(final CallbackInfo callbackInfo) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		if (skeleton instanceof WitherSkeleton) {
			if (this.mobsthinknow$smartNetherMeleeGoal != null) {
				this.goalSelector.removeGoal(this.mobsthinknow$smartNetherMeleeGoal);
			}
			if (!skeleton.isHolding(Items.BOW)) {
				// 原版刚在同一方法中注册 meleeGoal；只替换这个精确实例，弓手继续使用原版远程状态机。
				this.goalSelector.removeGoal(this.meleeGoal);
				if (this.mobsthinknow$smartNetherMeleeGoal == null) {
					this.mobsthinknow$smartNetherMeleeGoal = new SmartNetherUndeadMeleeGoal(skeleton);
				}
				this.goalSelector.addGoal(4, this.mobsthinknow$smartNetherMeleeGoal);
			}
			return;
		}
		if (!OverworldUndeadFamilies.isSkeletonFamily(skeleton)) {
			return;
		}

		this.goalSelector.removeGoal(this.bowGoal);
		if (this.mobsthinknow$smartCrossbowGoal == null) {
			this.mobsthinknow$smartCrossbowGoal = new SmartSkeletonCrossbowAttackGoal(skeleton);
		} else {
			this.goalSelector.removeGoal(this.mobsthinknow$smartCrossbowGoal);
		}
		if (this.mobsthinknow$smartBowGoal == null) {
			int vanillaInterval = SkeletonBowIntervals.vanillaInterval(skeleton);
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
			// 原版避日是优先级 2、避狼是 3、弓术是 4；贴脸全力逃跑必须先于它们抢占 MOVE/LOOK。
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
		} else if (skeleton.isHolding(Items.CROSSBOW)) {
			// 原版把弩当成近战武器；先撤掉刚在 reassessWeaponGoal 中注册的 meleeGoal。
			this.goalSelector.removeGoal(this.meleeGoal);
			this.goalSelector.addGoal(1, this.mobsthinknow$emergencyDisengageGoal);
			this.goalSelector.addGoal(4, this.mobsthinknow$smartCrossbowGoal);
			if (!this.mobsthinknow$emergencyDisengageGoalCounted) {
				this.mobsthinknow$emergencyDisengageGoalCounted = true;
				SmartSkeletonMetrics.emergencyGoalInstalled();
			}
		}
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		if (OverworldUndeadFamilies.isSkeletonFamily(skeleton)) {
			output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getSkeletonIntelligence());
			SkeletonEscapeSpeedProfile.save(skeleton, output);
		}
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadSkeletonIntelligence(final ValueInput input, final CallbackInfo callbackInfo) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		if (!OverworldUndeadFamilies.isSkeletonFamily(skeleton)) {
			return;
		}
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$skeletonIntelligence = saved == 0 ? 0 : SkeletonIntelligence.clamp(saved);
		SkeletonEscapeSpeedProfile.load(skeleton, input);
		SquadTheatrics.stripLeftoverRoleTag(skeleton);
		SkeletonIntelligenceName.apply(skeleton, this.mobsthinknow$getSkeletonIntelligence());
		SkeletonEscapeSpeedProfile.initialize(skeleton);
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void mobsthinknow$finalizeSkeletonLoadout(
		final ServerLevelAccessor level,
		final DifficultyInstance difficulty,
		final EntitySpawnReason spawnReason,
		final SpawnGroupData groupData,
		final CallbackInfoReturnable<SpawnGroupData> callbackInfo
	) {
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		if (skeleton instanceof WitherSkeleton) {
			// 石剑由 WitherSkeleton.populateDefaultEquipmentSlots 生成后再判定职业，弓手会在这里换装。
			NetherProfessionProfile.assignOnSpawn(skeleton, difficulty, level.getRandom());
			skeleton.reassessWeaponGoal();
			return;
		}
		if (!OverworldUndeadFamilies.isSkeletonFamily(skeleton)) {
			return;
		}
		SkeletonIntelligenceName.apply(skeleton, this.mobsthinknow$getSkeletonIntelligence());
		SkeletonEscapeSpeedProfile.initialize(skeleton);
		// 只有普通骷髅参与随机弩装；流浪者、沼骸和干尸保留原版特殊箭与出生辨识度。
		if (skeleton.getType() == EntityType.SKELETON) {
			SkeletonCrossbowLoadout.maybeEquip(skeleton, skeleton.level().getDifficulty(), ConfigManager.get());
		}
		// 换成弩时 setItemSlot 会重评一次；显式再调用可覆盖其他 Mod 直接修改 ItemStack 的路径。
		skeleton.reassessWeaponGoal();
	}

	@Override
	protected void customServerAiStep(final ServerLevel serverLevel) {
		super.customServerAiStep(serverLevel);
		AbstractSkeleton skeleton = (AbstractSkeleton)(Object)this;
		MobsThinkNowConfig config = ConfigManager.get();
		if (!OverworldUndeadFamilies.isSkeletonFamily(skeleton)
			|| !config.enabled
			|| !config.skeletonAiEnabled
			|| !config.packSurrounding) {
			return;
		}
		LivingEntity target = skeleton.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		boolean visible = skeleton.getSensing().hasLineOfSight(target);
		long now = serverLevel.getGameTime();
		ZombieSquadCoordinator.forLevel(serverLevel).heartbeat(
			skeleton,
			target,
			visible,
			visible ? target.position() : null,
			visible ? now : Long.MIN_VALUE
		);
	}

	@Override
	public int mobsthinknow$getSkeletonIntelligence() {
		if (this.mobsthinknow$skeletonIntelligence == 0) {
			this.mobsthinknow$skeletonIntelligence = this.random.nextInt(SkeletonIntelligence.MAXIMUM)
				+ SkeletonIntelligence.MINIMUM;
		}
		return this.mobsthinknow$skeletonIntelligence;
	}

	@Override
	public void mobsthinknow$setSkeletonIntelligence(final int intelligence) {
		this.mobsthinknow$skeletonIntelligence = SkeletonIntelligence.clamp(intelligence);
	}

	@Override
	public float mobsthinknow$getSkeletonEscapeSpeedFactor() {
		return this.mobsthinknow$skeletonEscapeSpeedFactor;
	}

	@Override
	public void mobsthinknow$setSkeletonEscapeSpeedFactor(final float factor) {
		this.mobsthinknow$skeletonEscapeSpeedFactor = factor;
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
		boolean overworldArcher = OverworldUndeadFamilies.isSkeletonFamily(skeleton)
			&& config.skeletonAiEnabled
			&& config.skeletonPredictiveAim;
		boolean witherHexer = skeleton instanceof WitherSkeleton
			&& config.netherAiEnabled
			&& NetherProfessionProfile.get(skeleton) == NetherProfession.WITHER_SKELETON_HEXER;
		if (!config.enabled || (!overworldArcher && !witherHexer)) {
			return;
		}

		double originalX = (Double)args.get(3);
		double originalZ = (Double)args.get(5);
		double difficultyFactor = SkeletonCombatMath.difficultyPredictionFactor(
			skeleton.level().getDifficulty().getId()
		);
		double predictionStrength = witherHexer
			? config.netherPredictionStrength * 1.10
			: config.skeletonAimPredictionStrength
				* (0.55 + SkeletonIntelligence.get(skeleton) * 0.045);
		SkeletonCombatMath.HorizontalLead lead = SkeletonCombatMath.horizontalLead(
			target.getDeltaMovement().x,
			target.getDeltaMovement().z,
			Math.sqrt(originalX * originalX + originalZ * originalZ),
			predictionStrength * difficultyFactor
		);
		if (lead.x() == 0.0 && lead.z() == 0.0) {
			return;
		}

		args.set(3, originalX + lead.x());
		args.set(5, originalZ + lead.z());
		if (witherHexer) {
			SmartNetherMetrics.netherUndeadPredictedShot();
		} else {
			SmartSkeletonMetrics.predictiveShot();
		}
	}

	@Override
	public NetherProfession mobsthinknow$getNetherProfession() {
		return NetherProfession.fromId(this.entityData.get(mobsthinknow$NETHER_PROFESSION_ID));
	}

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		this.entityData.set(
			mobsthinknow$NETHER_PROFESSION_ID,
			(profession == null ? NetherProfession.NONE : profession).id()
		);
	}
}

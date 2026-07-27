package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.zombie.ReactiveRetreatGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieAttackGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.SquadHurtByTargetGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventoryAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieFoodEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieFoodSearchGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierState;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidTacticsGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieIndividualTraits;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceName;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieTerrainTacticsGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceProfile;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadTheatrics;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieMixin extends Monster implements
	ZombieIntelligenceAccess,
	ZombieBuilderInventoryAccess,
	ZombieVoiceAccess,
	ZombieFluidCarrierAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";

	/** 0 只表示“尚未生成”，对外可见的合法智力值始终是 1～10。 */
	@Unique
	private int mobsthinknow$intelligence;
	/** 独立于双手装备的持久化建筑材料槽；普通生成始终为空。 */
	@Unique
	private ItemStack mobsthinknow$buildingBlocks = ItemStack.EMPTY;
	/** 每只僵尸的固定声线中心，0 表示旧存档尚未生成。 */
	@Unique
	private float mobsthinknow$voiceFactor;
	/** 水/岩浆从满桶离手到回收完成的持久化事务。 */
	@Unique
	private ZombieFluidCarrierState mobsthinknow$fluidCarrierState = ZombieFluidCarrierState.NONE;

	protected ZombieMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "addBehaviourGoals", at = @At("TAIL"))
	private void mobsthinknow$replaceZombieAttackGoal(final CallbackInfo callbackInfo) {
		Zombie zombie = (Zombie)(Object)this;
		if (zombie.getType() != EntityType.ZOMBIE) {
			return;
		}

		boolean hasVanillaAttackGoal = this.goalSelector
			.getAvailableGoals()
			.stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass() == ZombieAttackGoal.class);
		if (!hasVanillaAttackGoal) {
			return;
		}

		this.goalSelector.removeAllGoals(goal -> goal.getClass() == ZombieAttackGoal.class);
		// 独立的高优先级撤退 Goal 不依赖近战追击能否启动；MOVE/LOOK 冲突会自然暂停攻击与小队机动。
		this.goalSelector.addGoal(1, new ReactiveRetreatGoal(zombie));
		// 特殊桶兵优先承担支援/骚扰；已放出的源方块即使热关配置也会先完成回收事务。
		this.goalSelector.addGoal(2, new ZombieFluidTacticsGoal(zombie));
		// 低血觅食位于撤退与攻击之间：有可达食物才接管移动，受击撤退仍可立即抢占。
		this.goalSelector.addGoal(2, new ZombieFoodSearchGoal(zombie));
		// 地形战术与觅食同级但后注册：低血且附近有食物时先保命，其余时间才采集和搭建立柱。
		this.goalSelector.addGoal(2, new ZombieTerrainTacticsGoal(zombie));
		this.goalSelector.addGoal(3, new SmartZombieAttackGoal(zombie, 1.0, false));

		// 换成小队感知版的仇恨反击：队友误伤不转移仇恨，其余语义与原版一致。
		boolean hasVanillaHurtByGoal = this.targetSelector
			.getAvailableGoals()
			.stream()
			.anyMatch(wrapped -> wrapped.getGoal().getClass() == HurtByTargetGoal.class);
		if (hasVanillaHurtByGoal) {
			this.targetSelector.removeAllGoals(goal -> goal.getClass() == HurtByTargetGoal.class);
			this.targetSelector.addGoal(1, new SquadHurtByTargetGoal(zombie));
		}
		SmartZombieMetrics.goalInstalled();
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$saveIntelligence(final ValueOutput output, final CallbackInfo callbackInfo) {
		// 智力是个体特征，必须持久化；小队归属和命令刻意不保存，避免重载世界后引用失效实体。
		output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getIntelligence());
		ZombieBuilderInventory.save((Zombie)(Object)this, output);
		ZombieVoiceProfile.save((Zombie)(Object)this, output);
		ZombieSpecialEquipment.save((Zombie)(Object)this, output);
		// 自动保存若恰好发生在进食换手的 1～2 秒内，额外保存真正的武器/盾牌供读档恢复。
		ZombieFoodEquipment.saveTemporaryEquipment((Zombie)(Object)this, output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadIntelligence(final ValueInput input, final CallbackInfo callbackInfo) {
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$intelligence = saved == 0 ? 0 : ZombieIntelligence.clamp(saved);
		// 实体基础数据（含 CustomName）先于本回调加载；崩溃残留的职业名牌在这里剥掉。
		Zombie zombie = (Zombie)(Object)this;
		SquadTheatrics.stripLeftoverRoleTag(zombie);
		ZombieFoodEquipment.restoreSavedEquipment(zombie, input);
		ZombieBuilderInventory.load(zombie, input);
		ZombieVoiceProfile.load(zombie, input);
		ZombieSpecialEquipment.load(zombie, input);
		if (zombie.getType() == EntityType.ZOMBIE) {
			ZombieIntelligenceName.apply(zombie, this.mobsthinknow$getIntelligence());
		}
	}

	@Inject(method = "convertToZombieType", at = @At("HEAD"))
	private void mobsthinknow$stripRoleTagBeforeConversion(
		final ServerLevel level,
		final EntityType<? extends Zombie> zombieType,
		final CallbackInfo callbackInfo
	) {
		// 原版转化会把 CustomName 原样复制给新实体（僵尸→溺尸等）；转化前剥掉职业名牌，
		// 避免溺尸顶着本 Mod 的名牌继续存在。
		Zombie zombie = (Zombie)(Object)this;
		SquadTheatrics.stripLeftoverRoleTag(zombie);
		ZombieIntelligenceName.removeSyntheticMarker(zombie);
	}

	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void mobsthinknow$equipSquadWeapon(
		final ServerLevelAccessor level,
		final DifficultyInstance difficulty,
		final EntitySpawnReason spawnReason,
		final SpawnGroupData groupData,
		final CallbackInfoReturnable<SpawnGroupData> callbackInfo
	) {
		Zombie zombie = (Zombie)(Object)this;
		if (zombie.getType() == EntityType.ZOMBIE) {
			ZombieIntelligenceName.apply(zombie, this.mobsthinknow$getIntelligence());
			// 提前固化声线；首次环境音与后续小队叫声都会使用同一中心音高。
			ZombieVoiceProfile.factor(zombie);
		}
		// 村民转化等 CONVERSION 路径保留原有装备语义，与原版 populateDefaultEquipmentSlots 的边界一致。
		if (zombie.getType() != EntityType.ZOMBIE || spawnReason == EntitySpawnReason.CONVERSION) {
			return;
		}
		var config = ConfigManager.get();
		ZombieIndividualTraits.applyOnSpawn(zombie, difficulty, this.random, config);
		ZombieSpecialEquipment.maybeEquip(zombie, difficulty, this.random, config);
		ZombieArmory.maybeEquipForSquad(zombie, difficulty, this.random, config);
	}

	@Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
	private void mobsthinknow$leaveFoodForScavengingGoal(
		final ServerLevel level,
		final ItemStack itemStack,
		final CallbackInfoReturnable<Boolean> callbackInfo
	) {
		Zombie zombie = (Zombie)(Object)this;
		if (ZombieFoodSearchGoal.managesFood(zombie, itemStack, ConfigManager.get())) {
			callbackInfo.setReturnValue(false);
		}
	}

	@Override
	public int mobsthinknow$getIntelligence() {
		if (this.mobsthinknow$intelligence == 0) {
			this.mobsthinknow$intelligence = this.random.nextInt(ZombieIntelligence.MAXIMUM) + ZombieIntelligence.MINIMUM;
		}
		return this.mobsthinknow$intelligence;
	}

	@Override
	public void mobsthinknow$setIntelligence(final int intelligence) {
		this.mobsthinknow$intelligence = ZombieIntelligence.clamp(intelligence);
	}

	@Override
	public ItemStack mobsthinknow$getBuildingBlocks() {
		return this.mobsthinknow$buildingBlocks;
	}

	@Override
	public void mobsthinknow$setBuildingBlocks(final ItemStack stack) {
		this.mobsthinknow$buildingBlocks = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
	}

	@Override
	public float mobsthinknow$getVoiceFactor() {
		return this.mobsthinknow$voiceFactor;
	}

	@Override
	public void mobsthinknow$setVoiceFactor(final float factor) {
		this.mobsthinknow$voiceFactor = factor;
	}

	@Override
	public ZombieFluidCarrierState mobsthinknow$getFluidCarrierState() {
		return this.mobsthinknow$fluidCarrierState;
	}

	@Override
	public void mobsthinknow$setFluidCarrierState(final ZombieFluidCarrierState state) {
		this.mobsthinknow$fluidCarrierState = state == null ? ZombieFluidCarrierState.NONE : state;
	}

	/** 原版每次发声的小抖动保留，再乘固定个体声线，幼年僵尸的高音规则也照常叠加。 */
	@Override
	public float getVoicePitch() {
		return super.getVoicePitch() * ZombieVoiceProfile.factor((Zombie)(Object)this);
	}
}

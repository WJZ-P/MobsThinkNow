package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieAttackGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.SquadHurtByTargetGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceAccess;
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
public abstract class ZombieMixin extends Monster implements ZombieIntelligenceAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";

	/** 0 只表示“尚未生成”，对外可见的合法智力值始终是 1～10。 */
	@Unique
	private int mobsthinknow$intelligence;

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
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadIntelligence(final ValueInput input, final CallbackInfo callbackInfo) {
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$intelligence = saved == 0 ? 0 : ZombieIntelligence.clamp(saved);
		// 实体基础数据（含 CustomName）先于本回调加载；崩溃残留的职业名牌在这里剥掉。
		SquadTheatrics.stripLeftoverRoleTag((Zombie)(Object)this);
	}

	@Inject(method = "convertToZombieType", at = @At("HEAD"))
	private void mobsthinknow$stripRoleTagBeforeConversion(
		final ServerLevel level,
		final EntityType<? extends Zombie> zombieType,
		final CallbackInfo callbackInfo
	) {
		// 原版转化会把 CustomName 原样复制给新实体（僵尸→溺尸等）；转化前剥掉职业名牌，
		// 避免溺尸顶着本 Mod 的名牌继续存在。
		SquadTheatrics.stripLeftoverRoleTag((Zombie)(Object)this);
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
		// 村民转化等 CONVERSION 路径保留原有装备语义，与原版 populateDefaultEquipmentSlots 的边界一致。
		if (zombie.getType() != EntityType.ZOMBIE || spawnReason == EntitySpawnReason.CONVERSION) {
			return;
		}
		ZombieArmory.maybeEquipForSquad(zombie, difficulty, this.random, ConfigManager.get());
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
}

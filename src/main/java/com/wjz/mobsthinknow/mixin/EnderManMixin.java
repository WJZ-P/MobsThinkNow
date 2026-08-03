package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.enderman.EndermanCreeperDeliveryGoal;
import com.wjz.mobsthinknow.ai.enderman.EndermanIntelligence;
import com.wjz.mobsthinknow.ai.enderman.EndermanIntelligenceAccess;
import com.wjz.mobsthinknow.ai.enderman.EndermanIntelligenceName;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfession;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfessionAccess;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfessionCombatGoal;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfessionProfile;
import com.wjz.mobsthinknow.ai.enderman.EndermanVoidLancerGoal;
import com.wjz.mobsthinknow.ai.enderman.SmartEndermanMetrics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 普通末影人的智力、同步职业、职业战斗、苦力怕投送和胸前实体挂点接入。 */
@Mixin(EnderMan.class)
public abstract class EnderManMixin extends Monster implements EndermanIntelligenceAccess, EndermanProfessionAccess {
	@Unique
	private static final String mobsthinknow$INTELLIGENCE_TAG = "MobsThinkNowIntelligence";
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$PROFESSION_ID =
		SynchedEntityData.defineId(EnderMan.class, EntityDataSerializers.BYTE);
	@Unique
	private int mobsthinknow$endermanIntelligence;
	@Unique
	private @Nullable EndermanProfessionCombatGoal mobsthinknow$professionCombatGoal;

	protected EnderManMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installProfessionGoals(final CallbackInfo callbackInfo) {
		EnderMan enderman = (EnderMan)(Object)this;
		this.goalSelector.removeAllGoals(goal -> goal.getClass() == MeleeAttackGoal.class);
		this.mobsthinknow$professionCombatGoal = new EndermanProfessionCombatGoal(enderman);
		this.goalSelector.addGoal(1, new EndermanCreeperDeliveryGoal(enderman));
		this.goalSelector.addGoal(2, new EndermanVoidLancerGoal(enderman));
		this.goalSelector.addGoal(2, this.mobsthinknow$professionCombatGoal);
		// 职业 Goal 关闭或不适用时回退到原版近战；较低优先级保证职业战术仍可随配置热切换抢占。
		this.goalSelector.addGoal(3, new MeleeAttackGoal(enderman, 1.0, false));
		SmartEndermanMetrics.goalInstalled();
	}

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void mobsthinknow$defineProfession(
		final SynchedEntityData.Builder builder,
		final CallbackInfo callbackInfo
	) {
		builder.define(mobsthinknow$PROFESSION_ID, EndermanProfession.NONE.id());
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$initializeEndermanIdentity(
		final EntityType<? extends EnderMan> type,
		final Level level,
		final CallbackInfo callbackInfo
	) {
		if (!level.isClientSide() && type == EntityType.ENDERMAN) {
			EnderMan enderman = (EnderMan)(Object)this;
			EndermanIntelligenceName.apply(enderman, this.mobsthinknow$getEndermanIntelligence());
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$saveEndermanIntelligence(final ValueOutput output, final CallbackInfo callbackInfo) {
		output.putInt(mobsthinknow$INTELLIGENCE_TAG, this.mobsthinknow$getEndermanIntelligence());
		EndermanProfessionProfile.save((EnderMan)(Object)this, output);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void mobsthinknow$loadEndermanIntelligence(final ValueInput input, final CallbackInfo callbackInfo) {
		int saved = input.getIntOr(mobsthinknow$INTELLIGENCE_TAG, 0);
		this.mobsthinknow$endermanIntelligence = saved == 0 ? 0 : EndermanIntelligence.clamp(saved);
		EnderMan enderman = (EnderMan)(Object)this;
		EndermanProfessionProfile.load(enderman, input);
		EndermanIntelligenceName.apply(enderman, this.mobsthinknow$getEndermanIntelligence());
	}

	/**
	 * 原版盾牌对任意 LivingEntity 都能结算真实格挡，但“斧类禁用盾牌”只在 Player 覆盖中实现。
	 * 这里给虚空盾卫补上同一组件语义，并把真实成功格挡交给职业 Goal 安排延迟反击。
	 */
	@Override
	protected void blockUsingItem(final ServerLevel level, final LivingEntity attacker) {
		ItemStack blockingItem = this.getItemBlockingWith();
		super.blockUsingItem(level, attacker);
		if (blockingItem == null
			|| EndermanProfessionProfile.get((EnderMan)(Object)this) != EndermanProfession.VOID_GUARD) {
			return;
		}
		if (this.mobsthinknow$professionCombatGoal != null) {
			this.mobsthinknow$professionCombatGoal.onShieldBlock(attacker);
		}
		BlocksAttacks blocksAttacks = blockingItem.get(DataComponents.BLOCKS_ATTACKS);
		float disableSeconds = attacker.getSecondsToDisableBlocking();
		if (blocksAttacks != null && disableSeconds > 0.0F) {
			blocksAttacks.disable(level, (EnderMan)(Object)this, disableSeconds, blockingItem);
		}
	}

	/**
	 * 原版末影人已经有“手持方块”的双臂动作，但默认乘员挂点位于头顶。苦力怕载荷改挂到胸前外侧，
	 * 其脚底约在末影人腰部；乘员关系负责服务端同步和随传送移动，模型 Mixin 只负责双臂包住实体。
	 */
	@Override
	public Vec3 getPassengerRidingPosition(final Entity passenger) {
		if (passenger instanceof Creeper && this.getType() == EntityType.ENDERMAN) {
			float yawRadians = -this.yBodyRot * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(0.0, 0.0, 0.78).yRot(yawRadians);
			return this.position().add(forward).add(0.0, 0.68, 0.0);
		}
		return super.getPassengerRidingPosition(passenger);
	}

	/** 苦力怕是被抱着的载荷而不是驾驶者，不能夺走末影人的 MOVE/LOOK 控制。 */
	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		if (this.getFirstPassenger() instanceof Creeper && this.getType() == EntityType.ENDERMAN) {
			return null;
		}
		return super.getControllingPassenger();
	}

	@Override
	public int mobsthinknow$getEndermanIntelligence() {
		if (this.mobsthinknow$endermanIntelligence == 0) {
			this.mobsthinknow$endermanIntelligence = EndermanIntelligence.roll(this.level().getDifficulty(), this.random);
		}
		return this.mobsthinknow$endermanIntelligence;
	}

	@Override
	public void mobsthinknow$setEndermanIntelligence(final int intelligence) {
		this.mobsthinknow$endermanIntelligence = EndermanIntelligence.clamp(intelligence);
	}

	@Override
	public EndermanProfession mobsthinknow$getEndermanProfession() {
		return EndermanProfession.fromId(this.entityData.get(mobsthinknow$PROFESSION_ID));
	}

	@Override
	public void mobsthinknow$setEndermanProfession(final EndermanProfession profession) {
		this.entityData.set(
			mobsthinknow$PROFESSION_ID,
			(profession == null ? EndermanProfession.NONE : profession).id()
		);
	}
}

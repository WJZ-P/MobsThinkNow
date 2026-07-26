package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieAttackGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligenceAccess;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

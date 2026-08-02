package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import com.wjz.mobsthinknow.ai.nether.SmartNetherUndeadMeleeGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 僵尸猪灵保留原版中立仇恨与矛术，只把持剑时的贴脸 Goal 换成职业近战状态机。 */
@Mixin(ZombifiedPiglin.class)
public abstract class ZombifiedPiglinMixin extends Zombie {
	protected ZombifiedPiglinMixin(final EntityType<? extends Zombie> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$countNetherUndeadController(final CallbackInfo callbackInfo) {
		SmartNetherMetrics.controllerInstalled();
	}

	@Inject(method = "addBehaviourGoals", at = @At("TAIL"))
	private void mobsthinknow$installProfessionMelee(final CallbackInfo callbackInfo) {
		this.goalSelector.removeAllGoals(goal -> goal.getClass() == ZombieAttackGoal.class);
		// 原版 SpearUseGoal 保持 priority 1；持矛时本 Goal 的装备门控为 false，不会争抢 MOVE/LOOK。
		this.goalSelector.addGoal(2, new SmartNetherUndeadMeleeGoal((ZombifiedPiglin)(Object)this));
	}
}

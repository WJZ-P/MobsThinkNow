package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.BlazeChargeAccess;
import com.wjz.mobsthinknow.ai.nether.SmartBlazeAttackGoal;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 安装可热切回原版的烈焰人散兵 Goal，并桥接私有充能模型标志。 */
@Mixin(Blaze.class)
public abstract class BlazeMixin extends Monster implements BlazeChargeAccess {
	protected BlazeMixin(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
	}

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void mobsthinknow$installSmartBlazeGoal(final CallbackInfo callbackInfo) {
		this.goalSelector.addGoal(3, new SmartBlazeAttackGoal((Blaze)(Object)this));
		SmartNetherMetrics.controllerInstalled();
	}

	@Invoker("setCharged")
	protected abstract void mobsthinknow$invokeSetCharged(boolean charged);

	@Override
	public void mobsthinknow$setSmartCharged(final boolean charged) {
		this.mobsthinknow$invokeSetCharged(charged);
	}
}

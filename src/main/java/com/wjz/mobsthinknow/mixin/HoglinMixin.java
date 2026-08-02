package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.HoglinChargeAccess;
import com.wjz.mobsthinknow.ai.nether.HoglinChargeController;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 保留疣猪兽原版数量判断、繁殖与畏惧方块，仅在原版 FIGHT 之后追加冲锋窗口。 */
@Mixin(Hoglin.class)
public abstract class HoglinMixin implements HoglinChargeAccess {
	@Unique
	private final HoglinChargeController mobsthinknow$chargeController = new HoglinChargeController();

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$countController(final CallbackInfo callbackInfo) {
		SmartNetherMetrics.controllerInstalled();
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void mobsthinknow$tickCharge(final ServerLevel level, final CallbackInfo callbackInfo) {
		Hoglin hoglin = (Hoglin)(Object)this;
		this.mobsthinknow$chargeController.tick(level, hoglin, hoglin.isAdult());
	}

	@Override
	public HoglinChargeController.Phase mobsthinknow$getChargePhase() {
		return this.mobsthinknow$chargeController.phase();
	}

	@Override
	public int mobsthinknow$getChargeTicksRemaining() {
		return this.mobsthinknow$chargeController.ticksRemaining();
	}
}

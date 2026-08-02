package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.HoglinChargeAccess;
import com.wjz.mobsthinknow.ai.nether.HoglinChargeController;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 僵尸疣猪兽复用相同冲锋物理，但继续使用其“攻击几乎所有生物”的原版 Brain。 */
@Mixin(Zoglin.class)
public abstract class ZoglinMixin implements HoglinChargeAccess {
	@Unique
	private final HoglinChargeController mobsthinknow$chargeController = new HoglinChargeController();

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$countController(final CallbackInfo callbackInfo) {
		SmartNetherMetrics.controllerInstalled();
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void mobsthinknow$tickCharge(final ServerLevel level, final CallbackInfo callbackInfo) {
		Zoglin zoglin = (Zoglin)(Object)this;
		this.mobsthinknow$chargeController.tick(level, zoglin, !zoglin.isBaby());
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

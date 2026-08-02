package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.HoglinChargeAccess;
import com.wjz.mobsthinknow.ai.nether.HoglinChargeController;
import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionAccess;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 僵尸疣猪兽复用相同冲锋物理，但继续使用其“攻击几乎所有生物”的原版 Brain。 */
@Mixin(Zoglin.class)
public abstract class ZoglinMixin implements HoglinChargeAccess, NetherProfessionAccess {
	@Unique
	private static final EntityDataAccessor<Byte> mobsthinknow$NETHER_PROFESSION =
		SynchedEntityData.defineId(Zoglin.class, EntityDataSerializers.BYTE);

	@Unique
	private final HoglinChargeController mobsthinknow$chargeController = new HoglinChargeController();

	@Inject(method = "defineSynchedData", at = @At("TAIL"))
	private void mobsthinknow$defineNetherProfession(
		final SynchedEntityData.Builder builder,
		final CallbackInfo callbackInfo
	) {
		builder.define(mobsthinknow$NETHER_PROFESSION, NetherProfession.NONE.id());
	}

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

	@Override
	public NetherProfession mobsthinknow$getNetherProfession() {
		return NetherProfession.fromId(
			((Zoglin)(Object)this).getEntityData().get(mobsthinknow$NETHER_PROFESSION)
		);
	}

	@Override
	public void mobsthinknow$setNetherProfession(final NetherProfession profession) {
		((Zoglin)(Object)this).getEntityData().set(
			mobsthinknow$NETHER_PROFESSION,
			(profession == null ? NetherProfession.NONE : profession).id()
		);
	}
}

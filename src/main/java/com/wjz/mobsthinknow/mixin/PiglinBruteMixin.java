package com.wjz.mobsthinknow.mixin;

import com.wjz.mobsthinknow.ai.nether.PiglinBattleLineController;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 猪灵蛮兵占据战线中央，以较短侧移持续给弩手制造射界。 */
@Mixin(PiglinBrute.class)
public abstract class PiglinBruteMixin {
	@Unique
	private final PiglinBattleLineController mobsthinknow$battleLine = new PiglinBattleLineController();

	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$countController(final CallbackInfo callbackInfo) {
		SmartNetherMetrics.controllerInstalled();
	}

	@Inject(method = "customServerAiStep", at = @At("TAIL"))
	private void mobsthinknow$tickBattleLine(final ServerLevel level, final CallbackInfo callbackInfo) {
		this.mobsthinknow$battleLine.tick(level, (PiglinBrute)(Object)this);
	}
}

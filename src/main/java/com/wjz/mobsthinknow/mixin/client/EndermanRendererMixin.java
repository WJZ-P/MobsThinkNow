package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.enderman.EndermanCreeperDeliveryGoal;
import com.wjz.mobsthinknow.client.render.EndermanCarrierRenderStateAccess;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.entity.monster.EnderMan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 把客户端已同步的真实乘员关系投影到本帧末影人模型快照。 */
@Mixin(EndermanRenderer.class)
public abstract class EndermanRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mobsthinknow$extractHeldCreeper(
		final EnderMan enderman,
		final EndermanRenderState state,
		final float partialTicks,
		final CallbackInfo callbackInfo
	) {
		((EndermanCarrierRenderStateAccess)state).mobsthinknow$setHoldingCreeper(
			EndermanCreeperDeliveryGoal.isCarryingCreeper(enderman)
		);
	}
}

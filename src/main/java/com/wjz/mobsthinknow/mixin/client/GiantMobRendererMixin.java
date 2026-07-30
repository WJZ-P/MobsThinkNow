package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.giant.GiantPassengerLayout;
import com.wjz.mobsthinknow.client.render.GiantCarrierRenderStateAccess;
import net.minecraft.client.renderer.entity.GiantMobRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.world.entity.monster.Giant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将客户端已同步的两个真实手部乘员投影到本帧巨人模型快照。 */
@Mixin(GiantMobRenderer.class)
public abstract class GiantMobRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mobsthinknow$extractHandPayloads(
		final Giant giant,
		final ZombieRenderState state,
		final float partialTicks,
		final CallbackInfo callbackInfo
	) {
		int count = GiantPassengerLayout.payloads(giant).size();
		((GiantCarrierRenderStateAccess)state).mobsthinknow$setGiantHeldPayloads(count >= 1, count >= 2);
	}
}

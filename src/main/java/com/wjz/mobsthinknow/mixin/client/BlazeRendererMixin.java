package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.NetherProfessionTextures;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 烈焰人职业贴图选择。 */
@Mixin(BlazeRenderer.class)
public abstract class BlazeRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final LivingEntityRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().netherProfessionSkins) {
			return;
		}
		Identifier texture = NetherProfessionTextures.blaze(
			((NetherProfessionRenderStateAccess)state).mobsthinknow$getNetherProfession()
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}

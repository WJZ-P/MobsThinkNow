package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.NetherProfessionTextures;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.client.renderer.entity.ZoglinRenderer;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 成年僵尸疣猪兽职业贴图选择。 */
@Mixin(ZoglinRenderer.class)
public abstract class ZoglinRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/HoglinRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final HoglinRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().netherProfessionSkins || state.isBaby) {
			return;
		}
		Identifier texture = NetherProfessionTextures.hoglin(
			((NetherProfessionRenderStateAccess)state).mobsthinknow$getNetherProfession(),
			true
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}

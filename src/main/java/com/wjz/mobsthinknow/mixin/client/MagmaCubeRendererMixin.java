package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.NetherProfessionTextures;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.client.renderer.entity.MagmaCubeRenderer;
import net.minecraft.client.renderer.entity.state.SlimeRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 岩浆怪职业贴图选择；所有体型沿用原版相同 UV。 */
@Mixin(MagmaCubeRenderer.class)
public abstract class MagmaCubeRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SlimeRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final SlimeRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().netherProfessionSkins) {
			return;
		}
		Identifier texture = NetherProfessionTextures.magmaCube(
			((NetherProfessionRenderStateAccess)state).mobsthinknow$getNetherProfession()
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}

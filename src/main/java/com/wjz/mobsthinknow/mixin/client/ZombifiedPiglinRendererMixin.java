package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.NetherProfessionTextures;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.client.renderer.entity.ZombifiedPiglinRenderer;
import net.minecraft.client.renderer.entity.state.ZombifiedPiglinRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 成年僵尸猪灵按同步职业稳定选图；幼年体继续使用原版独立 UV。 */
@Mixin(ZombifiedPiglinRenderer.class)
public abstract class ZombifiedPiglinRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/ZombifiedPiglinRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final ZombifiedPiglinRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().netherProfessionSkins || state.isBaby) {
			return;
		}
		Identifier texture = NetherProfessionTextures.zombifiedPiglin(
			((NetherProfessionRenderStateAccess)state).mobsthinknow$getNetherProfession()
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}

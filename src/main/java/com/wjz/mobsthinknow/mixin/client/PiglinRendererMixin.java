package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.NetherProfessionTextures;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.state.PiglinRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 成年猪灵与蛮兵按同步职业选择稳定贴图，幼年体继续使用原版专用 UV。 */
@Mixin(PiglinRenderer.class)
public abstract class PiglinRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/PiglinRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final PiglinRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().netherProfessionSkins || state.isBaby) {
			return;
		}
		Identifier texture = NetherProfessionTextures.piglin(
			((NetherProfessionRenderStateAccess)state).mobsthinknow$getNetherProfession(),
			state.isBrute
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}

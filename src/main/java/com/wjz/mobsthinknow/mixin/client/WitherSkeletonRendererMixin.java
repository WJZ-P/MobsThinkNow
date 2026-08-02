package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.NetherProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.NetherProfessionTextures;
import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 凋灵骷髅的剑士、收割者与咒火弓手从同一同步职业快照选择原生 UV 贴图。 */
@Mixin(WitherSkeletonRenderer.class)
public abstract class WitherSkeletonRendererMixin {
	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final SkeletonRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		if (!ConfigManager.get().netherProfessionSkins) {
			return;
		}
		Identifier texture = NetherProfessionTextures.witherSkeleton(
			((NetherProfessionRenderStateAccess)state).mobsthinknow$getNetherProfession()
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}
}

package com.wjz.mobsthinknow.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 仅供末影人渲染器复用原版受保护的渲染层注册入口。 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererLayerInvoker {
	@Invoker("addLayer")
	boolean mobsthinknow$invokeAddLayer(RenderLayer<?, ?> layer);
}

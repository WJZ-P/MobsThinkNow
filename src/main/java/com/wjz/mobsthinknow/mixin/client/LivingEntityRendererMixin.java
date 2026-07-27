package com.wjz.mobsthinknow.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 给通用僵尸渲染器补上玩家鞘翅飞行时已有的水平机身旋转。 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(method = "setupRotations", at = @At("TAIL"))
	private void mobsthinknow$rotateGlidingZombie(
		final LivingEntityRenderState state,
		final PoseStack poseStack,
		final float bodyRot,
		final float entityScale,
		final CallbackInfo callbackInfo
	) {
		if (state instanceof ZombieRenderState zombieState
			&& zombieState.isFallFlying
			&& !zombieState.isAutoSpinAttack) {
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - zombieState.xRot));
		}
	}
}

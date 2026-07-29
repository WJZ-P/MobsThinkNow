package com.wjz.mobsthinknow.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.AbstractSkeletonRenderer;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 给原版骷髅渲染器补齐弩的双手装填与举弩姿态。 */
@Mixin(AbstractSkeletonRenderer.class)
public abstract class AbstractSkeletonRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/monster/skeleton/AbstractSkeleton;Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;F)V",
		at = @At("TAIL")
	)
	private void mobsthinknow$keepCrossbowPoseFromMeleeOverride(
		final AbstractSkeleton skeleton,
		final SkeletonRenderState state,
		final float partialTicks,
		final CallbackInfo callbackInfo
	) {
		// SkeletonModel 会在 aggressive && !isHoldingBow 时强制套用近战抬臂动画；
		// 弩同样属于双手远程姿态，因此借用这个渲染态标志阻止近战动画覆盖 ArmPose。
		if (skeleton.getMainHandItem().is(Items.CROSSBOW)) {
			state.isHoldingBow = true;
		}
	}

	@Inject(
		method = "getArmPose(Lnet/minecraft/world/entity/monster/skeleton/AbstractSkeleton;Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$crossbowPose(
		final AbstractSkeleton skeleton,
		final HumanoidArm arm,
		final CallbackInfoReturnable<HumanoidModel.ArmPose> callbackInfo
	) {
		if (skeleton.getMainArm() != arm || !skeleton.getMainHandItem().is(Items.CROSSBOW)) {
			return;
		}
		callbackInfo.setReturnValue(
			skeleton.isUsingItem()
				? HumanoidModel.ArmPose.CROSSBOW_CHARGE
				: CrossbowItem.isCharged(skeleton.getMainHandItem())
					? HumanoidModel.ArmPose.CROSSBOW_HOLD
					: HumanoidModel.ArmPose.EMPTY
		);
	}
}

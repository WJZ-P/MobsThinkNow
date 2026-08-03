package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.ai.enderman.EndermanCreeperDeliveryGoal;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfessionProfile;
import com.wjz.mobsthinknow.client.render.EndermanCarrierRenderStateAccess;
import com.wjz.mobsthinknow.client.render.EndermanItemInHandLayer;
import com.wjz.mobsthinknow.client.render.EndermanProfessionRenderStateAccess;
import com.wjz.mobsthinknow.client.render.EndermanProfessionTextures;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 把职业、双手装备与真实乘员关系投影到本帧末影人模型快照。 */
@Mixin(EndermanRenderer.class)
public abstract class EndermanRendererMixin {
	@Inject(method = "<init>", at = @At("TAIL"))
	private void mobsthinknow$installHeldItemLayer(
		final EntityRendererProvider.Context context,
		final CallbackInfo callbackInfo
	) {
		EndermanRenderer renderer = (EndermanRenderer)(Object)this;
		((LivingEntityRendererLayerInvoker)renderer).mobsthinknow$invokeAddLayer(new EndermanItemInHandLayer(renderer));
	}

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void mobsthinknow$extractProfessionAndHeldCreeper(
		final EnderMan enderman,
		final EndermanRenderState state,
		final float partialTicks,
		final CallbackInfo callbackInfo
	) {
		((EndermanCarrierRenderStateAccess)state).mobsthinknow$setHoldingCreeper(
			EndermanCreeperDeliveryGoal.isCarryingCreeper(enderman)
		);
		((EndermanProfessionRenderStateAccess)state).mobsthinknow$setEndermanProfession(
			EndermanProfessionProfile.get(enderman)
		);
		configureArmPoses(enderman, state);
	}

	@Inject(
		method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/EndermanRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void mobsthinknow$selectProfessionTexture(
		final EndermanRenderState state,
		final CallbackInfoReturnable<Identifier> callbackInfo
	) {
		// 客户端只信任服务端同步的职业 byte，不读取可能不同步的本地配置文件。
		Identifier texture = EndermanProfessionTextures.texture(
			((EndermanProfessionRenderStateAccess)state).mobsthinknow$getEndermanProfession()
		);
		if (texture != null) {
			callbackInfo.setReturnValue(texture);
		}
	}

	private static void configureArmPoses(final EnderMan enderman, final EndermanRenderState state) {
		if (!state.carriedBlock.isEmpty()) {
			state.rightHandItemState.clear();
			state.leftHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
			state.leftHandItemStack = ItemStack.EMPTY;
			state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
			state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
			return;
		}
		state.rightArmPose = poseFor(enderman, state, HumanoidArm.RIGHT);
		state.leftArmPose = poseFor(enderman, state, HumanoidArm.LEFT);
	}

	private static HumanoidModel.ArmPose poseFor(
		final EnderMan enderman,
		final EndermanRenderState state,
		final HumanoidArm arm
	) {
		ItemStack stack = arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		if (stack.isEmpty()) {
			return HumanoidModel.ArmPose.EMPTY;
		}
		boolean usedArm = enderman.isUsingItem()
			&& enderman.getUsedItemHand() == (arm == state.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
		if (usedArm && stack.getUseAnimation() == ItemUseAnimation.BLOCK) {
			return HumanoidModel.ArmPose.BLOCK;
		}
		if (stack.has(net.minecraft.core.component.DataComponents.KINETIC_WEAPON)) {
			return HumanoidModel.ArmPose.SPEAR;
		}
		return HumanoidModel.ArmPose.ITEM;
	}
}

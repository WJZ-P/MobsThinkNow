package com.wjz.mobsthinknow.mixin.client;

import com.wjz.mobsthinknow.client.render.EndermanArmPoseSelection;
import com.wjz.mobsthinknow.client.render.EndermanCarrierRenderStateAccess;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将原版“拿方块”的双臂语义扩展到真实苦力怕实体，并稍微内收长臂形成环抱动作。 */
@Mixin(EndermanModel.class)
public abstract class EndermanModelMixin {
	@Inject(method = "setupAnim", at = @At("TAIL"))
	private void mobsthinknow$poseArmsAroundCreeper(
		final EndermanRenderState state,
		final CallbackInfo callbackInfo
	) {
		if (!((EndermanCarrierRenderStateAccess)state).mobsthinknow$isHoldingCreeper()) {
			this.mobsthinknow$poseProfessionEquipment(state);
			return;
		}
		HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
		ModelPart rightArm = model.rightArm;
		ModelPart leftArm = model.leftArm;
		rightArm.xRot = -0.82F;
		leftArm.xRot = -0.82F;
		rightArm.yRot = -0.18F;
		leftArm.yRot = 0.18F;
		rightArm.zRot = 0.08F;
		leftArm.zRot = -0.08F;
	}

	private void mobsthinknow$poseProfessionEquipment(final EndermanRenderState state) {
		HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
		poseArm(
			model.rightArm,
			state.rightArmPose,
			false,
			EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.RIGHT)
		);
		poseArm(
			model.leftArm,
			state.leftArmPose,
			true,
			EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.LEFT)
		);
	}

	private static void poseArm(
		final ModelPart arm,
		final HumanoidModel.ArmPose pose,
		final boolean left,
		final boolean usingThisArm
	) {
		if (pose == HumanoidModel.ArmPose.BLOCK) {
			arm.xRot = -0.92F;
			arm.yRot = left ? 0.52F : -0.52F;
			arm.zRot = left ? -0.14F : 0.14F;
		} else if (pose == HumanoidModel.ArmPose.SPEAR) {
			arm.xRot = usingThisArm ? -1.32F : -0.88F;
			arm.yRot = left ? 0.08F : -0.08F;
			arm.zRot = left ? -0.04F : 0.04F;
		}
	}
}

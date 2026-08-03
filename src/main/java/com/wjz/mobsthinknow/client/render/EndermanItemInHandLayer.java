package com.wjz.mobsthinknow.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.effects.SpearAnimations;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwingAnimationType;

/** 把末影人的武器挂到长臂末端，同时保留盾牌贴合前臂的原版观感。 */
@Environment(EnvType.CLIENT)
public final class EndermanItemInHandLayer
	extends ItemInHandLayer<EndermanRenderState, EndermanModel<EndermanRenderState>> {
	public EndermanItemInHandLayer(
		final RenderLayerParent<EndermanRenderState, EndermanModel<EndermanRenderState>> renderer
	) {
		super(renderer);
	}

	@Override
	protected void submitArmWithItem(
		final EndermanRenderState state,
		final ItemStackRenderState item,
		final ItemStack itemStack,
		final HumanoidArm arm,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords
	) {
		if (item.isEmpty()) {
			return;
		}

		poseStack.pushPose();
		this.getParentModel().translateToHand(state, arm, poseStack);
		// 此时坐标系已经跟随手臂旋转，沿局部 Y 正方向移动才能准确到达长臂末端。
		poseStack.translate(
			0.0F,
			EndermanHeldItemPlacement.localArmYOffset(itemStack.has(DataComponents.BLOCKS_ATTACKS)),
			0.0F
		);
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.translate((arm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);

		if (state.attackTime > 0.0F
			&& state.attackArm == arm
			&& state.swingAnimationType == SwingAnimationType.STAB) {
			SpearAnimations.thirdPersonAttackItem(state, poseStack);
		}

		float ticksUsingItem = state.ticksUsingItem(arm);
		if (ticksUsingItem != 0.0F) {
			(arm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose)
				.animateUseItem(state, poseStack, ticksUsingItem, arm, itemStack);
		}

		item.submit(poseStack, submitNodeCollector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
		poseStack.popPose();
	}
}

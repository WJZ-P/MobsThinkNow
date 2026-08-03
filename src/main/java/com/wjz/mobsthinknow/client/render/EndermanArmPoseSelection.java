package com.wjz.mobsthinknow.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** 把主手/副手使用状态准确映射到末影人的右臂或左臂。 */
@Environment(EnvType.CLIENT)
public final class EndermanArmPoseSelection {
	private EndermanArmPoseSelection() {
	}

	public static boolean isUsingArm(final EndermanRenderState state, final HumanoidArm arm) {
		if (!state.isUsingItem) {
			return false;
		}
		boolean requestedMainHand = state.useItemHand == InteractionHand.MAIN_HAND;
		boolean requestedMainArm = arm == state.mainArm;
		return requestedMainHand == requestedMainArm;
	}
}

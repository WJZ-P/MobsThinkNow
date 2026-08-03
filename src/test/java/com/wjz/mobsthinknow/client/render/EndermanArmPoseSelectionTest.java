package com.wjz.mobsthinknow.client.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;

class EndermanArmPoseSelectionTest {
	@Test
	void mapsItemUseToOnlyThePhysicalArmHoldingThatHand() {
		EndermanRenderState state = new EndermanRenderState();
		state.isUsingItem = true;
		state.mainArm = HumanoidArm.RIGHT;
		state.useItemHand = InteractionHand.OFF_HAND;
		assertFalse(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.RIGHT));
		assertTrue(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.LEFT));

		state.useItemHand = InteractionHand.MAIN_HAND;
		assertTrue(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.RIGHT));
		assertFalse(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.LEFT));

		state.mainArm = HumanoidArm.LEFT;
		state.useItemHand = InteractionHand.OFF_HAND;
		assertTrue(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.RIGHT));
		assertFalse(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.LEFT));

		state.useItemHand = InteractionHand.MAIN_HAND;
		assertFalse(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.RIGHT));
		assertTrue(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.LEFT));

		state.isUsingItem = false;
		assertFalse(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.RIGHT));
		assertFalse(EndermanArmPoseSelection.isUsingArm(state, HumanoidArm.LEFT));
	}
}

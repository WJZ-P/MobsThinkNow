package com.wjz.mobsthinknow.ai.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

class ZombieFoodSearchGoalTest {
	@Test
	void foodSearchRequiresStrictlyLessThanHalfHealth() {
		assertTrue(ZombieFoodSearchGoal.isBelowFoodThreshold(9.99F, 20.0F));
		assertFalse(ZombieFoodSearchGoal.isBelowFoodThreshold(10.0F, 20.0F));
		assertFalse(ZombieFoodSearchGoal.isBelowFoodThreshold(0.0F, 20.0F));
	}

	@Test
	void intelligenceScalesEachSearchOpportunity() {
		assertEquals(0.0, ZombieFoodSearchGoal.searchChance(5, 6));
		assertEquals(0.25, ZombieFoodSearchGoal.searchChance(6, 6));
		assertEquals(0.65, ZombieFoodSearchGoal.searchChance(10, 6));
	}

	@Test
	void weaponInMainHandMovesFoodToOffhand() {
		assertEquals(InteractionHand.MAIN_HAND, ZombieFoodSearchGoal.preferredFoodHand(false));
		assertEquals(InteractionHand.OFF_HAND, ZombieFoodSearchGoal.preferredFoodHand(true));
	}
}

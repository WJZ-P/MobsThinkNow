package com.wjz.mobsthinknow.ai.enderman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class EndermanCreeperDeliveryGoalTest {
	@Test
	void defaultThresholdAssignsExactlyTheFirstEightyPercentToFrontDelivery() {
		assertEquals(
			EndermanCreeperDeliveryGoal.DeliverySide.FRONT,
			EndermanCreeperDeliveryGoal.chooseDeliverySide(0.0, 0.80)
		);
		assertEquals(
			EndermanCreeperDeliveryGoal.DeliverySide.FRONT,
			EndermanCreeperDeliveryGoal.chooseDeliverySide(0.799_999, 0.80)
		);
		assertEquals(
			EndermanCreeperDeliveryGoal.DeliverySide.REAR,
			EndermanCreeperDeliveryGoal.chooseDeliverySide(0.80, 0.80)
		);
		assertEquals(
			EndermanCreeperDeliveryGoal.DeliverySide.REAR,
			EndermanCreeperDeliveryGoal.chooseDeliverySide(0.999_999, 0.80)
		);
	}

	@Test
	void deliveryOffsetKeepsFrontAndRearOnTheirSelectedSide() {
		Vec3 look = new Vec3(1.0, 0.0, 0.0);
		Vec3 front = EndermanCreeperDeliveryGoal.deliveryOffset(
			look,
			3.0,
			EndermanCreeperDeliveryGoal.DeliverySide.FRONT,
			0.30,
			0.88
		);
		Vec3 rear = EndermanCreeperDeliveryGoal.deliveryOffset(
			look,
			3.0,
			EndermanCreeperDeliveryGoal.DeliverySide.REAR,
			-0.46,
			1.12
		);

		assertTrue(front.dot(look) > 0.0, "Front candidate crossed behind the player's look plane.");
		assertTrue(rear.dot(look) < 0.0, "Rear candidate crossed in front of the player's look plane.");
		// Vec3#yRot 接收 float 角度，长度会带入约 1e-4 的单精度三角函数误差。
		assertEquals(2.64, front.length(), 2.0E-4);
		assertEquals(3.36, rear.length(), 2.0E-4);
	}
}

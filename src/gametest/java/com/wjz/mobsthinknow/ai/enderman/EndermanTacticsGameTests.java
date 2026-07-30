package com.wjz.mobsthinknow.ai.enderman;

import com.wjz.mobsthinknow.config.ConfigManager;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** 从真实 Mixin 实体、乘员同步和状态机验证末影人苦力怕投送。 */
public final class EndermanTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void endermanMixinInstallsDeliveryGoalAndPersistentIdentity(final GameTestHelper helper) {
		long before = SmartEndermanMetrics.snapshot().installedGoals();
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);

		helper.assertTrue(
			SmartEndermanMetrics.snapshot().installedGoals() == before + 1,
			"Enderman construction did not install exactly one creeper-delivery goal."
		);
		int intelligence = EndermanIntelligence.get(enderman);
		helper.assertTrue(intelligence >= 1 && intelligence <= 10, "Enderman intelligence escaped the 1-10 range.");
		helper.assertTrue(
			enderman.getCustomName() != null && enderman.getCustomName().getString().endsWith("[" + intelligence + "]"),
			"Natural enderman name did not expose its stable intelligence."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void creeperPassengerUsesChestAttachmentAndNeverDrivesCarrier(final GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		enderman.setNoAi(true);
		creeper.setNoAi(true);
		enderman.setYBodyRot(0.0F);
		helper.assertTrue(creeper.startRiding(enderman, true, true), "Creeper fixture could not mount its enderman carrier.");
		enderman.positionRider(creeper);

		Vec3 offset = creeper.position().subtract(enderman.position());
		helper.assertTrue(Math.abs(offset.x) < 0.05, "Chest payload drifted sideways at zero body yaw: " + offset);
		helper.assertTrue(offset.z > 0.70 && offset.z < 0.86, "Chest payload was not held in front: " + offset);
		helper.assertTrue(offset.y > 0.60 && offset.y < 0.76, "Chest payload was not held at waist height: " + offset);
		helper.assertTrue(enderman.getControllingPassenger() == null, "Held creeper incorrectly took MOVE/LOOK control.");
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void hostileEndermanPicksTeleportsDropsAndIgnitesNearbyCreeper(final GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		enderman.setNoAi(true);
		creeper.setNoAi(true);
		EndermanIntelligence.set(enderman, 10);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 playerFeet = helper.absoluteVec(new Vec3(16.5, 2.0, 4.5));
		// 朝 +X 看，明确背对位于西侧的末影人，避免把原版凝视冻结误当成投送失败。
		player.snapTo(playerFeet.x, playerFeet.y, playerFeet.z, -90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "The delivery target fixture was not added.");
		enderman.setTarget(player);
		helper.assertTrue(enderman.getTarget() == player, "The hostile-player fixture was not retained as the target.");
		helper.assertTrue(
			!enderman.isLookingAtMe(player, 0.025, true, false, enderman.getEyeY()),
			"The mock player unexpectedly stared at the enderman."
		);
		helper.assertTrue(
			ConfigManager.get().enabled
				&& ConfigManager.get().endermanAiEnabled
				&& ConfigManager.get().endermanCreeperDelivery,
			"The production enderman delivery configuration was disabled."
		);
		helper.assertTrue(
			!enderman.isVehicle() && enderman.getCarriedBlock() == null && enderman.distanceToSqr(player) >= 25.0,
			"The enderman fixture violated a delivery precondition."
		);
		helper.assertTrue(
			!creeper.isPassenger() && !creeper.isVehicle() && !creeper.isIgnited() && creeper.getSwelling(1.0F) < 0.20F,
			"The creeper fixture was not an idle transport candidate."
		);

		EndermanCreeperDeliveryGoal goal = new EndermanCreeperDeliveryGoal(enderman);
		helper.assertTrue(goal.canUse(), "An already-hostile enderman did not reserve its adjacent idle creeper.");
		goal.start();
		goal.tick();
		helper.assertTrue(creeper.getVehicle() == enderman, "The adjacent creeper was not picked up as a real passenger.");
		helper.assertTrue(goal.phase() == EndermanCreeperDeliveryGoal.Phase.HOLDING, "Pickup skipped the visible holding phase.");

		for (int tick = 0; tick < 30 && !goal.hasReleasedPayload(); tick++) {
			goal.tick();
		}
		helper.assertTrue(goal.hasReleasedPayload(), "The held creeper was never released near the hostile player.");
		helper.assertTrue(!creeper.isPassenger(), "Released creeper remained attached to the enderman.");
		helper.assertTrue(creeper.isIgnited() && creeper.getSwellDir() > 0, "Delivered creeper did not enter a committed fuse.");
		helper.assertTrue(creeper.getTarget() == player, "Delivered creeper lost the enderman's hostile player target.");
		helper.assertTrue(
			creeper.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).lengthSqr() <= 7.0 * 7.0,
			"Delivered creeper landed too far from the target player."
		);
		goal.stop();
		player.discard();
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

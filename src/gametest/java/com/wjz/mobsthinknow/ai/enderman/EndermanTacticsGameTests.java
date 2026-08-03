package com.wjz.mobsthinknow.ai.enderman;

import com.wjz.mobsthinknow.config.ConfigManager;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 从真实 Mixin 实体、职业装备、乘员同步和状态机验证末影人战术。 */
public final class EndermanTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void endermanMixinInstallsDeliveryGoalAndPersistentIdentity(final GameTestHelper helper) {
		long before = SmartEndermanMetrics.snapshot().installedGoals();
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		enderman.finalizeSpawn(
			helper.getLevel(),
			helper.getLevel().getCurrentDifficultyAt(enderman.blockPosition()),
			EntitySpawnReason.NATURAL,
			null
		);

		helper.assertTrue(
			SmartEndermanMetrics.snapshot().installedGoals() == before + 1,
			"Enderman construction did not install exactly one creeper-delivery goal."
		);
		int intelligence = EndermanIntelligence.get(enderman);
		EndermanProfession profession = EndermanProfessionProfile.get(enderman);
		helper.assertTrue(intelligence >= 1 && intelligence <= 10, "Enderman intelligence escaped the 1-10 range.");
		helper.assertTrue(profession != EndermanProfession.NONE, "Natural enderman did not receive a profession.");
		switch (profession) {
			case RIFTBLADE -> helper.assertTrue(enderman.isHolding(Items.IRON_SWORD), "Riftblade lost its sword.");
			case VOID_GUARD -> helper.assertTrue(
				enderman.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS),
				"Void Guard lost its real blocking shield."
			);
			case VOID_LANCER -> helper.assertTrue(
				enderman.getMainHandItem().has(DataComponents.KINETIC_WEAPON),
				"Void Lancer lost its kinetic spear."
			);
			case CREEPER_HERALD -> helper.assertTrue(
				enderman.getMainHandItem().isEmpty() && enderman.getOffhandItem().isEmpty(),
				"Creeper Herald should keep both hands free for its payload."
			);
			case NONE -> throw new IllegalStateException("NONE was checked above.");
		}
		helper.assertTrue(
			enderman.getCustomName() != null && enderman.getCustomName().getString().endsWith("[" + intelligence + "]"),
			"Natural enderman name did not expose its stable intelligence."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 60, padding = 4)
	public void professionlessEndermanRetainsVanillaMeleeFallback(final GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 2, 4);
		target.setNoAi(true);
		EndermanProfessionProfile.applyShowcaseLoadout(enderman, EndermanProfession.NONE);
		enderman.setTarget(target);
		float initialHealth = target.getHealth();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			enderman.setTarget(target);
			if (target.getHealth() < initialHealth) {
				helper.succeed();
				return;
			}
			if (elapsed[0] >= 45) {
				helper.assertTrue(
					false,
					"An enderman without an active profession lost its vanilla melee fallback."
				);
			}
		});
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

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 40, padding = 4)
	public void hostileEndermanPicksTeleportsDropsAndIgnitesNearbyCreeper(final GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		enderman.setNoAi(true);
		creeper.setNoAi(true);
		EndermanIntelligence.set(enderman, 10);
		EndermanProfessionProfile.applyShowcaseLoadout(enderman, EndermanProfession.CREEPER_HERALD);

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
			helper.assertTrue(
				goal.canContinueToUse(),
				"A committed delivery was cancelled after appearing inside the player's view."
			);
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

		EndermanCreeperDeliveryGoal.DeliverySide side = goal.deliverySide();
		helper.assertTrue(side != null, "The delivery never committed a front/rear side.");
		Vec3 playerLook = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		Vec3 playerToPayload = creeper.position().subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
		double alignment = playerLook.dot(playerToPayload);
		if (side == EndermanCreeperDeliveryGoal.DeliverySide.FRONT) {
			helper.assertTrue(alignment >= 0.70, "Front delivery escaped the visible forward cone: " + alignment);
			helper.assertTrue(player.hasLineOfSight(creeper), "Front delivery was geometrically ahead but visually occluded.");
		} else {
			helper.assertTrue(alignment <= -0.50, "Rear delivery crossed into the player's forward half: " + alignment);
		}

		int retreatDelay = goal.retreatDelayTicks();
		helper.assertTrue(retreatDelay >= 5 && retreatDelay <= 8, "Retreat pause escaped the 5-8 tick window.");
		Vec3 revealPosition = enderman.position();
		for (int tick = 1; tick < retreatDelay; tick++) {
			helper.assertTrue(goal.canContinueToUse(), "Retreat stopped during the deliberate reveal pause.");
			goal.tick();
			helper.assertTrue(
				enderman.position().distanceToSqr(revealPosition) < 1.0E-6,
				"Enderman left before its short post-drop reveal pause elapsed."
			);
		}
		for (int tick = 0; tick < 30 && !goal.hasCompletedRetreat(); tick++) {
			helper.assertTrue(goal.canContinueToUse(), "Enderman abandoned a failed retreat teleport instead of retrying.");
			goal.tick();
		}
		helper.assertTrue(goal.hasCompletedRetreat(), "Enderman never completed its repeated post-drop retreat attempts.");
		helper.assertTrue(
			enderman.position().subtract(creeper.position()).multiply(1.0, 0.0, 1.0).lengthSqr() >= 12.0 * 12.0,
			"Enderman retreat remained inside the delivered creeper's blast pressure zone."
		);
		goal.stop();
		player.discard();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 30, padding = 4)
	public void voidGuardRaisesRealShieldThenOpensDelayedCounterWindow(final GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		enderman.setNoAi(true);
		EndermanIntelligence.set(enderman, 9);
		EndermanProfessionProfile.applyShowcaseLoadout(enderman, EndermanProfession.VOID_GUARD);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 playerFeet = helper.absoluteVec(new Vec3(5.4, 2.0, 4.5));
		player.snapTo(playerFeet.x, playerFeet.y, playerFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "The shield counter target was not added.");
		enderman.setTarget(player);

		EndermanProfessionCombatGoal goal = new EndermanProfessionCombatGoal(enderman);
		helper.assertTrue(goal.canUse(), "Void Guard profession did not activate its combat goal.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			enderman.isUsingItem()
				&& enderman.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND
				&& enderman.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS),
			"Void Guard did not raise a real offhand shield before attacking."
		);

		float healthBeforeCounter = player.getHealth();
		goal.onShieldBlock(player);
		helper.assertTrue(goal.counterFromBlock(), "A real shield-block signal did not schedule a counter.");
		boolean sawUnshieldedWindow = false;
		for (int tick = 0; tick < 9; tick++) {
			goal.tick();
			sawUnshieldedWindow |= !enderman.isUsingItem();
		}
		helper.assertTrue(sawUnshieldedWindow, "Void Guard never visibly lowered its shield for the counterattack.");
		helper.assertTrue(player.getHealth() < healthBeforeCounter, "Delayed Void Guard counterattack never hit its target.");
		goal.stop();
		player.discard();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 30, padding = 4)
	public void voidLancerUsesVanillaKineticSpearStateAfterFlankSetup(final GameTestHelper helper) {
		EnderMan enderman = helper.spawn(EntityType.ENDERMAN, 4, 2, 4);
		enderman.setNoAi(true);
		EndermanIntelligence.set(enderman, 9);
		EndermanProfessionProfile.applyShowcaseLoadout(enderman, EndermanProfession.VOID_LANCER);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 playerFeet = helper.absoluteVec(new Vec3(12.0, 2.0, 4.5));
		player.snapTo(playerFeet.x, playerFeet.y, playerFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "The spear target was not added.");
		enderman.setTarget(player);

		EndermanVoidLancerGoal goal = new EndermanVoidLancerGoal(enderman);
		helper.assertTrue(goal.canUse(), "Void Lancer profession did not activate the spear goal.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			enderman.getMainHandItem().has(DataComponents.KINETIC_WEAPON),
			"Void Lancer command loadout was not a real kinetic spear."
		);
		helper.assertTrue(
			enderman.isUsingItem()
				&& enderman.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND,
			"Void Lancer did not enter the vanilla spear charge/use state."
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

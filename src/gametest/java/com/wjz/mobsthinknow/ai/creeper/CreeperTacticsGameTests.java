package com.wjz.mobsthinknow.ai.creeper;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/** 从真实实体、感知与地面导航验证苦力怕接敌和引信状态机。 */
public final class CreeperTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void creeperMixinInstallsThreeGoalsAndAppliesPersistentIdentity(final GameTestHelper helper) {
		long before = SmartCreeperMetrics.snapshot().installedGoals();
		Creeper creeper = helper.spawn(EntityType.CREEPER, 2, 2, 2);

		helper.assertTrue(
			SmartCreeperMetrics.snapshot().installedGoals() == before + 3,
			"Creeper construction did not install evacuation plus smart approach and swell goals."
		);
		int intelligence = CreeperIntelligence.get(creeper);
		helper.assertTrue(intelligence >= 1 && intelligence <= 10, "Creeper intelligence escaped the 1-10 range.");
		helper.assertTrue(
			creeper.getCustomName() != null && creeper.getCustomName().getString().endsWith("[" + intelligence + "]"),
			"Natural creeper name did not expose its stable intelligence."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void watchedTargetMakesSkilledCreeperChooseARealFlankPath(final GameTestHelper helper) {
		Creeper creeper = helper.spawn(EntityType.CREEPER, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 8, 2, 2);
		creeper.setNoAi(true);
		target.setNoAi(true);
		creeper.setOnGround(true);
		target.setYRot(90.0F); // 从 +X 一侧朝 -X，也就是正面看着苦力怕。
		target.setYHeadRot(90.0F);
		CreeperIntelligence.set(creeper, 8);
		creeper.setTarget(target);

		CreeperTacticalController controller = new CreeperTacticalController(creeper);
		SmartCreeperApproachGoal goal = new SmartCreeperApproachGoal(creeper, controller);
		helper.assertTrue(goal.canUse(), "A valid visible target did not start smart creeper approach.");
		goal.start();
		goal.tick();

		helper.assertTrue(goal.approachMode().isFlanking(), "A watched IQ-8 creeper did not select a flank.");
		helper.assertTrue(controller.approachDestination() != null, "Flank selection produced no navigation destination.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void primedCreeperKeepsNavigatingTowardPredictedBlastPoint(final GameTestHelper helper) {
		Creeper creeper = helper.spawn(EntityType.CREEPER, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 2, 2);
		creeper.setNoAi(true);
		target.setNoAi(true);
		creeper.setOnGround(true);
		target.setDeltaMovement(0.15, 0.0, 0.0);
		CreeperIntelligence.set(creeper, 10);
		creeper.setTarget(target);

		CreeperTacticalController controller = new CreeperTacticalController(creeper);
		SmartCreeperSwellGoal goal = new SmartCreeperSwellGoal(creeper, controller);
		helper.assertTrue(goal.canUse(), "Close IQ-10 creeper did not start its fuse.");
		goal.start();
		goal.tick();

		helper.assertTrue(creeper.getSwellDir() == 1, "Smart fuse did not enter the priming direction.");
		helper.assertTrue(goal.isMovingFuse(), "Primed creeper stopped instead of retaining a navigation path.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void recentSoftWallTargetCommitsButObsidianIsRejected(final GameTestHelper helper) {
		Creeper creeper = helper.spawn(EntityType.CREEPER, 2, 2, 3);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 2, 3);
		creeper.setNoAi(true);
		target.setNoAi(true);
		CreeperIntelligence.set(creeper, 10);
		creeper.setTarget(target);
		CreeperTacticalController controller = new CreeperTacticalController(creeper);
		helper.assertTrue(controller.observe(target), "Open lane did not create direct sight memory.");

		BlockPos lowerWall = new BlockPos(4, 2, 3);
		BlockPos upperWall = lowerWall.above();
		helper.setBlock(lowerWall, Blocks.DIRT);
		helper.setBlock(upperWall, Blocks.DIRT);
		helper.assertTrue(
			CreeperBreachPlanner.hasBreachableBarrier(creeper, target),
			"A dirt barrier was not accepted as a useful breach."
		);
		SmartCreeperSwellGoal goal = new SmartCreeperSwellGoal(creeper, controller);
		helper.assertTrue(goal.canUse(), "Recent target behind dirt did not start a high-IQ breach fuse.");
		goal.start();
		helper.assertTrue(goal.isBreachFuse(), "Soft-wall start was not labeled as a breach commitment.");
		goal.stop();

		helper.setBlock(lowerWall, Blocks.OBSIDIAN);
		helper.setBlock(upperWall, Blocks.OBSIDIAN);
		helper.assertTrue(
			!CreeperBreachPlanner.hasBreachableBarrier(creeper, target),
			"Obsidian was incorrectly treated as a useful creeper breach."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

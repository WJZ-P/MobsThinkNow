package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;

/** 日晒自救、开放机关承重判定和单格跨沟的端到端回归。 */
public final class ZombieEnvironmentalTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(skyAccess = true, padding = 6)
	public void daylightWaterCarrierDeploysSourceUnderfoot(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		ZombieSunlightRules.forceExposureForTesting(zombie, true);
		zombie.setNoAi(true);
		zombie.setOnGround(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(
			new ZombieFluidCarrierState(UtilityClass.WATER, null, 0L, 0L)
		);
		zombie.igniteForSeconds(4.0F);

		ZombieSunlightSurvivalGoal goal = new ZombieSunlightSurvivalGoal(zombie);
		helper.assertTrue(goal.canUse(), "A daylight-exposed water carrier did not start its survival Goal.");
		goal.start();

		BlockPos source = zombie.blockPosition();
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(zombie);
		helper.assertTrue(helper.getLevel().getFluidState(source).isSource(), "The water bucket did not create a source under the zombie.");
		helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "Deploying sunlight water did not leave a real empty bucket.");
		helper.assertTrue(state.isDeployed() && state.isSunProtection(), "The water source was not persisted as a sunlight transaction.");
		helper.assertTrue(!zombie.isOnFire(), "Successful underfoot water deployment did not extinguish the zombie immediately.");
		Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		restored.restoreFrom(zombie);
		ZombieFluidCarrierState restoredState = ZombieSpecialEquipment.state(restored);
		helper.assertTrue(
			restoredState.isSunProtection() && source.equals(restoredState.source()),
			"The sunlight purpose/source did not survive the vanilla entity save/load path."
		);
		goal.stop();
		ZombieSunlightRules.forceExposureForTesting(zombie, false);
		helper.succeed();
	}

	@GameTest(skyAccess = true, padding = 8)
	public void recentAttackPreemptsSunEscapeAndDefersExposedSourceRecovery(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		ZombieSunlightRules.forceExposureForTesting(zombie, true);
		Villager attacker = helper.spawn(EntityType.VILLAGER, 5, 1, 2);
		zombie.setNoAi(true);
		attacker.setNoAi(true);
		zombie.setOnGround(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(
			new ZombieFluidCarrierState(UtilityClass.WATER, null, 0L, 0L)
		);
		zombie.setLastHurtByMob(attacker);

		ZombieSunlightSurvivalGoal sunlightGoal = new ZombieSunlightSurvivalGoal(zombie);
		helper.assertTrue(!sunlightGoal.canUse(), "Sun escape retained priority after a fresh living-entity attack.");

		BlockPos source = new BlockPos(3, 1, 2);
		helper.setBlock(source, Blocks.WATER);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.WATER,
			helper.absolutePos(source),
			0L,
			0L,
			FluidDeploymentPurpose.SUN_PROTECTION
		));
		ZombieFluidTacticsGoal fluidGoal = new ZombieFluidTacticsGoal(zombie);
		helper.assertTrue(!fluidGoal.canUse(), "Sun-water recovery attempted to override a fresh combat response.");
		helper.setBlock(source, Blocks.AIR);
		helper.assertTrue(
			!fluidGoal.canUse(),
			"A missing sunlight source cleanup attempted to override a fresh combat response."
		);
		ZombieSunlightRules.forceExposureForTesting(zombie, false);
		helper.succeed();
	}

	@GameTest
	public void openMechanismsLoseWalkableSupportWhileClosedTrapdoorsRemainValid(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		helper.assertTrue(
			zombie.getNavigation() instanceof SmartZombieGroundNavigation,
			"The zombie did not receive its smart ground navigation."
		);

		BlockPos openTrapdoor = new BlockPos(3, 1, 2);
		BlockPos closedTrapdoor = new BlockPos(4, 1, 2);
		BlockPos openGate = new BlockPos(5, 1, 2);
		helper.setBlock(openTrapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true));
		helper.setBlock(closedTrapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, false));
		helper.setBlock(openGate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true));

		SmartZombieWalkNodeEvaluator evaluator = new SmartZombieWalkNodeEvaluator();
		PathfindingContext context = new PathfindingContext(helper.getLevel(), zombie);
		PathType openTrapdoorType = evaluator.getPathType(
			context,
			helper.absolutePos(openTrapdoor).getX(),
			helper.absolutePos(openTrapdoor).getY() + 1,
			helper.absolutePos(openTrapdoor).getZ()
		);
		PathType closedTrapdoorType = evaluator.getPathType(
			context,
			helper.absolutePos(closedTrapdoor).getX(),
			helper.absolutePos(closedTrapdoor).getY() + 1,
			helper.absolutePos(closedTrapdoor).getZ()
		);
		PathType openGateType = evaluator.getPathType(
			context,
			helper.absolutePos(openGate).getX(),
			helper.absolutePos(openGate).getY() + 1,
			helper.absolutePos(openGate).getZ()
		);

		helper.assertTrue(openTrapdoorType == PathType.BLOCKED, "An open trapdoor was still accepted as zombie footing.");
		helper.assertTrue(openGateType == PathType.BLOCKED, "An open fence gate was still accepted as zombie footing.");
		helper.assertTrue(closedTrapdoorType != PathType.BLOCKED, "A closed supporting trapdoor was rejected together with the trap.");
		helper.succeed();
	}

	@GameTest(maxTicks = 80, padding = 8)
	public void zombiePhysicallyJumpsOneBlockGapTowardTarget(final GameTestHelper helper) {
		for (int x = 1; x <= 7; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
			}
		}
		// 当前脚下为 x=2，x=3 整列缺少支撑，x=4 是同高度安全落点。
		for (int z = 1; z <= 5; z++) {
			helper.setBlock(new BlockPos(3, 0, z), Blocks.AIR);
		}

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 3);
		Villager target = helper.spawn(EntityType.VILLAGER, 6, 1, 3);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		target.setNoAi(true);
		zombie.setTarget(target);
		double startY = zombie.getY();
		double[] maximumY = {startY};
		int[] elapsed = {0};

		helper.assertTrue(
			SmartZombieGapJumpGoal.findPlan(zombie, target) != null,
			"The exact one-block gap did not produce a safe jump plan."
		);
		helper.onEachTick(() -> {
			zombie.setTarget(target);
			elapsed[0]++;
			maximumY[0] = Math.max(maximumY[0], zombie.getY());
			if (zombie.getX() >= helper.absolutePos(new BlockPos(4, 1, 3)).getX() + 0.15
				&& zombie.onGround()) {
				helper.assertTrue(maximumY[0] >= startY + 0.35, "The zombie crossed without a visible jump arc.");
				helper.assertTrue(zombie.getY() >= startY - 0.1, "The zombie fell into the trench instead of landing across it.");
				helper.succeed();
			}
			if (elapsed[0] == 70) {
				helper.fail(
					"Gap jump stalled: position=" + zombie.position()
						+ ", movement=" + zombie.getDeltaMovement()
						+ ", onGround=" + zombie.onGround()
						+ ", maxY=" + maximumY[0]
				);
			}
		});
	}

	@GameTest(maxTicks = 220, skyAccess = true, padding = 10)
	public void daylightWaterCarrierLeavesWaterAndReachesNearbyShade(final GameTestHelper helper) {
		for (int x = 1; x <= 8; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		for (int x = 5; x <= 7; x++) {
			for (int z = 1; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
			}
		}

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		ZombieSunlightRules.forceExposureForTesting(zombie, true);
		zombie.setInvulnerable(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(
			new ZombieFluidCarrierState(UtilityClass.WATER, null, 0L, 0L)
		);
		BlockPos originalFeet = zombie.blockPosition();

		helper.onEachTick(() -> {
			if (ZombieSunlightRules.isShaded(zombie, helper.getLevel())) {
				ZombieFluidCarrierState state = ZombieSpecialEquipment.state(zombie);
				helper.assertTrue(helper.getLevel().getFluidState(originalFeet).isSource(), "The emergency source vanished before shade was reached.");
				helper.assertTrue(state.isSunProtection(), "The shade escape lost its sunlight deployment purpose.");
				helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "The carrier recovered exposed water during daylight.");
				ZombieSunlightRules.forceExposureForTesting(zombie, false);
				helper.succeed();
			}
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

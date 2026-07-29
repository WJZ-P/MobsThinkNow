package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.phys.Vec3;

/** 着火/日晒自救、水桶兵水下机动、开放机关承重判定和单格跨沟的端到端回归。 */
public final class ZombieEnvironmentalTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 180, skyAccess = true, padding = 10)
	public void burningZombieIgnoresRecentCombatAndEntersNearbyWater(final GameTestHelper helper) {
		for (int x = 1; x <= 8; x++) {
			for (int z = 1; z <= 5; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
		BlockPos water = new BlockPos(6, 1, 3);
		helper.setBlock(water, Blocks.WATER);

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 3);
		Villager attacker = helper.spawn(EntityType.VILLAGER, 8, 1, 3);
		attacker.setNoAi(true);
		attacker.setInvulnerable(true);
		zombie.setInvulnerable(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		zombie.setTarget(attacker);
		zombie.setLastHurtByMob(attacker);
		zombie.igniteForSeconds(15.0F);
		helper.assertTrue(zombie.isOnFire(), "The fire-water search test zombie was not burning.");

		helper.onEachTick(() -> {
			zombie.setTarget(attacker);
			if (!zombie.isOnFire()) {
				helper.assertTrue(
					zombie.isInWater()
						|| zombie.position().distanceToSqr(Vec3.atCenterOf(helper.absolutePos(water))) <= 2.25,
					"The zombie extinguished without reaching the selected nearby water."
				);
				helper.succeed();
			}
		});
	}

	@GameTest
	public void generatedWaterCarrierAlwaysGetsDepthStriderThreeBoots(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setNoAi(true);
		zombie.setBaby(false);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);

		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.waterBucketChance = 1.0;
		config.lavaBucketChance = 0.0;
		DifficultyInstance hardRegionalDifficulty = new DifficultyInstance(
			Difficulty.HARD,
			2_000_000L,
			4_000_000L,
			1.0F
		);
		ZombieSpecialEquipment.maybeEquip(
			zombie,
			hardRegionalDifficulty,
			RandomSource.create(0x5EEDL),
			config
		);

		helper.assertTrue(zombie.getMainHandItem().is(Items.WATER_BUCKET), "The forced water-carrier spawn did not receive its bucket.");
		ItemStack boots = zombie.getItemBySlot(EquipmentSlot.FEET);
		helper.assertTrue(!boots.isEmpty(), "The water carrier spawned without boots.");
		var enchantment = zombie.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(Enchantments.DEPTH_STRIDER);
		helper.assertTrue(
			EnchantmentHelper.getItemEnchantmentLevel(enchantment, boots) == 3,
			"The water carrier's boots were not guaranteed Depth Strider III."
		);
		helper.assertTrue(zombie.getNavigation().canFloat(), "The water carrier navigation was not configured to float.");
		helper.succeed();
	}
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

		ZombieFireSurvivalGoal goal = new ZombieFireSurvivalGoal(zombie);
		helper.assertTrue(goal.canUse(), "A daylight-exposed water carrier did not start its survival Goal.");
		goal.start();

		BlockPos source = zombie.blockPosition();
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(zombie);
		helper.assertTrue(helper.getLevel().getFluidState(source).isSource(), "The water bucket did not create a source under the zombie.");
		helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "Deploying sunlight water did not leave a real empty bucket.");
		helper.assertTrue(state.isDeployed() && state.isSurvivalProtection(), "The water source was not persisted as a survival transaction.");
		helper.assertTrue(!zombie.isOnFire(), "Successful underfoot water deployment did not extinguish the zombie immediately.");
		Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		restored.restoreFrom(zombie);
		ZombieFluidCarrierState restoredState = ZombieSpecialEquipment.state(restored);
		helper.assertTrue(
			restoredState.isSurvivalProtection() && source.equals(restoredState.source()),
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

		ZombieFireSurvivalGoal sunlightGoal = new ZombieFireSurvivalGoal(zombie);
		helper.assertTrue(!sunlightGoal.canUse(), "Sun escape retained priority after a fresh living-entity attack.");

		BlockPos source = new BlockPos(3, 1, 2);
		helper.setBlock(source, Blocks.WATER);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.WATER,
			helper.absolutePos(source),
			0L,
			0L,
			FluidDeploymentPurpose.SURVIVAL
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
				helper.assertTrue(state.isSurvivalProtection(), "The shade escape lost its survival deployment purpose.");
				helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "The carrier recovered exposed water during daylight.");
				ZombieSunlightRules.forceExposureForTesting(zombie, false);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 180, skyAccess = true, padding = 10)
	public void elevatedZombieDescendsTwoBlocksIntoAdjacentShade(final GameTestHelper helper) {
		// 还原截图里的高度关系：僵尸脚下平台比洞内地面高两格，中间各用一级台阶过渡。
		for (int x = 1; x <= 8; x++) {
			for (int z = 1; z <= 5; z++) {
				for (int y = 0; y <= 6; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		for (int z = 1; z <= 5; z++) {
			for (int x = 1; x <= 2; x++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
			}
			helper.setBlock(new BlockPos(3, 0, z), Blocks.STONE);
			helper.setBlock(new BlockPos(3, 1, z), Blocks.STONE);
			for (int x = 4; x <= 8; x++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
		// 先在洞口侧面降到洞内同层，再水平进入严格两格高的入口，避免把碰撞问题混进搜索回归。
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.AIR);
		for (int z = 1; z <= 5; z++) {
			helper.setBlock(new BlockPos(4, 3, z), Blocks.STONE);
		}

		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 3, 3);
		zombie.setNoAi(true);
		zombie.setInvulnerable(true);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

		int[] elapsed = {0};
		int[] startingFeetY = {Integer.MAX_VALUE};
		boolean[] initialized = {false};
		// 单一 tick 状态机既等待天空光照提交，也避免在 GameTest 正迭代任务表时再注册回调。
		helper.onEachTick(() -> {
			elapsed[0]++;
			if (!initialized[0]) {
				if (elapsed[0] < 3) {
					return;
				}
				zombie.setNoAi(false);
				zombie.setOnGround(true);
				zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				ZombieSunlightRules.forceExposureForTesting(zombie, true);
				// 相对出生点偏移 (2, -2, 1)，不在旧实现的 3/6/9/12 格十六方向采样点上。
				BlockPos caveFeet = helper.absolutePos(new BlockPos(4, 1, 4));
				startingFeetY[0] = zombie.blockPosition().getY();

				helper.assertTrue(
					ZombieTraversalRules.canStandAt(helper.getLevel(), caveFeet),
					"The sunken cave did not provide stable support plus two clear body blocks."
				);
				helper.assertTrue(
					ZombieFireSurvivalGoal.isShadeCandidate(helper.getLevel(), caveFeet),
					"The adjacent cave mouth was not recognized as a valid shaded standing cell."
				);
				Path directPath = zombie.getNavigation().createPath(caveFeet, 0);
				helper.assertTrue(
					directPath != null && directPath.canReach(),
					"The vanilla navigator could not reach the isolated cave target."
				);
				Path plannedPath = new ZombieFireSurvivalGoal(zombie, false).findShadePath(helper.getLevel());
				helper.assertTrue(
					plannedPath != null && plannedPath.canReach(),
					"No reachable path was found into the adjacent lower shade."
				);
				helper.assertTrue(
					plannedPath.getTarget().getY() <= startingFeetY[0] - 2,
					"Shade planning ignored the cave floor two blocks below the zombie."
				);
				initialized[0] = true;
			}

			if (ZombieSunlightRules.isShaded(zombie, helper.getLevel())) {
				helper.assertTrue(
					zombie.blockPosition().getY() <= startingFeetY[0] - 2,
					"The zombie reported shade without descending from the upper ledge."
				);
				ZombieSunlightRules.forceExposureForTesting(zombie, false);
				helper.succeed();
			}
			if (elapsed[0] == 170) {
				ZombieSunlightRules.forceExposureForTesting(zombie, false);
				helper.fail(
					"Elevated zombie stalled above nearby shade: position=" + zombie.position()
						+ ", navigationTarget=" + zombie.getNavigation().getTargetPos()
				);
			}
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

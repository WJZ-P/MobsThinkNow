package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 真实服务器实体、方块更新、跳跃物理和攻击范围共同参与的地形战术集成测试。 */
public final class ZombieTerrainTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void smartZombieHarvestsOneReachableSoftBlock(final GameTestHelper helper) {
		BlockPos dirtPos = new BlockPos(3, 1, 2);
		// 典型主世界表面是草方块而非裸泥土；空手采集后应按原版语义进入一块泥土。
		helper.setBlock(dirtPos, Blocks.GRASS_BLOCK);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, 5, 2, 2);

		// 只手动驱动待测 Goal；采集阶段无需实体 AI 或时间推进，因此不会被自动 GoalSelector 抢先消费材料。
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		golem.setNoAi(true);
		golem.setNoGravity(true);
		ZombieIntelligence.set(zombie, 10);
		zombie.setTarget(golem);

		ZombieTerrainTacticsGoal goal = new ZombieTerrainTacticsGoal(zombie);
		helper.assertTrue(goal.canUse(), "A nearby dirt block did not start the high-intelligence terrain goal.");
		goal.start();
		for (int tick = 0; tick < 30 && goal.canContinueToUse(); tick++) {
			goal.tick();
		}
		goal.stop();

		helper.assertTrue(helper.getBlockState(dirtPos).isAir(), "The selected dirt block was not mined.");
		helper.assertTrue(
			ZombieBuilderInventory.count(zombie) == 1,
			"The mined block did not enter the one-slot building inventory."
		);
		helper.assertTrue(
			ZombieBuilderInventory.stack(zombie).is(Items.DIRT),
			"Empty-hand dirt-family harvesting produced the wrong building material."
		);
		Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		restored.restoreFrom(zombie);
		helper.assertTrue(
			ZombieBuilderInventory.count(restored) == 1 && ZombieBuilderInventory.stack(restored).is(Items.DIRT),
			"The hidden building inventory did not survive the vanilla entity save/load path."
		);
		Zombie converted = EntityType.DROWNED.create(helper.getLevel(), EntitySpawnReason.CONVERSION);
		ZombieBuilderInventory.transfer(restored, converted);
		helper.assertTrue(
			ZombieBuilderInventory.count(restored) == 0
				&& ZombieBuilderInventory.count(converted) == 1
				&& ZombieBuilderInventory.stack(converted).is(Items.DIRT),
			"Zombie type conversion did not atomically transfer the hidden building inventory."
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 180)
	public void smartZombiePillarsAboveIronGolemReachAndStrikesDown(final GameTestHelper helper) {
		// 不依赖空模板的隐含地面高度：显式铺平地基并清空上方六格，让寻路和跳垫只受待测逻辑影响。
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos pillarBase = new BlockPos(2, 2, 3);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, pillarBase);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, 5, 2, 3);
		AtomicBoolean completePillarObserved = new AtomicBoolean();

		ZombieIntelligence.set(zombie, 10);
		for (int i = 0; i < ZombieTerrainTacticsGoal.PILLAR_HEIGHT; i++) {
			ZombieBuilderInventory.addOne(zombie, Items.DIRT.getDefaultInstance(), 8);
		}
		golem.setNoAi(true);
		golem.setNoGravity(true);
		golem.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		float originalHealth = golem.getHealth();
		zombie.setTarget(golem);
		ZombieTerrainTacticsGoal probe = new ZombieTerrainTacticsGoal(zombie);
		helper.assertTrue(
			probe.canUse(),
			"Prepared terrain goal rejected its controlled start: zombie=" + zombie.position()
				+ ", golem=" + golem.position()
				+ ", foundation=" + helper.getBlockState(pillarBase.below())
				+ ", storedBlocks=" + ZombieBuilderInventory.count(zombie)
		);

		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(golem);
			golem.invulnerableTime = 0;

			boolean complete = true;
			for (int dy = 0; dy < ZombieTerrainTacticsGoal.PILLAR_HEIGHT; dy++) {
				complete &= helper.getBlockState(pillarBase.above(dy)).is(Blocks.DIRT);
			}
			if (complete) {
				completePillarObserved.set(true);
			}

			if (completePillarObserved.get() && golem.getHealth() < originalHealth) {
				helper.assertTrue(
					ZombieBuilderInventory.count(zombie) == 0,
					"The three placed blocks were not consumed from the building inventory."
				);
				helper.assertTrue(
					zombie.getBoundingBox().minY >= golem.getBoundingBox().maxY,
					"The zombie attacked before its hitbox was vertically separated from the golem."
				);
				helper.assertTrue(
					!golem.isWithinMeleeAttackRange(zombie),
					"The completed three-block pillar remained inside the iron golem's vanilla melee range."
				);
				helper.succeed();
			}

			if (zombie.tickCount == 160) {
				helper.fail(
					"Terrain tactic stalled: completePillar=" + completePillarObserved.get()
						+ ", zombie=" + zombie.position()
						+ ", golem=" + golem.position()
						+ ", storedBlocks=" + ZombieBuilderInventory.count(zombie)
						+ ", golemHealth=" + golem.getHealth()
				);
			}
		});
	}

	@GameTest(maxTicks = 120)
	public void preloadedSmartZombieBuildsBeforeActiveGolemCanLandASecondHit(final GameTestHelper helper) {
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos pillarBase = new BlockPos(2, 2, 3);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, pillarBase);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, 6, 2, 3);
		ZombieIntelligence.set(zombie, 10);
		for (int i = 0; i < ZombieTerrainTacticsGoal.PILLAR_HEIGHT; i++) {
			ZombieBuilderInventory.addOne(zombie, Items.DIRT.getDefaultInstance(), 8);
		}
		zombie.setTarget(golem);
		golem.setTarget(zombie);

		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(golem);
			golem.setTarget(zombie);
			boolean complete = true;
			for (int dy = 0; dy < ZombieTerrainTacticsGoal.PILLAR_HEIGHT; dy++) {
				complete &= helper.getBlockState(pillarBase.above(dy)).is(Blocks.DIRT);
			}
			if (complete && zombie.isAlive()) {
				helper.succeed();
			}
			if (zombie.tickCount == 100) {
				helper.fail(
					"Active-golem build stalled: alive=" + zombie.isAlive()
						+ ", health=" + zombie.getHealth()
						+ ", zombie=" + zombie.position()
						+ ", golem=" + golem.position()
						+ ", stored=" + ZombieBuilderInventory.count(zombie)
				);
			}
		});
	}

	@GameTest(maxTicks = 200)
	public void preloadedSmartZombiePillarsTowardThreeBlockHighTarget(final GameTestHelper helper) {
		for (int x = 0; x <= 9; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y <= 9; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}

		BlockPos zombieSpawn = new BlockPos(3, 2, 3);
		BlockPos targetPillarBase = new BlockPos(5, 2, 3);
		// 八个相邻格都能在完成后进入原版近战范围；稳定候选顺序可随实体 ID 选择任意一侧。
		BlockPos[] attackPillarBases = {
			new BlockPos(4, 2, 3),
			new BlockPos(6, 2, 3),
			new BlockPos(5, 2, 2),
			new BlockPos(5, 2, 4),
			new BlockPos(4, 2, 2),
			new BlockPos(4, 2, 4),
			new BlockPos(6, 2, 2),
			new BlockPos(6, 2, 4)
		};
		for (int dy = 0; dy < 3; dy++) {
			helper.setBlock(targetPillarBase.above(dy), Blocks.STONE);
		}
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, zombieSpawn);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		Villager target = helper.spawn(EntityType.VILLAGER, targetPillarBase.above(3));
		ZombieIntelligence.set(zombie, 10);
		for (int block = 0; block < 3; block++) {
			ZombieBuilderInventory.addOne(zombie, Items.DIRT.getDefaultInstance(), 8);
		}
		target.setNoAi(true);
		target.setNoGravity(true);
		zombie.setTarget(target);

		ZombieTerrainTacticsGoal probe = new ZombieTerrainTacticsGoal(zombie);
		helper.assertTrue(
			probe.canUse(),
			"A three-block-high non-golem target did not produce an elevation pillar plan: zombie="
				+ zombie.position()
				+ ", target=" + target.position()
				+ ", required=" + ZombieTerrainTacticsGoal.requiredElevationPillarHeight(
					zombie.getY(), target.getBoundingBox().minY, ZombieTerrainTacticsGoal.MAX_ELEVATION_PILLAR_HEIGHT
				)
				+ ", stored=" + ZombieBuilderInventory.count(zombie)
		);
		int[] previousPlaced = {0};
			helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(target);
			int placed = 0;
			for (BlockPos candidateBase : attackPillarBases) {
				int candidatePlaced = 0;
				for (int dy = 0; dy < 3; dy++) {
					if (helper.getBlockState(candidateBase.above(dy)).is(Blocks.DIRT)) {
						candidatePlaced++;
					}
				}
				placed = Math.max(placed, candidatePlaced);
			}
			helper.assertTrue(
				placed - previousPlaced[0] <= 1,
				"The zombie placed multiple elevation blocks in one tick instead of visibly jump-pillaring."
			);
			previousPlaced[0] = placed;

			if (placed == 3 && Math.abs(zombie.getY() - target.getY()) < 0.35) {
				helper.assertTrue(
					zombie.isWithinMeleeAttackRange(target),
					"The adjacent elevation pillar still did not put the target inside vanilla melee reach."
				);
				helper.assertTrue(
					ZombieBuilderInventory.count(zombie) == 0,
					"The three elevation blocks were not consumed from the hidden inventory."
				);
				helper.succeed();
			}
			if (zombie.tickCount == 180) {
				helper.fail(
					"Elevation pillar stalled: placed=" + placed
						+ ", zombie=" + zombie.position()
						+ ", target=" + target.position()
						+ ", stored=" + ZombieBuilderInventory.count(zombie)
				);
			}
		});
	}

	@GameTest(maxTicks = 100)
	public void smartZombieCanMineSoftBlockUnderElevatedTarget(final GameTestHelper helper) {
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}

		BlockPos targetPillarBase = new BlockPos(5, 2, 3);
		for (int dy = 0; dy < 3; dy++) {
			helper.setBlock(targetPillarBase.above(dy), Blocks.DIRT);
		}
		BlockPos support = targetPillarBase.above(2);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 2, 3);
		Villager target = helper.spawn(EntityType.VILLAGER, targetPillarBase.above(3));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		target.setNoAi(true);
		target.setNoGravity(true);
		ZombieIntelligence.set(zombie, 10);
		zombie.setTarget(target);
		double originalTargetY = target.getY();

		ZombieTerrainTacticsGoal goal = new ZombieTerrainTacticsGoal(
			zombie,
			(candidate, intelligence, minimum) -> true
		);
		helper.assertTrue(goal.canUse(), "A reachable soft support block did not produce an undermine plan.");
		goal.start();
		int[] drivenTicks = {0};
		boolean[] goalStopped = {false};
		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(target);
			if (!goalStopped[0] && goal.canContinueToUse()) {
				goal.tick();
				drivenTicks[0]++;
			} else if (!goalStopped[0]) {
				goal.stop();
				goalStopped[0] = true;
			}

			if (helper.getBlockState(support).isAir()) {
				target.setNoAi(false);
				target.setNoGravity(false);
				helper.assertTrue(drivenTicks[0] >= 5, "The support block vanished without visible mining time.");
				helper.assertTrue(
					helper.getBlockState(targetPillarBase).is(Blocks.DIRT)
						&& helper.getBlockState(targetPillarBase.above()).is(Blocks.DIRT),
					"Undermining removed more than the single block directly under the target."
				);
				if (target.getY() <= originalTargetY - 0.75) {
					helper.succeed();
				}
			}
			if (target.tickCount >= 80) {
				helper.fail(
					"Soft-column undermine stalled: support=" + helper.getBlockState(support)
						+ ", targetY=" + target.getY()
						+ ", originalY=" + originalTargetY
						+ ", drivenTicks=" + drivenTicks[0]
						+ ", goalStopped=" + goalStopped[0]
				);
			}
		});
	}

	@GameTest
	public void groundZombieVariantsShareTerrainEligibility(final GameTestHelper helper) {
		Zombie husk = helper.spawn(EntityType.HUSK, 1, 0, 1);
		Zombie villager = helper.spawn(EntityType.ZOMBIE_VILLAGER, 3, 0, 1);
		Zombie drowned = helper.spawn(EntityType.DROWNED, 5, 0, 1);
		for (Zombie zombie : new Zombie[] {husk, villager, drowned}) {
			ZombieIntelligence.set(zombie, 10);
		}
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		helper.assertTrue(
			ZombieTerrainTacticsGoal.canUseTerrainTactics(husk, config),
			"Husk did not inherit the shared ground-family terrain state machine."
		);
		helper.assertTrue(
			ZombieTerrainTacticsGoal.canUseTerrainTactics(villager, config),
			"Zombie villager did not inherit the shared ground-family terrain state machine."
		);
		helper.assertTrue(
			!ZombieTerrainTacticsGoal.canUseTerrainTactics(drowned, config),
			"Drowned lost its deliberately separate amphibious tactics boundary."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

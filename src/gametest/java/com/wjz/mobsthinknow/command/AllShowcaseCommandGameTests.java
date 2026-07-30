package com.wjz.mobsthinknow.command;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证无参数 spawnall 覆盖当前全部智能 AI 生物。 */
public final class AllShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnAllCreatesEveryCurrentIntelligentMonsterArchetype(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		var source = helper.getLevel()
			.getServer()
			.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withPosition(Vec3.atBottomCenterOf(sourceBlock))
			.withRotation(Vec2.ZERO)
			.withSuppressedOutput();

		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source,
			"mtn spawnall"
		);
		AABB searchBox = new AABB(sourceBlock).inflate(24.0, 8.0, 24.0);
		List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(
			Zombie.class,
			searchBox,
			zombie -> zombie.getType() == EntityType.ZOMBIE && zombie.isAlive()
		);
		List<Skeleton> skeletons = helper.getLevel().getEntitiesOfClass(
			Skeleton.class,
			searchBox,
			skeleton -> skeleton.getType() == EntityType.SKELETON && skeleton.isAlive()
		);
		List<Creeper> creepers = helper.getLevel().getEntitiesOfClass(
			Creeper.class,
			searchBox,
			creeper -> creeper.getType() == EntityType.CREEPER && creeper.isAlive()
		);
		List<Spider> spiders = helper.getLevel().getEntitiesOfClass(
			Spider.class,
			searchBox,
			spider -> spider.getType() == EntityType.SPIDER && spider.isAlive()
		);

		helper.assertTrue(zombies.size() == 9, "Global spawnall did not create all nine zombie archetypes.");
		helper.assertTrue(skeletons.size() == 3, "Global spawnall did not create all three skeleton archetypes.");
		helper.assertTrue(spiders.size() == 4, "Global spawnall did not create all four spider archetypes.");
		helper.assertTrue(
			creepers.size() == 5,
			"Global spawnall did not create four standalone creepers plus the spider payload."
		);
		helper.assertTrue(
			creepers.stream().filter(creeper -> creeper.getVehicle() instanceof Spider).count() == 1,
			"The global formation did not retain exactly one mounted creeper payload."
		);
		helper.assertTrue(
			creepers.stream().filter(creeper -> !creeper.isPassenger()).count() == 4,
			"The global formation did not retain exactly four standalone creeper archetypes."
		);

		Set<BlockPos> rootPositions = new HashSet<>();
		zombies.forEach(entity -> rootPositions.add(entity.blockPosition()));
		skeletons.forEach(entity -> rootPositions.add(entity.blockPosition()));
		creepers.stream().filter(creeper -> !creeper.isPassenger())
			.forEach(entity -> rootPositions.add(entity.blockPosition()));
		spiders.forEach(entity -> rootPositions.add(entity.blockPosition()));
		helper.assertTrue(
			rootPositions.size() == AllShowcaseSpawner.ARCHETYPE_COUNT,
			"At least two global showcase roots occupied the same feet position."
		);
		helper.assertTrue(
			zombies.size() + skeletons.size() + creepers.size() + spiders.size() == 21,
			"Global spawnall did not create exactly 21 entities including the mounted payload."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

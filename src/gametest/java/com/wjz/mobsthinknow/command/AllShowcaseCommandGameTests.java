package com.wjz.mobsthinknow.command;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证 spawnall 与 spawn 子树覆盖当前全部智能 AI 生物。 */
public final class AllShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnAllCreatesEveryCurrentIntelligentMonsterArchetype(final GameTestHelper helper) {
		assertGlobalFormation(helper, "mtn spawnall");
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnAllChildUnderSpawnCreatesEveryCurrentArchetype(final GameTestHelper helper) {
		assertGlobalFormation(helper, "mtn spawn all");
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnCommandTreeExposesEveryBaseGroupAndTacticalLiteral(final GameTestHelper helper) {
		var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
		var mtnNode = dispatcher.getRoot().getChild("mtn");
		var spawnNode = mtnNode == null ? null : mtnNode.getChild("spawn");
		helper.assertTrue(spawnNode != null, "The /mtn spawn command node was not registered.");

		Set<String> expected = new HashSet<>(List.of(
			"all",
			"zombie",
			"skeleton",
			"creeper",
			"spider",
			"enderman",
			"giant",
			"zombies",
			"skeletons",
			"creepers",
			"spiders",
			"endermen",
			"giants"
		));
		Arrays.stream(ZombieShowcaseSpawner.ShowcaseArchetype.values())
			.map(ZombieShowcaseSpawner.ShowcaseArchetype::commandId)
			.forEach(expected::add);
		Arrays.stream(SkeletonShowcaseSpawner.ShowcaseArchetype.values())
			.map(SkeletonShowcaseSpawner.ShowcaseArchetype::commandId)
			.forEach(expected::add);
		Arrays.stream(CreeperShowcaseSpawner.ShowcaseArchetype.values())
			.map(CreeperShowcaseSpawner.ShowcaseArchetype::commandId)
			.forEach(expected::add);
		Arrays.stream(SpiderShowcaseSpawner.ShowcaseArchetype.values())
			.map(SpiderShowcaseSpawner.ShowcaseArchetype::commandId)
			.forEach(expected::add);
		Arrays.stream(EndermanShowcaseSpawner.ShowcaseArchetype.values())
			.map(EndermanShowcaseSpawner.ShowcaseArchetype::commandId)
			.forEach(expected::add);
		Arrays.stream(GiantShowcaseSpawner.ShowcaseArchetype.values())
			.map(GiantShowcaseSpawner.ShowcaseArchetype::commandId)
			.forEach(expected::add);

		Set<String> actual = new HashSet<>();
		spawnNode.getChildren().forEach(child -> actual.add(child.getName()));
		Set<String> missing = new HashSet<>(expected);
		missing.removeAll(actual);
		helper.assertTrue(missing.isEmpty(), "The /mtn spawn tree is missing literals: " + missing);
		helper.assertTrue(
			actual.size() == expected.size(),
			"The /mtn spawn tree contains unexpected or duplicate root literals: " + actual
		);
		helper.succeed();
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnBaseAliasesCreateEveryIntelligentMonsterFamily(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		CommandSourceStack source = commandSource(helper, sourceBlock);
		assertBaseAlias(helper, source, sourceBlock, "zombie", EntityType.ZOMBIE);
		assertBaseAlias(helper, source, sourceBlock, "skeleton", EntityType.SKELETON);
		assertBaseAlias(helper, source, sourceBlock, "creeper", EntityType.CREEPER);
		assertBaseAlias(helper, source, sourceBlock, "spider", EntityType.SPIDER);
		assertBaseAlias(helper, source, sourceBlock, "enderman", EntityType.ENDERMAN);
		assertBaseAlias(helper, source, sourceBlock, "giant", EntityType.GIANT);
		helper.succeed();
	}

	private static void assertGlobalFormation(final GameTestHelper helper, final String command) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		CommandSourceStack source = commandSource(helper, sourceBlock);

		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source,
			command
		);
		// 23 根实体只占前方五排；竖直范围覆盖 12 格高巨人与其头顶射手。
		AABB searchBox = new AABB(sourceBlock).move(0.0, 0.0, 8.0).inflate(12.0, 16.0, 12.0);
		List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(
			Zombie.class,
			searchBox,
			zombie -> zombie.getType() == EntityType.ZOMBIE && isShowcase(zombie)
		);
		List<Skeleton> skeletons = helper.getLevel().getEntitiesOfClass(
			Skeleton.class,
			searchBox,
			skeleton -> skeleton.getType() == EntityType.SKELETON && isShowcase(skeleton)
		);
		List<Creeper> creepers = helper.getLevel().getEntitiesOfClass(
			Creeper.class,
			searchBox,
			creeper -> creeper.getType() == EntityType.CREEPER && isShowcase(creeper)
		);
		List<Spider> spiders = helper.getLevel().getEntitiesOfClass(
			Spider.class,
			searchBox,
			spider -> spider.getType() == EntityType.SPIDER && isShowcase(spider)
		);
		List<EnderMan> endermen = helper.getLevel().getEntitiesOfClass(
			EnderMan.class,
			searchBox,
			enderman -> enderman.getType() == EntityType.ENDERMAN && isShowcase(enderman)
		);
		List<Giant> giants = helper.getLevel().getEntitiesOfClass(
			Giant.class,
			searchBox,
			giant -> giant.getType() == EntityType.GIANT && isShowcase(giant)
		);

		helper.assertTrue(
			zombies.size() == 10,
			"Global spawnall expected nine roots plus one Giant-held zombie but found " + zombies.size() + "."
		);
		helper.assertTrue(
			skeletons.size() == 4,
			"Global spawnall expected three roots plus one Giant head rider but found " + skeletons.size() + "."
		);
		helper.assertTrue(
			spiders.size() == 4,
			"Global spawnall expected four spider archetypes but found " + spiders.size() + "."
		);
		helper.assertTrue(
			endermen.size() == 2,
			"Global spawnall expected two enderman archetypes but found " + endermen.size() + "."
		);
		helper.assertTrue(
			creepers.size() == 7,
			"Global spawnall expected seven creepers including three mounted payloads but found " + creepers.size() + "."
		);
		helper.assertTrue(
			creepers.stream().filter(creeper -> creeper.getVehicle() instanceof Spider).count() == 1,
			"The global formation did not retain exactly one spider-mounted creeper payload."
		);
		helper.assertTrue(
			creepers.stream().filter(creeper -> creeper.getVehicle() instanceof EnderMan).count() == 1,
			"The global formation did not retain exactly one enderman-held creeper payload."
		);
		helper.assertTrue(
			creepers.stream().filter(creeper -> creeper.getVehicle() instanceof Giant).count() == 1,
			"The global formation did not retain exactly one Giant-held creeper payload."
		);
		helper.assertTrue(
			creepers.stream().filter(creeper -> !creeper.isPassenger()).count() == 4,
			"The global formation did not retain exactly four standalone creeper archetypes."
		);
		helper.assertTrue(giants.size() == 1, "Global spawnall did not create exactly one Giant root.");
		helper.assertTrue(
			zombies.stream().filter(zombie -> zombie.getVehicle() instanceof Giant).count() == 1,
			"The global formation did not retain exactly one Giant-held zombie payload."
		);
		helper.assertTrue(
			skeletons.stream().filter(skeleton -> skeleton.getVehicle() instanceof Giant).count() == 1,
			"The global formation did not retain exactly one Giant head rider."
		);

		Set<BlockPos> rootPositions = new HashSet<>();
		zombies.stream().filter(zombie -> !zombie.isPassenger()).forEach(entity -> rootPositions.add(entity.blockPosition()));
		skeletons.stream().filter(skeleton -> !skeleton.isPassenger()).forEach(entity -> rootPositions.add(entity.blockPosition()));
		creepers.stream().filter(creeper -> !creeper.isPassenger())
			.forEach(entity -> rootPositions.add(entity.blockPosition()));
		spiders.forEach(entity -> rootPositions.add(entity.blockPosition()));
		endermen.forEach(entity -> rootPositions.add(entity.blockPosition()));
		giants.forEach(entity -> rootPositions.add(entity.blockPosition()));
		helper.assertTrue(
			rootPositions.size() == AllShowcaseSpawner.ARCHETYPE_COUNT,
			"At least two global showcase roots occupied the same feet position."
		);
		helper.assertTrue(
			zombies.size() + skeletons.size() + creepers.size() + spiders.size() + endermen.size() + giants.size() == 28,
			"Global spawnall did not create exactly 28 entities including all mounted riders and payloads."
		);
		helper.succeed();
	}

	private static void assertBaseAlias(
		final GameTestHelper helper,
		final CommandSourceStack source,
		final BlockPos sourceBlock,
		final String literal,
		final EntityType<?> expectedType
	) {
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source,
			"mtn spawn " + literal + " 2"
		);
		List<Mob> spawned = helper.getLevel().getEntitiesOfClass(
			Mob.class,
			new AABB(sourceBlock).move(0.0, 0.0, 5.0).inflate(8.0, 16.0, 8.0),
			mob -> mob.getType() == expectedType && isShowcase(mob)
		);
		helper.assertTrue(
			spawned.size() == 2,
			"The /mtn spawn " + literal + " base alias did not create exactly two expected mobs."
		);
		spawned.forEach(AllShowcaseCommandGameTests::discardTree);
	}

	private static void discardTree(final net.minecraft.world.entity.Entity root) {
		for (net.minecraft.world.entity.Entity passenger : List.copyOf(root.getPassengers())) {
			discardTree(passenger);
		}
		root.discard();
	}

	private static boolean isShowcase(final Mob mob) {
		return mob.isAlive() && mob.isPersistenceRequired() && mob.isCustomNameVisible() && mob.getCustomName() != null;
	}

	private static CommandSourceStack commandSource(final GameTestHelper helper, final BlockPos sourceBlock) {
		return helper.getLevel()
			.getServer()
			.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withPosition(Vec3.atBottomCenterOf(sourceBlock))
			.withRotation(Vec2.ZERO)
			.withSuppressedOutput();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

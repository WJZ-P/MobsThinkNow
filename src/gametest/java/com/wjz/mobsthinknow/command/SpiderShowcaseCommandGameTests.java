package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证蜘蛛预设、合体载荷、批量参数与快捷指令。 */
public final class SpiderShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void everySpiderLiteralCreatesItsRequestedPreset(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		var source = source(helper, sourceBlock);
		for (SpiderShowcaseSpawner.ShowcaseArchetype archetype : SpiderShowcaseSpawner.ShowcaseArchetype.values()) {
			helper.getLevel().getServer().getCommands().performPrefixedCommand(
				source,
				"mtn spawn " + archetype.commandId()
			);
		}

		List<Spider> spiders = spidersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(spiders.size() == 4, "Specific commands did not create all four spider presets.");
		Map<Integer, Long> intelligenceCounts = spiders.stream().collect(Collectors.groupingBy(
			SpiderIntelligence::get,
			Collectors.counting()
		));
		helper.assertTrue(
			intelligenceCounts.equals(Map.of(5, 1L, 8, 1L, 10, 2L)),
			"Spider preset intelligence no longer matches its tactic thresholds: " + intelligenceCounts
		);
		helper.assertTrue(
			spiders.stream().filter(spider -> spider.getFirstPassenger() instanceof Creeper).count() == 1,
			"Exactly one showcase spider should carry a creeper payload."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spiderCreeperBomberLiteralSupportsBatchCount(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawn spider_creeper_bomber 4"
		);

		List<Spider> spiders = spidersNear(helper, sourceBlock, 14.0);
		List<Creeper> creepers = creepersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(spiders.size() == 4, "Bomber batch did not create exactly four spiders.");
		helper.assertTrue(creepers.size() == 4, "Bomber batch did not create exactly four creeper payloads.");
		helper.assertTrue(
			spiders.stream().allMatch(spider -> SpiderIntelligence.get(spider) == 10),
			"At least one bomber spider lost its IQ-10 preset."
		);
		helper.assertTrue(
			creepers.stream().allMatch(creeper -> creeper.getVehicle() instanceof Spider),
			"At least one bomber payload was not mounted on its spider."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnSpidersShortcutCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnspiders"
		);

		List<Spider> spiders = spidersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(spiders.size() == 4, "Spider shortcut did not create one of every preset.");
		helper.assertTrue(
			spiders.stream().allMatch(spider -> spider.isPersistenceRequired() && spider.isCustomNameVisible()),
			"A spider showcase entity was not persistent or lacked its visible preset name."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnAllSpidersAliasCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnall spiders"
		);

		List<Spider> spiders = spidersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(spiders.size() == 4, "Nested spider shortcut did not create all four presets.");
		helper.assertTrue(
			spiders.stream().filter(spider -> spider.getFirstPassenger() instanceof Creeper).count() == 1,
			"Nested shortcut lost its spider-creeper bomber."
		);
		helper.succeed();
	}

	private static net.minecraft.commands.CommandSourceStack source(
		final GameTestHelper helper,
		final BlockPos sourceBlock
	) {
		return helper.getLevel()
			.getServer()
			.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withPosition(Vec3.atBottomCenterOf(sourceBlock))
			.withRotation(Vec2.ZERO)
			.withSuppressedOutput();
	}

	private static List<Spider> spidersNear(
		final GameTestHelper helper,
		final BlockPos sourceBlock,
		final double radius
	) {
		return helper.getLevel().getEntitiesOfClass(
			Spider.class,
			new AABB(sourceBlock).inflate(radius, 8.0, radius),
			spider -> spider.getType() == EntityType.SPIDER && spider.isAlive()
		);
	}

	private static List<Creeper> creepersNear(
		final GameTestHelper helper,
		final BlockPos sourceBlock,
		final double radius
	) {
		return helper.getLevel().getEntitiesOfClass(
			Creeper.class,
			new AABB(sourceBlock).inflate(radius, 8.0, radius),
			creeper -> creeper.getType() == EntityType.CREEPER && creeper.isAlive()
		);
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

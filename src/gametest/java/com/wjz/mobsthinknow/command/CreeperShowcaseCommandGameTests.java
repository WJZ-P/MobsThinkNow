package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证苦力怕预设、带电状态、批量参数与快捷指令。 */
public final class CreeperShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void everyCreeperLiteralCreatesItsRequestedPreset(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		var source = source(helper, sourceBlock);
		for (CreeperShowcaseSpawner.ShowcaseArchetype archetype : CreeperShowcaseSpawner.ShowcaseArchetype.values()) {
			helper.getLevel().getServer().getCommands().performPrefixedCommand(
				source,
				"mtn spawn " + archetype.commandId()
			);
		}

		List<Creeper> creepers = creepersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(creepers.size() == 4, "Specific commands did not create all four creeper presets.");
		Map<Integer, Long> intelligenceCounts = creepers.stream().collect(Collectors.groupingBy(
			CreeperIntelligence::get,
			Collectors.counting()
		));
		helper.assertTrue(
			intelligenceCounts.equals(Map.of(5, 1L, 8, 1L, 10, 2L)),
			"Creeper preset intelligence no longer matches its tactic thresholds: " + intelligenceCounts
		);
		helper.assertTrue(creepers.stream().filter(Creeper::isPowered).count() == 1, "Charged preset count was not one.");
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void chargedBreacherLiteralSupportsBatchCount(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawn creeper_charged_breacher 4"
		);

		List<Creeper> creepers = creepersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(creepers.size() == 4, "Creeper batch command did not create exactly four entities.");
		helper.assertTrue(
			creepers.stream().allMatch(creeper -> creeper.isPowered() && CreeperIntelligence.get(creeper) == 10),
			"At least one charged-breacher batch member lost its exact preset."
		);
		helper.assertTrue(
			creepers.stream().map(Creeper::blockPosition).distinct().count() == 4,
			"Two creeper test entities shared one feet position."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnCreepersShortcutCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawncreepers"
		);

		List<Creeper> creepers = creepersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(creepers.size() == 4, "Creeper shortcut did not create one of every preset.");
		helper.assertTrue(
			creepers.stream().allMatch(creeper -> creeper.isPersistenceRequired() && creeper.isCustomNameVisible()),
			"A creeper showcase entity was not persistent or had no visible preset name."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnAllCreepersAliasCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnall creepers"
		);

		List<Creeper> creepers = creepersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(creepers.size() == 4, "Nested creeper shortcut did not create all four presets.");
		helper.assertTrue(creepers.stream().filter(Creeper::isPowered).count() == 1, "Nested shortcut lost its charged preset.");
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

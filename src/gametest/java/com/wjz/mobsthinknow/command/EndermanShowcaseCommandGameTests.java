package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.enderman.EndermanIntelligence;
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
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证末影人预设、真实苦力怕乘客、批量参数与快捷指令。 */
public final class EndermanShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void everyEndermanLiteralCreatesItsRequestedPreset(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		var source = source(helper, sourceBlock);
		for (EndermanShowcaseSpawner.ShowcaseArchetype archetype : EndermanShowcaseSpawner.ShowcaseArchetype.values()) {
			helper.getLevel().getServer().getCommands().performPrefixedCommand(
				source,
				"mtn spawn " + archetype.commandId()
			);
		}

		List<EnderMan> endermen = endermenNear(helper, sourceBlock, 14.0);
		helper.assertTrue(endermen.size() == 2, "Specific commands did not create both enderman presets.");
		Map<Integer, Long> intelligenceCounts = endermen.stream().collect(Collectors.groupingBy(
			EndermanIntelligence::get,
			Collectors.counting()
		));
		helper.assertTrue(
			intelligenceCounts.equals(Map.of(7, 1L, 10, 1L)),
			"Enderman preset intelligence no longer matches its tactic thresholds: " + intelligenceCounts
		);
		helper.assertTrue(
			endermen.stream().filter(enderman -> enderman.getFirstPassenger() instanceof Creeper).count() == 1,
			"Exactly one showcase enderman should carry a creeper payload."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void endermanCreeperBomberLiteralSupportsBatchCount(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawn enderman_creeper_bomber 4"
		);

		List<EnderMan> endermen = endermenNear(helper, sourceBlock, 14.0);
		List<Creeper> creepers = creepersNear(helper, sourceBlock, 14.0);
		helper.assertTrue(endermen.size() == 4, "Bomber batch did not create exactly four endermen.");
		helper.assertTrue(creepers.size() == 4, "Bomber batch did not create exactly four creeper payloads.");
		helper.assertTrue(
			endermen.stream().allMatch(enderman -> EndermanIntelligence.get(enderman) == 10),
			"At least one bomber enderman lost its IQ-10 preset."
		);
		helper.assertTrue(
			creepers.stream().allMatch(creeper -> creeper.getVehicle() instanceof EnderMan),
			"At least one bomber payload was not mounted on its enderman."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnEndermenShortcutCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnendermen"
		);

		List<EnderMan> endermen = endermenNear(helper, sourceBlock, 14.0);
		helper.assertTrue(endermen.size() == 2, "Enderman shortcut did not create one of every preset.");
		helper.assertTrue(
			endermen.stream().allMatch(enderman -> enderman.isPersistenceRequired() && enderman.isCustomNameVisible()),
			"An enderman showcase entity was not persistent or lacked its visible preset name."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnAllEndermenAliasCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnall endermen"
		);

		List<EnderMan> endermen = endermenNear(helper, sourceBlock, 14.0);
		helper.assertTrue(endermen.size() == 2, "Nested enderman shortcut did not create both presets.");
		helper.assertTrue(
			endermen.stream().filter(enderman -> enderman.getFirstPassenger() instanceof Creeper).count() == 1,
			"Nested shortcut lost its enderman-creeper bomber."
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

	private static List<EnderMan> endermenNear(
		final GameTestHelper helper,
		final BlockPos sourceBlock,
		final double radius
	) {
		return helper.getLevel().getEntitiesOfClass(
			EnderMan.class,
			new AABB(sourceBlock).inflate(radius, 8.0, radius),
			enderman -> enderman.getType() == EntityType.ENDERMAN && enderman.isAlive()
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

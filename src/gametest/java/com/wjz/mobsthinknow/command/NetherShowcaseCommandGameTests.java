package com.wjz.mobsthinknow.command;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证七种下界预设、批量参数和三种整组快捷入口。 */
public final class NetherShowcaseCommandGameTests implements CustomTestMethodInvoker {
	private static final Set<EntityType<?>> NETHER_TYPES = Arrays.stream(NetherShowcaseSpawner.ShowcaseArchetype.values())
		.map(NetherShowcaseSpawner.ShowcaseArchetype::entityType)
		.collect(Collectors.toUnmodifiableSet());

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnNetherShortcutCreatesOneConfiguredPresetOfEveryType(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnnether"
		);
		assertCompleteFormation(helper, sourceBlock);
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnAllNetherAliasCreatesTheSameCompleteFormation(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnall nether"
		);
		assertCompleteFormation(helper, sourceBlock);
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spawnNetherGroupUnderSpawnCreatesTheSameCompleteFormation(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawn nether"
		);
		assertCompleteFormation(helper, sourceBlock);
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void bothBaseAliasAndTacticalLiteralSupportBatchCounts(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		CommandSourceStack source = source(helper, sourceBlock);
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "mtn spawn blaze 4");
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "mtn spawn piglin_brute 3");

		List<Mob> mobs = netherMobsNear(helper, sourceBlock);
		helper.assertTrue(
			mobs.stream().filter(mob -> mob.getType() == EntityType.BLAZE).count() == 4,
			"The Blaze base alias did not honor its batch count."
		);
		helper.assertTrue(
			mobs.stream().filter(mob -> mob.getType() == EntityType.PIGLIN_BRUTE).count() == 3,
			"The Piglin Brute tactical literal did not honor its batch count."
		);
		helper.assertTrue(
			mobs.stream().map(Mob::blockPosition).distinct().count() == 7,
			"At least two Nether batch entities shared a feet position."
		);
		helper.succeed();
	}

	private static void assertCompleteFormation(final GameTestHelper helper, final BlockPos sourceBlock) {
		List<Mob> mobs = netherMobsNear(helper, sourceBlock);
		helper.assertTrue(mobs.size() == 7, "Nether group command did not create all seven presets.");
		helper.assertTrue(
			mobs.stream().map(Mob::getType).collect(Collectors.toSet()).equals(NETHER_TYPES),
			"Nether group command did not create exactly one of every configured type."
		);
		helper.assertTrue(
			mobs.stream().allMatch(mob -> mob.isPersistenceRequired() && mob.isCustomNameVisible()),
			"At least one Nether showcase entity lost persistence or its visible preset name."
		);
		helper.assertTrue(
			mobs.stream().filter(mob -> mob.getType() == EntityType.PIGLIN)
				.allMatch(mob -> mob.getMainHandItem().is(Items.CROSSBOW)),
			"The Piglin battle-line preset did not force its crossbow."
		);
		helper.assertTrue(
			mobs.stream().filter(mob -> mob instanceof MagmaCube)
				.map(mob -> (MagmaCube)mob).allMatch(cube -> cube.getSize() == 3),
			"The Magma Cube hunter did not retain its readable showcase size."
		);
		helper.succeed();
	}

	private static List<Mob> netherMobsNear(final GameTestHelper helper, final BlockPos sourceBlock) {
		return helper.getLevel().getEntitiesOfClass(
			Mob.class,
			new AABB(sourceBlock).move(0.0, 4.0, 6.0).inflate(22.0, 24.0, 22.0),
			mob -> NETHER_TYPES.contains(mob.getType()) && mob.isAlive() && mob.isCustomNameVisible()
		);
	}

	private static CommandSourceStack source(final GameTestHelper helper, final BlockPos sourceBlock) {
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

package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionProfile;
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

/** 从真实 Brigadier 入口验证全部下界职业预设、批量参数和三种整组快捷入口。 */
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
		helper.assertTrue(
			mobs.size() == NetherShowcaseSpawner.ShowcaseArchetype.values().length,
			"Nether group command did not create every profession preset."
		);
		helper.assertTrue(
			mobs.stream().map(Mob::getType).collect(Collectors.toSet()).equals(NETHER_TYPES),
			"Nether group command did not cover every configured entity type."
		);
		helper.assertTrue(
			mobs.stream().allMatch(mob -> mob.isPersistenceRequired() && mob.isCustomNameVisible()),
			"At least one Nether showcase entity lost persistence or its visible preset name."
		);
		for (NetherShowcaseSpawner.ShowcaseArchetype archetype
			: NetherShowcaseSpawner.ShowcaseArchetype.values()) {
			boolean present = mobs.stream().anyMatch(mob -> mob.getType() == archetype.entityType()
				&& mob.getCustomName() != null
				&& mob.getCustomName().getString().equals(archetype.displayName().getString())
				&& NetherProfessionProfile.get(mob) == archetype.profession());
			helper.assertTrue(present, "Missing or mismatched profession preset " + archetype.commandId());
		}
		helper.assertTrue(mobs.stream()
			.filter(mob -> NetherProfessionProfile.get(mob) == NetherProfession.PIGLIN_MARKSMAN)
			.allMatch(mob -> mob.getMainHandItem().is(Items.CROSSBOW)),
			"The Piglin marksman did not retain its crossbow."
		);
		helper.assertTrue(mobs.stream()
			.filter(mob -> NetherProfessionProfile.get(mob) == NetherProfession.ZOMBIFIED_PIGLIN_LANCER)
			.allMatch(mob -> mob.getMainHandItem().is(Items.GOLDEN_SPEAR)),
			"The Zombified Piglin lancer did not retain its golden spear."
		);
		helper.assertTrue(mobs.stream()
			.filter(mob -> NetherProfessionProfile.get(mob) == NetherProfession.WITHER_SKELETON_HEXER)
			.allMatch(mob -> mob.getMainHandItem().is(Items.BOW)),
			"The Wither Skeleton hexer did not retain its bow."
		);
		helper.assertTrue(mobs.stream().filter(mob -> mob instanceof MagmaCube).allMatch(mob -> {
			MagmaCube cube = (MagmaCube)mob;
			return switch (NetherProfessionProfile.get(cube)) {
				case MAGMA_AMBUSHER -> cube.getSize() == 2;
				case MAGMA_TITAN -> cube.getSize() == 4;
				case MAGMA_HUNTER -> cube.getSize() == 3;
				default -> false;
			};
		}), "A Magma Cube profession lost its configured showcase size.");
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

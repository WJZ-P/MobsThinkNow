package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.zombie.ZombieAirAssault;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventory;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 通过真实 Brigadier 命令入口验证九种样本，而不是绕过命令直接调用生成器。 */
public final class ZombieShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnSpecificCommandRoutesEveryLiteralToItsRequestedArchetype(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		var source = helper.getLevel()
			.getServer()
			.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withPosition(Vec3.atBottomCenterOf(sourceBlock))
			.withRotation(Vec2.ZERO)
			.withSuppressedOutput();

		for (ZombieShowcaseSpawner.ShowcaseArchetype archetype
			: ZombieShowcaseSpawner.ShowcaseArchetype.values()) {
			helper.getLevel().getServer().getCommands().performPrefixedCommand(
				source,
				"mtn spawn " + archetype.commandId()
			);
		}

		List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(
			Zombie.class,
			new AABB(sourceBlock).inflate(12.0, 8.0, 12.0),
			zombie -> zombie.getType() == EntityType.ZOMBIE && zombie.isAlive()
		);
		helper.assertTrue(zombies.size() == 9, "Specific spawn commands did not create exactly nine zombies.");
		for (ZombieShowcaseSpawner.ShowcaseArchetype archetype
			: ZombieShowcaseSpawner.ShowcaseArchetype.values()) {
			String expectedName = archetype.displayName().getString();
			long matchingNames = zombies.stream()
				.filter(zombie -> zombie.getCustomName() != null)
				.filter(zombie -> zombie.getCustomName().getString().contains(expectedName))
				.count();
			helper.assertTrue(
				matchingNames == 1,
				"Command literal " + archetype.commandId() + " did not route to exactly one named archetype."
			);
		}
		helper.succeed();
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnAllCommandCreatesOneOfEveryTacticalArchetype(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		Vec3 sourcePosition = Vec3.atBottomCenterOf(sourceBlock);
		var source = helper.getLevel()
			.getServer()
			.createCommandSourceStack()
			.withLevel(helper.getLevel())
			.withPosition(sourcePosition)
			.withRotation(Vec2.ZERO)
			.withSuppressedOutput();

		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "mtn spawnall");
		List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(
			Zombie.class,
			new AABB(sourceBlock).inflate(20.0, 8.0, 20.0),
			zombie -> zombie.getType() == EntityType.ZOMBIE && zombie.isAlive()
		);

		helper.assertTrue(zombies.size() == 9, "The command did not create exactly nine tactical zombie archetypes.");
		helper.assertTrue(
			zombies.stream().allMatch(zombie -> zombie.isPersistenceRequired() && zombie.isCustomNameVisible()),
			"A showcase zombie was not persistent or did not expose its archetype name."
		);
		helper.assertTrue(
			zombies.stream().map(Zombie::blockPosition).distinct().count() == 9,
			"Two showcase zombies were placed on the same feet position."
		);

		assertExactlyOne(helper, zombies, zombie ->
			zombie.getMainHandItem().isEmpty()
				&& zombie.getOffhandItem().isEmpty()
				&& ZombieBuilderInventory.count(zombie) == 0,
			"unarmed zombie"
		);
		assertExactlyOne(helper, zombies, zombie ->
			zombie.getMainHandItem().is(Items.IRON_SWORD) && zombie.getOffhandItem().isEmpty(),
			"swordsman"
		);
		assertExactlyOne(helper, zombies, zombie ->
			zombie.getMainHandItem().is(Items.IRON_AXE) && zombie.getOffhandItem().isEmpty(),
			"axeman"
		);
		assertExactlyOne(helper, zombies, zombie ->
			zombie.getMainHandItem().is(Items.IRON_SWORD) && zombie.getOffhandItem().is(Items.SHIELD),
			"sword-and-shield zombie"
		);
		assertExactlyOne(helper, zombies, zombie ->
			zombie.getMainHandItem().is(Items.IRON_AXE) && zombie.getOffhandItem().is(Items.SHIELD),
			"axe-and-shield zombie"
		);
		assertExactlyOne(helper, zombies, zombie ->
			zombie.getMainHandItem().isEmpty()
				&& zombie.getOffhandItem().isEmpty()
				&& ZombieBuilderInventory.count(zombie) > 0
				&& ZombieEngineerProfile.isEngineer(zombie),
			"engineer"
		);
		helper.assertTrue(
			zombies.stream().filter(ZombieEngineerProfile::isEngineer).count() == 3,
			"The builder, water, and lava variants were not merged into exactly three engineer identities."
		);
		assertExactlyOne(helper, zombies, zombie ->
			ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.WATER
				&& ZombieEngineerProfile.isEngineer(zombie),
			"water support zombie"
		);
		assertExactlyOne(helper, zombies, zombie ->
			ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.LAVA
				&& ZombieEngineerProfile.isEngineer(zombie),
			"lava harasser zombie"
		);
		assertExactlyOne(helper, zombies, zombie ->
			ZombieAirAssault.isAirAssaultLoadout(zombie)
				&& zombie.getMainHandItem().is(Items.IRON_SPEAR)
				&& zombie.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
				&& zombie.getOffhandItem().is(Items.FIREWORK_ROCKET)
				&& zombie.getOffhandItem().getCount() >= ZombieAirAssault.MINIMUM_ROCKETS
				&& zombie.getOffhandItem().getCount() <= ZombieAirAssault.MAXIMUM_ROCKETS,
			"spear air-assault zombie"
		);

		Map<Integer, Long> intelligenceCounts = zombies.stream().collect(Collectors.groupingBy(
			ZombieIntelligence::get,
			Collectors.counting()
		));
		helper.assertTrue(
			intelligenceCounts.equals(Map.of(3, 1L, 6, 1L, 7, 2L, 8, 2L, 9, 1L, 10, 2L)),
			"Showcase intelligence values no longer match the abilities each archetype needs: " + intelligenceCounts
		);
		helper.succeed();
	}

	private static void assertExactlyOne(
		final GameTestHelper helper,
		final List<Zombie> zombies,
		final Predicate<Zombie> predicate,
		final String archetype
	) {
		long count = zombies.stream().filter(predicate).count();
		helper.assertTrue(count == 1, "Expected one " + archetype + ", found " + count + ".");
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

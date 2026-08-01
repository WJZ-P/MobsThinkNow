package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证骷髅家族战术兵种、主世界变种和批量参数。 */
public final class SkeletonShowcaseCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void everySkeletonLiteralCreatesItsRequestedArchetype(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		var source = source(helper, sourceBlock);

		for (SkeletonShowcaseSpawner.ShowcaseArchetype archetype
			: SkeletonShowcaseSpawner.ShowcaseArchetype.values()) {
			helper.getLevel().getServer().getCommands().performPrefixedCommand(
				source,
				"mtn spawn " + archetype.commandId()
			);
		}

		List<AbstractSkeleton> skeletons = skeletonsNear(helper, sourceBlock, 12.0);
		helper.assertTrue(
			skeletons.size() == SkeletonShowcaseSpawner.ShowcaseArchetype.values().length,
			"Specific commands did not create every skeleton-family entry."
		);
		assertExactlyOne(helper, skeletons, skeleton ->
			skeleton.getType() == EntityType.SKELETON && skeleton.getMainHandItem().is(Items.BOW),
			"bow skeleton"
		);
		assertExactlyOne(helper, skeletons, skeleton ->
			skeleton.getType() == EntityType.SKELETON
				&& skeleton.getMainHandItem().is(Items.CROSSBOW)
				&& skeleton.getOffhandItem().isEmpty(),
			"ordinary crossbow skeleton"
		);
		assertExactlyOne(helper, skeletons, SkeletonShowcaseCommandGameTests::hasExplosiveCrossbow, "firework crossbow skeleton");
		for (SkeletonShowcaseSpawner.ShowcaseArchetype archetype
			: SkeletonShowcaseSpawner.ShowcaseArchetype.values()) {
			String expectedName = archetype.displayName().getString();
			helper.assertTrue(
				skeletons.stream().filter(skeleton ->
					skeleton.getCustomName() != null
						&& skeleton.getCustomName().getString().contains(expectedName)
				).count() == 1,
				"Command literal " + archetype.commandId() + " did not retain its test name."
			);
		}
		helper.succeed();
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void skeletonLiteralSupportsBatchCount(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawn skeleton_firework_crossbow 4"
		);

		List<AbstractSkeleton> skeletons = skeletonsNear(helper, sourceBlock, 14.0);
		helper.assertTrue(skeletons.size() == 4, "The skeleton batch command did not create exactly four entities.");
		helper.assertTrue(
			skeletons.stream().map(AbstractSkeleton::blockPosition).distinct().count() == 4,
			"Two skeleton test entities shared one feet position."
		);
		helper.assertTrue(
			skeletons.stream().allMatch(skeleton ->
				hasExplosiveCrossbow(skeleton)
					&& skeleton.getOffhandItem().getCount() == 6
					&& SkeletonIntelligence.get(skeleton) == 10
			),
			"At least one batch member lost its explosive crossbow loadout."
		);
		helper.succeed();
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 20,
		padding = 4
	)
	public void spawnSkeletonsShortcutCreatesOneOfEach(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnskeletons"
		);

		List<AbstractSkeleton> skeletons = skeletonsNear(helper, sourceBlock, 14.0);
		helper.assertTrue(
			skeletons.size() == SkeletonShowcaseSpawner.ShowcaseArchetype.values().length,
			"The skeleton shortcut did not create one of every tactical loadout and variant."
		);
		helper.assertTrue(
			skeletons.stream().allMatch(skeleton -> skeleton.isPersistenceRequired() && skeleton.isCustomNameVisible()),
			"A skeleton showcase entity was not persistent or had no visible archetype name."
		);
		Map<Integer, Long> intelligenceCounts = skeletons.stream().collect(Collectors.groupingBy(
			SkeletonIntelligence::get,
			Collectors.counting()
		));
		helper.assertTrue(
			intelligenceCounts.equals(Map.of(5, 1L, 7, 1L, 8, 2L, 9, 1L, 10, 1L)),
			"Skeleton showcase intelligence no longer matches its abilities: " + intelligenceCounts
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

	private static List<AbstractSkeleton> skeletonsNear(
		final GameTestHelper helper,
		final BlockPos sourceBlock,
		final double radius
	) {
		return helper.getLevel().getEntitiesOfClass(
			AbstractSkeleton.class,
			new AABB(sourceBlock).inflate(radius, 8.0, radius),
			skeleton -> OverworldUndeadFamilies.isSkeletonFamily(skeleton) && skeleton.isAlive()
		);
	}

	private static boolean hasExplosiveCrossbow(final AbstractSkeleton skeleton) {
		return skeleton.getMainHandItem().is(Items.CROSSBOW)
			&& skeleton.getOffhandItem().is(Items.FIREWORK_ROCKET)
			&& skeleton.getOffhandItem().has(DataComponents.FIREWORKS);
	}

	private static void assertExactlyOne(
		final GameTestHelper helper,
		final List<AbstractSkeleton> skeletons,
		final Predicate<AbstractSkeleton> predicate,
		final String archetype
	) {
		long count = skeletons.stream().filter(predicate).count();
		helper.assertTrue(count == 1, "Expected one " + archetype + ", found " + count + ".");
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

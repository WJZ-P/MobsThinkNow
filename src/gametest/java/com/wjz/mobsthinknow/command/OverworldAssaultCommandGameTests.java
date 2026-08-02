package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** 从真实 Brigadier 入口验证八单位主世界联合兵种演示编成。 */
public final class OverworldAssaultCommandGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void commandCreatesACompleteCombinedArmsGroup(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		CommandSourceStack source = source(helper, sourceBlock);
		helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "mtn spawn overworld_assault");

		AABB searchBox = new AABB(sourceBlock).move(0.0, 3.0, 8.0).inflate(14.0, 16.0, 14.0);
		List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(
			Zombie.class,
			searchBox,
			zombie -> OverworldUndeadFamilies.isZombieFamily(zombie) && isShowcase(zombie)
		);
		List<AbstractSkeleton> skeletons = helper.getLevel().getEntitiesOfClass(
			AbstractSkeleton.class,
			searchBox,
			skeleton -> OverworldUndeadFamilies.isSkeletonFamily(skeleton) && isShowcase(skeleton)
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

		helper.assertTrue(zombies.size() == 3, "Assault group did not create exactly three zombie specialists.");
		helper.assertTrue(skeletons.size() == 2, "Assault group did not create exactly two ranged specialists.");
		helper.assertTrue(creepers.size() == 1, "Assault group did not create exactly one breaching payload.");
		helper.assertTrue(spiders.size() == 2, "Assault group did not create exactly two carrier spiders.");
		helper.assertTrue(
			zombies.stream().filter(zombie -> zombie.getMainHandItem().is(Items.IRON_SWORD)
				&& zombie.getOffhandItem().is(Items.SHIELD)).count() == 1,
			"Assault group lost its sword-and-shield frontliner."
		);
		helper.assertTrue(
			zombies.stream().filter(zombie -> zombie.getMainHandItem().is(Items.IRON_AXE)).count() == 1,
			"Assault group lost its axeman."
		);
		helper.assertTrue(
			zombies.stream().filter(zombie -> ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.WATER).count() == 1,
			"Assault group lost its water-support engineer."
		);
		helper.assertTrue(
			skeletons.stream().filter(skeleton -> skeleton.getMainHandItem().is(Items.BOW)).count() == 1
				&& skeletons.stream().filter(skeleton -> skeleton.getMainHandItem().is(Items.CROSSBOW)).count() == 1,
			"Assault group did not retain one bow and one crossbow fire-support unit."
		);
		helper.assertTrue(
			zombies.stream().mapToInt(ZombieIntelligence::get).max().orElse(0) >= 8
				&& spiders.stream().mapToInt(SpiderIntelligence::get).max().orElse(0) == 10,
			"Assault presets no longer meet their high-IQ coordination thresholds."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void shortcutSupportsBatchGroupCount(final GameTestHelper helper) {
		BlockPos sourceBlock = helper.absolutePos(new BlockPos(32, 1, 8));
		helper.getLevel().getServer().getCommands().performPrefixedCommand(
			source(helper, sourceBlock),
			"mtn spawnoverworldassault 2"
		);

		AABB searchBox = new AABB(sourceBlock).move(0.0, 4.0, 10.0).inflate(18.0, 20.0, 18.0);
		long roots = helper.getLevel().getEntitiesOfClass(
			net.minecraft.world.entity.Mob.class,
			searchBox,
			OverworldAssaultCommandGameTests::isShowcase
		).size();
		helper.assertTrue(roots == 16, "Two assault groups did not create sixteen root mobs.");
		helper.succeed();
	}

	private static boolean isShowcase(final net.minecraft.world.entity.Mob mob) {
		return mob.isAlive() && mob.isPersistenceRequired() && mob.isCustomNameVisible() && mob.getCustomName() != null;
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

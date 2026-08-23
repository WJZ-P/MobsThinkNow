package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 地面实体、拾取动画链、装备槽和旧物掉落共同参与的武器换装集成测试。 */
public final class ZombieWeaponPickupGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void nearbyZombiesReserveDifferentGroundWeapons(final GameTestHelper helper) {
		Zombie first = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		Zombie second = helper.spawn(EntityType.ZOMBIE, 3, 0, 2);
		first.setNoAi(true);
		second.setNoAi(true);
		first.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		second.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);

		ItemEntity ironSword = new ItemEntity(
			helper.getLevel(),
			(first.getX() + second.getX()) * 0.5,
			first.getY(),
			(first.getZ() + second.getZ()) * 0.5,
			new ItemStack(Items.IRON_SWORD)
		);
		ItemEntity stoneSword = new ItemEntity(
			helper.getLevel(),
			(first.getX() + second.getX()) * 0.5,
			first.getY(),
			(first.getZ() + second.getZ()) * 0.5,
			new ItemStack(Items.STONE_SWORD)
		);
		helper.getLevel().addFreshEntity(ironSword);
		helper.getLevel().addFreshEntity(stoneSword);

		ZombieWeaponPickupGoal firstGoal = new ZombieWeaponPickupGoal(first);
		ZombieWeaponPickupGoal secondGoal = new ZombieWeaponPickupGoal(second);
		helper.assertTrue(firstGoal.canUse(), "The first zombie could not reserve the best weapon.");
		helper.assertTrue(secondGoal.canUse(), "The second zombie did not fall back to the unreserved weapon.");
		firstGoal.start();
		secondGoal.start();
		firstGoal.tick();
		secondGoal.tick();

		helper.assertTrue(first.getMainHandItem().is(Items.IRON_SWORD), "The first zombie lost its reserved iron sword.");
		helper.assertTrue(second.getMainHandItem().is(Items.STONE_SWORD), "Both zombies chased the iron sword instead of splitting up.");
		firstGoal.stop();
		secondGoal.stop();
		helper.succeed();
	}

	@GameTest
	public void groundZombieVariantsShareManagedWeaponPickup(final GameTestHelper helper) {
		Zombie husk = helper.spawn(EntityType.HUSK, 1, 0, 1);
		Zombie villager = helper.spawn(EntityType.ZOMBIE_VILLAGER, 3, 0, 1);
		Zombie drowned = helper.spawn(EntityType.DROWNED, 5, 0, 1);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		helper.assertTrue(
			ZombieWeaponPickupGoal.managesWeapon(husk, sword, config),
			"Husk weapon pickup bypassed the shared upgrade transaction."
		);
		helper.assertTrue(
			ZombieWeaponPickupGoal.managesWeapon(villager, sword, config),
			"Zombie villager weapon pickup bypassed the shared upgrade transaction."
		);
		helper.assertTrue(
			!ZombieWeaponPickupGoal.managesWeapon(drowned, sword, config),
			"Drowned lost its separate amphibious equipment boundary."
		);
		helper.succeed();
	}

	@GameTest
	public void zombiePrioritizesBestGroundWeaponAndDropsMainHandJunk(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		zombie.setNoAi(true);
		zombie.clearFire();
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ROTTEN_FLESH, 3));
		helper.assertTrue(
			ZombieWeaponPickupGoal.canReplaceMainHand(new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.IRON_SWORD)),
			"Main-hand junk was not considered replaceable."
		);
		helper.assertTrue(
			!ZombieWeaponPickupGoal.canReplaceMainHand(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.WOODEN_SWORD)),
			"A weaker weapon was incorrectly considered an upgrade."
		);
		helper.assertTrue(
			!ZombieWeaponPickupGoal.canReplaceMainHand(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.IRON_SWORD)),
			"A tactical water bucket was incorrectly treated as junk."
		);

		ItemEntity woodenSword = new ItemEntity(
			helper.getLevel(), zombie.getX(), zombie.getY(), zombie.getZ(), new ItemStack(Items.WOODEN_SWORD)
		);
		ItemEntity ironSword = new ItemEntity(
			helper.getLevel(), zombie.getX(), zombie.getY(), zombie.getZ(), new ItemStack(Items.IRON_SWORD)
		);
		helper.getLevel().addFreshEntity(woodenSword);
		helper.getLevel().addFreshEntity(ironSword);

		ZombieWeaponPickupGoal goal = new ZombieWeaponPickupGoal(zombie);
		helper.assertTrue(goal.canUse(), "The zombie did not detect either reachable ground weapon.");
		goal.start();
		goal.tick();

		helper.assertTrue(zombie.getMainHandItem().is(Items.IRON_SWORD), "The wooden sword outranked the iron sword.");
		helper.assertTrue(ironSword.isRemoved(), "The equipped iron sword remained duplicated on the ground.");
		helper.assertTrue(woodenSword.isAlive(), "Picking the best weapon also consumed the rejected wooden sword.");
		List<ItemEntity> nearbyItems = helper.getLevel().getEntitiesOfClass(
			ItemEntity.class,
			zombie.getBoundingBox().inflate(2.0),
			entity -> entity.getItem().is(Items.ROTTEN_FLESH)
		);
		helper.assertTrue(
			nearbyItems.stream().anyMatch(entity -> entity.getItem().getCount() == 3),
			"The replaced main-hand junk was deleted instead of being dropped intact."
		);
		goal.stop();
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

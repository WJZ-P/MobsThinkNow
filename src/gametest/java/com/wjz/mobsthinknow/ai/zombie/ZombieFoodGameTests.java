package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

public final class ZombieFoodGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void foodReservationPreventsDogpileAndReleasesRemainingStack(final GameTestHelper helper) {
		Zombie first = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		Zombie second = helper.spawn(EntityType.ZOMBIE, 3, 0, 2);
		for (Zombie zombie : new Zombie[]{first, second}) {
			zombie.setNoAi(true);
			zombie.setHealth(8.0F);
			ZombieIntelligence.set(zombie, 10);
		}
		ItemEntity bread = new ItemEntity(
			helper.getLevel(),
			(first.getX() + second.getX()) * 0.5,
			first.getY(),
			(first.getZ() + second.getZ()) * 0.5,
			new ItemStack(Items.BREAD, 2)
		);
		helper.getLevel().addFreshEntity(bread);

		ZombieFoodSearchGoal firstGoal = new ZombieFoodSearchGoal(first, (candidate, intelligence, minimum) -> true);
		ZombieFoodSearchGoal blockedGoal = new ZombieFoodSearchGoal(second, (candidate, intelligence, minimum) -> true);
		helper.assertTrue(firstGoal.canUse(), "The first zombie could not reserve the bread stack.");
		helper.assertTrue(!blockedGoal.canUse(), "Two zombies reserved the same ItemEntity at once.");

		firstGoal.start();
		firstGoal.tick();
		helper.assertTrue(bread.getItem().getCount() == 1, "The first zombie did not take exactly one serving.");
		ZombieFoodSearchGoal releasedGoal = new ZombieFoodSearchGoal(second, (candidate, intelligence, minimum) -> true);
		helper.assertTrue(releasedGoal.canUse(), "The remaining bread stayed reserved after the first serving was taken.");
		releasedGoal.stop();
		firstGoal.stop();
		helper.succeed();
	}

	@GameTest
	public void treasureFoodOutranksRottenFleshAtTheSameDistance(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		zombie.setNoAi(true);
		zombie.clearFire();
		zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setHealth(8.0F);
		ZombieIntelligence.set(zombie, 10);

		ItemEntity rottenFlesh = new ItemEntity(
			helper.getLevel(), zombie.getX(), zombie.getY(), zombie.getZ(), new ItemStack(Items.ROTTEN_FLESH)
		);
		ItemEntity enchantedApple = new ItemEntity(
			helper.getLevel(), zombie.getX(), zombie.getY(), zombie.getZ(), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)
		);
		helper.getLevel().addFreshEntity(rottenFlesh);
		helper.getLevel().addFreshEntity(enchantedApple);

		ZombieFoodSearchGoal goal = new ZombieFoodSearchGoal(zombie, (candidate, intelligence, minimum) -> true);
		helper.assertTrue(goal.canUse(), "The smart zombie did not find either available food item.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			zombie.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE),
			"Rotten flesh was selected before the enchanted golden apple."
		);
		helper.assertTrue(rottenFlesh.isAlive(), "The fallback rotten flesh was consumed with treasure available.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void smartZombieEatsOneServingHealsAndRestoresBothHands(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		zombie.setNoAi(true);
		zombie.clearFire();
		helper.assertTrue(ZombieFoodSearchGoal.isFood(new ItemStack(Items.ROTTEN_FLESH)), "Rotten flesh was not recognized as food.");
		helper.assertTrue(!ZombieFoodSearchGoal.isFood(new ItemStack(Items.SHIELD)), "A shield was incorrectly recognized as food.");
		helper.assertTrue(
			!ZombieFoodSearchGoal.isFood(zombie.getMainHandItem())
				&& !ZombieFoodSearchGoal.isFood(zombie.getOffhandItem()),
			"A newly spawned zombie unexpectedly started with food."
		);

		ZombieIntelligence.set(zombie, 10);
		zombie.setHealth(8.0F);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		ItemEntity food = new ItemEntity(
			helper.getLevel(),
			zombie.getX(), zombie.getY(), zombie.getZ(),
			new ItemStack(Items.BREAD, 3)
		);
		helper.getLevel().addFreshEntity(food);

		ZombieFoodSearchGoal goal = new ZombieFoodSearchGoal(zombie, (candidate, intelligence, minimum) -> true);
		helper.assertTrue(goal.canUse(), "An IQ-10 low-health zombie did not find reachable bread at its feet.");
		goal.start();
		goal.tick();
		helper.assertTrue(zombie.isUsingItem(), "The zombie did not enter the vanilla use-item animation.");
		helper.assertTrue(zombie.getUsedItemHand() == InteractionHand.OFF_HAND, "A weapon holder did not eat with its offhand.");
		helper.assertTrue(zombie.getMainHandItem().is(Items.IRON_SWORD), "The main-hand weapon was replaced while eating.");
		helper.assertTrue(zombie.getOffhandItem().is(Items.BREAD), "The temporary offhand does not contain the selected food.");
		helper.assertTrue(food.getItem().getCount() == 2, "The pickup consumed more than one serving from the ground stack.");
		helper.assertTrue(
			zombie.getCustomName() != null && zombie.getCustomName().getString().endsWith("[10]"),
			"The visible zombie name was not updated with its intelligence value."
		);

		AtomicBoolean resolved = new AtomicBoolean();
		helper.onEachTick(() -> {
			zombie.clearFire();
			if (goal.canContinueToUse()) {
				goal.tick();
				return;
			}
			if (resolved.getAndSet(true)) {
				return;
			}
			goal.stop();
			helper.assertTrue(zombie.getHealth() == 13.0F, "Bread nutrition 5 did not immediately restore exactly 5 health.");
			helper.assertTrue(zombie.getMainHandItem().is(Items.IRON_SWORD), "The weapon was not restored after eating.");
			helper.assertTrue(zombie.getOffhandItem().is(Items.SHIELD), "The temporarily stowed shield was not restored.");
			helper.assertTrue(food.isAlive() && food.getItem().getCount() == 2, "The remaining bread stack was altered after the meal.");
			helper.succeed();
		});
	}

	@GameTest
	public void groundZombieVariantsShareFoodInterception(final GameTestHelper helper) {
		Zombie husk = helper.spawn(EntityType.HUSK, 1, 0, 1);
		Zombie villager = helper.spawn(EntityType.ZOMBIE_VILLAGER, 3, 0, 1);
		Zombie drowned = helper.spawn(EntityType.DROWNED, 5, 0, 1);
		var config = new MobsThinkNowConfig();
		ItemStack bread = new ItemStack(Items.BREAD);
		helper.assertTrue(
			ZombieFoodSearchGoal.managesFood(husk, bread, config),
			"Husk food was left to vanilla looting instead of the shared ground-family transaction."
		);
		helper.assertTrue(
			ZombieFoodSearchGoal.managesFood(villager, bread, config),
			"Zombie villager food was left to vanilla looting instead of the shared ground-family transaction."
		);
		helper.assertTrue(
			!ZombieFoodSearchGoal.managesFood(drowned, bread, config),
			"Drowned lost its deliberately separate amphibious behavior boundary."
		);
		helper.succeed();
	}

	@GameTest
	public void conversionRestoresStowedWeaponBeforeEquipmentIsCopied(final GameTestHelper helper) throws Exception {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		zombie.setNoAi(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		ZombieFoodEquipment.begin(zombie, InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
		helper.assertTrue(zombie.getMainHandItem().is(Items.BREAD), "The fixture did not expose temporary food.");

		Method conversion = Zombie.class.getDeclaredMethod(
			"convertToZombieType",
			net.minecraft.server.level.ServerLevel.class,
			EntityType.class
		);
		conversion.setAccessible(true);
		conversion.invoke(zombie, helper.getLevel(), EntityType.DROWNED);

		List<Drowned> converted = helper.getLevel().getEntitiesOfClass(
			Drowned.class,
			new AABB(zombie.blockPosition()).inflate(3.0),
			Drowned::isAlive
		);
		helper.assertTrue(converted.size() == 1, "The controlled zombie-to-drowned conversion did not complete.");
		helper.assertTrue(
			converted.getFirst().getMainHandItem().is(Items.IRON_SWORD),
			"Conversion permanently copied the one-serving animation food over the stowed weapon."
		);
		helper.assertTrue(!ZombieFoodEquipment.isActive(zombie), "The removed source retained a stale food hand swap.");
		helper.assertTrue(
			helper.getLevel().getEntitiesOfClass(
				ItemEntity.class,
				new AABB(zombie.blockPosition()).inflate(3.0),
				item -> item.isAlive() && item.getItem().is(Items.BREAD)
			).size() == 1,
			"The interrupted serving was neither consumed nor returned to the world."
		);
		helper.succeed();
	}

	@GameTest
	public void missingFoodOrLowIntelligenceLeavesNormalAiAvailable(final GameTestHelper helper) {
		Zombie smartButEmpty = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		smartButEmpty.setNoAi(true);
		smartButEmpty.setHealth(8.0F);
		ZombieIntelligence.set(smartButEmpty, 10);
		ZombieFoodSearchGoal noFoodGoal = new ZombieFoodSearchGoal(
			smartButEmpty,
			(candidate, intelligence, minimum) -> true
		);
		helper.assertTrue(!noFoodGoal.canUse(), "The food Goal started without finding a food entity.");

		Zombie lowIntelligence = helper.spawn(EntityType.ZOMBIE, 4, 0, 2);
		lowIntelligence.setNoAi(true);
		lowIntelligence.setHealth(8.0F);
		ZombieIntelligence.set(lowIntelligence, 5);
		ItemEntity bread = new ItemEntity(
			helper.getLevel(),
			lowIntelligence.getX(), lowIntelligence.getY(), lowIntelligence.getZ(),
			new ItemStack(Items.BREAD)
		);
		helper.getLevel().addFreshEntity(bread);
		ZombieFoodSearchGoal lowIntelligenceGoal = new ZombieFoodSearchGoal(
			lowIntelligence,
			(candidate, intelligence, minimum) -> true
		);
		helper.assertTrue(!lowIntelligenceGoal.canUse(), "An IQ-5 zombie incorrectly mastered food scavenging.");
		helper.assertTrue(bread.getItem().getCount() == 1, "The rejected food was removed from the world.");
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

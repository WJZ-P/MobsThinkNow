package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.MovementMode;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class SkeletonRangedTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void regularSkeletonReceivesSmartBowGoal(final GameTestHelper helper) {
		long installedBefore = SmartSkeletonMetrics.snapshot().installedGoals();
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		// helper.spawn 不执行自然生成的默认装备流程；显式模拟 finalizeSpawn 末尾的武器重评。
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.reassessWeaponGoal();

		helper.assertTrue(skeleton.isAlive(), "The integration-test skeleton did not spawn.");
		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().installedGoals() > installedBefore,
			"Creating a regular skeleton did not install the smart bow goal."
		);
		helper.succeed();
	}

	@GameTest
	public void closeTargetImmediatelySelectsRetreatInsteadOfFaceTanking(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 4, 2, 2);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(target);

		SmartSkeletonBowAttackGoal goal = new SmartSkeletonBowAttackGoal(skeleton, 1.0, 40, 15.0F);
		helper.assertTrue(goal.canUse(), "A bow skeleton with a live target could not start its ranged goal.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			goal.movementMode() == MovementMode.RETREAT,
			"A target two blocks away did not immediately select the retreat band."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest
	public void incomingArrowSelectsBoundedDodgeBurst(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 4, 2, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 12, 2, 4);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(target);

		Vec3 center = skeleton.getBoundingBox().getCenter();
		Arrow arrow = new Arrow(
			helper.getLevel(),
			center.x,
			center.y,
			center.z - 4.0,
			new ItemStack(Items.ARROW),
			new ItemStack(Items.BOW)
		);
		arrow.setOwner(target);
		arrow.setNoGravity(true);
		arrow.setNoPhysics(true);
		arrow.setDeltaMovement(0.0, 0.0, 0.75);
		helper.assertTrue(helper.getLevel().addFreshEntity(arrow), "The incoming-arrow fixture was not added.");

		SmartSkeletonBowAttackGoal goal = new SmartSkeletonBowAttackGoal(skeleton, 1.0, 40, 15.0F);
		helper.assertTrue(goal.canUse(), "The dodge test bow goal did not start.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			goal.movementMode() == MovementMode.DODGE,
			"An arrow crossing the skeleton center within eight ticks did not start a dodge burst."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest
	public void movingTargetReceivesHorizontalPredictionBeforeArrowSpawn(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 12, 2, 2);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		target.setInvulnerable(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		target.setDeltaMovement(0.0, 0.0, 0.25);
		long predictionsBefore = SmartSkeletonMetrics.snapshot().predictiveShots();

		skeleton.performRangedAttack(target, 1.0F);

		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().predictiveShots() > predictionsBefore,
			"The moving target did not receive a horizontal prediction adjustment."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

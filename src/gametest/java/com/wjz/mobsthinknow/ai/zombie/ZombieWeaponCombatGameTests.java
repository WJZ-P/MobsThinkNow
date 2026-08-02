package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 剑士佯攻与斧手武器节奏的真实实体集成测试。 */
public final class ZombieWeaponCombatGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 60)
	public void swordFeintDealsNoDamageUntilBlockingTargetDropsShield(final GameTestHelper helper) {
		Zombie swordsman = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		Zombie defender = helper.spawn(EntityType.ZOMBIE, 3, 0, 2);
		swordsman.setNoAi(true);
		defender.setNoAi(true);
		swordsman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		swordsman.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		defender.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		defender.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		defender.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0);
		defender.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		defender.setHealth(40.0F);
		defender.startUsingItem(InteractionHand.OFF_HAND);
		ZombieIntelligence.set(swordsman, 10);

		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.swordFeints = true;
		config.swordFeintMinimumIntelligence = 7;
		config.swordFeintChance = 1.0;
		ZombieWeaponCombat combat = new ZombieWeaponCombat(swordsman);
		long feintsBefore = SmartZombieMetrics.snapshot().swordFeints();
		boolean[] feintStarted = {false};
		long[] feintStartedAt = {Long.MIN_VALUE};
		float untouchedHealth = defender.getHealth();

		helper.onEachTick(() -> {
			swordsman.clearFire();
			defender.clearFire();
			defender.invulnerableTime = 0;
			if (!feintStarted[0] && !defender.isBlocking()) {
				return;
			}

			long now = helper.getLevel().getGameTime();
			if (!feintStarted[0]) {
				boolean delegated = combat.tick(defender, config);
				ZombieBodyActionAccess action = (ZombieBodyActionAccess)swordsman;
				helper.assertTrue(!delegated, "The eligible sword cycle skipped feint ownership.");
				helper.assertTrue(
					action.mobsthinknow$getBodyAction() == ZombieBodyAction.SWORD_FEINT,
					"The sword feint did not publish its synchronized body action."
				);
				helper.assertTrue(
					SmartZombieMetrics.snapshot().swordFeints() > feintsBefore,
					"The started sword feint was absent from /mtn status diagnostics."
				);
				feintStarted[0] = true;
				feintStartedAt[0] = action.mobsthinknow$getBodyActionStartedAt();
				return;
			}

			long elapsed = now - feintStartedAt[0];
			helper.assertTrue(
				defender.getHealth() == untouchedHealth,
				"The visual sword feint dealt damage before a real attack was authorized."
			);
			if (elapsed >= 8L && defender.isUsingItem()) {
				defender.stopUsingItem();
			}

			boolean delegated = combat.tick(defender, config);
			if (!delegated) {
				return;
			}
			helper.assertTrue(elapsed >= 6L, "Dropping the shield bypassed the readable feint windup.");
			helper.assertTrue(!defender.isBlocking(), "The real strike was authorized while the shield remained raised.");
			helper.assertTrue(
				((ZombieBodyActionAccess)swordsman).mobsthinknow$getBodyAction() == ZombieBodyAction.NONE,
				"Committing from a feint left the model stuck in its fake swing."
			);
			helper.assertTrue(combat.canPerformAttack(defender), "The dropped shield did not open the real strike.");
			boolean hit = combat.performAttack(defender, false);
			combat.onAttackPerformed(defender);
			helper.assertTrue(
				hit && defender.getHealth() < untouchedHealth,
				"The committed sword strike did not deal real damage."
			);
			combat.stop();
			helper.succeed();
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

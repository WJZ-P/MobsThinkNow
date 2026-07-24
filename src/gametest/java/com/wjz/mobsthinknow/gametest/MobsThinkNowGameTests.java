package com.wjz.mobsthinknow.gametest;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class MobsThinkNowGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void vanillaZombieReceivesSmartAttackGoal(final GameTestHelper helper) {
		long installedBefore = SmartZombieMetrics.snapshot().installedGoals();
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		long installedAfter = SmartZombieMetrics.snapshot().installedGoals();

		helper.assertTrue(zombie.isAlive(), "The integration-test zombie did not spawn.");
		helper.assertTrue(
			installedAfter > installedBefore,
			"Creating a vanilla zombie did not install the Mobs Think Now attack goal."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

package com.wjz.mobsthinknow.gametest;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;

public final class MobsThinkNowGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void vanillaZombieReceivesSmartAttackGoal(final GameTestHelper helper) {
		long installedBefore = SmartZombieMetrics.snapshot().installedGoals();
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		long installedAfter = SmartZombieMetrics.snapshot().installedGoals();
		ZombieIntelligence.set(zombie, 9);
		Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		restored.restoreFrom(zombie);

		helper.assertTrue(zombie.isAlive(), "The integration-test zombie did not spawn.");
		helper.assertTrue(
			installedAfter > installedBefore,
			"Creating a vanilla zombie did not install the Mobs Think Now attack goal."
		);
		helper.assertTrue(
			ZombieIntelligence.get(restored) == 9,
			"Zombie intelligence did not survive the vanilla entity save/load path."
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void squadElectsSmartestZombieAndReelectsAfterLeaderLoss(final GameTestHelper helper) {
		Zombie first = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie smartest = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie successor = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		Villager target = helper.spawn(EntityType.VILLAGER, 10, 2, 1);
		List<Zombie> zombies = List.of(first, smartest, successor);

		// 固定实体，避免寻路和日照等无关因素影响这个协调器集成测试。
		target.setNoAi(true);
		target.setNoGravity(true);
		for (Zombie zombie : zombies) {
			zombie.setNoAi(true);
			zombie.setNoGravity(true);
			zombie.setInvulnerable(true);
			zombie.setTarget(target);
		}
		ZombieIntelligence.set(first, 4);
		ZombieIntelligence.set(smartest, 10);
		ZombieIntelligence.set(successor, 8);

		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		AtomicBoolean originalLeaderRemoved = new AtomicBoolean();
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			for (Zombie zombie : zombies) {
				if (zombie.isAlive() && !zombie.isRemoved()) {
					zombie.setTarget(target);
					coordinator.heartbeat(zombie, target, true, target.position(), now);
				}
			}
			ZombieSquadCoordinator.tickLevel(helper.getLevel());

			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(successor);
			if (view == null) {
				return;
			}

			if (!originalLeaderRemoved.get()) {
				helper.assertTrue(view.leaderEntityId() == smartest.getId(), "The highest-intelligence zombie was not elected.");
				helper.discard(smartest);
				originalLeaderRemoved.set(true);
				return;
			}

			if (view.term() >= 2) {
				helper.assertTrue(view.leaderEntityId() == successor.getId(), "The best surviving zombie was not re-elected.");
				helper.assertTrue(view.memberCount() == 2, "The reorganized squad has an unexpected member count.");
				helper.succeed();
			}
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

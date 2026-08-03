package com.wjz.mobsthinknow.ai.activity;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** 在真实 Mob 键上验证租约的优先级抢占、所有权和精确释放。 */
public final class TacticalActivityLeaseGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 20)
	public void higherPriorityActivityPreemptsWithoutAllowingOldOwnerToRenew(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		zombie.setNoAi(true);
		long now = helper.getLevel().getGameTime();
		TacticalActivityLease.Handle melee = TacticalActivityLease.handle(TacticalActivity.MELEE);
		TacticalActivityLease.Handle otherMelee = TacticalActivityLease.handle(TacticalActivity.MELEE);
		TacticalActivityLease.Handle engineering = TacticalActivityLease.handle(TacticalActivity.ENGINEERING);
		TacticalActivityLease.Handle retreat = TacticalActivityLease.handle(TacticalActivity.RETREAT);

		helper.assertTrue(melee.acquire(zombie, now), "Initial melee activity did not acquire its lease.");
		helper.assertTrue(!otherMelee.canAcquire(zombie, now), "Equal-priority activity incorrectly stole the lease.");
		helper.assertTrue(engineering.acquire(zombie, now), "Higher-priority engineering did not preempt melee.");
		helper.assertTrue(!melee.owns(zombie, now), "Preempted melee still reported ownership.");
		helper.assertTrue(!melee.renew(zombie, now), "Preempted melee renewed somebody else's lease.");
		helper.assertTrue(retreat.acquire(zombie, now), "Retreat did not preempt engineering.");
		TacticalActivityLease.Snapshot active = TacticalActivityLease.snapshot(zombie, now);
		helper.assertTrue(
			active != null && active.activity() == TacticalActivity.RETREAT,
			"The active lease did not expose the highest-priority activity."
		);

		retreat.release(zombie);
		helper.assertTrue(TacticalActivityLease.snapshot(zombie, now) == null, "Exact-owner release left a stale lease.");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void abandonedLeaseExpiresAfterItsBoundedGraceWindow(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		zombie.setNoAi(true);
		long now = helper.getLevel().getGameTime();
		TacticalActivityLease.Handle ranged = TacticalActivityLease.handle(TacticalActivity.RANGED);

		helper.assertTrue(ranged.acquire(zombie, now), "Ranged activity did not acquire its lease.");
		helper.assertTrue(
			TacticalActivityLease.snapshot(zombie, now + 4L) == null,
			"An abandoned lease survived beyond the three-tick grace window."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

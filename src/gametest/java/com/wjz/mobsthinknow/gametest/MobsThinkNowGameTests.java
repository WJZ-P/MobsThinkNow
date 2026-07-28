package com.wjz.mobsthinknow.gametest;

import com.wjz.mobsthinknow.ai.zombie.ReactiveRetreatGoal;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierState;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.ai.zombie.ZombieVoiceProfile;
import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class MobsThinkNowGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void vanillaZombieReceivesSmartAttackGoal(final GameTestHelper helper) {
		long installedBefore = SmartZombieMetrics.snapshot().installedGoals();
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		long installedAfter = SmartZombieMetrics.snapshot().installedGoals();
		ZombieIntelligence.set(zombie, 9);
		ZombieEngineerProfile.setEngineer(zombie, true);
		BlockPos engineerWaterSource = zombie.blockPosition().offset(2, 0, 0);
		ZombieSpecialEquipment.markEngineerDeployed(
			zombie,
			UtilityClass.WATER,
			engineerWaterSource,
			helper.getLevel().getGameTime() + 80L
		);
		float voiceFactor = ZombieVoiceProfile.factor(zombie);
		double movementSpeed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
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
		helper.assertTrue(
			ZombieEngineerProfile.isEngineer(restored),
			"The formal engineer identity did not survive the vanilla entity save/load path."
		);
		ZombieFluidCarrierState restoredEngineerFluid = ZombieSpecialEquipment.state(restored);
		helper.assertTrue(
			restoredEngineerFluid.isEngineerDeployment()
				&& restoredEngineerFluid.utility() == UtilityClass.WATER
				&& engineerWaterSource.equals(restoredEngineerFluid.source()),
			"The engineer's pending synthetic-bucket source transaction did not survive save/load."
		);
		helper.assertTrue(
			Math.abs(ZombieVoiceProfile.factor(restored) - voiceFactor) < 0.0001F,
			"The zombie's individual voice pitch did not survive save/load."
		);
		helper.assertTrue(
			Math.abs(restored.getAttributeValue(Attributes.MOVEMENT_SPEED) - movementSpeed) < 0.000001,
			"The permanent individual movement trait did not survive save/load."
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
				helper.assertTrue(smartest.getCustomName() != null, "The squad leader did not receive a role name tag.");
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

	@GameTest(maxTicks = 60)
	public void lowHealthZombieRetreatsAfterFreshAttack(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		// 放近只为稳定测试伤害来源；独立的撤退 Goal 本身不依赖近战追击 Goal 是否启动。
		Villager attacker = helper.spawn(EntityType.VILLAGER, 3, 2, 2);
		long retreatsBefore = SmartZombieMetrics.snapshot().retreats();
		AtomicBoolean damageApplied = new AtomicBoolean();

		attacker.setNoAi(true);
		attacker.setNoGravity(true);
		attacker.setInvulnerable(true);
		// 生成装备可能带来护甲减伤；从阈值本身开始受击，保证结算后仍位于 20% 以下。
		zombie.setHealth(4.0F);
		zombie.setTarget(attacker);

		helper.onEachTick(() -> {
			zombie.setTarget(attacker);
			zombie.clearFire();
			if (!damageApplied.get() && zombie.tickCount >= 2) {
				boolean hurt = zombie.hurtServer(
					helper.getLevel(),
					zombie.damageSources().mobAttack(attacker),
					1.0F
				);
				helper.assertTrue(hurt, "The test attack did not damage the zombie.");
				helper.assertTrue(
					zombie.getHealth() <= 4.0F,
					"The post-damage health did not reach the 20% retreat threshold."
				);
				damageApplied.set(true);
			}

			if (SmartZombieMetrics.snapshot().retreats() > retreatsBefore) {
				helper.assertTrue(zombie.getHealth() <= 4.0F, "Retreat triggered above the 20% health threshold.");
				helper.succeed();
			}
		});
	}

	@GameTest
	public void retreatGoalStopsAtConfiguredFiveBlockSafetyRadius(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 0, 2);
		Villager attacker = helper.spawn(EntityType.VILLAGER, 3, 0, 2);

		// 关闭实体自己的 GoalSelector，单独驱动待验证的 Goal，避免它抢先消费同步伤害事件。
		zombie.setNoAi(true);
		attacker.setNoAi(true);
		attacker.setInvulnerable(true);
		zombie.setHealth(4.0F);
		boolean hurt = zombie.hurtServer(
			helper.getLevel(),
			zombie.damageSources().mobAttack(attacker),
			1.0F
		);
		helper.assertTrue(hurt, "The safety-radius test attack did not reach the zombie.");

		ReactiveRetreatGoal goal = new ReactiveRetreatGoal(zombie);
		helper.assertTrue(goal.canUse(), "A nearby attacker did not start the retreat goal.");
		goal.start();
		helper.assertTrue(goal.canContinueToUse(), "The retreat ended before either termination boundary was reached.");

		zombie.setPos(attacker.getX() + 5.0, zombie.getY(), attacker.getZ());
		helper.assertTrue(
			!goal.canContinueToUse(),
			"The retreat continued at the configured five-block horizontal safety radius."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void heavyHitZombieRetreatsWhileStillAboveLowHealthThreshold(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		Villager attacker = helper.spawn(EntityType.VILLAGER, 3, 2, 2);
		long retreatsBefore = SmartZombieMetrics.snapshot().retreats();
		AtomicBoolean damageApplied = new AtomicBoolean();

		attacker.setNoAi(true);
		attacker.setNoGravity(true);
		attacker.setInvulnerable(true);
		// 清掉生成时的随机护甲并归零护甲属性，让 6 点攻击恰好验证“最大生命值 30%”边界。
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
		zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		zombie.setHealth(zombie.getMaxHealth());
		zombie.setTarget(attacker);

		helper.onEachTick(() -> {
			zombie.setTarget(attacker);
			zombie.clearFire();
			if (!damageApplied.get() && zombie.tickCount >= 2) {
				float healthBefore = zombie.getHealth();
				boolean hurt = zombie.hurtServer(
					helper.getLevel(),
					zombie.damageSources().mobAttack(attacker),
					6.0F
				);
				helper.assertTrue(hurt, "The heavy-hit test attack did not damage the zombie.");
				helper.assertTrue(
					healthBefore - zombie.getHealth() >= zombie.getMaxHealth() * 0.30F,
					"The hit did not remove 30% of maximum health."
				);
				helper.assertTrue(
					zombie.getHealth() > zombie.getMaxHealth() * 0.20F,
					"The heavy-hit test accidentally entered the low-health trigger range."
				);
				damageApplied.set(true);
			}

			if (SmartZombieMetrics.snapshot().retreats() > retreatsBefore) {
				helper.assertTrue(
					zombie.getHealth() > zombie.getMaxHealth() * 0.20F,
					"Retreat was not isolated to the heavy-hit trigger."
				);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 120)
	public void axeZombieUsesFallingLeapCritical(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 0, 2);
		AtomicBoolean sawAirborneLeap = new AtomicBoolean();
		AtomicBoolean sawDescendingLeap = new AtomicBoolean();
		float[] targetHealthBeforeAttack = {100.0F};

		target.setNoAi(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		target.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		target.setHealth(100.0F);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_AXE));
		// 隔离武器时序：头盔避免日光生存 Goal 抢占本测试。
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setTarget(target);

		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(target);
			target.invulnerableTime = 0;
			if (!zombie.onGround()) {
				sawAirborneLeap.set(true);
				if (zombie.getDeltaMovement().y < -0.02) {
					sawDescendingLeap.set(true);
				}
			}

			if (target.getHealth() < targetHealthBeforeAttack[0]) {
				float damage = targetHealthBeforeAttack[0] - target.getHealth();
				float normalWeaponDamage = (float)zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
				helper.assertTrue(sawAirborneLeap.get(), "The axe zombie hit without visibly leaving the ground.");
				helper.assertTrue(sawDescendingLeap.get(), "The axe zombie did not wait for the falling phase.");
				helper.assertTrue(
					damage >= normalWeaponDamage * 1.45F,
					"The axe leap did not apply the expected 1.5x critical damage."
				);
				helper.succeed();
			}
			if (zombie.tickCount == 100) {
				helper.fail(combatDiagnostic("axe", zombie, target));
			}
		});
	}

	@GameTest(maxTicks = 140)
	public void swordZombieCirclesDuringWeaponCooldownWithoutJumping(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 3, 0, 2);
		long[] firstHitAt = {Long.MIN_VALUE};
		float[] healthAfterFirstHit = {100.0F};
		double[] maximumDistanceAfterFirstHit = {0.0};
		AtomicBoolean becameAirborne = new AtomicBoolean();

		target.setNoAi(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		target.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		target.setHealth(100.0F);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		// 隔离武器时序：头盔避免日光生存 Goal 抢占本测试。
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		zombie.setTarget(target);

		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(target);
			// 测试只验证武器 CD；清掉目标通用 20 tick 受伤无敌帧，避免它遮蔽剑的 13 tick 组件冷却。
			target.invulnerableTime = 0;
			long now = helper.getLevel().getGameTime();

			if (firstHitAt[0] == Long.MIN_VALUE && target.getHealth() < 100.0F) {
				firstHitAt[0] = now;
				healthAfterFirstHit[0] = target.getHealth();
				return;
			}
			if (firstHitAt[0] == Long.MIN_VALUE) {
				return;
			}

			maximumDistanceAfterFirstHit[0] = Math.max(
				maximumDistanceAfterFirstHit[0],
				ZombieDistance.horizontalSquared(zombie.position(), target.position())
			);
			if (!zombie.onGround()) {
				becameAirborne.set(true);
			}

			if (target.getHealth() < healthAfterFirstHit[0]) {
				long elapsed = now - firstHitAt[0];
				helper.assertTrue(elapsed >= 13L, "The iron sword attacked before its 13-tick item cooldown.");
				helper.assertTrue(
					maximumDistanceAfterFirstHit[0] >= 1.8 * 1.8,
					"The sword zombie stayed face-to-face instead of circling during cooldown."
				);
				helper.assertTrue(!becameAirborne.get(), "The sword zombie incorrectly used the axe leap behavior.");
				helper.succeed();
			}
			if (zombie.tickCount == 120) {
				helper.fail(combatDiagnostic("sword", zombie, target));
			}
		});
	}

	private static String combatDiagnostic(final String weapon, final Zombie zombie, final Villager target) {
		return weapon
			+ " combat stalled: zombie=" + zombie.position()
			+ ", target=" + target.position()
			+ ", distance=" + Math.sqrt(zombie.distanceToSqr(target))
			+ ", lineOfSight=" + zombie.getSensing().hasLineOfSight(target)
			+ ", navigationDone=" + zombie.getNavigation().isDone()
			+ ", aggressive=" + zombie.isAggressive()
			+ ", onGround=" + zombie.onGround()
			+ ", movement=" + zombie.getDeltaMovement()
			+ ", targetHealth=" + target.getHealth();
	}

	/** GameTest 源集不依赖主代码的包私有测试辅助方法，保留一个最小二维距离函数。 */
	private static final class ZombieDistance {
		private ZombieDistance() {
		}

		private static double horizontalSquared(final Vec3 first, final Vec3 second) {
			double x = first.x - second.x;
			double z = first.z - second.z;
			return x * x + z * z;
		}
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 装备、真实滑翔/烟花物理、长矛碰撞伤害和弹尽着陆的端到端验证。 */
public final class ZombieAirAssaultGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void spearLoadoutReceivesElytraAndDifficultyWeightedRocketsAndPersists(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setNoAi(true);
		zombie.setOnGround(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

		boolean equipped = ZombieAirAssault.equipForSpawn(
			zombie,
			Difficulty.HARD,
			zombie.getRandom(),
			new MobsThinkNowConfig()
		);
		int rocketCount = zombie.getOffhandItem().getCount();
		Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		restored.restoreFrom(zombie);

		helper.assertTrue(equipped, "A spear zombie was not converted into an air-assault loadout.");
		helper.assertTrue(zombie.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), "The loadout did not equip an elytra.");
		helper.assertTrue(zombie.getOffhandItem().is(Items.FIREWORK_ROCKET), "The loadout did not equip rockets in the off hand.");
		helper.assertTrue(rocketCount >= 16 && rocketCount <= 64, "The generated rocket count escaped the 16-64 range.");
		helper.assertTrue(restored.getMainHandItem().is(Items.IRON_SPEAR), "The spear did not survive entity persistence.");
		helper.assertTrue(restored.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), "The elytra did not survive entity persistence.");
		helper.assertTrue(
			restored.getOffhandItem().is(Items.FIREWORK_ROCKET) && restored.getOffhandItem().getCount() == rocketCount,
			"The remaining rocket stack did not survive entity persistence."
		);
		helper.succeed();
	}

	@GameTest
	public void groundSpearGoalWaitsForAirAssaultAmmunitionToBeEmpty(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 1, 2);
		zombie.setNoAi(true);
		zombie.setOnGround(true);
		target.setNoAi(true);
		zombie.setTarget(target);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET, 16));
		((ZombieFlightAccess)zombie).mobsthinknow$stopFallFlying();
		zombie.setOnGround(true);
		zombie.stopUsingItem();

		SmartZombieSpearUseGoal goal = new SmartZombieSpearUseGoal(zombie, 1.0, 1.0, 10.0F, 2.0F);
		helper.assertTrue(zombie.getTarget() == target, "The ground-combat fixture did not retain an attackable target.");
		helper.assertTrue(!goal.canUse(), "The ground spear Goal started while the air-assault zombie still had rockets.");
		zombie.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		helper.assertTrue(goal.canUse(), "The original ground spear Goal did not return after ammunition was exhausted.");
		helper.succeed();
	}

	@GameTest
	public void targetlessWalkingGapDoesNotToggleFallFlyingPose(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.setTarget(null);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET, 16));
		((ZombieFlightAccess)zombie).mobsthinknow$stopFallFlying();
		// 模拟走下台阶、跨半砖或普通跳跃产生的单 tick 离地；旧逻辑仅凭 onGround=false 就接管滑翔。
		zombie.setOnGround(false);

		ZombieSpearAirAssaultGoal goal = new ZombieSpearAirAssaultGoal(zombie);
		helper.assertTrue(!goal.canUse(), "A targetless walking gap incorrectly started the air-assault Goal.");
		helper.assertTrue(!zombie.isFallFlying(), "The targetless zombie entered fall-flying pose without an attack target.");
		helper.assertTrue(
			status(zombie).mobsthinknow$getAirAssaultPhase() == ZombieSpearAirAssaultGoal.Phase.IDLE,
			"The targetless zombie left the idle air-assault phase."
		);
		helper.succeed();
	}

	@GameTest
	public void markedZombieRocketUsesSynchronizedHalfEfficiency(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 4, 2);
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.setOnGround(false);
		zombie.setYRot(-90.0F);
		zombie.setYHeadRot(-90.0F);
		zombie.setXRot(0.0F);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		((ZombieFlightAccess)zombie).mobsthinknow$startFallFlying();
		zombie.setDeltaMovement(Vec3.ZERO);

		ItemStack fired = new ItemStack(Items.FIREWORK_ROCKET);
		helper.assertTrue(
			!ZombieAirAssault.hasMarkedRocketEfficiency(fired)
				&& ZombieAirAssault.markedRocketEfficiency(fired) == 1.0,
			"An unmarked rocket did not retain vanilla efficiency."
		);
		ZombieAirAssault.markRocketEfficiency(fired, 0.5);
		helper.assertTrue(
			ZombieAirAssault.hasMarkedRocketEfficiency(fired)
				&& ZombieAirAssault.markedRocketEfficiency(fired) == 0.5,
			"The launched rocket did not carry its server-selected efficiency."
		);
		FireworkRocketEntity firework = new FireworkRocketEntity(helper.getLevel(), fired, zombie);
		helper.getLevel().addFreshEntity(firework);
		Vec3 expected = ZombieAirAssault.rocketBoostMovement(Vec3.ZERO, zombie.getLookAngle(), 0.5);
		firework.tick();

		Vec3 actual = zombie.getDeltaMovement();
		helper.assertTrue(
			actual.distanceToSqr(expected) < 1.0E-12,
			"The attached rocket did not apply its synchronized 0.5 efficiency: expected=" + expected + ", actual=" + actual
		);
		helper.assertTrue(actual.length() < 0.85, "The half-efficiency rocket retained the vanilla first-tick boost.");
		firework.discard();
		helper.succeed();
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 900,
		skyAccess = true,
		padding = 8
	)
	public void airAssaultUsesVanillaFlightAndKineticSpearDamage(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 27, 1, 16);
		// Keep the stationary target above the structure floor: no-gravity entities do not
		// resolve an accidentally intersecting spawn block and would otherwise suffocate.
		Villager target = helper.spawn(EntityType.VILLAGER, 32, 3, 16);
		AtomicBoolean sawRocketUse = new AtomicBoolean();
		AtomicBoolean sawFallFlying = new AtomicBoolean();
		AtomicBoolean sawRaisedSpear = new AtomicBoolean();
		AtomicBoolean sawDamage = new AtomicBoolean();
		double[] maximumAltitude = {zombie.getY()};
		double[] minimumDiveDistance = {Double.POSITIVE_INFINITY};
		double[] maximumDistanceAfterHit = {0.0};
		float[] healthAtFirstRaisedSpear = {Float.NaN};
		float[] healthAfterFirstHit = {Float.NaN};
		int[] firstOrbitAt = {-1};
		int[] firstDiveAt = {-1};
		int[] postAttackOrbitAt = {-1};
		int[] firstOrbitRocketBaseline = {-1};
		int[] firstOrbitObservedLaunches = {-1};
		int[] firstOrbitLastRocketAt = {-1};
		int[] secondOrbitRocketBaseline = {-1};
		int[] elapsedTicks = {0};

		zombie.setInvulnerable(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET, 16));
		((ZombieFlightAccess)zombie).mobsthinknow$stopFallFlying();
		zombie.setTarget(target);
		target.setNoAi(true);
		target.setNoGravity(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		target.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
		target.setHealth(200.0F);

		helper.onEachTick(() -> {
			elapsedTicks[0]++;
			zombie.clearFire();
			zombie.setTarget(target);
			target.invulnerableTime = 0;
			maximumAltitude[0] = Math.max(maximumAltitude[0], zombie.getY());
			if (zombie.getOffhandItem().getCount() < 16) {
				sawRocketUse.set(true);
			}
			if (zombie.isFallFlying()) {
				sawFallFlying.set(true);
			}
			ZombieSpearAirAssaultGoal.Phase phase = status(zombie).mobsthinknow$getAirAssaultPhase();
			if (phase == ZombieSpearAirAssaultGoal.Phase.ORBITING && firstOrbitAt[0] < 0) {
				firstOrbitAt[0] = elapsedTicks[0];
				firstOrbitRocketBaseline[0] = status(zombie).mobsthinknow$getRocketsLaunched();
				firstOrbitObservedLaunches[0] = firstOrbitRocketBaseline[0];
			}
			if (phase == ZombieSpearAirAssaultGoal.Phase.ORBITING && firstDiveAt[0] < 0) {
				int launches = status(zombie).mobsthinknow$getRocketsLaunched();
				if (launches > firstOrbitObservedLaunches[0]) {
					helper.assertTrue(
						launches == firstOrbitObservedLaunches[0] + 1,
						"The initial orbit launched multiple rockets during one tick."
					);
					if (firstOrbitLastRocketAt[0] >= 0) {
						helper.assertTrue(
							elapsedTicks[0] - firstOrbitLastRocketAt[0]
								>= ZombieSpearAirAssaultGoal.MINIMUM_ORBIT_ROCKET_GAP_TICKS,
							"Orbit rockets were launched before the minimum inertia interval elapsed."
						);
					}
					firstOrbitLastRocketAt[0] = elapsedTicks[0];
					firstOrbitObservedLaunches[0] = launches;
				}
			}
			if (phase == ZombieSpearAirAssaultGoal.Phase.DIVING) {
				if (firstDiveAt[0] < 0) {
					firstDiveAt[0] = elapsedTicks[0];
					helper.assertTrue(firstOrbitAt[0] >= 0, "The zombie dived before entering its initial orbit.");
					helper.assertTrue(
						firstDiveAt[0] - firstOrbitAt[0] >= ZombieSpearAirAssaultGoal.MINIMUM_ORBIT_TICKS,
						"The first dive started before the randomized minimum orbit duration elapsed."
					);
					int orbitRockets = status(zombie).mobsthinknow$getRocketsLaunched()
						- firstOrbitRocketBaseline[0];
					helper.assertTrue(
						orbitRockets >= 1 && orbitRockets <= 2,
						"The initial orbit did not use its one-to-two rocket budget: " + orbitRockets
					);
				}
				minimumDiveDistance[0] = Math.min(minimumDiveDistance[0], Math.sqrt(zombie.distanceToSqr(target)));
			}
			if (zombie.isFallFlying() && zombie.isUsingItem() && zombie.getUseItem().is(Items.IRON_SPEAR)) {
				sawRaisedSpear.set(true);
				if (Float.isNaN(healthAtFirstRaisedSpear[0])) {
					target.setHealth(200.0F);
					target.invulnerableTime = 0;
					healthAtFirstRaisedSpear[0] = target.getHealth();
				}
			}

			if (!Float.isNaN(healthAtFirstRaisedSpear[0]) && target.getHealth() < healthAtFirstRaisedSpear[0]) {
				if (!sawDamage.get()) {
					healthAfterFirstHit[0] = target.getHealth();
				}
				sawDamage.set(true);
			}
			if (sawDamage.get()) {
				maximumDistanceAfterHit[0] = Math.max(maximumDistanceAfterHit[0], Math.sqrt(zombie.distanceToSqr(target)));
				if (status(zombie).mobsthinknow$getDivesStarted() == 1) {
					helper.assertTrue(
						target.getHealth() >= healthAfterFirstHit[0],
						"One dive damaged the same target more than once before the next orbit cycle."
					);
				}
			}
			if (sawDamage.get()
				&& phase == ZombieSpearAirAssaultGoal.Phase.ORBITING
				&& postAttackOrbitAt[0] < 0) {
				helper.assertTrue(sawRocketUse.get(), "The target was damaged before any rocket-powered launch.");
				helper.assertTrue(sawFallFlying.get(), "The zombie damaged its target without entering vanilla fall-flying state.");
				helper.assertTrue(sawRaisedSpear.get(), "The kinetic hit happened without a raised spear during the dive.");
				helper.assertTrue(maximumAltitude[0] >= target.getY() + 3.0, "The zombie never climbed high enough to perform an air attack.");
				helper.assertTrue(
					maximumDistanceAfterHit[0] >= 6.0,
					"The zombie turned back into orbit before clearing the target after its attack."
				);
				helper.assertTrue(!zombie.isUsingItem(), "The zombie kept its spear raised after returning to orbit.");
				postAttackOrbitAt[0] = elapsedTicks[0];
				secondOrbitRocketBaseline[0] = status(zombie).mobsthinknow$getRocketsLaunched();
			}
			if (postAttackOrbitAt[0] >= 0
				&& phase == ZombieSpearAirAssaultGoal.Phase.DIVING
				&& status(zombie).mobsthinknow$getDivesStarted() >= 2) {
				helper.assertTrue(
					elapsedTicks[0] - postAttackOrbitAt[0] >= ZombieSpearAirAssaultGoal.MINIMUM_ORBIT_TICKS,
					"The second attack skipped the post-pass randomized orbit delay."
				);
				int orbitRockets = status(zombie).mobsthinknow$getRocketsLaunched()
					- secondOrbitRocketBaseline[0];
				helper.assertTrue(
					orbitRockets >= 1 && orbitRockets <= 2,
					"The post-pass orbit did not use its one-to-two rocket budget: " + orbitRockets
				);
				helper.succeed();
				return;
			}
			if (elapsedTicks[0] == 860) {
				helper.fail(
					flightDiagnostic("A full orbit-dive-pass-recovery cycle did not finish", zombie, target, maximumAltitude[0])
						+ ", minDiveDistance=" + minimumDiveDistance[0]
						+ ", sawRaisedSpear=" + sawRaisedSpear.get()
						+ ", sawDamage=" + sawDamage.get()
						+ ", firstOrbitAt=" + firstOrbitAt[0]
						+ ", firstDiveAt=" + firstDiveAt[0]
						+ ", postAttackOrbitAt=" + postAttackOrbitAt[0]
						+ ", maxDistanceAfterHit=" + maximumDistanceAfterHit[0]
						+ ", lineOfSight=" + zombie.hasLineOfSight(target)
				);
			}
		});
	}

	@GameTest(
		structure = "mobsthinknow-gametest:air_assault_arena",
		maxTicks = 420,
		skyAccess = true,
		padding = 8
	)
	public void lastRocketCompletesFlightThenZombieLandsBeforeGroundCombat(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 27, 1, 16);
		Villager target = helper.spawn(EntityType.VILLAGER, 32, 3, 16);
		AtomicBoolean sawFlight = new AtomicBoolean();
		AtomicBoolean consumedLastRocket = new AtomicBoolean();
		int[] elapsedTicks = {0};

		zombie.setInvulnerable(true);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET));
		((ZombieFlightAccess)zombie).mobsthinknow$stopFallFlying();
		zombie.setTarget(target);
		target.setNoAi(true);
		target.setNoGravity(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		target.setHealth(200.0F);

		helper.onEachTick(() -> {
			elapsedTicks[0]++;
			zombie.clearFire();
			zombie.setTarget(target);
			if (zombie.isFallFlying()) {
				sawFlight.set(true);
			}
			if (zombie.getOffhandItem().isEmpty()) {
				consumedLastRocket.set(true);
			}

			if (sawFlight.get()
				&& consumedLastRocket.get()
				&& zombie.onGround()
				&& !zombie.isFallFlying()) {
				SmartZombieSpearUseGoal groundGoal = new SmartZombieSpearUseGoal(zombie, 1.0, 1.0, 10.0F, 2.0F);
				helper.assertTrue(groundGoal.canUse(), "Ground spear combat remained blocked after landing with no rockets.");
				helper.succeed();
				return;
			}
			if (elapsedTicks[0] == 390) {
				helper.fail(flightDiagnostic("Last-rocket landing did not finish", zombie, target, zombie.getY()));
			}
		});
	}

	@GameTest(skyAccess = true, padding = 12)
	public void oneRocketCreatesAttachedBoostAndIsConsumedImmediately(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 4, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 8, 1, 2);
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.setOnGround(false);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		zombie.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET));
		zombie.setTarget(target);
		target.setNoAi(true);

		ZombieSpearAirAssaultGoal goal = new ZombieSpearAirAssaultGoal(zombie);
		helper.assertTrue(goal.canUse(), "The airborne one-rocket fixture did not qualify for air assault.");
		goal.start();
		helper.assertTrue(zombie.isFallFlying(), "Starting the airborne Goal did not set the vanilla fall-flying flag.");
		helper.assertTrue(zombie.getOffhandItem().isEmpty(), "Launching an attached firework did not consume the final rocket.");
		helper.assertTrue(
			!helper.getLevel().getEntitiesOfClass(
				FireworkRocketEntity.class,
				zombie.getBoundingBox().inflate(2.0),
				entity -> entity.isAlive()
			).isEmpty(),
			"No attached FireworkRocketEntity was added beside the gliding zombie."
		);
		goal.stop();
		helper.succeed();
	}

	private static String flightDiagnostic(
		final String reason,
		final Zombie zombie,
		final Villager target,
		final double maximumAltitude
	) {
		return reason
			+ ": phase-visible state={position=" + zombie.position()
			+ ", phase=" + status(zombie).mobsthinknow$getAirAssaultPhase()
			+ ", launched=" + status(zombie).mobsthinknow$getRocketsLaunched()
			+ ", dives=" + status(zombie).mobsthinknow$getDivesStarted()
			+ ", movement=" + zombie.getDeltaMovement()
			+ ", target=" + target.position()
			+ ", rockets=" + zombie.getOffhandItem().getCount()
			+ ", fallFlying=" + zombie.isFallFlying()
			+ ", usingSpear=" + zombie.isUsingItem()
			+ ", onGround=" + zombie.onGround()
			+ ", horizontalCollision=" + zombie.horizontalCollision
			+ ", maxY=" + maximumAltitude
			+ ", targetHealth=" + target.getHealth() + "}";
	}

	private static ZombieAirAssaultStatusAccess status(final Zombie zombie) {
		return (ZombieAirAssaultStatusAccess)zombie;
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

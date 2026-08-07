package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.MountedSkeletonTargetGoal;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonBowAttackGoal;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadAssaultPlan;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadRole;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** 从真实实体、GoalSelector、骑乘关系和引信数据验证蜘蛛战术。 */
public final class SpiderTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spiderMixinInstallsEightGoalsAndAppliesPersistentIdentity(final GameTestHelper helper) {
		long before = SmartSpiderMetrics.snapshot().installedGoals();
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);

		helper.assertTrue(
			SmartSpiderMetrics.snapshot().installedGoals() == before + 8,
			"Spider construction did not install blast/casualty evacuation, preparation, web ambush, combat, and carrier goals."
		);
		int intelligence = SpiderIntelligence.get(spider);
		helper.assertTrue(intelligence >= 1 && intelligence <= 10, "Spider intelligence escaped the 1-10 range.");
		helper.assertTrue(
			spider.getCustomName() != null && spider.getCustomName().getString().endsWith("[" + intelligence + "]"),
			"Natural spider name did not expose its stable intelligence."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 90, padding = 4)
	public void temporaryWebTrapPlacesAndRestoresItsPreviousBlock(final GameTestHelper helper) {
		BlockPos relative = new BlockPos(5, 2, 2);
		helper.setBlock(relative.below(), Blocks.STONE);
		helper.setBlock(relative, Blocks.AIR);
		BlockPos absolute = helper.absolutePos(relative);
		long now = helper.getLevel().getGameTime();
		UUID owner = UUID.randomUUID();

		helper.assertTrue(
			SpiderWebTrapRegistry.tryPlace(helper.getLevel(), absolute, owner, now, 40),
			"A supported air block did not accept a managed spider web trap."
		);
		helper.assertTrue(helper.getLevel().getBlockState(absolute).is(Blocks.COBWEB), "Managed trap did not place cobweb.");
		helper.assertTrue(SpiderWebTrapRegistry.isOwnedTrap(helper.getLevel(), absolute), "Placed web was not registered.");
		helper.assertTrue(owner.equals(SpiderWebTrapRegistry.ownerAt(helper.getLevel(), absolute)), "Trap owner was not queryable.");
		int[] elapsed = {0};
		helper.onEachTick(() -> {
			elapsed[0]++;
			if (elapsed[0] < 45) {
				return;
			}
			helper.assertTrue(helper.getLevel().getBlockState(absolute).isAir(), "Expired trap did not restore air.");
			helper.assertTrue(
				!SpiderWebTrapRegistry.isOwnedTrap(helper.getLevel(), absolute),
				"Expired trap left a stale registry entry."
			);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 90, padding = 4)
	public void temporaryWebCleanupPreservesPlayerReplacement(final GameTestHelper helper) {
		BlockPos relative = new BlockPos(6, 2, 2);
		helper.setBlock(relative.below(), Blocks.STONE);
		helper.setBlock(relative, Blocks.AIR);
		BlockPos absolute = helper.absolutePos(relative);
		long now = helper.getLevel().getGameTime();

		helper.assertTrue(
			SpiderWebTrapRegistry.tryPlace(helper.getLevel(), absolute, UUID.randomUUID(), now, 40),
			"Replacement-preservation fixture could not place its managed web."
		);
		helper.setBlock(relative, Blocks.OAK_PLANKS);
		int[] elapsed = {0};
		helper.onEachTick(() -> {
			elapsed[0]++;
			if (elapsed[0] < 45) {
				return;
			}
			helper.assertTrue(
				helper.getLevel().getBlockState(absolute).is(Blocks.OAK_PLANKS),
				"Trap cleanup overwrote a block that had replaced the managed cobweb."
			);
			helper.assertTrue(
				!SpiderWebTrapRegistry.isOwnedTrap(helper.getLevel(), absolute),
				"Replaced trap left a stale registry entry."
			);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void skilledSpiderPouncesTowardMovingTargetPrediction(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 7, 2, 2);
		spider.setNoAi(true);
		target.setNoAi(true);
		spider.setOnGround(true);
		target.setDeltaMovement(0.0, 0.0, 0.20);
		SpiderIntelligence.set(spider, 8);
		spider.setTarget(target);

		SmartSpiderPounceGoal goal = new SmartSpiderPounceGoal(spider);
		helper.assertTrue(goal.canUse(), "Visible IQ-8 spider did not prepare a predictive pounce.");
		goal.start();
		helper.assertTrue(spider.getDeltaMovement().x > 0.35, "Pounce did not travel toward the target.");
		helper.assertTrue(spider.getDeltaMovement().z > 0.0, "Pounce ignored the target's lateral movement.");
		helper.assertTrue(spider.getDeltaMovement().y >= 0.40, "Pounce lacked its vertical launch component.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void skilledSpiderPerformsReadableWindupAndTrapsPredictedFootfall(final GameTestHelper helper) {
		for (int x = 1; x <= 12; x++) {
			for (int z = 1; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 7, 2, 2);
		spider.setNoAi(true);
		target.setNoAi(true);
		spider.setOnGround(true);
		target.setDeltaMovement(0.15, 0.0, 0.0);
		SpiderIntelligence.set(spider, 10);
		spider.setTarget(target);
		spider.getSensing().tick();
		long windupsBefore = SmartSpiderMetrics.snapshot().webTrapWindups();
		long websBefore = SmartSpiderMetrics.snapshot().webTrapsPlaced();

		SpiderWebTrapGoal goal = new SpiderWebTrapGoal(spider);
		helper.assertTrue(goal.canUse(), "IQ-10 spider did not select a supported predicted web position.");
		goal.start();
		helper.assertTrue(
			SmartSpiderMetrics.snapshot().webTrapWindups() == windupsBefore + 1,
			"Web goal did not expose its windup telemetry."
		);
		for (int tick = 0; tick < 8; tick++) {
			goal.tick();
		}
		helper.assertTrue(
			SmartSpiderMetrics.snapshot().webTrapsPlaced() == websBefore + 1,
			"Web goal reached its release frame without placing a trap."
		);
		boolean foundPredictedWeb = false;
		for (int x = 7; x <= 11; x++) {
			for (int z = 1; z <= 3; z++) {
				if (helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.COBWEB)) {
					foundPredictedWeb = true;
				}
			}
		}
		helper.assertTrue(foundPredictedWeb, "Spider did not place the web ahead of the moving target.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void nearbyCreeperMountsWithoutTakingControlOfSpider(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 4, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 9, 2, 2);
		spider.setNoAi(true);
		creeper.setNoAi(true);
		target.setNoAi(true);
		SpiderIntelligence.set(spider, 10);
		CreeperIntelligence.set(creeper, 10);
		spider.setTarget(target);
		creeper.setTarget(target);
		creeper.setSwellDir(1); // 模拟实体 tick 顺序使苦力怕提前一拍进入早期引信。

		SpiderCreeperCarrierGoal goal = new SpiderCreeperCarrierGoal(spider);
		SpiderTransportRouteEvaluator.Assessment route = SpiderTransportRouteEvaluator.assess(
			spider,
			SpiderCombatMath.carrierDestination(target.position(), target.getDeltaMovement(), 10),
			creeper.getBbHeight()
		);
		helper.assertTrue(
			goal.canUse(),
			"Nearby spider and creeper did not reserve a transport pair: route=" + route.status()
				+ ",nodes=" + route.sampledNodes()
				+ ",path=" + (route.path() == null ? "null" : route.path().canReach())
				+ ",lease=" + TacticalActivityLease.snapshot(spider, helper.getLevel().getGameTime())
				+ ",reserved=" + ((CreeperTransportAccess)creeper)
					.mobsthinknow$isReservedForAnyCarrier(helper.getLevel().getGameTime())
		);
		goal.start();
		double configuredCarrierMaximum = ConfigManager.get().spiderCreeperCarrierSpeed;
		double actualCarrierMaximum = goal.carrierSpeedMaximum();
		helper.assertTrue(
			actualCarrierMaximum <= configuredCarrierMaximum
				&& actualCarrierMaximum >= Math.max(1.10, configuredCarrierMaximum * 0.88),
			"Transport pair did not retain its randomized 88%-100% speed ceiling."
		);
		helper.assertTrue(goal.isBoardingLeapActive(), "Reserved creeper skipped the boarding leap phase.");
		helper.assertTrue(creeper.getVehicle() == null, "Creeper snapped onto the spider before showing its leap.");
		helper.assertTrue(creeper.getDeltaMovement().y >= 0.38, "Boarding leap lacked a visible vertical arc.");
		helper.assertTrue(creeper.getSwellDir() == -1, "Early fuse was not reset so rendezvous could happen first.");

		int[] elapsed = {0};
		helper.onEachTick(() -> {
			elapsed[0]++;
			goal.tick();
			if (creeper.getVehicle() == spider) {
				helper.assertTrue(elapsed[0] >= 3, "Creeper mounted before the minimum three-tick leap presentation.");
				helper.assertTrue(goal.isCarryingCreeper(), "Carrier state did not enter the mounted phase.");
				helper.assertTrue(
					spider.getControllingPassenger() == null,
					"Creeper payload was treated as a driver and would pause spider movement goals."
				);
				goal.stop();
				helper.succeed();
			}
			if (elapsed[0] >= 12) {
				helper.assertTrue(false, "Creeper performed the leap but did not complete boarding.");
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void mountedCarrierStartsFuseOnlyInsideDeliveryEnvelope(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 2, 2, 3);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 2, 2);
		spider.setNoAi(true);
		creeper.setNoAi(true);
		target.setNoAi(true);
		SpiderIntelligence.set(spider, 10);
		CreeperIntelligence.set(creeper, 10);
		spider.setTarget(target);
		creeper.setTarget(target);
		creeper.setYRot(180.0F);
		creeper.setYHeadRot(180.0F);
		helper.assertTrue(creeper.startRiding(spider, true, true), "GameTest setup could not mount creeper payload.");

		SpiderCreeperCarrierGoal goal = new SpiderCreeperCarrierGoal(spider);
		helper.assertTrue(goal.canUse(), "Mounted pair did not activate delivery mode.");
		goal.start();
		goal.tick();
		helper.assertTrue(goal.isFuseCommitted(), "Carrier reached delivery range without committing the fuse.");
		helper.assertTrue(creeper.getSwellDir() == 1, "Mounted creeper did not enter positive swell direction.");
		helper.assertTrue(goal.phase() == SpiderCreeperCarrierGoal.Phase.FINAL_CHARGE, "Carrier skipped final charge phase.");
		helper.assertTrue(
			SpiderCombatMath.isTargetWatching(
				creeper.getLookAngle(),
				target.position().subtract(creeper.position())
			),
			"Mounted creeper did not turn its head toward the shared target."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void rareSpiderSpawnSpeedUsesAVisibleInfiniteLevelTwoEffect(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		SpiderSpawnEffects.maybeApplySpeed(spider, 0.0);
		var speed = spider.getEffect(MobEffects.SPEED);
		helper.assertTrue(speed != null, "Deterministic rare roll did not apply a speed effect.");
		helper.assertTrue(speed.getAmplifier() == 1, "Rare top-tier roll did not produce Speed II.");
		helper.assertTrue(speed.isInfiniteDuration(), "Spawn speed trait did not persist for the spider's lifetime.");
		helper.assertTrue(speed.isVisible(), "Spawn speed trait hid its potion particles.");
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 80, padding = 4)
	public void zombieSkeletonCreeperAndSpiderFormOneSquadWithCreeperLeader(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 3, 2, 3);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 4, 2, 2);
		Spider spider = helper.spawn(EntityType.SPIDER, 3, 2, 1);
		Villager target = helper.spawn(EntityType.VILLAGER, 11, 2, 2);
		zombie.setNoAi(true);
		skeleton.setNoAi(true);
		creeper.setNoAi(true);
		spider.setNoAi(true);
		target.setNoAi(true);
		ZombieIntelligence.set(zombie, 8);
		SkeletonIntelligence.set(skeleton, 7);
		CreeperIntelligence.set(creeper, 10);
		SpiderIntelligence.set(spider, 9);
		zombie.setTarget(target);
		skeleton.setTarget(target);
		creeper.setTarget(target);
		spider.setTarget(target);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(zombie, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			coordinator.heartbeat(creeper, target, true, target.position(), now);
			coordinator.heartbeat(spider, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(creeper);
			if (view == null) {
				return;
			}
			helper.assertTrue(view.memberCount() == 4, "The four-species squad omitted one of its hostile members.");
			helper.assertTrue(view.leaderEntityId() == creeper.getId(), "The unique IQ-10 creeper was not elected leader.");
			helper.assertTrue(
				view.assaultPlan() == SquadAssaultPlan.COMBINED_ARMS,
				"A high-IQ four-species squad did not choose the combined-arms assault plan."
			);
			SquadDirective creeperOrder = coordinator.directiveFor(creeper);
			SquadDirective spiderOrder = coordinator.directiveFor(spider);
			SquadDirective skeletonOrder = coordinator.directiveFor(skeleton);
			helper.assertTrue(creeperOrder != null && creeperOrder.role() == SquadRole.LEADER, "Creeper leader lost its leader role.");
			helper.assertTrue(spiderOrder != null && spiderOrder.role() == SquadRole.CARRIER, "Assigned spider was not marked as carrier.");
			helper.assertTrue(skeletonOrder != null && skeletonOrder.role() == SquadRole.RANGED, "Skeleton lost its ranged role.");
			helper.assertTrue(coordinator.assignedTransportPartnerFor(spider) == creeper, "Carrier did not prioritize the creeper payload.");
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 60, padding = 4)
	public void squadSpiderPhysicallyBoardsAndAcceleratesSkeleton(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 4, 2, 2);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 3, 2, 3);
		Villager target = helper.spawn(EntityType.VILLAGER, 7, 2, 2);
		spider.setNoAi(true);
		skeleton.setNoAi(true);
		zombie.setNoAi(true);
		target.setNoAi(true);
		SpiderIntelligence.set(spider, 10);
		SkeletonIntelligence.set(skeleton, 8);
		ZombieIntelligence.set(zombie, 6);
		spider.setTarget(target);
		skeleton.setTarget(target);
		zombie.setTarget(target);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		SpiderSquadCarrierGoal carrierGoal = new SpiderSquadCarrierGoal(spider);
		boolean[] started = {false};
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(spider, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			coordinator.heartbeat(zombie, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			if (!started[0] && carrierGoal.canUse()) {
				started[0] = true;
				carrierGoal.start();
			}
			if (started[0]) {
				carrierGoal.tick();
			}
			if (skeleton.getVehicle() == spider) {
				double configuredMaximum = ConfigManager.get().spiderCreeperCarrierSpeed;
				double actualMaximum = carrierGoal.carrierSpeedMaximum();
				helper.assertTrue(carrierGoal.isCarryingSquadmate(), "Carrier state did not retain its skeleton passenger.");
				helper.assertTrue(spider.getControllingPassenger() == null, "Skeleton became a driver and disabled spider navigation.");
				helper.assertTrue(actualMaximum >= Math.max(1.10, configuredMaximum * 0.88)
					&& actualMaximum <= configuredMaximum, "Squad carrier escaped its randomized acceleration cap.");
				helper.succeed();
			}
			if (elapsed[0] >= 45) {
				Mob assigned = coordinator.assignedTransportPartnerFor(spider);
				SpiderTransportRouteEvaluator.Assessment route = SpiderTransportRouteEvaluator.assess(
					spider,
					SpiderCombatMath.carrierDestination(target.position(), target.getDeltaMovement(), 10),
					skeleton.getBbHeight()
				);
				helper.assertTrue(
					false,
					"Squad spider did not complete the visible boarding sequence: started=" + started[0]
						+ ",assigned=" + (assigned == null ? "null" : assigned.getId())
						+ ",route=" + route.status()
						+ ",nodes=" + route.sampledNodes()
						+ ",lease=" + TacticalActivityLease.snapshot(spider, now)
				);
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 100, padding = 4)
	public void managedSpiderPassengerMirrorsTargetAndKeepsShooting(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 3, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 3, 2, 3);
		Villager target = helper.spawn(EntityType.VILLAGER, 13, 2, 2);
		spider.setNoAi(true);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		SkeletonIntelligence.set(skeleton, 10);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		spider.setTarget(target);
		skeleton.setTarget(null);
		((SpiderSquadTransportAccess)spider).mobsthinknow$markSquadPassenger(skeleton.getId());
		helper.assertTrue(skeleton.startRiding(spider, true, true), "Managed skeleton could not mount its squad spider.");

		MountedSkeletonTargetGoal targetGoal = new MountedSkeletonTargetGoal(skeleton);
		helper.assertTrue(targetGoal.canUse(), "Spider passenger did not discover the mount's live target.");
		targetGoal.start();
		helper.assertTrue(skeleton.getTarget() == target, "Spider passenger did not mirror the mount target.");
		SmartSkeletonBowAttackGoal bowGoal = new SmartSkeletonBowAttackGoal(skeleton, 1.0, 40, 15.0F);
		helper.assertTrue(bowGoal.canUse(), "Mounted bow goal did not start with the mirrored target.");
		bowGoal.start();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			targetGoal.tick();
			skeleton.getSensing().tick();
			bowGoal.tick();
			// Metrics are global to the GameTest JVM and other parallel tests can increment them first.
			// An arrow owned by this exact passenger is the entity-local proof that its bow goal fired.
			boolean passengerArrowSpawned = helper.getEntities(EntityType.ARROW).stream()
				.anyMatch(arrow -> arrow.getOwner() == skeleton);
			if (passengerArrowSpawned) {
				helper.assertTrue(skeleton.getTarget() == target, "Passenger lost the spider's target while firing.");
				bowGoal.stop();
				targetGoal.stop();
				helper.succeed();
			}
			if (elapsed[0] >= 90) {
				helper.fail(
					"Managed spider passenger never fired: target=" + skeleton.getTarget()
						+ ",using=" + skeleton.isUsingItem()
						+ ",los=" + skeleton.getSensing().hasLineOfSight(target)
				);
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void unrelatedVanillaSpiderJockeyKeepsItsNormalDriverSemantics(final GameTestHelper helper) {
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 3);
		skeleton.setNoAi(true);
		helper.assertTrue(skeleton.startRiding(spider, true, true), "Vanilla spider-jockey fixture could not mount.");
		helper.assertTrue(
			spider.getControllingPassenger() == skeleton,
			"Squad carrier override leaked into an unrelated vanilla spider jockey."
		);
		skeleton.stopRiding();
		skeleton.discard();
		spider.discard();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 160, padding = 4)
	public void productionGoalsRendezvousCarryAndPrimeAcrossRealTicks(final GameTestHelper helper) {
		for (int x = 1; x <= 15; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 7, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 14, 2, 2);
		target.setNoAi(true);
		spider.setInvulnerable(true);
		SpiderIntelligence.set(spider, 10);
		CreeperIntelligence.set(creeper, 10);
		spider.setTarget(target);
		creeper.setTarget(target);
		boolean[] sawMountedPair = {false};
		int[] elapsedTicks = {0};

		helper.onEachTick(() -> {
			elapsedTicks[0]++;
			spider.setTarget(target);
			creeper.setTarget(target);
			if (creeper.getVehicle() == spider) {
				sawMountedPair[0] = true;
				helper.assertTrue(
					spider.getControllingPassenger() == null,
					"Production carrier lost MOVE control after rendezvous."
				);
			}
			if (creeper.getSwellDir() > 0) {
				helper.assertTrue(sawMountedPair[0], "Creeper primed before the pair completed its rendezvous.");
				helper.assertTrue(creeper.getVehicle() == spider, "Payload dismounted before starting its delivery fuse.");
				helper.succeed();
			}
			if (elapsedTicks[0] >= 150) {
				helper.assertTrue(
					false,
					"Production carrier stalled: spider=" + spider.position()
						+ ", creeper=" + creeper.position()
						+ ", target=" + target.position()
						+ ", mounted=" + (creeper.getVehicle() == spider)
						+ ", sawMounted=" + sawMountedPair[0]
						+ ", swellDir=" + creeper.getSwellDir()
						+ ", spiderNavDone=" + spider.getNavigation().isDone()
						+ ", creeperNavDone=" + creeper.getNavigation().isDone()
						+ ", distanceToPayload=" + spider.distanceTo(creeper)
						+ ", distanceToTarget=" + spider.distanceTo(target)
				);
			}
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

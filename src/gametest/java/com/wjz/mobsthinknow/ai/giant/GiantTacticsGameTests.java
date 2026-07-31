package com.wjz.mobsthinknow.ai.giant;

import java.lang.reflect.Method;
import java.util.List;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadRole;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 从真实 Mixin 实体验证巨人属性、三个挂点、双手抛投与延迟出生替换。 */
public final class GiantTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void giantMixinInstallsGoalsHeavyProfileAndPersistentIdentity(final GameTestHelper helper) {
		long before = SmartGiantMetrics.snapshot().installedGoals();
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);

		helper.assertTrue(
			SmartGiantMetrics.snapshot().installedGoals() == before + 1,
			"Giant construction did not install exactly one smart goal set."
		);
		helper.assertTrue(giant.getMaxHealth() >= 100.0F, "Giant did not receive its heavy health profile.");
		helper.assertTrue(
			giant.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < 0.23,
			"Giant movement was not slower than an ordinary zombie."
		);
		int intelligence = GiantIntelligence.get(giant);
		helper.assertTrue(intelligence >= 1 && intelligence <= 10, "Giant intelligence escaped the 1-10 range.");
		giant.setHealth(13.0F);
		GiantZombieProfile.applyAttributes(giant, com.wjz.mobsthinknow.config.ConfigManager.get());
		helper.assertTrue(giant.getHealth() == 13.0F, "Live config reapplication unexpectedly healed the Giant.");
		helper.assertTrue(
			giant.getCustomName() != null && giant.getCustomName().getString().endsWith("[" + intelligence + "]"),
			"Giant name did not expose its stable intelligence."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void headRiderAndBothHandPayloadsUseIndependentAttachments(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Skeleton rider = helper.spawn(EntityType.SKELETON, 5, 2, 4);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 5, 2, 5);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 6);
		giant.setNoAi(true);
		rider.setNoAi(true);
		creeper.setNoAi(true);
		zombie.setNoAi(true);
		giant.setYBodyRot(0.0F);

		helper.assertTrue(rider.startRiding(giant, true, true), "Skeleton could not reserve the Giant head seat.");
		GiantTacticsState.assignPayload(giant, GiantHand.RIGHT, creeper);
		GiantTacticsState.transitionHand(giant, GiantHand.RIGHT, GiantHandPhase.HOLDING);
		GiantTacticsState.assignPayload(giant, GiantHand.LEFT, zombie);
		GiantTacticsState.transitionHand(giant, GiantHand.LEFT, GiantHandPhase.HOLDING);
		helper.assertTrue(creeper.startRiding(giant, true, true), "Creeper could not reserve the Giant right hand.");
		helper.assertTrue(zombie.startRiding(giant, true, true), "Zombie could not reserve the Giant left hand.");
		giant.positionRider(rider);
		giant.positionRider(creeper);
		giant.positionRider(zombie);

		helper.assertTrue(rider.getY() - giant.getY() > 10.5, "Ranged rider was not positioned on the Giant head.");
		Vec3 right = creeper.position().subtract(giant.position());
		Vec3 left = zombie.position().subtract(giant.position());
		helper.assertTrue(right.x < -2.4 && left.x > 2.4, "Dual payloads did not occupy opposite hands.");
		helper.assertTrue(right.z > 1.8 && left.z > 1.8, "Hand payloads were not visibly held forward.");
		helper.assertTrue(
			Math.abs(right.y - left.y) < 0.01 && right.y > 5.70,
			"Different vehicle attachment offsets left a hand payload hanging below the palm."
		);
		helper.assertTrue(giant.getControllingPassenger() == null, "A tactical passenger incorrectly drove the Giant.");
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void removingRightPayloadNeverMovesLeftPayloadAcrossHands(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Creeper rightPayload = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		Zombie leftPayload = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
		giant.setNoAi(true);
		rightPayload.setNoAi(true);
		leftPayload.setNoAi(true);
		giant.setYBodyRot(0.0F);
		GiantTacticsState.assignPayload(giant, GiantHand.RIGHT, rightPayload);
		GiantTacticsState.transitionHand(giant, GiantHand.RIGHT, GiantHandPhase.HOLDING);
		GiantTacticsState.assignPayload(giant, GiantHand.LEFT, leftPayload);
		GiantTacticsState.transitionHand(giant, GiantHand.LEFT, GiantHandPhase.HOLDING);
		helper.assertTrue(rightPayload.startRiding(giant, true, true), "Right payload could not mount.");
		helper.assertTrue(leftPayload.startRiding(giant, true, true), "Left payload could not mount.");
		giant.positionRider(leftPayload);
		Vec3 before = leftPayload.position();

		rightPayload.stopRiding();
		GiantTacticsState.resetHand(giant, GiantHand.RIGHT);
		GiantTacticsState.reconcile(giant);
		giant.positionRider(leftPayload);

		helper.assertTrue(
			GiantTacticsState.payloadForHand(giant, GiantHand.LEFT) == leftPayload,
			"Removing the right payload erased the left UUID slot."
		);
		helper.assertTrue(
			GiantTacticsState.payloadForHand(giant, GiantHand.RIGHT) == null,
			"The empty right hand silently adopted the remaining left payload."
		);
		helper.assertTrue(
			leftPayload.getX() - giant.getX() > 2.4 && leftPayload.position().distanceTo(before) < 0.01,
			"The left payload jumped to the opposite hand after passenger list compression."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void distantLeftCandidateNeverStallsLoadedRightHand(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Creeper loadedRight = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		Zombie distantLeft = helper.spawn(EntityType.ZOMBIE, 18, 2, 4);
		giant.setNoAi(true);
		loadedRight.setNoAi(true);
		distantLeft.setNoAi(true);
		GiantTacticsState.assignPayload(giant, GiantHand.RIGHT, loadedRight);
		GiantTacticsState.transitionHand(giant, GiantHand.RIGHT, GiantHandPhase.HOLDING);
		GiantTacticsState.assignPayload(giant, GiantHand.LEFT, distantLeft);
		GiantTacticsState.transitionHand(giant, GiantHand.LEFT, GiantHandPhase.RENDEZVOUS);
		helper.assertTrue(loadedRight.startRiding(giant, true, true), "Loaded right payload could not mount.");

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 playerFeet = helper.absoluteVec(new Vec3(22.5, 2.0, 4.5));
		player.snapTo(playerFeet.x, playerFeet.y, playerFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Throw target fixture was not added.");
		giant.setTarget(player);
		GiantPayloadThrowGoal goal = new GiantPayloadThrowGoal(giant);
		helper.assertTrue(goal.canUse(), "Independent-hand goal rejected a loaded right hand.");
		goal.start();
		for (int tick = 0; tick < 30 && loadedRight.isPassenger(); tick++) {
			goal.tick();
		}

		helper.assertTrue(!loadedRight.isPassenger(), "The ready right hand waited for the distant left candidate.");
		helper.assertTrue(loadedRight.isIgnited(), "The independently released Creeper was not armed.");
		helper.assertTrue(
			!distantLeft.isPassenger()
				&& GiantTacticsState.payloadCandidate(giant, GiantHand.LEFT) == distantLeft
				&& GiantTacticsState.handPhase(giant, GiantHand.LEFT) == GiantHandPhase.RENDEZVOUS,
			"The delayed left candidate did not keep its own independent rendezvous state."
		);
		player.discard();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 50, padding = 4)
	public void pickupAnimationCatchesPayloadLowThenRaisesItToPalm(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Zombie candidate = helper.spawn(EntityType.ZOMBIE, 6, 2, 4);
		giant.setNoAi(true);
		candidate.setNoAi(true);
		GiantTacticsState.assignPayload(giant, GiantHand.RIGHT, candidate);
		GiantTacticsState.transitionHand(giant, GiantHand.RIGHT, GiantHandPhase.RENDEZVOUS);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 playerFeet = helper.absoluteVec(new Vec3(22.5, 2.0, 4.5));
		player.snapTo(playerFeet.x, playerFeet.y, playerFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Pickup target fixture was not added.");
		giant.setTarget(player);
		GiantPayloadThrowGoal goal = new GiantPayloadThrowGoal(giant);
		helper.assertTrue(goal.canUse(), "A reserved nearby payload did not start the hand pipeline.");
		goal.start();
		boolean[] sawLowCatch = {false};

		helper.onEachTick(() -> {
			if (goal.canContinueToUse()) {
				goal.tick();
			}
			GiantHandPhase phase = GiantTacticsState.handPhase(giant, GiantHand.RIGHT);
			if (phase == GiantHandPhase.PICKUP && candidate.getVehicle() == giant) {
				sawLowCatch[0] |= candidate.getY() - giant.getY() < 4.0;
			}
			if (phase == GiantHandPhase.HOLDING && candidate.getVehicle() == giant) {
				giant.positionRider(candidate);
				helper.assertTrue(sawLowCatch[0], "Payload mounted only after the pickup animation had ended.");
				helper.assertTrue(
					candidate.getY() - giant.getY() > 5.5,
					"The attached payload was not raised from the low catch point to the normal palm."
				);
				player.discard();
				helper.succeed();
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void preloadedDualHandsThrowCreeperAndZombieWithStagger(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
		giant.setNoAi(true);
		creeper.setNoAi(true);
		zombie.setNoAi(true);
		GiantTacticsState.assignPayload(giant, GiantHand.RIGHT, creeper);
		GiantTacticsState.transitionHand(giant, GiantHand.RIGHT, GiantHandPhase.HOLDING);
		GiantTacticsState.assignPayload(giant, GiantHand.LEFT, zombie);
		GiantTacticsState.transitionHand(giant, GiantHand.LEFT, GiantHandPhase.HOLDING);
		helper.assertTrue(creeper.startRiding(giant, true, true), "Creeper payload could not mount.");
		helper.assertTrue(zombie.startRiding(giant, true, true), "Zombie payload could not mount.");
		giant.positionRider(creeper);
		giant.positionRider(zombie);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 playerFeet = helper.absoluteVec(new Vec3(22.5, 2.0, 4.5));
		player.snapTo(playerFeet.x, playerFeet.y, playerFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Throw target fixture was not added.");
		giant.setTarget(player);

		GiantPayloadThrowGoal goal = new GiantPayloadThrowGoal(giant);
		helper.assertTrue(goal.canUse(), "A preloaded Giant did not enter its throwing state machine.");
		goal.start();
		int firstRelease = -1;
		int secondRelease = -1;
		double maximumReleaseJump = 0.0;
		for (int tick = 0; tick < 40; tick++) {
			boolean creeperMountedBefore = creeper.isPassenger();
			boolean zombieMountedBefore = zombie.isPassenger();
			Vec3 creeperBefore = creeper.position();
			Vec3 zombieBefore = zombie.position();
			goal.tick();
			if (creeperMountedBefore && !creeper.isPassenger()) {
				maximumReleaseJump = Math.max(maximumReleaseJump, creeper.position().distanceTo(creeperBefore));
			}
			if (zombieMountedBefore && !zombie.isPassenger()) {
				maximumReleaseJump = Math.max(maximumReleaseJump, zombie.position().distanceTo(zombieBefore));
			}
			int mounted = goal.heldPayloadCount();
			if (mounted == 1 && firstRelease < 0) firstRelease = tick;
			if (mounted == 0) {
				secondRelease = tick;
				break;
			}
		}
		helper.assertTrue(firstRelease >= 11, "The Giant threw before its visible aim windup.");
		helper.assertTrue(secondRelease - firstRelease >= 10, "Both hands released in the same unreadable instant.");
		helper.assertTrue(creeper.isIgnited() && !creeper.isPassenger(), "Thrown creeper was not released and ignited.");
		helper.assertTrue(!zombie.isPassenger(), "Thrown zombie remained attached to the Giant.");
		helper.assertTrue(
			maximumReleaseJump < 0.01,
			"A payload jumped between its animated palm position and projectile origin."
		);
		Vec3 toward = player.position().subtract(giant.position()).multiply(1.0, 0.0, 1.0).normalize();
		helper.assertTrue(creeper.getDeltaMovement().dot(toward) > 0.45, "Creeper throw did not travel toward the player.");
		helper.assertTrue(zombie.getDeltaMovement().dot(toward) > 0.45, "Zombie throw did not travel toward the player.");
		player.discard();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 80, padding = 4)
	public void mixedSquadReservesHeadRiderAndBothGiantPayloads(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 3, 2, 3);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 4, 2, 3);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 3, 2, 4);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 11, 2, 3);
		giant.setNoAi(true);
		skeleton.setNoAi(true);
		creeper.setNoAi(true);
		zombie.setNoAi(true);
		target.setNoAi(true);
		GiantIntelligence.set(giant, 8);
		SkeletonIntelligence.set(skeleton, 10);
		CreeperIntelligence.set(creeper, 7);
		ZombieIntelligence.set(zombie, 6);
		giant.setTarget(target);
		skeleton.setTarget(target);
		creeper.setTarget(target);
		zombie.setTarget(target);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(giant, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			coordinator.heartbeat(creeper, target, true, target.position(), now);
			coordinator.heartbeat(zombie, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(giant);
			if (view == null) {
				return;
			}
			helper.assertTrue(view.memberCount() == 4, "The Giant mixed squad omitted one assigned member.");
			helper.assertTrue(view.leaderEntityId() == skeleton.getId(), "The IQ-10 ranged member was not elected leader.");
			helper.assertTrue(
				coordinator.assignedGiantHeadRiderFor(giant) == skeleton,
				"The squad did not reserve its ranged member for the Giant head seat."
			);
			List<net.minecraft.world.entity.Mob> payloads = coordinator.assignedGiantPayloadsFor(giant);
			helper.assertTrue(payloads.size() == 2, "The Giant did not receive two independent hand reservations.");
			helper.assertTrue(payloads.get(0) == creeper, "The higher-priority Creeper did not reserve the first hand.");
			helper.assertTrue(payloads.get(1) == zombie, "The Zombie did not reserve the second hand.");
			SquadDirective giantOrder = coordinator.directiveFor(giant);
			helper.assertTrue(
				giantOrder != null && giantOrder.role() == SquadRole.CARRIER,
				"A loaded non-leader Giant was not assigned the carrier role."
			);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 180, padding = 4)
	public void skeletonIsCaughtInPalmLiftedToShoulderThenPlacedOnHead(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 6, 2, 4);
		Zombie squadMate = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
		// 目标进入紧急交战距离，让本测试只覆盖登乘动作，不等待完整会议/部署计时。
		Villager target = helper.spawn(EntityType.VILLAGER, 8, 2, 4);
		giant.setNoAi(true);
		skeleton.setNoAi(true);
		squadMate.setNoAi(true);
		target.setNoAi(true);
		GiantIntelligence.set(giant, 8);
		SkeletonIntelligence.set(skeleton, 10);
		ZombieIntelligence.set(squadMate, 6);
		giant.setTarget(target);
		skeleton.setTarget(target);
		squadMate.setTarget(target);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		GiantRiderBoardingGoal goal = new GiantRiderBoardingGoal(skeleton);
		boolean[] started = {false};
		boolean[] sawCatching = {false};
		boolean[] sawLowPalm = {false};
		boolean[] sawShoulder = {false};
		boolean[] sawHeadTransfer = {false};

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(giant, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			coordinator.heartbeat(squadMate, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());

			if (!started[0]) {
				if (!goal.canUse()) {
					return;
				}
				goal.start();
				started[0] = true;
			}
			if (goal.canContinueToUse()) {
				goal.tick();
			}

			GiantBoardingPhase phase = GiantTacticsState.boardingPhase(giant);
			sawCatching[0] |= phase == GiantBoardingPhase.CATCHING;
			if (phase == GiantBoardingPhase.LIFTING && skeleton.getVehicle() == giant) {
				sawLowPalm[0] |= skeleton.getY() - giant.getY() < 4.0;
			}
			if (phase == GiantBoardingPhase.SHOULDER) {
				double relativeY = skeleton.getY() - giant.getY();
				sawShoulder[0] |= relativeY > 7.0 && relativeY < 10.0;
			}
			sawHeadTransfer[0] |= phase == GiantBoardingPhase.TO_HEAD;

			if (started[0] && phase == GiantBoardingPhase.NONE && skeleton.getVehicle() == giant) {
				giant.positionRider(skeleton);
				helper.assertTrue(sawCatching[0], "The Giant never showed its low-hand catch phase.");
				helper.assertTrue(sawLowPalm[0], "The Skeleton skipped directly from its leap to a high attachment.");
				helper.assertTrue(sawShoulder[0], "The Skeleton never paused visibly at the Giant shoulder.");
				helper.assertTrue(sawHeadTransfer[0], "The shoulder-to-head transfer phase never ran.");
				helper.assertTrue(
					skeleton.getY() - giant.getY() > 10.5,
					"The completed rider was not left on the Giant head."
				);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void meleeActionTelegraphsThenDamagesExactlyOnceOnItsImpactFrame(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 7, 2, 4);
		giant.setNoAi(true);
		target.setNoAi(true);
		GiantIntelligence.set(giant, 10);
		giant.setTarget(target);
		float initialHealth = target.getHealth();
		long impactsBefore = SmartGiantMetrics.snapshot().meleeImpacts();
		GiantMeleeCombatGoal goal = new GiantMeleeCombatGoal(giant, 0.92);
		helper.assertTrue(goal.canUse(), "Smart melee goal rejected a valid close target.");
		goal.start();
		goal.tick();
		GiantMeleeAction action = goal.currentAction();
		helper.assertTrue(action.isActive(), "The Giant did not select a close-combat action.");

		for (int tick = 1; tick < action.impactTick(); tick++) {
			goal.tick();
		}
		helper.assertTrue(
			target.getHealth() == initialHealth,
			"The target took damage during the readable windup instead of the impact frame."
		);
		goal.tick();
		helper.assertTrue(target.getHealth() < initialHealth, "The action impact frame dealt no damage.");
		float healthAfterImpact = target.getHealth();
		for (int tick = action.impactTick() + 1; tick <= action.durationTicks(); tick++) {
			goal.tick();
		}
		helper.assertTrue(
			target.getHealth() == healthAfterImpact,
			"A single melee animation damaged its target more than once."
		);
		helper.assertTrue(
			SmartGiantMetrics.snapshot().meleeImpacts() == impactsBefore + 1,
			"The diagnostic counter did not record exactly one resolved impact."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void twoLoadedHandsForceAStompAndNeverDamageCarriedAllies(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Creeper rightPayload = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		Zombie leftPayload = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
		Villager target = helper.spawn(EntityType.VILLAGER, 7, 2, 4);
		giant.setNoAi(true);
		rightPayload.setNoAi(true);
		leftPayload.setNoAi(true);
		target.setNoAi(true);
		GiantIntelligence.set(giant, 10);
		GiantTacticsState.assignPayload(giant, GiantHand.RIGHT, rightPayload);
		GiantTacticsState.transitionHand(giant, GiantHand.RIGHT, GiantHandPhase.HOLDING);
		GiantTacticsState.assignPayload(giant, GiantHand.LEFT, leftPayload);
		GiantTacticsState.transitionHand(giant, GiantHand.LEFT, GiantHandPhase.HOLDING);
		helper.assertTrue(rightPayload.startRiding(giant, true, true), "Right payload setup failed.");
		helper.assertTrue(leftPayload.startRiding(giant, true, true), "Left payload setup failed.");
		giant.setTarget(target);
		float rightHealth = rightPayload.getHealth();
		float leftHealth = leftPayload.getHealth();

		GiantMeleeCombatGoal goal = new GiantMeleeCombatGoal(giant, 0.92);
		helper.assertTrue(goal.canUse(), "Loaded Giant rejected a stomp-range target.");
		goal.start();
		goal.tick();
		GiantMeleeAction action = goal.currentAction();
		helper.assertTrue(
			action.family() == GiantMeleeAction.Family.STOMP,
			"A Giant with two loaded hands illegally selected an arm attack."
		);
		for (int tick = 0; tick < action.impactTick(); tick++) {
			goal.tick();
		}
		helper.assertTrue(target.getHealth() < target.getMaxHealth(), "Loaded-hand stomp missed its close target.");
		helper.assertTrue(
			rightPayload.getHealth() == rightHealth && leftPayload.getHealth() == leftHealth,
			"The stomp damaged one of the Giant's carried allies."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void iqTenGiantUsesTwoHandedGroundSmashAgainstAFrontCrowd(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 7, 2, 4);
		Villager second = helper.spawn(EntityType.VILLAGER, 7, 2, 6);
		Villager third = helper.spawn(EntityType.VILLAGER, 8, 2, 3);
		Cow neutral = helper.spawn(EntityType.COW, 7, 2, 5);
		giant.setNoAi(true);
		target.setNoAi(true);
		second.setNoAi(true);
		third.setNoAi(true);
		neutral.setNoAi(true);
		GiantIntelligence.set(giant, 10);
		giant.setTarget(target);
		float neutralHealth = neutral.getHealth();

		GiantMeleeCombatGoal goal = new GiantMeleeCombatGoal(giant, 0.92);
		helper.assertTrue(goal.canUse(), "Crowd fixture did not activate Giant melee.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			goal.currentAction() == GiantMeleeAction.GROUND_SMASH,
			"An IQ-10 Giant with two free hands did not select its crowd-control smash."
		);
		for (int tick = 0; tick < GiantMeleeAction.GROUND_SMASH.impactTick(); tick++) {
			goal.tick();
		}
		helper.assertTrue(
			target.getHealth() < target.getMaxHealth()
				&& second.getHealth() < second.getMaxHealth()
				&& third.getHealth() < third.getMaxHealth(),
			"The front-centered ground smash failed to hit the complete visible crowd."
		);
		helper.assertTrue(
			neutral.getHealth() == neutralHealth,
			"The area attack damaged unrelated neutral livestock."
		);
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void queuedZombieReplacementPreservesIntelligenceAndCreatesOneGiant(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 6, 2, 6);
		ZombieIntelligenceBridge.set(zombie, 8);
		((GiantZombieSpawnAccess)zombie).mobsthinknow$markGiantReplacement();
		GiantZombieSpawnConversion.queueIfMarked(zombie, helper.getLevel());
		GiantZombieSpawnConversion.tickLevel(helper.getLevel());

		List<Giant> giants = helper.getLevel().getEntitiesOfClass(
			Giant.class,
			new AABB(zombie.blockPosition()).inflate(4.0, 14.0, 4.0),
			Giant::isAlive
		);
		helper.assertTrue(zombie.isRemoved(), "Marked source zombie was not replaced.");
		helper.assertTrue(giants.size() == 1, "Queued replacement did not create exactly one Giant.");
		helper.assertTrue(GiantIntelligence.get(giants.getFirst()) == 8, "Replacement lost the source zombie intelligence.");
		helper.succeed();
	}

	/** 避免测试包公开 ZombieIntelligence 的内部访问协议。 */
	private static final class ZombieIntelligenceBridge {
		private static void set(final Zombie zombie, final int value) {
			ZombieIntelligence.set(zombie, value);
		}
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

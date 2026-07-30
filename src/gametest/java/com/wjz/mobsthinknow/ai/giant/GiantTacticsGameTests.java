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
		helper.assertTrue(giant.getControllingPassenger() == null, "A tactical passenger incorrectly drove the Giant.");
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void preloadedDualHandsThrowCreeperAndZombieWithStagger(final GameTestHelper helper) {
		Giant giant = helper.spawn(EntityType.GIANT, 4, 2, 4);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 5, 2, 4);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 5);
		giant.setNoAi(true);
		creeper.setNoAi(true);
		zombie.setNoAi(true);
		helper.assertTrue(creeper.startRiding(giant, true, true), "Creeper payload could not mount.");
		helper.assertTrue(zombie.startRiding(giant, true, true), "Zombie payload could not mount.");

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
		for (int tick = 0; tick < 40; tick++) {
			goal.tick();
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

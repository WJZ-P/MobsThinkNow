package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperMetrics;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 验证四种核心怪物会在近身危险窗口中断集结，而不是继续机械走阵位。 */
public final class SquadCombatUrgencyGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 80, padding = 4)
	public void mixedSquadYieldsPreparationToEverySpeciesCombatWindow(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 3, 2, 3);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 4, 2, 2);
		Spider spider = helper.spawn(EntityType.SPIDER, 3, 2, 1);
		Villager target = helper.spawn(EntityType.VILLAGER, 14, 2, 2);
		zombie.setNoAi(true);
		skeleton.setNoAi(true);
		creeper.setNoAi(true);
		spider.setNoAi(true);
		target.setNoAi(true);
		ZombieIntelligence.set(zombie, 10);
		SkeletonIntelligence.set(skeleton, 10);
		CreeperIntelligence.set(creeper, 10);
		SpiderIntelligence.set(spider, 10);
		zombie.setTarget(target);
		skeleton.setTarget(target);
		creeper.setTarget(target);
		spider.setTarget(target);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		SquadPreparationGoal creeperPreparation = new SquadPreparationGoal(creeper, 1.0);

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(zombie, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			coordinator.heartbeat(creeper, target, true, target.position(), now);
			coordinator.heartbeat(spider, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			SquadDirective directive = coordinator.directiveFor(creeper);
			if (directive == null || (!directive.isMeetingPhase() && directive.state() != SquadState.DEPLOYING)) {
				return;
			}

			helper.assertTrue(creeperPreparation.canUse(), "A distant target incorrectly interrupted squad preparation.");
			target.snapTo(zombie.getX() + 1.2, zombie.getY(), zombie.getZ(), 0.0F, 0.0F);
			zombie.getSensing().tick();
			skeleton.getSensing().tick();
			creeper.getSensing().tick();
			spider.getSensing().tick();
			helper.assertTrue(
				SquadCombatUrgency.shouldInterruptPreparation(zombie, target),
				"A melee-ready zombie kept obeying the meeting order."
			);
			helper.assertTrue(
				SquadCombatUrgency.shouldInterruptPreparation(skeleton, target),
				"A cornered skeleton kept obeying the meeting order."
			);
			helper.assertTrue(
				SquadCombatUrgency.shouldInterruptPreparation(creeper, target),
				"A fuse-ready creeper kept obeying the meeting order."
			);
			helper.assertTrue(
				SquadCombatUrgency.shouldInterruptPreparation(spider, target),
				"A melee-ready spider kept obeying the meeting order."
			);
			helper.assertTrue(
				!creeperPreparation.canUse(),
				"Preparation Goal still owned MOVE/LOOK after the creeper entered its fuse window."
			);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 80, padding = 4)
	public void primedSquadCreeperMakesGroundAlliesEvacuateWithoutBreakingItsSpiderCarrier(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 5, 2, 2);
		Creeper creeper = helper.spawn(EntityType.CREEPER, 7, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 7, 2, 3);
		Spider spider = helper.spawn(EntityType.SPIDER, 9, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 15, 2, 2);
		zombie.setNoAi(true);
		creeper.setNoAi(true);
		skeleton.setNoAi(true);
		spider.setNoAi(true);
		target.setNoAi(true);
		// 手动驱动 Goal 的夹具不会执行原版 AI 落地初始化；显式标记后才能建立地面导航路径。
		zombie.setOnGround(true);
		skeleton.setOnGround(true);
		spider.setOnGround(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		zombie.setTarget(target);
		creeper.setTarget(target);
		skeleton.setTarget(target);
		spider.setTarget(target);
		ZombieIntelligence.set(zombie, 8);
		CreeperIntelligence.set(creeper, 10);
		SkeletonIntelligence.set(skeleton, 8);
		SpiderIntelligence.set(spider, 8);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		boolean[] verified = {false};
		long evacuationsBefore = SmartCreeperMetrics.snapshot().squadEvacuations();

		helper.onEachTick(() -> {
			if (verified[0]) {
				return;
			}
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(zombie, target, true, target.position(), now);
			coordinator.heartbeat(creeper, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			coordinator.heartbeat(spider, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			if (coordinator.viewFor(creeper) == null) {
				return;
			}

			creeper.setSwellDir(1);
			coordinator.heartbeat(creeper, target, true, target.position(), now);
			helper.assertTrue(
				coordinator.nearestPrimedCreeperThreatFor(zombie) == creeper,
				"The active-fuse index did not expose the primed squad creeper."
			);
			SquadCreeperEvadeGoal zombieGoal = new SquadCreeperEvadeGoal(zombie);
			SquadCreeperEvadeGoal skeletonGoal = new SquadCreeperEvadeGoal(skeleton);
			SquadCreeperEvadeGoal spiderGoal = new SquadCreeperEvadeGoal(spider);
			helper.assertTrue(zombieGoal.canUse(), "Nearby zombie did not react to its squad creeper's fuse.");
			helper.assertTrue(skeletonGoal.canUse(), "Nearby skeleton did not react to its squad creeper's fuse.");
			helper.assertTrue(spiderGoal.canUse(), "Nearby spider did not react to its squad creeper's fuse.");
			helper.assertTrue(
				!new SquadCreeperEvadeGoal(creeper).canUse(),
				"The committed bomber cancelled its own fuse to flee itself."
			);

			skeleton.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
			zombieGoal.start();
			skeletonGoal.start();
			spiderGoal.start();
			helper.assertTrue(
				SmartCreeperMetrics.snapshot().squadEvacuations() == evacuationsBefore + 3,
				"Squad evacuation diagnostics did not record all three reacting allies."
			);
			assertDestinationEscapes(helper, zombie, creeper, zombieGoal.evacuationDestination(), "zombie");
			assertDestinationEscapes(helper, skeleton, creeper, skeletonGoal.evacuationDestination(), "skeleton");
			assertDestinationEscapes(helper, spider, creeper, spiderGoal.evacuationDestination(), "spider");
			helper.assertTrue(!skeleton.isUsingItem(), "Evacuating skeleton kept drawing its bow.");
			zombieGoal.stop();
			skeletonGoal.stop();
			spiderGoal.stop();

			helper.assertTrue(
				creeper.startRiding(spider, true, true),
				"The carrier regression fixture could not mount its primed creeper."
			);
			helper.assertTrue(
				coordinator.nearestPrimedCreeperThreatFor(spider) == null,
				"Spider treated its own committed payload as an external blast threat."
			);
			helper.assertTrue(
				!new SquadCreeperEvadeGoal(spider).canUse(),
				"Spider carrier abandoned the primed creeper on its back."
			);
			verified[0] = true;
			helper.succeed();
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private static void assertDestinationEscapes(
		final GameTestHelper helper,
		final net.minecraft.world.entity.PathfinderMob member,
		final Creeper threat,
		final Vec3 destination,
		final String label
	) {
		helper.assertTrue(destination != null, "The " + label + " produced no reachable evacuation destination.");
		helper.assertTrue(
			destination.distanceToSqr(threat.position()) > member.distanceToSqr(threat),
			"The " + label + " evacuation destination did not increase blast distance."
		);
	}
}

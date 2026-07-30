package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

/** 从真实实体、GoalSelector、骑乘关系和引信数据验证蜘蛛战术。 */
public final class SpiderTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void spiderMixinInstallsThreeGoalsAndAppliesPersistentIdentity(final GameTestHelper helper) {
		long before = SmartSpiderMetrics.snapshot().installedGoals();
		Spider spider = helper.spawn(EntityType.SPIDER, 2, 2, 2);

		helper.assertTrue(
			SmartSpiderMetrics.snapshot().installedGoals() == before + 3,
			"Spider construction did not replace pounce/melee and install carrier coordination."
		);
		int intelligence = SpiderIntelligence.get(spider);
		helper.assertTrue(intelligence >= 1 && intelligence <= 10, "Spider intelligence escaped the 1-10 range.");
		helper.assertTrue(
			spider.getCustomName() != null && spider.getCustomName().getString().endsWith("[" + intelligence + "]"),
			"Natural spider name did not expose its stable intelligence."
		);
		helper.succeed();
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
		helper.assertTrue(goal.canUse(), "Nearby spider and creeper did not reserve a transport pair.");
		goal.start();
		helper.assertTrue(creeper.getVehicle() == spider, "Reserved creeper did not mount the nearby spider.");
		helper.assertTrue(goal.isCarryingCreeper(), "Carrier state did not enter the mounted phase.");
		helper.assertTrue(creeper.getSwellDir() == -1, "Early fuse was not reset so rendezvous could happen first.");
		helper.assertTrue(
			spider.getControllingPassenger() == null,
			"Creeper payload was treated as a driver and would pause spider movement goals."
		);
		goal.stop();
		helper.succeed();
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
		helper.assertTrue(creeper.startRiding(spider, true, true), "GameTest setup could not mount creeper payload.");

		SpiderCreeperCarrierGoal goal = new SpiderCreeperCarrierGoal(spider);
		helper.assertTrue(goal.canUse(), "Mounted pair did not activate delivery mode.");
		goal.start();
		goal.tick();
		helper.assertTrue(goal.isFuseCommitted(), "Carrier reached delivery range without committing the fuse.");
		helper.assertTrue(creeper.getSwellDir() == 1, "Mounted creeper did not enter positive swell direction.");
		helper.assertTrue(goal.phase() == SpiderCreeperCarrierGoal.Phase.FINAL_CHARGE, "Carrier skipped final charge phase.");
		goal.stop();
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

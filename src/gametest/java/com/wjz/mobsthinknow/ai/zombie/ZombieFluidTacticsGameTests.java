package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** 真实流体源、BucketPickup 与实体持久状态共同参与的集成测试。 */
public final class ZombieFluidTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void retreatingWaterCarrierDeterministicallyBuildsWaterScreen(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Villager attacker = helper.spawn(EntityType.VILLAGER, 3, 1, 2);
		zombie.setNoAi(true);
		attacker.setNoAi(true);
		attacker.setInvulnerable(true);
		zombie.setHealth(4.0F);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.WATER, null, 0L, 0L
		));

		boolean hurt = zombie.hurtServer(
			helper.getLevel(),
			zombie.damageSources().mobAttack(attacker),
			1.0F
		);
		helper.assertTrue(hurt, "The retreat-water test attack was not applied.");
		ReactiveRetreatGoal goal = new ReactiveRetreatGoal(zombie);
		helper.assertTrue(goal.canUse(), "The low-health water carrier did not begin retreating.");
		goal.start();

		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(zombie);
		helper.assertTrue(state.isDeployed(), "The retreat began without a deterministic water-screen attempt.");
		helper.assertTrue(
			helper.getLevel().getFluidState(state.source()).is(FluidTags.WATER),
			"The retreat screen did not create a real water source."
		);
		helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "The retreat screen did not consume the filled bucket.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(maxTicks = 120, padding = 8)
	public void squadWaterCarrierExtinguishesBurningTeammate(final GameTestHelper helper) {
		Zombie carrier = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie victim = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie third = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		Villager target = helper.spawn(EntityType.VILLAGER, 10, 2, 1);
		List<Zombie> squad = List.of(carrier, victim, third);
		for (Zombie zombie : squad) {
			zombie.setNoAi(true);
			zombie.setNoGravity(true);
			zombie.setInvulnerable(true);
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
			zombie.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			zombie.setTarget(target);
		}
		target.setNoAi(true);
		target.setNoGravity(true);
		carrier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		((ZombieFluidCarrierAccess)carrier).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.WATER, null, 0L, 0L
		));

		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		boolean[] supportTriggered = {false};
		int[] elapsed = {0};
		helper.onEachTick(() -> {
			elapsed[0]++;
			long now = helper.getLevel().getGameTime();
			for (Zombie zombie : squad) {
				zombie.setTarget(target);
				coordinator.heartbeat(zombie, target, true, target.position(), now);
			}
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			if (supportTriggered[0] || coordinator.viewFor(victim) == null) {
				if (elapsed[0] == 110) {
					helper.fail(
						"Squad fire-support setup did not form: activeSquads="
							+ ZombieSquadCoordinator.activeSquadCount()
							+ ", carrierTarget=" + (carrier.getTarget() == target)
							+ ", victimTarget=" + (victim.getTarget() == target)
							+ ", thirdTarget=" + (third.getTarget() == target)
							+ ", now=" + now
					);
				}
				return;
			}

			supportTriggered[0] = true;
			victim.igniteForSeconds(10.0F);
			helper.assertTrue(victim.isOnFire(), "The squad support victim was not burning.");
			ZombieSquadCoordinator.onSquadMemberBurning(victim);
			ZombieFluidTacticsGoal goal = new ZombieFluidTacticsGoal(carrier);
			helper.assertTrue(goal.canUse(), "The selected squad water carrier did not consume the fire-support order.");
			goal.start();
			goal.tick();

			ZombieFluidCarrierState state = ZombieSpecialEquipment.state(carrier);
			helper.assertTrue(state.isDeployed(), "The squad helper reached its teammate without deploying water.");
			helper.assertTrue(
				helper.getLevel().getFluidState(state.source()).is(FluidTags.WATER),
				"The squad fire-support order did not create a water source."
			);
			helper.assertTrue(!victim.isOnFire(), "Water placed at the teammate's feet did not extinguish it.");
			goal.stop();
			helper.succeed();
		});
	}
	@GameTest(maxTicks = 80)
	public void installedGoalDeploysLavaAgainstIronGolemTarget(final GameTestHelper helper) {
		BlockPos targetFeet = new BlockPos(4, 1, 2);
		helper.setBlock(targetFeet.below(), Blocks.STONE);
		helper.setBlock(targetFeet, Blocks.AIR);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, targetFeet);
		zombie.clearFire();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LAVA_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.LAVA, null, 0L, 0L
		));
		target.setNoAi(true);
		zombie.setTarget(target);
		ZombieFluidTacticsGoal probe = new ZombieFluidTacticsGoal(zombie);
		helper.assertTrue(
			probe.canUse(),
			"A full lava carrier did not recognize its iron-golem target before GoalSelector scheduling."
		);

		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(target);
			ZombieFluidCarrierState state = ZombieSpecialEquipment.state(zombie);
			if (!state.isDeployed()) {
				return;
			}
			helper.assertTrue(
				helper.getLevel().getFluidState(state.source()).is(FluidTags.LAVA),
				"The installed GoalSelector did not place lava against an iron-golem target."
			);
			helper.assertTrue(
				zombie.swinging && zombie.swingingArm == InteractionHand.MAIN_HAND,
				"The lava carrier placed fluid without its visible bucket-use animation."
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void lavaCarrierSkipsOccupiedFeetAndUsesAdjacentCandidate(final GameTestHelper helper) {
		BlockPos targetFeet = new BlockPos(4, 1, 2);
		helper.setBlock(targetFeet.below(), Blocks.STONE);
		helper.setBlock(targetFeet, Blocks.AIR);
		Zombie carrier = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Zombie friendly = helper.spawn(EntityType.ZOMBIE, targetFeet);
		Villager target = helper.spawn(EntityType.VILLAGER, targetFeet);
		carrier.clearFire();
		carrier.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		carrier.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LAVA_BUCKET));
		((ZombieFluidCarrierAccess)carrier).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.LAVA, null, 0L, 0L
		));
		friendly.setNoAi(true);
		friendly.clearFire();
		target.setNoAi(true);
		carrier.setTarget(target);

		helper.onEachTick(() -> {
			carrier.clearFire();
			friendly.clearFire();
			carrier.setTarget(target);
			ZombieFluidCarrierState state = ZombieSpecialEquipment.state(carrier);
			if (!state.isDeployed()) {
				return;
			}
			helper.assertTrue(
				!targetFeet.equals(helper.relativePos(state.source())),
				"The lava carrier poured directly into a friendly zombie instead of trying an adjacent cell."
			);
			helper.assertTrue(
				helper.getLevel().getFluidState(state.source()).is(FluidTags.LAVA),
				"The adjacent fallback did not create a lava source."
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void nonPlayerAttackEventOrdersWaterSupportToDeploy(final GameTestHelper helper) {
		BlockPos attackerFeet = new BlockPos(4, 1, 2);
		helper.setBlock(attackerFeet.below(), Blocks.STONE);
		helper.setBlock(attackerFeet, Blocks.AIR);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Villager attacker = helper.spawn(EntityType.VILLAGER, attackerFeet);
		boolean[] damageApplied = {false};
		zombie.clearFire();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WATER_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.WATER, null, 0L, 0L
		));
		attacker.setNoAi(true);
		attacker.setInvulnerable(true);
		zombie.setTarget(attacker);

		helper.onEachTick(() -> {
			zombie.clearFire();
			zombie.setTarget(attacker);
			if (!damageApplied[0] && zombie.tickCount >= 2) {
				zombie.invulnerableTime = 0;
				boolean hurt = zombie.hurtServer(
					helper.getLevel(),
					zombie.damageSources().mobAttack(attacker),
					1.0F
				);
				helper.assertTrue(hurt, "The non-player fluid-support test attack was not applied.");
				damageApplied[0] = true;
			}

			ZombieFluidCarrierState state = ZombieSpecialEquipment.state(zombie);
			if (!state.isDeployed()) {
				return;
			}
			helper.assertTrue(damageApplied[0], "Water deployed before the teammate/self attack alert.");
			helper.assertTrue(
				helper.getLevel().getFluidState(state.source()).is(FluidTags.WATER),
				"The water support alert did not produce a real water source."
			);
			helper.assertTrue(
				zombie.swinging && zombie.swingingArm == InteractionHand.MAIN_HAND,
				"The water carrier placed fluid without its visible bucket-use animation."
			);
			helper.succeed();
		});
	}

	@GameTest
	public void lavaCarrierDeploysAtPlayerFeetThenRecoversAndDisengages(final GameTestHelper helper) {
		BlockPos playerFeet = new BlockPos(4, 1, 2);
		helper.setBlock(playerFeet.below(), Blocks.STONE);
		helper.setBlock(playerFeet, Blocks.AIR);
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setNoAi(true);
		zombie.clearFire();
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LAVA_BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.LAVA, null, 0L, 0L
		));

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 absolutePlayerFeet = helper.absoluteVec(Vec3.atBottomCenterOf(playerFeet));
		player.snapTo(absolutePlayerFeet.x, absolutePlayerFeet.y, absolutePlayerFeet.z, 0.0F, 0.0F);
		player.setInvulnerable(true);
		helper.getLevel().addFreshEntity(player);
		// GameTest 的轻量 Mock Player 不进入服务器玩家列表，Mob#canAttack 会拒绝把它设成 target；
		// 通过与真实受击广播相同的短期威胁入口驱动 Goal，后续投放逻辑完全一致。
		ZombieFluidThreatMemory.record(zombie, player, zombie.position());

		ZombieFluidTacticsGoal goal = new ZombieFluidTacticsGoal(zombie);
		helper.assertTrue(
			goal.canUse(),
			"A full lava carrier did not start from a real fluid-threat signal."
		);
		goal.start();
		goal.tick();

		ZombieFluidCarrierState deployed = ZombieSpecialEquipment.state(zombie);
		helper.assertTrue(deployed.isDeployed(), "The lava bucket was not deployed.");
		helper.assertTrue(
			helper.getLevel().getFluidState(deployed.source()).is(FluidTags.LAVA),
			"The deployed source under the player was not lava."
		);
		helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "The deployed lava bucket did not become empty.");

		// 跳过等待窗口，真实执行同一 Goal 的 BucketPickup 回收分支。
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.LAVA,
			deployed.source(),
			helper.getLevel().getGameTime(),
			0L
		));
		goal.tick();
		helper.assertTrue(zombie.getMainHandItem().is(Items.LAVA_BUCKET), "The lava source was not recovered.");
		helper.assertTrue(
			helper.getLevel().getFluidState(deployed.source()).isEmpty(),
			"The source remained after the lava carrier recovered it."
		);
		goal.stop();
		player.discard();
		helper.succeed();
	}

	@GameTest
	public void deployedWaterSourceIsRecoveredIntoTheSameBucket(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setNoAi(true);
		zombie.clearFire();
		BlockPos relativeSource = new BlockPos(3, 1, 2);
		BlockPos source = helper.absolutePos(relativeSource);
		helper.setBlock(relativeSource, Blocks.WATER);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.WATER,
			source,
			helper.getLevel().getGameTime(),
			0L
		));
		Zombie restored = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
		restored.restoreFrom(zombie);
		ZombieFluidCarrierState restoredState = ZombieSpecialEquipment.state(restored);
		helper.assertTrue(
			restoredState.utility() == UtilityClass.WATER && source.equals(restoredState.source()),
			"The deployed fluid source did not survive the vanilla entity save/load path."
		);

		ZombieFluidTacticsGoal goal = new ZombieFluidTacticsGoal(restored);
		helper.assertTrue(goal.canUse(), "A persisted deployed source did not resume its recovery transaction.");
		goal.start();
		goal.tick();

		helper.assertTrue(restored.getMainHandItem().is(Items.WATER_BUCKET), "The source did not refill the empty bucket.");
		helper.assertTrue(helper.getBlockState(relativeSource).isAir(), "The recovered source remained in the world.");
		helper.assertTrue(
			!ZombieSpecialEquipment.state(restored).isDeployed(),
			"The deployed transaction was not cleared after recovery."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest
	public void removedSourceLeavesEmptyBucketAndDropsUtilityRole(final GameTestHelper helper) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		zombie.setNoAi(true);
		zombie.clearFire();
		BlockPos relativeMissingSource = new BlockPos(3, 1, 2);
		BlockPos missingSource = helper.absolutePos(relativeMissingSource);
		helper.setBlock(relativeMissingSource, Blocks.AIR);
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BUCKET));
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(new ZombieFluidCarrierState(
			UtilityClass.LAVA,
			missingSource,
			helper.getLevel().getGameTime(),
			0L
		));

		ZombieFluidTacticsGoal goal = new ZombieFluidTacticsGoal(zombie);
		helper.assertTrue(goal.canUse(), "A pending deployed transaction was not resumed.");
		goal.start();
		goal.tick();

		helper.assertTrue(zombie.getMainHandItem().is(Items.BUCKET), "A missing source fabricated a filled bucket.");
		helper.assertTrue(
			ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.NONE,
			"The zombie kept its utility role after the player removed its fluid."
		);
		goal.stop();
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

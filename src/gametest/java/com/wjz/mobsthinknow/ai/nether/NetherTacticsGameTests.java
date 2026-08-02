package com.wjz.mobsthinknow.ai.nether;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** 使用真实实体 tick、GoalSelector、Brain 与 Mixin 验证第一批下界战术。 */
public final class NetherTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void naturalFinalizationAssignsOneCompatibleSyncedProfessionPerFamily(final GameTestHelper helper) {
		EntityType<?>[] types = {
			EntityType.PIGLIN,
			EntityType.PIGLIN_BRUTE,
			EntityType.BLAZE,
			EntityType.GHAST,
			EntityType.HOGLIN,
			EntityType.ZOGLIN,
			EntityType.MAGMA_CUBE,
			EntityType.ZOMBIFIED_PIGLIN,
			EntityType.WITHER_SKELETON
		};
		BlockPos feet = helper.absolutePos(new BlockPos(4, 2, 4));
		for (EntityType<?> type : types) {
			Mob mob = (Mob)type.create(helper.getLevel(), EntitySpawnReason.NATURAL);
			helper.assertTrue(mob != null, "Could not create profession fixture for " + type);
			mob.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
			mob.finalizeSpawn(
				helper.getLevel(),
				helper.getLevel().getCurrentDifficultyAt(feet),
				EntitySpawnReason.NATURAL,
				null
			);
			NetherProfession profession = NetherProfessionProfile.get(mob);
			helper.assertTrue(mob instanceof NetherProfessionAccess, type + " has no synced profession slot.");
			helper.assertTrue(
				profession != NetherProfession.NONE
					&& profession.belongsTo(NetherProfessionProfile.familyOf(mob)),
				type + " received incompatible profession " + profession
			);
			mob.discard();
		}
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 20, padding = 4)
	public void everyNetherEntityInstallsItsExpectedRuntimeBridge(final GameTestHelper helper) {
		long before = SmartNetherMetrics.snapshot().installedControllers();
		Blaze blaze = helper.spawn(EntityType.BLAZE, 2, 3, 2);
		Ghast ghast = helper.spawn(EntityType.GHAST, 6, 8, 6);
		Piglin piglin = helper.spawn(EntityType.PIGLIN, 3, 2, 3);
		PiglinBrute brute = helper.spawn(EntityType.PIGLIN_BRUTE, 4, 2, 3);
		Hoglin hoglin = helper.spawn(EntityType.HOGLIN, 5, 2, 3);
		Zoglin zoglin = helper.spawn(EntityType.ZOGLIN, 6, 2, 3);
		MagmaCube cube = helper.spawn(EntityType.MAGMA_CUBE, 7, 2, 3);
		ZombifiedPiglin zombifiedPiglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, 8, 2, 3);
		var witherSkeleton = helper.spawn(EntityType.WITHER_SKELETON, 9, 2, 3);

		piglin.setImmuneToZombification(true);
		brute.setImmuneToZombification(true);
		hoglin.setImmuneToZombification(true);
		helper.assertTrue(blaze instanceof BlazeChargeAccess, "Blaze invoker bridge was not mixed in.");
		helper.assertTrue(hoglin instanceof HoglinChargeAccess, "Hoglin charge-state bridge was not mixed in.");
		helper.assertTrue(zoglin instanceof HoglinChargeAccess, "Zoglin did not reuse the charge-state bridge.");
		helper.assertTrue(
			SmartNetherMetrics.snapshot().installedControllers() == before + 9,
			"Expected nine Nether runtime controllers/goals to install exactly once."
		);
		zombifiedPiglin.discard();
		witherSkeleton.discard();
		ghast.discard();
		cube.discard();
		helper.succeed();
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 130, padding = 4)
	public void blazeUsesAChargedVolleyInsteadOfPerTickFireballs(final GameTestHelper helper) {
		Blaze blaze = helper.spawn(EntityType.BLAZE, 4, 5, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 14, 2, 4);
		target.setNoAi(true);
		blaze.setTarget(target);
		long volleyBefore = SmartNetherMetrics.snapshot().blazeVolleys();
		long fireballsBefore = SmartNetherMetrics.snapshot().blazeFireballs();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			blaze.setTarget(target);
			SmartNetherMetrics.Snapshot metrics = SmartNetherMetrics.snapshot();
			if (metrics.blazeVolleys() > volleyBefore && metrics.blazeFireballs() > fireballsBefore) {
				helper.assertTrue(
					metrics.blazeFireballs() - fireballsBefore <= 4,
					"A single Blaze volley exceeded the four-shot hard-mode ceiling."
				);
				helper.succeed();
			}
			if (elapsed[0] >= 120) {
				helper.assertTrue(false, "Blaze never completed its visible charge and first predictive shot.");
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 100, padding = 4)
	public void ghastChargesPredictiveArtilleryAndRelocatesAfterFiring(final GameTestHelper helper) {
		Ghast ghast = helper.spawn(EntityType.GHAST, 5, 14, 5);
		Villager target = helper.spawn(EntityType.VILLAGER, 15, 2, 5);
		target.setNoAi(true);
		ghast.setTarget(target);
		long shotsBefore = SmartNetherMetrics.snapshot().ghastShots();
		long relocationsBefore = SmartNetherMetrics.snapshot().ghastRelocations();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			ghast.setTarget(target);
			SmartNetherMetrics.Snapshot metrics = SmartNetherMetrics.snapshot();
			if (metrics.ghastShots() > shotsBefore && metrics.ghastRelocations() > relocationsBefore) {
				helper.succeed();
			}
			if (elapsed[0] >= 90) {
				helper.assertTrue(false, "Ghast did not charge, fire, and request a new artillery position.");
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 70, padding = 4)
	public void hoglinTelegraphsBeforeEnteringItsSafeLaneCharge(final GameTestHelper helper) {
		Hoglin hoglin = helper.spawn(EntityType.HOGLIN, 4, 2, 5);
		var target = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 targetFeet = helper.absoluteVec(new Vec3(12.0, 2.0, 5.0));
		target.snapTo(targetFeet.x, targetFeet.y, targetFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(target), "Hoglin target fixture was not added.");
		hoglin.setImmuneToZombification(true);
		hoglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		HoglinChargeAccess access = (HoglinChargeAccess)hoglin;
		boolean[] sawWindup = {false};
		boolean[] sawCharging = {false};
		Vec3 chargeOrigin = hoglin.position();
		Vec3 chargeForward = target.position().subtract(chargeOrigin).multiply(1.0, 0.0, 1.0).normalize();
		long chargesBefore = SmartNetherMetrics.snapshot().hoglinCharges();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			hoglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
			if (access.mobsthinknow$getChargePhase() == HoglinChargeController.Phase.WINDUP) {
				sawWindup[0] = true;
			}
			if (access.mobsthinknow$getChargePhase() == HoglinChargeController.Phase.CHARGING) {
				sawCharging[0] = true;
				helper.assertTrue(sawWindup[0], "Hoglin entered charge without a readable windup phase.");
				helper.assertTrue(
					SmartNetherMetrics.snapshot().hoglinCharges() > chargesBefore,
					"Hoglin charge diagnostics did not record the physical impulse."
				);
				if (hoglin.position().subtract(chargeOrigin).dot(chargeForward) > 0.10) {
					helper.succeed();
				}
			}
			if (sawCharging[0] && access.mobsthinknow$getChargePhase() == HoglinChargeController.Phase.RECOVERING) {
				helper.assertTrue(false, "Hoglin entered recovery without advancing along its telegraphed lane.");
			}
			if (elapsed[0] >= 60) {
				helper.assertTrue(false, "Hoglin never advanced from windup into its safe-lane charge.");
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 50, padding = 4)
	public void piglinCrossbowControllerWritesAReachableBattleLane(final GameTestHelper helper) {
		// 该通用结构故意是纯空气；本测试自己铺设足够覆盖左右散列通道的真实寻路平面。
		for (int x = 1; x <= 14; x++) {
			for (int z = 7; z <= 25; z++) {
				helper.setBlock(new net.minecraft.core.BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		Piglin piglin = helper.spawn(EntityType.PIGLIN, 4, 2, 16);
		var target = helper.makeMockPlayer(GameType.SURVIVAL);
		Vec3 targetFeet = helper.absoluteVec(new Vec3(12.0, 2.0, 16.0));
		target.snapTo(targetFeet.x, targetFeet.y, targetFeet.z, 90.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(target), "Piglin target fixture was not added.");
		piglin.setImmuneToZombification(true);
		piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		long before = SmartNetherMetrics.snapshot().piglinFormationMoves();

		int[] elapsed = {0};
		helper.onEachTick(() -> {
			elapsed[0]++;
			piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
			if (!piglin.onGround()) {
				if (elapsed[0] >= 40) {
					helper.assertTrue(false, "Piglin did not settle onto the dedicated navigation floor.");
				}
				return;
			}
			if (SmartNetherMetrics.snapshot().piglinFormationMoves() > before) {
				helper.assertTrue(
					piglin.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(),
					"Piglin battle-line controller produced no reachable walk target."
				);
				helper.assertTrue(
					piglin.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).isPresent(),
					"Piglin battle-line controller did not keep its ranged target in view."
				);
				helper.succeed();
			}
			if (elapsed[0] >= 40) {
				helper.assertTrue(false, "Settled Piglin did not select one reachable hashed crossbow lane.");
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 90, padding = 4)
	public void magmaCubeAddsOnePredictiveHorizontalImpulsePerRealJump(final GameTestHelper helper) {
		MagmaCube cube = helper.spawn(EntityType.MAGMA_CUBE, 4, 2, 5);
		Villager target = helper.spawn(EntityType.VILLAGER, 12, 2, 5);
		cube.setSize(3, true);
		target.setNoAi(true);
		cube.setTarget(target);
		long before = SmartNetherMetrics.snapshot().magmaPounces();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			cube.setTarget(target);
			if (SmartNetherMetrics.snapshot().magmaPounces() > before) {
				Vec3 movement = cube.getDeltaMovement();
				helper.assertTrue(movement.x > 0.20, "Predictive Magma Cube jump did not accelerate toward the target.");
				helper.assertTrue(movement.y > 0.0, "Predictive pounce overwrote the vanilla vertical jump.");
				helper.succeed();
			}
			if (elapsed[0] >= 80) {
				helper.assertTrue(false, "Magma Cube did not perform a predictive real jump.");
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 70, padding = 4)
	public void zombifiedPiglinBerserkerTelegraphsBeforeItsMidRangeLunge(final GameTestHelper helper) {
		for (int x = 2; x <= 13; x++) {
			for (int z = 3; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.BLACKSTONE);
			}
		}
		ZombifiedPiglin piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, 4, 2, 5);
		Villager target = helper.spawn(EntityType.VILLAGER, 10, 2, 5);
		target.setNoAi(true);
		piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
		NetherProfessionProfile.set(piglin, NetherProfession.ZOMBIFIED_PIGLIN_BERSERKER);
		piglin.setTarget(target);
		long before = SmartNetherMetrics.snapshot().netherUndeadLunges();
		int[] elapsed = {0};
		Vec3[] lungeOrigin = {null};

		helper.onEachTick(() -> {
			elapsed[0]++;
			piglin.setTarget(target);
			if (SmartNetherMetrics.snapshot().netherUndeadLunges() > before) {
				if (lungeOrigin[0] == null) {
					helper.assertTrue(elapsed[0] >= 6, "Berserker lunged without a readable windup window.");
					lungeOrigin[0] = piglin.position();
				} else if (piglin.position().subtract(lungeOrigin[0]).horizontalDistanceSqr() > 0.04) {
					// 速度会在实体移动积分时被地面摩擦消费；跨 tick 的真实位移才是玩家最终看到的突进。
					helper.succeed();
				}
			}
			if (elapsed[0] >= 60) {
				helper.assertTrue(
					false,
					lungeOrigin[0] == null
						? "Berserker never advanced from windup into its mid-range lunge."
						: "Berserker entered lunge state but produced no visible displacement."
				);
			}
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

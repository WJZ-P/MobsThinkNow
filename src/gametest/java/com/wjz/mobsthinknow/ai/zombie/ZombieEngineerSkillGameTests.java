package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.ConfigManager;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/** 工程兵 TNT、流体和直接点燃技能的真实世界集成测试。 */
public final class ZombieEngineerSkillGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 80, padding = 8)
	public void demolitionSkillPlacesAndPrimesOwnedTntThenRestoresWeapon(final GameTestHelper helper) {
		prepareStoneArena(helper);
		Zombie engineer = createEngineer(helper, 2, 1, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 1, 2);
		target.setNoAi(true);
		engineer.setTarget(target);

		long tntBefore = SmartZombieMetrics.snapshot().engineerTntCharges();
		ZombieEngineerSkillGoal goal = new ZombieEngineerSkillGoal(
			engineer,
			(candidate, available) -> ZombieEngineerSkillGoal.Skill.DEMOLITION_CHARGE,
			0
		);
		helper.assertTrue(
			goal.canUse(),
			"The engineer did not find a legal TNT site near an isolated target: " + diagnostic(engineer, target)
		);
		goal.start();

		helper.onEachTick(() -> {
			engineer.clearFire();
			engineer.setTarget(target);
			if (goal.canContinueToUse()) {
				goal.tick();
			}
			List<PrimedTnt> charges = helper.getLevel().getEntitiesOfClass(
				PrimedTnt.class,
				new AABB(engineer.blockPosition()).inflate(10.0),
				charge -> charge.isAlive()
			);
			if (charges.isEmpty()) {
				if (!goal.canContinueToUse()) {
					goal.stop();
					helper.fail("The demolition state machine ended before creating PrimedTnt.");
				}
				return;
			}

			PrimedTnt charge = charges.getFirst();
			helper.assertTrue(charge.getOwner() == engineer, "PrimedTnt did not retain the engineer as its owner.");
			helper.assertTrue(charge.getFuse() > 0 && charge.getFuse() <= 80, "The charge has an invalid vanilla fuse.");
			helper.assertTrue(
				!helper.getLevel().getBlockState(charge.blockPosition()).is(Blocks.TNT),
				"The placed TNT block remained after conversion to PrimedTnt."
			);
			helper.assertTrue(
				engineer.getMainHandItem().is(Items.IRON_SWORD) && engineer.getOffhandItem().isEmpty(),
				"The demolition animation did not restore the engineer's original hands."
			);
			helper.assertTrue(
				SmartZombieMetrics.snapshot().engineerTntCharges() > tntBefore,
				"The primed charge was not recorded in diagnostics."
			);
			goal.stop();
			helper.discard(charge);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120, padding = 8)
	public void waterControlDeploysAndRecoversARealSource(final GameTestHelper helper) {
		runFluidSkill(helper, UtilityClass.WATER, ZombieEngineerSkillGoal.Skill.WATER_CONTROL);
	}

	@GameTest(maxTicks = 120, padding = 8)
	public void lavaControlDeploysAndRecoversARealSource(final GameTestHelper helper) {
		runFluidSkill(helper, UtilityClass.LAVA, ZombieEngineerSkillGoal.Skill.LAVA_CONTROL);
	}

	@GameTest(maxTicks = 50)
	public void flintSkillDirectlyIgnitesTargetAndRestoresWeapon(final GameTestHelper helper) {
		Zombie engineer = createEngineer(helper, 1, 1, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
		target.setNoAi(true);
		target.clearFire();
		engineer.setTarget(target);

		helper.assertTrue(
			ZombieEngineerSkillGoal.isVisualTool(new ItemStack(Items.TNT))
				&& ZombieEngineerSkillGoal.isVisualTool(new ItemStack(Items.FLINT_AND_STEEL))
				&& ZombieEngineerSkillGoal.isVisualTool(new ItemStack(Items.WATER_BUCKET))
				&& ZombieEngineerSkillGoal.isVisualTool(new ItemStack(Items.LAVA_BUCKET))
				&& ZombieEngineerSkillGoal.isVisualTool(new ItemStack(Items.BUCKET))
				&& !ZombieEngineerSkillGoal.isVisualTool(new ItemStack(Items.IRON_INGOT)),
			"The engineer's temporary-tool whitelist still contains a removed repair tool or misses a new tool."
		);

		long ignitionsBefore = SmartZombieMetrics.snapshot().engineerIgnitions();
		ZombieEngineerSkillGoal goal = new ZombieEngineerSkillGoal(
			engineer,
			(candidate, available) -> ZombieEngineerSkillGoal.Skill.IGNITE_TARGET,
			0
		);
		helper.assertTrue(
			goal.canUse(),
			"The engineer did not prepare direct ignition against a nearby unlit target: "
				+ diagnostic(engineer, target)
		);
		goal.start();
		helper.onEachTick(() -> {
			engineer.clearFire();
			engineer.setTarget(target);
			if (goal.canContinueToUse()) {
				goal.tick();
			}
			if (!target.isOnFire()) {
				if (!goal.canContinueToUse()) {
					goal.stop();
					helper.fail("The direct-ignition state machine ended without lighting the target.");
				}
				return;
			}
			goal.stop();
			helper.assertTrue(
				engineer.getMainHandItem().is(Items.IRON_SWORD) && engineer.getOffhandItem().isEmpty(),
				"Direct ignition did not restore the engineer's original hands."
			);
			helper.assertTrue(
				SmartZombieMetrics.snapshot().engineerIgnitions() > ignitionsBefore,
				"The successful target ignition was not recorded in diagnostics."
			);
			helper.succeed();
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private static void runFluidSkill(
		final GameTestHelper helper,
		final UtilityClass utility,
		final ZombieEngineerSkillGoal.Skill skill
	) {
		prepareStoneArena(helper);
		Zombie engineer = createEngineer(helper, 2, 1, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 4, 1, 2);
		target.setNoAi(true);
		// 岩浆测试只验证流体事务，不让环境伤害提前移除合法战斗目标。
		target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
		engineer.setTarget(target);

		SmartZombieMetrics.Snapshot before = SmartZombieMetrics.snapshot();
		ZombieEngineerSkillGoal goal = new ZombieEngineerSkillGoal(engineer, (candidate, available) -> skill, 0);
		helper.assertTrue(
			goal.canUse(),
			"The engineer did not find a legal " + utility + " site: " + diagnostic(engineer, target)
		);
		goal.start();
		boolean[] deployed = {false};
		BlockPos[] source = {null};

		helper.onEachTick(() -> {
			engineer.clearFire();
			engineer.setTarget(target);
			if (goal.canContinueToUse()) {
				goal.tick();
			}
			ZombieFluidCarrierState state = ZombieSpecialEquipment.state(engineer);
			if (state.isEngineerDeployment()) {
				deployed[0] = true;
				source[0] = state.source();
				helper.assertTrue(state.utility() == utility, "The persisted engineer fluid type changed mid-skill.");
				helper.assertTrue(
					state.source() != null && (utility == UtilityClass.WATER
						? helper.getLevel().getFluidState(state.source()).is(FluidTags.WATER)
						: helper.getLevel().getFluidState(state.source()).is(FluidTags.LAVA)),
					"The engineer did not create a real matching fluid source."
				);
				return;
			}

			if (!deployed[0]) {
				if (!goal.canContinueToUse()) {
					goal.stop();
					helper.fail("The engineer fluid state machine ended before deployment.");
				}
				return;
			}

			goal.stop();
			helper.assertTrue(source[0] != null, "The test lost the deployed source coordinate.");
			helper.assertTrue(
				helper.getLevel().getFluidState(source[0]).isEmpty(),
				"The engineer finished the skill without recovering its source block."
			);
			helper.assertTrue(
				engineer.getMainHandItem().is(Items.IRON_SWORD) && engineer.getOffhandItem().isEmpty(),
				"The fluid skill did not restore the engineer's original hands."
			);
		SmartZombieMetrics.Snapshot after = SmartZombieMetrics.snapshot();
		long beforeSpecific = utility == UtilityClass.WATER
			? before.engineerWaterDeployments()
			: before.engineerLavaDeployments();
		long afterSpecific = utility == UtilityClass.WATER
			? after.engineerWaterDeployments()
			: after.engineerLavaDeployments();
			helper.assertTrue(afterSpecific > beforeSpecific, "The engineer fluid deployment metric did not advance.");
			helper.assertTrue(after.fluidRecoveries() > before.fluidRecoveries(), "The real source was not recorded as recovered.");
			helper.succeed();
		});
	}

	private static Zombie createEngineer(
		final GameTestHelper helper,
		final int x,
		final int y,
		final int z
	) {
		Zombie engineer = helper.spawn(EntityType.ZOMBIE, x, y, z);
		engineer.setNoAi(true);
		engineer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		engineer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		engineer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		ZombieEngineerProfile.setEngineer(engineer, true);
		return engineer;
	}

	private static void prepareStoneArena(final GameTestHelper helper) {
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static String diagnostic(final Zombie engineer, final Villager target) {
		var config = ConfigManager.get();
		var rules = ((ServerLevel)engineer.level()).getGameRules();
		return "enabled=" + config.enabled
			+ ", zombieAi=" + config.zombieAiEnabled
			+ ", engineerSkills=" + config.engineerSkills
			+ ", engineerTnt=" + config.engineerTntSkill
			+ ", engineerFluid=" + config.engineerFluidSkills
			+ ", engineerIgnition=" + config.engineerIgnitionSkill
			+ ", mobGriefing=" + rules.get(GameRules.MOB_GRIEFING)
			+ ", tntExplodes=" + rules.get(GameRules.TNT_EXPLODES)
			+ ", marked=" + ZombieEngineerProfile.isEngineer(engineer)
			+ ", alive=" + engineer.isAlive()
			+ ", baby=" + engineer.isBaby()
			+ ", type=" + engineer.getType()
			+ ", target=" + (engineer.getTarget() == target)
			+ ", targetAlive=" + target.isAlive()
			+ ", distance2=" + engineer.distanceToSqr(target)
			+ ", utility=" + ZombieSpecialEquipment.utilityClassOf(engineer)
			+ ", air=" + ZombieAirAssault.isAirAssaultLoadout(engineer);
	}
}

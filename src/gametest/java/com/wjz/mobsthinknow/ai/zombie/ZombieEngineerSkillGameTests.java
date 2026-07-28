package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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

/** 工程兵技能的真实实体、方块、效果与装备恢复集成测试。 */
public final class ZombieEngineerSkillGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 80, padding = 8)
	public void demolitionSkillPlacesAndPrimesOwnedTntThenRestoresWeapon(final GameTestHelper helper) {
		for (int x = 0; x <= 8; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				for (int y = 1; y <= 3; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		Zombie engineer = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 1, 2);
		ItemStack originalWeapon = new ItemStack(Items.IRON_SWORD);
		engineer.setNoAi(true);
		engineer.setItemSlot(EquipmentSlot.MAINHAND, originalWeapon.copy());
		engineer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		engineer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		ZombieEngineerProfile.setEngineer(engineer, true);
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

	@GameTest(maxTicks = 60)
	public void fieldRepairFixesMostDamagedAlliedEquipment(final GameTestHelper helper) {
		Zombie engineer = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
		Zombie ally = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 4, 1, 2);
		ItemStack damagedHelmet = new ItemStack(Items.IRON_HELMET);
		damagedHelmet.setDamageValue(100);
		engineer.setNoAi(true);
		ally.setNoAi(true);
		target.setNoAi(true);
		engineer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		engineer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		ally.setItemSlot(EquipmentSlot.HEAD, damagedHelmet);
		ZombieEngineerProfile.setEngineer(engineer, true);
		engineer.setTarget(target);
		ally.setTarget(target);

		long repairsBefore = SmartZombieMetrics.snapshot().engineerRepairs();
		ZombieEngineerSkillGoal goal = new ZombieEngineerSkillGoal(
			engineer,
			(candidate, available) -> ZombieEngineerSkillGoal.Skill.FIELD_REPAIR,
			0
		);
		helper.assertTrue(
			goal.canUse(),
			"The engineer did not recognize damaged allied equipment: " + diagnostic(engineer, target)
		);
		goal.start();
		helper.onEachTick(() -> {
			engineer.clearFire();
			ally.clearFire();
			engineer.setTarget(target);
			ally.setTarget(target);
			if (goal.canContinueToUse()) {
				goal.tick();
			}
			if (ally.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() >= 100) {
				if (!goal.canContinueToUse()) {
					goal.stop();
					helper.fail("The field-repair state machine ended without repairing the helmet.");
				}
				return;
			}
			goal.stop();
			helper.assertTrue(
				engineer.getMainHandItem().is(Items.IRON_SWORD) && engineer.getOffhandItem().isEmpty(),
				"Field repair did not restore the engineer's original hands."
			);
			helper.assertTrue(
				SmartZombieMetrics.snapshot().engineerRepairs() > repairsBefore,
				"The completed repair was not recorded in diagnostics."
			);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 60)
	public void fortificationBuffsOnlyCurrentAllies(final GameTestHelper helper) {
		Zombie engineer = helper.spawn(EntityType.ZOMBIE, 1, 1, 2);
		Zombie ally = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
		Zombie outsider = helper.spawn(EntityType.ZOMBIE, 2, 1, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 4, 1, 2);
		engineer.setNoAi(true);
		ally.setNoAi(true);
		outsider.setNoAi(true);
		target.setNoAi(true);
		ZombieEngineerProfile.setEngineer(engineer, true);
		engineer.setTarget(target);
		ally.setTarget(target);
		outsider.setTarget(null);

		long fortificationsBefore = SmartZombieMetrics.snapshot().engineerFortifications();
		ZombieEngineerSkillGoal goal = new ZombieEngineerSkillGoal(
			engineer,
			(candidate, available) -> ZombieEngineerSkillGoal.Skill.FORTIFY_SQUAD,
			0
		);
		helper.assertTrue(
			goal.canUse(),
			"The engineer did not prepare its always-available fortification skill: " + diagnostic(engineer, target)
		);
		goal.start();
		helper.onEachTick(() -> {
			engineer.clearFire();
			ally.clearFire();
			outsider.clearFire();
			engineer.setTarget(target);
			ally.setTarget(target);
			outsider.setTarget(null);
			if (goal.canContinueToUse()) {
				goal.tick();
			}
			if (!engineer.hasEffect(MobEffects.RESISTANCE)) {
				if (!goal.canContinueToUse()) {
					goal.stop();
					helper.fail("The fortification state machine ended without applying resistance.");
				}
				return;
			}
			helper.assertTrue(ally.hasEffect(MobEffects.RESISTANCE), "A same-target ally missed fortification.");
			helper.assertTrue(!outsider.hasEffect(MobEffects.RESISTANCE), "An unrelated zombie received fortification.");
		goal.stop();
		helper.assertTrue(
			SmartZombieMetrics.snapshot().engineerFortifications() > fortificationsBefore,
			"The completed fortification was not recorded in diagnostics."
		);
		helper.succeed();
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private static String diagnostic(final Zombie engineer, final Villager target) {
		var config = ConfigManager.get();
		var rules = ((ServerLevel)engineer.level()).getGameRules();
		return "enabled=" + config.enabled
			+ ", zombieAi=" + config.zombieAiEnabled
			+ ", engineerSkills=" + config.engineerSkills
			+ ", engineerTnt=" + config.engineerTntSkill
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

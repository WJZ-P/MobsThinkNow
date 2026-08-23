package com.wjz.mobsthinknow.ai.utility;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieGroundNavigation;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadRole;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 对真实实体执行跨变种选举，避免“分类函数通过、运行时却没有心跳”的假兼容。 */
public final class OverworldUndeadSquadGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 120)
	public void everyOverworldUndeadVariantJoinsOneMixedSquad(final GameTestHelper helper) {
		List<Mob> members = List.of(
			helper.spawn(EntityType.ZOMBIE, 1, 2, 1),
			helper.spawn(EntityType.HUSK, 2, 2, 1),
			helper.spawn(EntityType.DROWNED, 3, 2, 1),
			helper.spawn(EntityType.ZOMBIE_VILLAGER, 4, 2, 1),
			helper.spawn(EntityType.SKELETON, 1, 2, 2),
			helper.spawn(EntityType.STRAY, 2, 2, 2),
			helper.spawn(EntityType.BOGGED, 3, 2, 2),
			helper.spawn(EntityType.PARCHED, 4, 2, 2)
		);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, 3, 2, 4);
		int[] intelligence = {2, 4, 5, 6, 3, 7, 8, 10};
		for (int index = 0; index < members.size(); index++) {
			configureMember(members.get(index), target, intelligence[index]);
		}
		target.setNoAi(true);
		target.setNoGravity(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0);
		target.setHealth(target.getMaxHealth());

		Mob expectedLeader = members.getLast();
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		int[] elapsed = {0};
		helper.onEachTick(() -> {
			elapsed[0]++;
			target.setHealth(target.getMaxHealth());
			for (Mob member : members) {
				member.setTarget(target);
			}

			ZombieSquadCoordinator.SquadView first = coordinator.viewFor(members.getFirst());
			if (first == null || first.memberCount() != members.size()) {
				if (elapsed[0] >= 110) {
					String detail = "tracked=" + coordinator.trackedMemberCount() + ",members=" + members.stream().map(member -> {
						ZombieSquadCoordinator.SquadView view = coordinator.viewFor(member);
						return member.getType() + "@" + member.blockPosition()
							+ "[target=" + (member.getTarget() == null ? "none" : member.getTarget().getType())
							+ ",los=" + member.getSensing().hasLineOfSight(target) + "]="
							+ (view == null ? "none" : view.squadId() + "/" + view.memberCount());
					}).collect(java.util.stream.Collectors.joining(","));
					helper.fail("Overworld family never converged into one squad: " + detail);
				}
				return;
			}
			helper.assertTrue(first.leaderEntityId() == expectedLeader.getId(), "The smartest variant was not elected leader.");
			for (Mob member : members) {
				ZombieSquadCoordinator.SquadView view = coordinator.viewFor(member);
				helper.assertTrue(view != null && view.squadId() == first.squadId(), "A variant received a different squad id.");
				SquadDirective directive = coordinator.directiveFor(member);
				helper.assertTrue(directive != null, "A supported variant did not receive a squad directive.");
				if (member instanceof AbstractSkeleton && member != expectedLeader) {
					helper.assertTrue(directive.role() == SquadRole.RANGED, "A skeleton variant lost its ranged squad role.");
				}
			}
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 180)
	public void lateSmarterVariantJoinsAndReplacesAnEstablishedLeader(final GameTestHelper helper) {
		List<Mob> initialMembers = List.of(
			helper.spawn(EntityType.ZOMBIE, 1, 2, 1),
			helper.spawn(EntityType.SKELETON, 2, 2, 1),
			helper.spawn(EntityType.HUSK, 3, 2, 1)
		);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, 3, 2, 4);
		configureMember(initialMembers.get(0), target, 2);
		configureMember(initialMembers.get(1), target, 5);
		configureMember(initialMembers.get(2), target, 7);
		target.setNoAi(true);
		target.setNoGravity(true);
		target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10_000.0);
		target.setHealth(target.getMaxHealth());

		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		Mob[] lateMember = {null};
		int[] elapsed = {0};
		int[] recruitedAt = {-1};
		helper.onEachTick(() -> {
			elapsed[0]++;
			target.setHealth(target.getMaxHealth());
			initialMembers.forEach(member -> member.setTarget(target));
			if (lateMember[0] == null) {
				ZombieSquadCoordinator.SquadView established = coordinator.viewFor(initialMembers.getFirst());
				if (established != null && established.memberCount() == initialMembers.size()) {
					lateMember[0] = helper.spawn(EntityType.PARCHED, 4, 2, 1);
					configureMember(lateMember[0], target, 10);
					recruitedAt[0] = elapsed[0];
				} else if (elapsed[0] >= 100) {
					helper.fail("Initial three-member squad was never established.");
				}
				return;
			}

			lateMember[0].setTarget(target);
			ZombieSquadCoordinator.SquadView joined = coordinator.viewFor(lateMember[0]);
			ZombieSquadCoordinator.SquadView existing = coordinator.viewFor(initialMembers.getFirst());
			if (joined != null
				&& existing != null
				&& joined.squadId() == existing.squadId()
				&& joined.memberCount() == 4
				&& joined.leaderEntityId() == lateMember[0].getId()) {
				helper.succeed();
				return;
			}
			if (elapsed[0] - recruitedAt[0] >= 60) {
				helper.fail("Late smarter Parched was not recruited and promoted by the existing squad.");
			}
		});
	}

	@GameTest
	public void drownedKeepsAmphibiousNavigationWhileGroundVariantsUseSmartNavigation(final GameTestHelper helper) {
		Zombie husk = helper.spawn(EntityType.HUSK, 1, 2, 1);
		Zombie villager = helper.spawn(EntityType.ZOMBIE_VILLAGER, 3, 2, 1);
		Drowned drowned = helper.spawn(EntityType.DROWNED, 5, 2, 1);
		helper.assertTrue(husk.getNavigation() instanceof SmartZombieGroundNavigation, "Husk did not receive smart ground navigation.");
		helper.assertTrue(
			villager.getNavigation() instanceof SmartZombieGroundNavigation,
			"Zombie villager did not receive smart ground navigation."
		);
		helper.assertTrue(
			drowned.getNavigation() instanceof AmphibiousPathNavigation,
			"Drowned amphibious navigation was overwritten by the ground-family integration."
		);
		helper.succeed();
	}

	@GameTest
	public void netherUndeadRemainOutsideTheOverworldSquadFamily(final GameTestHelper helper) {
		Mob piglin = helper.spawn(EntityType.ZOMBIFIED_PIGLIN, 1, 2, 1);
		Mob witherSkeleton = helper.spawn(EntityType.WITHER_SKELETON, 3, 2, 1);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 2, 1);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		coordinator.heartbeat(piglin, target, true, target.position(), helper.getLevel().getGameTime());
		coordinator.heartbeat(witherSkeleton, target, true, target.position(), helper.getLevel().getGameTime());
		ZombieSquadCoordinator.tickLevel(helper.getLevel());
		helper.assertTrue(coordinator.viewFor(piglin) == null, "Zombified piglin leaked into the overworld squad family.");
		helper.assertTrue(coordinator.viewFor(witherSkeleton) == null, "Wither skeleton leaked into the overworld squad family.");
		helper.succeed();
	}

	private static void configureMember(final Mob member, final Mob target, final int intelligence) {
		member.setInvulnerable(true);
		// Keep every real AI controller ticking while removing navigation beyond the small empty-test floor.
		member.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
		// 测试结构露天；统一戴头盔，避免避日 Goal 抢占地面僵尸的真实心跳路径。
		member.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		member.setTarget(target);
		if (member instanceof Zombie zombie) {
			ZombieIntelligence.set(zombie, intelligence);
		} else if (member instanceof AbstractSkeleton skeleton) {
			SkeletonIntelligence.set(skeleton, intelligence);
		}
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

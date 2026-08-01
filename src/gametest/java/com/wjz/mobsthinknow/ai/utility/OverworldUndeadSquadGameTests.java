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
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** 对真实实体执行跨变种选举，避免“分类函数通过、运行时却没有心跳”的假兼容。 */
public final class OverworldUndeadSquadGameTests implements CustomTestMethodInvoker {
	@GameTest(maxTicks = 100)
	public void everyOverworldUndeadVariantJoinsOneMixedSquad(final GameTestHelper helper) {
		List<Mob> members = List.of(
			helper.spawn(EntityType.ZOMBIE, 1, 2, 1),
			helper.spawn(EntityType.HUSK, 2, 2, 1),
			helper.spawn(EntityType.DROWNED, 3, 2, 1),
			helper.spawn(EntityType.ZOMBIE_VILLAGER, 4, 2, 1),
			helper.spawn(EntityType.SKELETON, 5, 2, 1),
			helper.spawn(EntityType.STRAY, 6, 2, 1),
			helper.spawn(EntityType.BOGGED, 7, 2, 1),
			helper.spawn(EntityType.PARCHED, 8, 2, 1)
		);
		Villager target = helper.spawn(EntityType.VILLAGER, 5, 2, 6);
		int[] intelligence = {2, 4, 5, 6, 3, 7, 8, 10};
		for (int index = 0; index < members.size(); index++) {
			Mob member = members.get(index);
			member.setInvulnerable(true);
			// 测试结构露天；统一戴头盔，避免避日 Goal 抢占地面僵尸的真实心跳路径。
			member.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			member.setTarget(target);
			if (member instanceof Zombie zombie) {
				ZombieIntelligence.set(zombie, intelligence[index]);
			} else if (member instanceof AbstractSkeleton skeleton) {
				SkeletonIntelligence.set(skeleton, intelligence[index]);
			}
		}
		target.setNoAi(true);
		target.setNoGravity(true);
		target.setInvulnerable(true);

		Mob expectedLeader = members.getLast();
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		helper.onEachTick(() -> {
			for (Mob member : members) {
				member.setTarget(target);
			}

			ZombieSquadCoordinator.SquadView first = coordinator.viewFor(members.getFirst());
			if (first == null || first.memberCount() != members.size()) {
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

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

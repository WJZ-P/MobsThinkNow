package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

/** 验证小队黑板、爆点预约、射击走廊与有限多目标分兵在真实服务器实体上的连接。 */
public final class SquadBattlefieldCoordinationGameTests implements CustomTestMethodInvoker {
	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 80, padding = 4)
	public void overlappingCreepersQueueBehindOneBlastReservation(final GameTestHelper helper) {
		Creeper first = helper.spawn(EntityType.CREEPER, 2, 2, 2);
		Creeper second = helper.spawn(EntityType.CREEPER, 3, 2, 3);
		Zombie witness = helper.spawn(EntityType.ZOMBIE, 8, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 12, 2, 2);
		prepare(first, target);
		prepare(second, target);
		prepare(witness, target);
		prepare(target);
		CreeperIntelligence.set(first, 10);
		CreeperIntelligence.set(second, 9);
		ZombieIntelligence.set(witness, 8);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			heartbeat(coordinator, List.of(first, second, witness), target, now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			if (coordinator.viewFor(first) == null) {
				return;
			}

			Vec3 predictedCenter = target.position().add(0.75, 0.0, 0.0);
			helper.assertTrue(
				coordinator.tryReserveBlast(first, target, predictedCenter, false),
				"The first squad creeper failed to acquire an empty blast reservation."
			);
			helper.assertTrue(
				!coordinator.tryReserveBlast(second, target, predictedCenter, false),
				"A second ordinary creeper committed an overlapping fuse against the same target."
			);
			helper.assertTrue(
				coordinator.blastQueueStagingPointFor(second, target, predictedCenter) != null,
				"The queued creeper received no flank staging point outside the active reservation."
			);

			first.setSwellDir(1);
			coordinator.heartbeat(first, target, true, target.position(), now);
			ZombieSquadCoordinator.SquadBlastThreat threat = coordinator.nearestBlastThreatFor(witness);
			helper.assertTrue(threat != null && threat.creeper() == first, "The active fuse was not indexed for allies.");
			helper.assertTrue(
				threat.center().distanceToSqr(predictedCenter) < 1.0E-6,
				"Allies evacuated from the creeper's feet instead of its reserved predicted blast center."
			);
			coordinator.releaseBlastReservation(first);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 180, padding = 4)
	public void groundAllySidestepsAReservedSkeletonFiringLane(final GameTestHelper helper) {
		Skeleton shooter = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		Zombie blocker = helper.spawn(EntityType.ZOMBIE, 7, 2, 2);
		Zombie wing = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, 12, 2, 2);
		prepare(shooter, target);
		prepare(blocker, target);
		prepare(wing, target);
		prepare(target);
		shooter.setOnGround(true);
		blocker.setOnGround(true);
		SkeletonIntelligence.set(shooter, 10);
		ZombieIntelligence.set(blocker, 8);
		ZombieIntelligence.set(wing, 7);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			heartbeat(coordinator, List.of(shooter, blocker, wing), target, now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(shooter);
			if (view == null || view.state() != SquadState.ENGAGING) {
				return;
			}

			coordinator.reserveFiringLane(shooter, target, false);
			helper.assertTrue(
				coordinator.blockingFiringLaneFor(blocker) != null,
				"A squadmate inside the drawn bow corridor did not see the short firing-lane reservation."
			);
			SquadFiringLaneClearGoal clearGoal = new SquadFiringLaneClearGoal(blocker, 1.15);
			helper.assertTrue(clearGoal.canUse(), "The lower-priority ground ally did not yield to its squad shooter.");
			clearGoal.start();
			Vec3 destination = clearGoal.destination();
			helper.assertTrue(destination != null, "Firing-lane yielding found no reachable side step.");
			helper.assertTrue(
				Math.abs(destination.z - blocker.getZ()) > 0.5,
				"The firing-lane clear step stayed on the original shot axis."
			);
			clearGoal.stop();
			coordinator.releaseFiringLane(shooter);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 180, padding = 4)
	public void witnessedAttackerReceivesOnlyABoundedResponseTeam(final GameTestHelper helper) {
		List<Zombie> members = List.of(
			helper.spawn(EntityType.ZOMBIE, 2, 2, 2),
			helper.spawn(EntityType.ZOMBIE, 3, 2, 3),
			helper.spawn(EntityType.ZOMBIE, 4, 2, 2),
			helper.spawn(EntityType.ZOMBIE, 5, 2, 3),
			helper.spawn(EntityType.ZOMBIE, 6, 2, 2)
		);
		Villager primary = helper.spawn(EntityType.VILLAGER, 14, 2, 2);
		IronGolem attacker = helper.spawn(EntityType.IRON_GOLEM, 8, 2, 6);
		prepare(primary);
		prepare(attacker);
		for (int index = 0; index < members.size(); index++) {
			Zombie member = members.get(index);
			prepare(member, primary);
			ZombieIntelligence.set(member, 10 - index);
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		boolean[] reported = {false};

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			for (Zombie member : members) {
				LivingEntity observed = member.getTarget() == null ? primary : member.getTarget();
				coordinator.heartbeat(member, observed, true, observed.position(), now);
			}
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(members.getFirst());
			if (view == null) {
				return;
			}
			if (view.state() != SquadState.ENGAGING) {
				Zombie contact = members.getLast();
				primary.snapTo(contact.getX() + 1.5, contact.getY(), contact.getZ(), 0.0F, 0.0F);
				return;
			}
			if (!reported[0]) {
				reported[0] = true;
				ZombieSquadCoordinator.onSquadMemberAttacked(members.getLast(), attacker);
				return;
			}

			long responding = members.stream()
				.filter(member -> coordinator.assignedTargetFor(member) == attacker)
				.count();
			if (responding == 0) {
				return;
			}
			helper.assertTrue(responding <= 2, "More than 40% of a five-member squad abandoned the primary target.");
			helper.assertTrue(
				members.size() - responding >= 3,
				"The squad did not retain its guaranteed 60% pressure group on the primary target."
			);
			helper.succeed();
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 190, padding = 4)
	public void deployedMembersShareOneReadinessBarrierAndCommitTick(final GameTestHelper helper) {
		List<Zombie> members = List.of(
			helper.spawn(EntityType.ZOMBIE, 2, 2, 2),
			helper.spawn(EntityType.ZOMBIE, 3, 2, 3),
			helper.spawn(EntityType.ZOMBIE, 4, 2, 2)
		);
		Villager target = helper.spawn(EntityType.VILLAGER, 14, 2, 2);
		prepare(target);
		for (int index = 0; index < members.size(); index++) {
			Zombie member = members.get(index);
			prepare(member, target);
			ZombieIntelligence.set(member, 10 - index);
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		long[] lastVisibleAt = {helper.getLevel().getGameTime()};
		long[] sharedCommitAt = {Long.MAX_VALUE};
		int[] sharedCombatEpoch = {-1};

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			for (Zombie member : members) {
				// 部署点可能靠近目标；此时故意只上报缓存视野，避免紧急接敌旁路抢占本测试的同步口令。
				boolean visibleOutsideEmergencyRange = member.distanceToSqr(target) > 36.0;
				if (visibleOutsideEmergencyRange) {
					lastVisibleAt[0] = now;
				}
				coordinator.heartbeat(
					member,
					target,
					visibleOutsideEmergencyRange,
					target.position(),
					lastVisibleAt[0]
				);
			}
			ZombieSquadCoordinator.tickLevel(helper.getLevel());

			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(members.getFirst());
			if (view == null) {
				return;
			}
			for (Zombie member : members) {
				SquadDirective directive = coordinator.directiveFor(member);
				if (directive != null && directive.destination() != null && directive.state() != SquadState.ENGAGING) {
					Vec3 destination = directive.destination();
					member.snapTo(destination.x, destination.y, destination.z, member.getYRot(), member.getXRot());
				}
			}

			if (view.state() == SquadState.DEPLOYING && view.combatExecuteAt() != Long.MAX_VALUE) {
				if (sharedCommitAt[0] == Long.MAX_VALUE) {
					sharedCommitAt[0] = view.combatExecuteAt();
					sharedCombatEpoch[0] = view.combatEpoch();
				}
				helper.assertTrue(now < sharedCommitAt[0], "The squad engaged before its shared execution tick.");
				helper.assertTrue(
					view.combatBeat() == SquadCombatBeat.PREPARE || view.combatBeat() == SquadCombatBeat.SUPPRESS,
					"Deployment exposed an invalid pre-commit combat beat."
				);
				for (Zombie member : members) {
					SquadDirective directive = coordinator.directiveFor(member);
					helper.assertTrue(directive != null, "A deployed member lost its squad directive.");
					helper.assertTrue(
						directive.combatExecuteAt() == sharedCommitAt[0]
							&& directive.combatEpoch() == sharedCombatEpoch[0],
						"Members received different combat timelines."
					);
				}
				return;
			}

			if (view.state() == SquadState.ENGAGING) {
				helper.assertTrue(sharedCommitAt[0] != Long.MAX_VALUE, "Engagement skipped the readiness barrier.");
				helper.assertTrue(now >= sharedCommitAt[0], "Engagement began before the committed tick.");
				helper.assertTrue(view.combatBeat() == SquadCombatBeat.COMMIT, "The first assault tick was not COMMIT.");
				helper.assertTrue(
					view.combatEpoch() == sharedCombatEpoch[0] && view.combatExecuteAt() == sharedCommitAt[0],
					"Entering combat replaced the already announced timeline."
				);
				helper.succeed();
			}
		});
	}

	@GameTest(structure = "mobsthinknow-gametest:air_assault_arena", maxTicks = 320, padding = 4)
	public void secondWaveRegroupsAroundMovedTargetInsteadOfOriginalBriefing(final GameTestHelper helper) {
		List<Zombie> members = List.of(
			helper.spawn(EntityType.ZOMBIE, 2, 2, 2),
			helper.spawn(EntityType.ZOMBIE, 3, 2, 3),
			helper.spawn(EntityType.ZOMBIE, 4, 2, 2)
		);
		Villager target = helper.spawn(EntityType.VILLAGER, 14, 2, 2);
		prepare(target);
		for (int index = 0; index < members.size(); index++) {
			Zombie member = members.get(index);
			prepare(member, target);
			ZombieIntelligence.set(member, 10 - index);
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		Map<Integer, Vec3> firstDeployment = new HashMap<>();
		boolean[] targetMoved = {false};

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			heartbeat(coordinator, members, target, now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(members.getFirst());
			if (view == null) {
				return;
			}

			if (view.state() == SquadState.DEPLOYING && firstDeployment.isEmpty()) {
				for (Zombie member : members) {
					SquadDirective directive = coordinator.directiveFor(member);
					helper.assertTrue(
						directive != null && directive.destination() != null,
						"Initial deployment did not assign every member a formation destination."
					);
					firstDeployment.put(member.getId(), directive.destination());
				}
			}

			if (view.state() != SquadState.ENGAGING) {
				for (Zombie member : members) {
					SquadDirective directive = coordinator.directiveFor(member);
					if (directive != null && directive.destination() != null) {
						Vec3 destination = directive.destination();
						member.snapTo(destination.x, destination.y, destination.z, member.getYRot(), member.getXRot());
					}
				}
				return;
			}

			if (!targetMoved[0]) {
				helper.assertTrue(!firstDeployment.isEmpty(), "Engagement skipped the initial deployment snapshot.");
				targetMoved[0] = true;
				target.snapTo(target.getX(), target.getY(), target.getZ() + 8.0, target.getYRot(), target.getXRot());
				return;
			}
			if (view.combatBeat() != SquadCombatBeat.RESET) {
				return;
			}

			for (Zombie member : members) {
				SquadDirective directive = coordinator.directiveFor(member);
				Vec3 oldDestination = firstDeployment.get(member.getId());
				helper.assertTrue(
					directive != null && directive.holdsCombatFormation() && directive.destination() != null,
					"A second-wave RESET member did not receive a fresh formation order."
				);
				helper.assertTrue(
					horizontalDistance(oldDestination, directive.destination()) > 5.0,
					"A member returned to the first briefing position after the target moved eight blocks."
				);
				helper.assertTrue(
					horizontalDistance(target.position(), directive.destination()) < 12.0,
					"A refreshed formation destination was detached from the target's current battlefield position."
				);
			}
			helper.succeed();
		});
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private static void heartbeat(
		final ZombieSquadCoordinator coordinator,
		final List<? extends Mob> members,
		final LivingEntity target,
		final long now
	) {
		for (Mob member : members) {
			coordinator.heartbeat(member, target, true, target.position(), now);
		}
	}

	private static void prepare(final Mob mob, final LivingEntity target) {
		mob.setNoAi(true);
		mob.setTarget(target);
	}

	private static void prepare(final Mob mob) {
		mob.setNoAi(true);
	}

	private static double horizontalDistance(final Vec3 first, final Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}

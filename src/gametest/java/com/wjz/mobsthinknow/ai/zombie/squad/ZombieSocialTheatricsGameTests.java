package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyActionAccess;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.lang.reflect.Method;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** 从真实 Zombie 实体数据验证战术会议、部署和首领继任动作会以低频同步状态发布。 */
public final class ZombieSocialTheatricsGameTests implements CustomTestMethodInvoker {
	private static final long SQUAD_ID = 17L;

	@GameTest(maxTicks = 20)
	public void tacticalBriefingPublishesRouteObjectionsCorrectionsAndFinalOrder(final GameTestHelper helper) {
		Zombie leader = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie pressurer = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie left = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		Zombie right = helper.spawn(EntityType.ZOMBIE, 4, 2, 1);
		List<SquadTheatrics.RoleMember> members = List.of(
			member(leader, SquadRole.LEADER, SquadRouteOutcome.UNASSESSED),
			member(pressurer, SquadRole.PRESSURER, SquadRouteOutcome.UNASSESSED),
			member(left, SquadRole.FLANK_LEFT, SquadRouteOutcome.REROUTED),
			member(right, SquadRole.FLANK_RIGHT, SquadRouteOutcome.BLOCKED)
		);
		SquadTheatrics theatrics = new SquadTheatrics();
		MobsThinkNowConfig config = bodyLanguageOnlyConfig();
		long now = helper.getLevel().getGameTime();
		SmartZombieMetrics.Snapshot metricsBefore = SmartZombieMetrics.snapshot();

		publishAndAssert(theatrics, helper, SquadState.FORMING, 0L, leader, members, config, now,
			leader, ZombieBodyAction.CALL_TO_MEETING, "Leader did not summon the forming squad.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 0L, leader, members, config, now,
			leader, ZombieBodyAction.SURVEY_MEMBERS, "Leader did not survey the gathered members.");
		clearActions(members, now);
		tickAt(theatrics, helper, SquadState.BRIEFING, 8L, leader, members, config, now);
		helper.assertTrue(
			members.stream().skip(1).map(SquadTheatrics.RoleMember::mob)
				.anyMatch(mob -> actionOf((Zombie)mob) == ZombieBodyAction.CONFER),
			"No follower performed the short conference gesture."
		);
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 16L, leader, members, config, now,
			leader, ZombieBodyAction.COMMAND_LEFT, "Leader did not point out the left-flank order.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 26L, leader, members, config, now,
			left, ZombieBodyAction.SHAKE_HEAD, "Rerouted left flanker did not object to the original path.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 33L, leader, members, config, now,
			leader, ZombieBodyAction.COMMAND_LEFT, "Leader did not publish the revised left route.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 38L, leader, members, config, now,
			leader, ZombieBodyAction.COMMAND_RIGHT, "Leader did not point out the right-flank order.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 48L, leader, members, config, now,
			right, ZombieBodyAction.SHAKE_HEAD, "Blocked right flanker did not reject the unusable path.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 55L, leader, members, config, now,
			leader, ZombieBodyAction.COMMAND, "Leader did not replace the blocked role with a fallback order.");
		publishAndAssert(theatrics, helper, SquadState.BRIEFING, 60L, leader, members, config, now,
			leader, ZombieBodyAction.COMMAND, "Leader did not close the briefing with a final order.");

		SmartZombieMetrics.Snapshot metricsAfter = SmartZombieMetrics.snapshot();
		helper.assertTrue(
			metricsAfter.leaderSocialGestures() - metricsBefore.leaderSocialGestures() == 7L,
			"Leader social gesture metrics did not count call, plan, corrections and final order."
		);
		helper.assertTrue(
			metricsAfter.memberSocialGestures() - metricsBefore.memberSocialGestures() == 3L,
			"Member social gesture metrics did not count conference and two route reports."
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void successionPublishesSearchSaluteAcceptanceAndCommand(final GameTestHelper helper) {
		Zombie leader = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie first = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie second = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		List<SquadTheatrics.RoleMember> members = List.of(
			member(leader, SquadRole.LEADER, SquadRouteOutcome.UNASSESSED),
			member(first, SquadRole.PRESSURER, SquadRouteOutcome.UNASSESSED),
			member(second, SquadRole.SUPPORT, SquadRouteOutcome.UNASSESSED)
		);
		SquadTheatrics theatrics = new SquadTheatrics();
		MobsThinkNowConfig config = bodyLanguageOnlyConfig();
		long now = helper.getLevel().getGameTime();

		clearActions(members, now);
		tickAt(theatrics, helper, SquadState.REORGANIZING, 0L, leader, members, config, now);
		helper.assertTrue(
			actionOf(first) == ZombieBodyAction.SUCCESSION_LOOK_AROUND
				|| actionOf(second) == ZombieBodyAction.SUCCESSION_LOOK_AROUND,
			"Followers did not look for the replacement leader."
		);
		publishAndAssert(theatrics, helper, SquadState.REORGANIZING, 14L, leader, members, config, now,
			leader, ZombieBodyAction.SUCCESSION_SALUTE, "Replacement leader did not claim command.");
		clearActions(members, now);
		tickAt(theatrics, helper, SquadState.REORGANIZING, 26L, leader, members, config, now);
		helper.assertTrue(
			actionOf(first) == ZombieBodyAction.NOD || actionOf(second) == ZombieBodyAction.NOD,
			"Followers did not accept the replacement leader."
		);
		publishAndAssert(theatrics, helper, SquadState.REORGANIZING, 43L, leader, members, config, now,
			leader, ZombieBodyAction.COMMAND, "Replacement leader did not resume command.");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void deploymentPublishesAdvanceOrderAndStaggeredAcknowledgements(final GameTestHelper helper) {
		Zombie leader = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie first = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie second = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		List<SquadTheatrics.RoleMember> members = List.of(
			member(leader, SquadRole.LEADER, SquadRouteOutcome.UNASSESSED),
			member(first, SquadRole.PRESSURER, SquadRouteOutcome.UNASSESSED),
			member(second, SquadRole.SUPPORT, SquadRouteOutcome.UNASSESSED)
		);
		SquadTheatrics theatrics = new SquadTheatrics();
		MobsThinkNowConfig config = bodyLanguageOnlyConfig();
		long now = helper.getLevel().getGameTime();

		publishAndAssert(theatrics, helper, SquadState.DEPLOYING, 0L, leader, members, config, now,
			leader, ZombieBodyAction.ADVANCE_ORDER, "Leader did not issue the deployment gesture.");
		clearActions(members, now);
		tickAt(theatrics, helper, SquadState.DEPLOYING, 2L, leader, members, config, now);
		helper.assertTrue(
			actionOf(first) == ZombieBodyAction.ACKNOWLEDGE || actionOf(second) == ZombieBodyAction.ACKNOWLEDGE,
			"The first staggered deployment response was not synchronized."
		);
		clearActions(members, now);
		tickAt(theatrics, helper, SquadState.DEPLOYING, 4L, leader, members, config, now);
		helper.assertTrue(
			actionOf(first) == ZombieBodyAction.ACKNOWLEDGE || actionOf(second) == ZombieBodyAction.ACKNOWLEDGE,
			"The second staggered deployment response was not synchronized."
		);
		helper.succeed();
	}

	private static SquadTheatrics.RoleMember member(
		final Zombie zombie,
		final SquadRole role,
		final SquadRouteOutcome outcome
	) {
		return new SquadTheatrics.RoleMember(
			zombie,
			role,
			role,
			outcome,
			10,
			SquadSocialChoreography.IdleStyle.NONE
		);
	}

	private static MobsThinkNowConfig bodyLanguageOnlyConfig() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.zombieBodyLanguage = true;
		config.squadVisualEffects = false;
		config.squadRoleNameTags = false;
		return config;
	}

	private static void publishAndAssert(
		final SquadTheatrics theatrics,
		final GameTestHelper helper,
		final SquadState state,
		final long phase,
		final Zombie leader,
		final List<SquadTheatrics.RoleMember> members,
		final MobsThinkNowConfig config,
		final long now,
		final Zombie actor,
		final ZombieBodyAction expected,
		final String message
	) {
		clearActions(members, now);
		tickAt(theatrics, helper, state, phase, leader, members, config, now);
		assertAction(helper, actor, expected, message);
	}

	private static void clearActions(final List<SquadTheatrics.RoleMember> members, final long now) {
		for (SquadTheatrics.RoleMember member : members) {
			((ZombieBodyActionAccess)member.mob()).mobsthinknow$setBodyAction(ZombieBodyAction.NONE, now);
		}
	}

	private static void tickAt(
		final SquadTheatrics theatrics,
		final GameTestHelper helper,
		final SquadState state,
		final long phase,
		final Zombie leader,
		final List<SquadTheatrics.RoleMember> members,
		final MobsThinkNowConfig config,
		final long now
	) {
		theatrics.tickSquad(helper.getLevel(), SQUAD_ID, state, now - phase, leader, members, config, now);
	}

	private static ZombieBodyAction actionOf(final Zombie zombie) {
		return ((ZombieBodyActionAccess)zombie).mobsthinknow$getBodyAction();
	}

	private static void assertAction(
		final GameTestHelper helper,
		final Zombie zombie,
		final ZombieBodyAction expected,
		final String message
	) {
		helper.assertTrue(actionOf(zombie) == expected, message);
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}

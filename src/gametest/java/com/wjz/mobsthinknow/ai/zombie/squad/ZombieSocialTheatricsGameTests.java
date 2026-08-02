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

/** 从真实 Zombie 实体数据验证会议和部署动作会发布为低频同步状态。 */
public final class ZombieSocialTheatricsGameTests implements CustomTestMethodInvoker {
	private static final long SQUAD_ID = 17L;

	@GameTest(maxTicks = 20)
	public void meetingPublishesCallSurveyDirectionalOrdersAndMemberResponses(final GameTestHelper helper) {
		Zombie leader = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie pressurer = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie left = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		Zombie right = helper.spawn(EntityType.ZOMBIE, 4, 2, 1);
		List<SquadTheatrics.RoleMember> members = List.of(
			new SquadTheatrics.RoleMember(leader, SquadRole.LEADER),
			new SquadTheatrics.RoleMember(pressurer, SquadRole.PRESSURER),
			new SquadTheatrics.RoleMember(left, SquadRole.FLANK_LEFT),
			new SquadTheatrics.RoleMember(right, SquadRole.FLANK_RIGHT)
		);
		SquadTheatrics theatrics = new SquadTheatrics();
		MobsThinkNowConfig config = bodyLanguageOnlyConfig();
		long now = helper.getLevel().getGameTime();
		SmartZombieMetrics.Snapshot metricsBefore = SmartZombieMetrics.snapshot();

		tickAt(theatrics, helper, SquadState.FORMING, 0L, leader, members, config, now);
		assertAction(helper, leader, ZombieBodyAction.CALL_TO_MEETING, "Leader did not summon the forming squad.");
		tickAt(theatrics, helper, SquadState.BRIEFING, 0L, leader, members, config, now);
		assertAction(helper, leader, ZombieBodyAction.SURVEY_MEMBERS, "Leader did not survey the gathered members.");
		tickAt(theatrics, helper, SquadState.BRIEFING, 3L, leader, members, config, now);
		helper.assertTrue(
			members.stream().skip(1).map(SquadTheatrics.RoleMember::mob)
				.anyMatch(mob -> actionOf((Zombie)mob) == ZombieBodyAction.CONFER),
			"No follower performed the short conference gesture."
		);
		tickAt(theatrics, helper, SquadState.BRIEFING, 6L, leader, members, config, now);
		assertAction(helper, leader, ZombieBodyAction.COMMAND_LEFT, "Leader did not point out the left-flank order.");
		tickAt(theatrics, helper, SquadState.BRIEFING, 10L, leader, members, config, now);
		assertAction(helper, left, ZombieBodyAction.NOD, "Left flanker did not nod to its explicit order.");
		tickAt(theatrics, helper, SquadState.BRIEFING, 13L, leader, members, config, now);
		helper.assertTrue(
			members.stream().skip(1).map(SquadTheatrics.RoleMember::mob)
				.anyMatch(mob -> actionOf((Zombie)mob) == ZombieBodyAction.SHAKE_HEAD),
			"No follower questioned the first plan with a head shake."
		);
		tickAt(theatrics, helper, SquadState.BRIEFING, 16L, leader, members, config, now);
		assertAction(helper, leader, ZombieBodyAction.COMMAND_RIGHT, "Leader did not redirect attention to the right flank.");
		tickAt(theatrics, helper, SquadState.BRIEFING, 20L, leader, members, config, now);
		assertAction(helper, right, ZombieBodyAction.ACKNOWLEDGE, "Right flanker did not acknowledge its order.");
		SmartZombieMetrics.Snapshot metricsAfter = SmartZombieMetrics.snapshot();
		helper.assertTrue(
			metricsAfter.leaderSocialGestures() - metricsBefore.leaderSocialGestures() == 4L,
			"Leader social gesture metrics did not count the four published orders."
		);
		helper.assertTrue(
			metricsAfter.memberSocialGestures() - metricsBefore.memberSocialGestures() == 4L,
			"Member social gesture metrics did not count the four published responses."
		);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void deploymentPublishesAdvanceOrderAndStaggeredAcknowledgements(final GameTestHelper helper) {
		Zombie leader = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
		Zombie first = helper.spawn(EntityType.ZOMBIE, 2, 2, 1);
		Zombie second = helper.spawn(EntityType.ZOMBIE, 3, 2, 1);
		List<SquadTheatrics.RoleMember> members = List.of(
			new SquadTheatrics.RoleMember(leader, SquadRole.LEADER),
			new SquadTheatrics.RoleMember(first, SquadRole.PRESSURER),
			new SquadTheatrics.RoleMember(second, SquadRole.SUPPORT)
		);
		SquadTheatrics theatrics = new SquadTheatrics();
		MobsThinkNowConfig config = bodyLanguageOnlyConfig();
		long now = helper.getLevel().getGameTime();

		tickAt(theatrics, helper, SquadState.DEPLOYING, 0L, leader, members, config, now);
		assertAction(helper, leader, ZombieBodyAction.ADVANCE_ORDER, "Leader did not issue the deployment gesture.");
		tickAt(theatrics, helper, SquadState.DEPLOYING, 2L, leader, members, config, now);
		helper.assertTrue(
			actionOf(first) == ZombieBodyAction.ACKNOWLEDGE || actionOf(second) == ZombieBodyAction.ACKNOWLEDGE,
			"The first staggered deployment response was not synchronized."
		);
		tickAt(theatrics, helper, SquadState.DEPLOYING, 4L, leader, members, config, now);
		helper.assertTrue(
			actionOf(first) == ZombieBodyAction.ACKNOWLEDGE && actionOf(second) == ZombieBodyAction.ACKNOWLEDGE,
			"Both followers did not receive one staggered acknowledgement by the second response tick."
		);
		helper.succeed();
	}

	private static MobsThinkNowConfig bodyLanguageOnlyConfig() {
		MobsThinkNowConfig config = new MobsThinkNowConfig();
		config.zombieBodyLanguage = true;
		config.squadVisualEffects = false;
		config.squadRoleNameTags = false;
		return config;
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

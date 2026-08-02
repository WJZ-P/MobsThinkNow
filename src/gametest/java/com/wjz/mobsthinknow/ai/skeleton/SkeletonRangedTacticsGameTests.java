package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath.MovementMode;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonBowAttackGoal.CoverPhase;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadRole;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class SkeletonRangedTacticsGameTests implements CustomTestMethodInvoker {
	@GameTest
	public void regularSkeletonReceivesSmartBowGoal(final GameTestHelper helper) {
		long installedBefore = SmartSkeletonMetrics.snapshot().installedGoals();
		long emergencyInstalledBefore = SmartSkeletonMetrics.snapshot().installedEmergencyGoals();
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		// helper.spawn 不执行自然生成的默认装备流程；显式模拟 finalizeSpawn 末尾的武器重评。
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.reassessWeaponGoal();

		helper.assertTrue(skeleton.isAlive(), "The integration-test skeleton did not spawn.");
		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().installedGoals() > installedBefore,
			"Creating a regular skeleton did not install the smart bow goal."
		);
		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().installedEmergencyGoals() > emergencyInstalledBefore,
			"Creating a regular bow skeleton did not install its emergency disengage goal."
		);
		helper.succeed();
	}

	@GameTest
	public void closePlayerCancelsBowDrawUntilSafeRange(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		skeleton.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		// 智力 5 的安全线约为 8.85 格，保留旧用例对九格迟滞区边界的验证。
		SkeletonIntelligence.set(skeleton, 5);

		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		player.snapTo(skeleton.getX() + 3.0, skeleton.getY(), skeleton.getZ(), 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "The close-player fixture was not added.");
		skeleton.setTarget(player);
		helper.assertTrue(skeleton.getTarget() == player, "The survival player was not accepted as a target.");

		skeleton.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(skeleton.isUsingItem(), "The bow-draw fixture did not start.");
		long disengagesBefore = SmartSkeletonMetrics.snapshot().emergencyDisengages();
		SkeletonEmergencyDisengageGoal goal = new SkeletonEmergencyDisengageGoal(skeleton);
		helper.assertTrue(goal.canUse(), "A player three blocks away did not trigger emergency disengage.");
		goal.start();
		helper.assertTrue(!skeleton.isUsingItem(), "Emergency disengage did not cancel the current bow draw.");
		skeleton.setOnGround(true);
		goal.tick();
		helper.assertTrue(
			isFacingAwayFrom(skeleton, player),
			"Full escape did not turn the skeleton's head and body toward its escape route."
		);
		helper.assertTrue(goal.canContinueToUse(), "The disengage ended before reaching its safe threshold.");
		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().emergencyDisengages() > disengagesBefore,
			"Starting the emergency goal did not record its state transition."
		);

		player.snapTo(skeleton.getX() + 9.0, skeleton.getY(), skeleton.getZ(), 0.0F, 0.0F);
		helper.assertTrue(!goal.canContinueToUse(), "The disengage still owned movement at the nine-block safe line.");
		goal.stop();
		player.discard();
		helper.succeed();
	}

	@GameTest
	public void closeIronGolemTriggersTheSameFullDisengageAsAPlayer(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, 5, 2, 2);
		skeleton.setNoAi(true);
		golem.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		SkeletonIntelligence.set(skeleton, 7);
		skeleton.setTarget(golem);
		skeleton.startUsingItem(InteractionHand.MAIN_HAND);
		skeleton.setXRot(55.0F); // 回归夹具：旧实现会继续把脚底路径节点当作凝视点。

		SkeletonEmergencyDisengageGoal goal = new SkeletonEmergencyDisengageGoal(skeleton);
		helper.assertTrue(goal.canUse(), "An iron golem three blocks away did not trigger full disengagement.");
		goal.start();
		helper.assertTrue(!skeleton.isUsingItem(), "Disengaging from an iron golem did not lower the bow.");
		skeleton.setOnGround(true);
		goal.tick();
		skeleton.getLookControl().tick();
		helper.assertTrue(
			isFacingAwayFrom(skeleton, golem),
			"The skeleton did not face its escape route when an iron golem closed in."
		);
		helper.assertTrue(
			Math.abs(skeleton.getXRot()) <= 1.0F,
			"Full escape kept looking down at the path node instead of looking horizontally ahead."
		);
		helper.assertTrue(goal.canContinueToUse(), "The golem disengage ended before reaching safe range.");
		goal.stop();
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void skeletonAndZombiesFormOneMixedSquad(final GameTestHelper helper) {
		Zombie leader = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
		Zombie wing = helper.spawn(EntityType.ZOMBIE, 4, 2, 2);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 3, 2, 4);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, 10, 2, 3);
		leader.setNoAi(true);
		wing.setNoAi(true);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		ZombieIntelligence.set(leader, 10);
		ZombieIntelligence.set(wing, 8);
		SkeletonIntelligence.set(skeleton, 5);
		leader.setTarget(target);
		wing.setTarget(target);
		skeleton.setTarget(target);

		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());
		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(leader, target, true, target.position(), now);
			coordinator.heartbeat(wing, target, true, target.position(), now);
			coordinator.heartbeat(skeleton, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			ZombieSquadCoordinator.SquadView view = coordinator.viewFor(skeleton);
			if (view == null) {
				return;
			}
			helper.assertTrue(view.memberCount() == 3, "The mixed squad did not contain all three members.");
			helper.assertTrue(view.leaderEntityId() == leader.getId(), "The highest-intelligence zombie was not elected leader.");
			SquadDirective directive = coordinator.directiveFor(skeleton);
			helper.assertTrue(directive != null, "The skeleton received no mixed-squad directive.");
			helper.assertTrue(directive.role() == SquadRole.RANGED, "The skeleton was assigned a melee squad role.");
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 80)
	public void squadmateBlocksArrowCorridorAndFireworkDangerZone(final GameTestHelper helper) {
		Skeleton shooter = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		Zombie blocker = helper.spawn(EntityType.ZOMBIE, 7, 2, 2);
		Zombie wing = helper.spawn(EntityType.ZOMBIE, 3, 2, 5);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, 12, 2, 2);
		shooter.setNoAi(true);
		blocker.setNoAi(true);
		wing.setNoAi(true);
		target.setNoAi(true);
		SkeletonIntelligence.set(shooter, 10);
		ZombieIntelligence.set(blocker, 8);
		ZombieIntelligence.set(wing, 7);
		shooter.setTarget(target);
		blocker.setTarget(target);
		wing.setTarget(target);
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(helper.getLevel());

		helper.onEachTick(() -> {
			long now = helper.getLevel().getGameTime();
			coordinator.heartbeat(shooter, target, true, target.position(), now);
			coordinator.heartbeat(blocker, target, true, target.position(), now);
			coordinator.heartbeat(wing, target, true, target.position(), now);
			ZombieSquadCoordinator.tickLevel(helper.getLevel());
			if (!ZombieSquadCoordinator.areSquadmates(shooter, blocker)) {
				return;
			}

			helper.assertTrue(
				!SkeletonShotSafety.hasClearShot(shooter, target, false),
				"A squadmate standing inside the arrow corridor did not hold the shot."
			);
			blocker.snapTo(target.getX(), target.getY(), target.getZ() + 3.0, 0.0F, 0.0F);
			helper.assertTrue(
				SkeletonShotSafety.hasClearShot(shooter, target, false),
				"An off-axis squadmate incorrectly blocked a normal arrow."
			);
			helper.assertTrue(
				!SkeletonShotSafety.hasClearShot(shooter, target, true),
				"A squadmate inside the intended firework blast area did not hold the explosive shot."
			);
			helper.succeed();
		});
	}

	@GameTest(
		structure = "mobsthinknow-gametest:skeleton_cover_arena",
		maxTicks = 100,
		skyAccess = true,
		padding = 8
	)
	public void explosiveCrossbowLoadsAndFiresARealRocket(final GameTestHelper helper) {
		buildCoverLane(helper);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		IronGolem target = helper.spawn(EntityType.IRON_GOLEM, 12, 2, 2);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		SkeletonIntelligence.set(skeleton, 10);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
		skeleton.setItemSlot(EquipmentSlot.OFFHAND, SkeletonCrossbowLoadout.explosiveRockets(3));
		skeleton.setTarget(target);
		skeleton.getSensing().tick();
		helper.assertTrue(skeleton.getSensing().hasLineOfSight(target), "The crossbow fixture had no clear firing line.");
		long fireworkShotsBefore = SmartSkeletonMetrics.snapshot().fireworkCrossbowShots();
		SmartSkeletonCrossbowAttackGoal goal = new SmartSkeletonCrossbowAttackGoal(skeleton);
		helper.assertTrue(goal.canUse(), "A crossbow skeleton with a live target could not start its goal.");
		goal.start();
		int[] elapsed = {0};

		helper.onEachTick(() -> {
			elapsed[0]++;
			skeleton.getSensing().tick();
			goal.tick();
			if (SmartSkeletonMetrics.snapshot().fireworkCrossbowShots() > fireworkShotsBefore) {
				helper.assertTrue(
					skeleton.getOffhandItem().getCount() == 2,
					"Loading one explosive rocket did not consume exactly one offhand item."
				);
				helper.assertTrue(
					!helper.getEntities(EntityType.FIREWORK_ROCKET).isEmpty(),
					"The crossbow state machine recorded a shot without spawning a real firework entity."
				);
				goal.stop();
				helper.succeed();
			}
			if (elapsed[0] >= 90) {
				helper.fail(
					"Crossbow state machine stalled: " + goal.diagnosticState()
						+ ",los=" + skeleton.getSensing().hasLineOfSight(target)
						+ ",rockets=" + skeleton.getOffhandItem().getCount()
				);
			}
		});
	}

	@GameTest
	public void closeNonPlayerSelectsTargetFacingKiteInsteadOfBackwardsAiming(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 4, 2, 2);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(target);
		setFacingDirectlyAwayFrom(skeleton, target);
		skeleton.startUsingItem(InteractionHand.MAIN_HAND);

		SmartSkeletonBowAttackGoal goal = new SmartSkeletonBowAttackGoal(skeleton, 1.0, 40, 15.0F);
		helper.assertTrue(goal.canUse(), "A bow skeleton with a live target could not start its ranged goal.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			goal.movementMode() == MovementMode.KITE,
			"A target two blocks away did not immediately select the bow-combat kite band."
		);
		skeleton.getMoveControl().tick();
		skeleton.getLookControl().tick();
		helper.assertTrue(skeleton.isUsingItem(), "Kiting incorrectly lowered the bow like a full escape.");
		helper.assertTrue(
			isHeadAndBodyFacing(skeleton, target),
			"Kiting left the skeleton's head, body, or bow facing away from its target."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest(
		structure = "mobsthinknow-gametest:skeleton_cover_arena",
		maxTicks = 120,
		skyAccess = true,
		padding = 8
	)
	public void skeletonDrawsBehindCoverThenPreparesAdjacentPeek(final GameTestHelper helper) {
		buildCoverLane(helper);
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 9, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 1, 2, 3);
		skeleton.setNoAi(true);
		// GameTest 的 tick 0 实体尚未经过一次落地物理更新；原版地面导航只在 onGround 时建路。
		skeleton.setOnGround(true);
		target.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(target);

		BlockPos expectedHide = helper.absolutePos(new BlockPos(9, 2, 3));
		BlockPos expectedPeek = helper.absolutePos(new BlockPos(9, 2, 2));
		helper.assertTrue(
			SkeletonCoverPlanner.isStandable(skeleton, expectedHide),
			"The designed hidden cell was not standable."
		);
		helper.assertTrue(
			SkeletonCoverPlanner.isStandable(skeleton, expectedPeek),
			"The designed peek cell was not standable."
		);
		helper.assertTrue(
			SkeletonCoverPlanner.isHiddenFromTarget(skeleton, target, expectedHide),
			"The two-block wall did not hide the designed cover cell."
		);
		helper.assertTrue(
			SkeletonCoverPlanner.hasClearShotFrom(skeleton, target, expectedPeek),
			"The designed side cell could not see around the wall edge."
		);

		List<SkeletonCoverPlanner.CoverPlan> plans = SkeletonCoverPlanner.findPlans(skeleton, target, 10.0);
		helper.assertTrue(!plans.isEmpty(), "The two-block wall produced no hide-and-peek cover pair.");
		helper.assertTrue(
			plans.stream().allMatch(plan ->
				SkeletonCoverPlanner.isHiddenFromTarget(skeleton, target, plan.hide())
					&& SkeletonCoverPlanner.hasClearShotFrom(skeleton, target, plan.peek())
			),
			"A returned cover pair did not combine a hidden cell with an adjacent clear firing cell."
		);

		long coverPlansBefore = SmartSkeletonMetrics.snapshot().coverPlans();
		SmartSkeletonBowAttackGoal goal = new SmartSkeletonBowAttackGoal(skeleton, 1.0, 40, 15.0F);
		helper.assertTrue(goal.canUse(), "The cover test bow goal did not start.");
		goal.start();
		for (int tick = 0; tick < 3 && goal.coverPhase() == CoverPhase.INACTIVE; tick++) {
			skeleton.getSensing().tick();
			goal.tick();
		}
		helper.assertTrue(
			goal.coverPhase() == CoverPhase.MOVING_TO_COVER,
			"A visible target and nearby valid wall did not start movement to cover."
		);
		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().coverPlans() > coverPlansBefore,
			"Starting the cover plan did not record its state transition."
		);

		SkeletonCoverPlanner.CoverPlan selected = goal.coverPlan();
		helper.assertTrue(selected != null, "The active cover phase did not retain its plan.");
		Vec3 hideCenter = Vec3.atBottomCenterOf(selected.hide());
		skeleton.snapTo(hideCenter.x, hideCenter.y, hideCenter.z, 0.0F, 0.0F);
		skeleton.getSensing().tick();
		goal.tick();
		helper.assertTrue(
			goal.coverPhase() == CoverPhase.DRAWING_IN_COVER,
			"Reaching the hidden cell did not enter the protected bow-draw phase."
		);

		// attackTime 的启动抖动最大为八 tick；十次手动决策足以证明无视线时仍会在掩体内拉弓。
		for (int tick = 0; tick < 10 && !skeleton.isUsingItem(); tick++) {
			skeleton.getSensing().tick();
			goal.tick();
		}
		helper.assertTrue(skeleton.isUsingItem(), "The skeleton never drew its bow while fully hidden.");

		long coverShotsBefore = SmartSkeletonMetrics.snapshot().coverShots();
		AtomicBoolean shotObserved = new AtomicBoolean();
		helper.onEachTick(() -> {
			SkeletonCoverPlanner.CoverPlan activePlan = goal.coverPlan();
			helper.assertTrue(activePlan != null, "The cover plan ended before its peek shot.");
			if (goal.coverPhase() == CoverPhase.MOVING_TO_PEEK) {
				Vec3 peekCenter = Vec3.atBottomCenterOf(activePlan.peek());
				skeleton.snapTo(peekCenter.x, peekCenter.y, peekCenter.z, 0.0F, 0.0F);
			}
			skeleton.getSensing().tick();
			goal.tick();

			if (!shotObserved.get() && SmartSkeletonMetrics.snapshot().coverShots() > coverShotsBefore) {
				helper.assertTrue(
					goal.coverPhase() == CoverPhase.POST_SHOT_FACING,
					"The peek shot did not retain a short target-facing visual recovery phase."
				);
				helper.assertTrue(
					isHeadAndBodyFacing(skeleton, target),
					"The skeleton turned toward cover in the same tick that its arrow left the bow."
				);
				shotObserved.set(true);
			}
			if (shotObserved.get() && goal.coverPhase() == CoverPhase.RETURNING_TO_COVER) {
				goal.stop();
				helper.succeed();
			}
		});
	}

	@GameTest
	public void incomingArrowSelectsBoundedDodgeBurst(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 4, 2, 4);
		Villager target = helper.spawn(EntityType.VILLAGER, 12, 2, 4);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(target);
		setFacingDirectlyAwayFrom(skeleton, target);
		skeleton.startUsingItem(InteractionHand.MAIN_HAND);

		Vec3 center = skeleton.getBoundingBox().getCenter();
		Arrow arrow = new Arrow(
			helper.getLevel(),
			center.x,
			center.y,
			center.z - 4.0,
			new ItemStack(Items.ARROW),
			new ItemStack(Items.BOW)
		);
		arrow.setOwner(target);
		arrow.setNoGravity(true);
		arrow.setNoPhysics(true);
		arrow.setDeltaMovement(0.0, 0.0, 0.75);
		helper.assertTrue(helper.getLevel().addFreshEntity(arrow), "The incoming-arrow fixture was not added.");

		SmartSkeletonBowAttackGoal goal = new SmartSkeletonBowAttackGoal(skeleton, 1.0, 40, 15.0F);
		helper.assertTrue(goal.canUse(), "The dodge test bow goal did not start.");
		goal.start();
		goal.tick();
		helper.assertTrue(
			goal.movementMode() == MovementMode.DODGE,
			"An arrow crossing the skeleton center within eight ticks did not start a dodge burst."
		);
		skeleton.getMoveControl().tick();
		skeleton.getLookControl().tick();
		helper.assertTrue(skeleton.isUsingItem(), "Arrow dodging incorrectly lowered the combatant's bow.");
		helper.assertTrue(
			isHeadAndBodyFacing(skeleton, target),
			"Arrow dodging did not keep the skeleton's head and body locked on its target."
		);
		goal.stop();
		helper.succeed();
	}

	@GameTest
	public void movingTargetReceivesHorizontalPredictionBeforeArrowSpawn(final GameTestHelper helper) {
		Skeleton skeleton = helper.spawn(EntityType.SKELETON, 2, 2, 2);
		Villager target = helper.spawn(EntityType.VILLAGER, 12, 2, 2);
		skeleton.setNoAi(true);
		target.setNoAi(true);
		target.setInvulnerable(true);
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		target.setDeltaMovement(0.0, 0.0, 0.25);
		long predictionsBefore = SmartSkeletonMetrics.snapshot().predictiveShots();

		skeleton.performRangedAttack(target, 1.0F);

		helper.assertTrue(
			SmartSkeletonMetrics.snapshot().predictiveShots() > predictionsBefore,
			"The moving target did not receive a horizontal prediction adjustment."
		);
		helper.succeed();
	}

	@Override
	public void invokeTestMethod(final GameTestHelper helper, final Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}

	private static void buildCoverLane(final GameTestHelper helper) {
		for (int x = 0; x <= 13; x++) {
			for (int z = 0; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		// 单格宽、两格高：正后方完全遮住骷髅眼睛，相邻南北格则能从墙角获得射界。
		helper.setBlock(new BlockPos(8, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(8, 3, 3), Blocks.STONE);
	}

	private static void setFacingDirectlyAwayFrom(final Skeleton skeleton, final Villager target) {
		float targetYaw = yawFromTo(skeleton.position(), target.position());
		float awayYaw = targetYaw + 180.0F;
		skeleton.setYRot(awayYaw);
		skeleton.setYBodyRot(awayYaw);
		skeleton.setYHeadRot(awayYaw);
	}

	private static boolean isHeadAndBodyFacing(final Skeleton skeleton, final Villager target) {
		float expected = yawFromTo(skeleton.position(), target.position());
		return Math.abs(Mth.wrapDegrees(skeleton.getYRot() - expected)) <= 1.0F
			&& Math.abs(Mth.wrapDegrees(skeleton.yBodyRot - expected)) <= 1.0F
			&& Math.abs(Mth.wrapDegrees(skeleton.getYHeadRot() - expected)) <= 1.0F;
	}

	private static boolean isFacingAwayFrom(final Skeleton skeleton, final LivingEntity threat) {
		Vec3 away = skeleton.position().subtract(threat.position()).multiply(1.0, 0.0, 1.0).normalize();
		Vec3 facing = skeleton.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
		return away.dot(facing) > 0.5
			&& Math.abs(Mth.wrapDegrees(skeleton.yBodyRot - skeleton.getYRot())) <= 1.0F
			&& Math.abs(Mth.wrapDegrees(skeleton.getYHeadRot() - skeleton.getYRot())) <= 1.0F;
	}

	private static float yawFromTo(final Vec3 origin, final Vec3 target) {
		return (float)(Mth.atan2(target.z - origin.z, target.x - origin.x) * 180.0F / Math.PI) - 90.0F;
	}
}

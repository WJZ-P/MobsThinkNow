package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 正式工程兵的低频环境控制技能调度器。
 *
 * <p>每项技能完整结束后随机等待 6～10 秒，再从当时真正可执行的技能中等概率选择：</p>
 * <ul>
 *     <li><b>TNT 爆破</b>：真实放置、点燃带 owner 的原版 PrimedTnt，然后撤离爆心；</li>
 *     <li><b>水/岩浆控制</b>：使用原版 BucketItem 投放真实源方块，拉开后返回并通过
 *     BucketPickup 回收；</li>
 *     <li><b>打火石点燃</b>：走到目标近身范围，经过可见前摇后点燃目标五秒。</li>
 * </ul>
 *
 * <p>维修和群体抗性不再属于工程兵。技能工具只是表现层，不覆盖真实武器；流体源事务会
 * 持久化，所以自动保存或高优先级 Goal 打断后仍能继续回收。</p>
 */
public final class ZombieEngineerSkillGoal extends Goal {
	static final int MINIMUM_SKILL_DELAY_TICKS = 120;
	static final int MAXIMUM_SKILL_DELAY_TICKS = 200;
	private static final int SKILL_DELAY_RANGE = MAXIMUM_SKILL_DELAY_TICKS - MINIMUM_SKILL_DELAY_TICKS;
	private static final double TARGET_MAXIMUM_DISTANCE_SQUARED = 18.0 * 18.0;
	private static final int PATH_REFRESH_TICKS = 10;

	private static final double TNT_INTERACTION_DISTANCE_SQUARED = 3.25 * 3.25;
	private static final double TNT_ALLY_SAFETY_RADIUS = 3.25;
	private static final int TNT_PLACEMENT_WINDUP_TICKS = 8;
	private static final int TNT_IGNITION_WINDUP_TICKS = 12;
	private static final int TNT_FUSE_TICKS = 80;
	private static final int TNT_RETREAT_MAXIMUM_TICKS = 60;
	private static final double TNT_RETREAT_SAFE_DISTANCE_SQUARED = 8.0 * 8.0;
	private static final double TNT_RETREAT_SPEED = 1.35;

	private static final double FLUID_INTERACTION_DISTANCE_SQUARED = 4.25 * 4.25;
	private static final int FLUID_DEPLOY_WINDUP_TICKS = 10;
	private static final int WATER_HOLD_TICKS = 45;
	private static final int LAVA_HOLD_TICKS = 32;
	private static final double FLUID_APPROACH_SPEED = 1.12;
	private static final double FLUID_WITHDRAW_SPEED = 1.35;

	private static final double IGNITION_REACH_SQUARED = 2.6 * 2.6;
	private static final int IGNITION_WINDUP_TICKS = 8;
	private static final float IGNITION_DURATION_SECONDS = 5.0F;
	private static final double IGNITION_APPROACH_SPEED = 1.15;

	private static final int[][] TNT_OFFSETS = {
		{0, 0},
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	private static final int[][] FLUID_OFFSETS = {
		{0, 0},
		{1, 0}, {-1, 0}, {0, 1}, {0, -1}
	};

	private final Zombie zombie;
	private final SkillDecision skillDecision;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.ENGINEERING);
	private Phase phase = Phase.IDLE;
	private @Nullable Skill selectedSkill;
	private @Nullable LivingEntity combatTarget;
	private @Nullable TntPlan tntPlan;
	private @Nullable FluidPlan fluidPlan;
	private @Nullable IgnitionPlan ignitionPlan;
	private @Nullable Vec3 retreatDestination;
	private InteractionHand visibleHand = InteractionHand.MAIN_HAND;
	private long nextSkillAt;
	private long phaseDeadline;
	private long nextPathRefreshAt;
	private boolean placedTnt;
	private boolean primedTnt;

	public ZombieEngineerSkillGoal(final Zombie zombie) {
		this(
			zombie,
			(candidate, available) -> available.get(candidate.getRandom().nextInt(available.size())),
			skillDelayTicks(zombie.getRandom().nextDouble())
		);
	}

	/** 测试可以固定技能和首次等待；生产构造器始终使用单只僵尸自己的随机源。 */
	ZombieEngineerSkillGoal(
		final Zombie zombie,
		final SkillDecision skillDecision,
		final int initialDelayTicks
	) {
		this.zombie = zombie;
		this.skillDecision = skillDecision;
		this.nextSkillAt = zombie.level().getGameTime() + Math.max(0, initialDelayTicks);
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.zombie.level() instanceof ServerLevel level) || !this.zombie.isAlive()) {
			return false;
		}

		ZombieFluidCarrierState carrierState = ZombieSpecialEquipment.state(this.zombie);
		if (carrierState.isEngineerDeployment()) {
			return this.preparePersistedFluidTransaction(carrierState)
				&& this.activityLease.canAcquire(this.zombie, level.getGameTime());
		}
		// 真实桶兵已经开始的救火/骚扰事务仍由 ZombieFluidTacticsGoal 回收，不能并发抢走 MOVE/LOOK。
		if (carrierState.isDeployed()) {
			return false;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(this.zombie, config)) {
			return false;
		}
		long now = level.getGameTime();
		if (now < this.nextSkillAt) {
			return false;
		}

		LivingEntity target = this.zombie.getTarget();
		if (!isEligibleTarget(target)
			|| this.zombie.distanceToSqr(target) > TARGET_MAXIMUM_DISTANCE_SQUARED) {
			// 没有战斗目标时不消费完整技能周期，一秒后轻量重试。
			this.nextSkillAt = now + 20L;
			return false;
		}

		this.clearPreparedSkill();
		this.combatTarget = target;
		List<Skill> available = new ArrayList<>(4);
		@Nullable FluidPlan waterPlan = null;
		@Nullable FluidPlan lavaPlan = null;
		if (config.engineerTntSkill
			&& level.getGameRules().get(GameRules.MOB_GRIEFING)
			&& level.getGameRules().get(GameRules.TNT_EXPLODES)) {
			this.tntPlan = this.findTntPlan(level, target);
			if (this.tntPlan != null) {
				available.add(Skill.DEMOLITION_CHARGE);
			}
		}
		if (config.engineerFluidSkills && level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			waterPlan = this.findFluidPlan(level, target, UtilityClass.WATER);
			if (waterPlan != null) {
				available.add(Skill.WATER_CONTROL);
			}
			lavaPlan = this.findFluidPlan(level, target, UtilityClass.LAVA);
			if (lavaPlan != null) {
				available.add(Skill.LAVA_CONTROL);
			}
		}
		if (config.engineerIgnitionSkill && !target.isOnFire()) {
			this.ignitionPlan = this.findIgnitionPlan(target);
			if (this.ignitionPlan != null) {
				available.add(Skill.IGNITE_TARGET);
			}
		}

		if (available.isEmpty()) {
			this.nextSkillAt = now + 20L;
			this.clearPreparedSkill();
			return false;
		}
		Skill chosen = this.skillDecision.choose(this.zombie, List.copyOf(available));
		if (chosen == null || !available.contains(chosen)) {
			this.nextSkillAt = now + 20L;
			this.clearPreparedSkill();
			return false;
		}
		this.selectedSkill = chosen;
		if (chosen == Skill.WATER_CONTROL) {
			this.fluidPlan = waterPlan;
		} else if (chosen == Skill.LAVA_CONTROL) {
			this.fluidPlan = lavaPlan;
		}
		return this.activityLease.canAcquire(this.zombie, now);
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.zombie.isAlive() || !(this.zombie.level() instanceof ServerLevel level)
			|| this.phase == Phase.IDLE || this.phase == Phase.DONE) {
			return false;
		}
		if (!this.activityLease.owns(this.zombie, level.getGameTime())) {
			return false;
		}
		if (this.phase == Phase.FLUID_HOLD || this.phase == Phase.RETRIEVING_FLUID) {
			return ZombieSpecialEquipment.state(this.zombie).isEngineerDeployment();
		}

		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(this.zombie, config)) {
			return false;
		}
		if (this.isTntPhase() && (!config.engineerTntSkill
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| !level.getGameRules().get(GameRules.TNT_EXPLODES))) {
			return false;
		}
		if (this.isUncommittedFluidPhase() && (!config.engineerFluidSkills
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING))) {
			return false;
		}
		if (this.isIgnitionPhase() && !config.engineerIgnitionSkill) {
			return false;
		}
		if (this.phase == Phase.TNT_RETREAT) {
			return level.getGameTime() < this.phaseDeadline
				&& this.tntPlan != null
				&& this.zombie.position().distanceToSqr(Vec3.atCenterOf(this.tntPlan.position()))
					< TNT_RETREAT_SAFE_DISTANCE_SQUARED;
		}

		LivingEntity target = this.combatTarget;
		return isEligibleTarget(target) && this.zombie.getTarget() == target;
	}

	@Override
	public void start() {
		this.activityLease.acquire(this.zombie, this.zombie.level().getGameTime());
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		long now = this.zombie.level().getGameTime();
		this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;

		ZombieFluidCarrierState carrierState = ZombieSpecialEquipment.state(this.zombie);
		if (carrierState.isEngineerDeployment() && this.fluidPlan != null) {
			this.visibleHand = ZombieEngineerEquipment.begin(this.zombie, new ItemStack(Items.BUCKET));
			this.phase = now >= carrierState.retrieveAt() ? Phase.RETRIEVING_FLUID : Phase.FLUID_HOLD;
			this.phaseDeadline = carrierState.retrieveAt();
			this.nextPathRefreshAt = now;
			return;
		}
		if (this.selectedSkill == Skill.DEMOLITION_CHARGE && this.tntPlan != null) {
			this.visibleHand = ZombieEngineerEquipment.begin(this.zombie, new ItemStack(Items.TNT));
			if (this.isWithinTntReach(this.tntPlan.position())) {
				this.beginTntPlacement(now);
			} else {
				this.phase = Phase.MOVING_TO_TNT_SITE;
				this.beginTntNavigation();
			}
			return;
		}
		if ((this.selectedSkill == Skill.WATER_CONTROL || this.selectedSkill == Skill.LAVA_CONTROL)
			&& this.fluidPlan != null) {
			this.visibleHand = ZombieEngineerEquipment.begin(
				this.zombie,
				new ItemStack(this.fluidPlan.utility() == UtilityClass.WATER ? Items.WATER_BUCKET : Items.LAVA_BUCKET)
			);
			if (this.isWithinFluidReach(this.fluidPlan.position())) {
				this.beginFluidDeployment(now);
			} else {
				this.phase = Phase.MOVING_TO_FLUID_SITE;
				this.beginFluidNavigation();
			}
			return;
		}
		if (this.selectedSkill == Skill.IGNITE_TARGET && this.ignitionPlan != null) {
			this.visibleHand = ZombieEngineerEquipment.begin(this.zombie, new ItemStack(Items.FLINT_AND_STEEL));
			if (this.isWithinIgnitionReach(this.ignitionPlan.target())
				&& this.zombie.getSensing().hasLineOfSight(this.ignitionPlan.target())) {
				this.beginIgnition(now);
			} else {
				this.phase = Phase.MOVING_TO_IGNITE;
				this.beginIgnitionNavigation();
			}
			return;
		}
		this.phase = Phase.DONE;
	}

	@Override
	public void tick() {
		if (!this.activityLease.renew(this.zombie, this.zombie.level().getGameTime())) {
			return;
		}
		this.zombie.setAggressive(false);
		switch (this.phase) {
			case MOVING_TO_TNT_SITE -> this.tickMovingToTnt();
			case PLACING_TNT -> this.tickPlacingTnt();
			case ARMING_TNT -> this.tickArmingTnt();
			case TNT_RETREAT -> this.tickTntRetreat();
			case MOVING_TO_FLUID_SITE -> this.tickMovingToFluid();
			case DEPLOYING_FLUID -> this.tickDeployingFluid();
			case FLUID_HOLD -> this.tickFluidHold();
			case RETRIEVING_FLUID -> this.tickRetrievingFluid();
			case MOVING_TO_IGNITE -> this.tickMovingToIgnite();
			case IGNITING_TARGET -> this.tickIgnitingTarget();
			case IDLE, DONE -> {
			}
		}
	}

	@Override
	public void stop() {
		this.stopTntWorkPose();
		this.cleanupUnprimedTnt();
		ZombieEngineerEquipment.restore(this.zombie, false);
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.nextSkillAt = this.zombie.level().getGameTime()
			+ skillDelayTicks(this.zombie.getRandom().nextDouble());
		this.phase = Phase.IDLE;
		this.selectedSkill = null;
		this.combatTarget = null;
		this.tntPlan = null;
		this.fluidPlan = null;
		this.ignitionPlan = null;
		this.retreatDestination = null;
		this.phaseDeadline = 0L;
		this.nextPathRefreshAt = 0L;
		this.placedTnt = false;
		this.primedTnt = false;
		this.activityLease.release(this.zombie);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tickMovingToTnt() {
		TntPlan plan = this.tntPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)
			|| !this.isValidTntSite(level, plan.position())) {
			this.phase = Phase.DONE;
			return;
		}
		this.lookAt(plan.position());
		if (this.isWithinTntReach(plan.position())) {
			this.zombie.getNavigation().stop();
			this.beginTntPlacement(level.getGameTime());
			return;
		}
		if (level.getGameTime() >= this.nextPathRefreshAt) {
			this.beginTntNavigation();
			this.nextPathRefreshAt = level.getGameTime() + PATH_REFRESH_TICKS;
		}
	}

	private void beginTntPlacement(final long now) {
		this.phase = Phase.PLACING_TNT;
		this.phaseDeadline = now + TNT_PLACEMENT_WINDUP_TICKS;
		ZombieBodyLanguage.startPersistent(this.zombie, ZombieBodyAction.ENGINEER_WORK);
	}

	private void tickPlacingTnt() {
		TntPlan plan = this.tntPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.stopTntWorkPose();
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.lookAt(plan.position());
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}
		if (!this.isWithinTntReach(plan.position()) || !this.placeTnt(level, plan.position())) {
			this.stopTntWorkPose();
			this.phase = Phase.DONE;
			return;
		}
		this.visibleHand = ZombieEngineerEquipment.show(this.zombie, new ItemStack(Items.FLINT_AND_STEEL));
		this.phase = Phase.ARMING_TNT;
		this.phaseDeadline = level.getGameTime() + TNT_IGNITION_WINDUP_TICKS;
	}

	private void tickArmingTnt() {
		TntPlan plan = this.tntPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.stopTntWorkPose();
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.lookAt(plan.position());
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}
		if (!level.getBlockState(plan.position()).is(Blocks.TNT)) {
			this.stopTntWorkPose();
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.swing(this.visibleHand);
		level.playSound(null, plan.position(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F,
			0.9F + this.zombie.getRandom().nextFloat() * 0.2F);
		if (!this.primeTnt(level, plan.position())) {
			this.stopTntWorkPose();
			this.phase = Phase.DONE;
			return;
		}
		this.stopTntWorkPose();
		ZombieEngineerEquipment.restore(this.zombie, false);
		this.phase = Phase.TNT_RETREAT;
		this.phaseDeadline = level.getGameTime() + TNT_RETREAT_MAXIMUM_TICKS;
		this.retreatDestination = null;
		this.nextPathRefreshAt = level.getGameTime();
		SmartZombieMetrics.engineerTntCharge();
	}

	private void stopTntWorkPose() {
		ZombieBodyLanguage.stopPersistent(this.zombie, ZombieBodyAction.ENGINEER_WORK);
	}

	private void tickTntRetreat() {
		TntPlan plan = this.tntPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		Vec3 charge = Vec3.atCenterOf(plan.position());
		if (this.zombie.position().distanceToSqr(charge) >= TNT_RETREAT_SAFE_DISTANCE_SQUARED) {
			this.phase = Phase.DONE;
			return;
		}
		this.moveAwayFrom(charge, TNT_RETREAT_SPEED, 8.0, 11.0);
	}

	private void tickMovingToFluid() {
		FluidPlan plan = this.fluidPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)
			|| !this.isValidFluidSite(level, plan.utility(), plan.position())) {
			this.phase = Phase.DONE;
			return;
		}
		this.lookAt(plan.position());
		if (this.isWithinFluidReach(plan.position())) {
			this.zombie.getNavigation().stop();
			this.beginFluidDeployment(level.getGameTime());
			return;
		}
		if (level.getGameTime() >= this.nextPathRefreshAt) {
			this.beginFluidNavigation();
			this.nextPathRefreshAt = level.getGameTime() + PATH_REFRESH_TICKS;
		}
	}

	private void beginFluidDeployment(final long now) {
		this.phase = Phase.DEPLOYING_FLUID;
		this.phaseDeadline = now + FLUID_DEPLOY_WINDUP_TICKS;
	}

	private void tickDeployingFluid() {
		FluidPlan plan = this.fluidPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.lookAt(plan.position());
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}
		long retrieveAt = level.getGameTime() + fluidHoldTicks(plan.utility(), this.zombie.getId());
		if (!this.isWithinFluidReach(plan.position()) || !ZombieFluidActions.tryDeployEngineer(
			level,
			this.zombie,
			plan.utility(),
			plan.position(),
			retrieveAt,
			this.visibleHand
		)) {
			this.phase = Phase.DONE;
			return;
		}
		ZombieEngineerEquipment.show(this.zombie, new ItemStack(Items.BUCKET));
		this.phase = Phase.FLUID_HOLD;
		this.phaseDeadline = retrieveAt;
		this.retreatDestination = null;
		this.nextPathRefreshAt = level.getGameTime();
	}

	private void tickFluidHold() {
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		if (!(this.zombie.level() instanceof ServerLevel level) || !state.isEngineerDeployment()
			|| state.source() == null) {
			this.phase = Phase.DONE;
			return;
		}
		if (!isMatchingSource(level, state.source(), state.utility())) {
			this.loseEngineerFluidSource();
			return;
		}
		this.lookAt(state.source());
		if (level.getGameTime() >= state.retrieveAt()) {
			this.phase = Phase.RETRIEVING_FLUID;
			this.nextPathRefreshAt = level.getGameTime();
			return;
		}
		this.moveAwayFrom(Vec3.atCenterOf(state.source()), FLUID_WITHDRAW_SPEED, 5.0, 7.0);
	}

	private void tickRetrievingFluid() {
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		if (!(this.zombie.level() instanceof ServerLevel level) || !state.isEngineerDeployment()
			|| state.source() == null) {
			this.phase = Phase.DONE;
			return;
		}
		BlockPos source = state.source();
		if (!isMatchingSource(level, source, state.utility())) {
			this.loseEngineerFluidSource();
			return;
		}
		this.lookAt(source);
		if (!this.isWithinFluidReach(source)) {
			long now = level.getGameTime();
			if (now >= this.nextPathRefreshAt) {
				this.zombie.getNavigation().moveTo(
					source.getX() + 0.5,
					source.getY(),
					source.getZ() + 0.5,
					FLUID_APPROACH_SPEED
				);
				this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
			}
			return;
		}

		BlockState blockState = level.getBlockState(source);
		if (!(blockState.getBlock() instanceof BucketPickup pickup)) {
			this.loseEngineerFluidSource();
			return;
		}
		ItemStack recovered = pickup.pickupBlock(this.zombie, level, source, blockState);
		if (!isExpectedBucket(recovered, state.utility())) {
			this.loseEngineerFluidSource();
			return;
		}
		pickup.getPickupSound().ifPresent(sound -> level.playSound(
			null, source, sound, SoundSource.BLOCKS, 1.0F, 1.0F
		));
		level.gameEvent(this.zombie, GameEvent.FLUID_PICKUP, source);
		this.zombie.swing(this.visibleHand);
		// 先清状态再展示临时满桶，避免把动画桶误判成僵尸真正持有的职业装备。
		ZombieSpecialEquipment.clearEngineerDeployment(this.zombie);
		ZombieEngineerEquipment.show(this.zombie, recovered);
		SmartZombieMetrics.fluidRecovered();
		this.phase = Phase.DONE;
	}

	private void loseEngineerFluidSource() {
		ZombieSpecialEquipment.clearEngineerDeployment(this.zombie);
		SmartZombieMetrics.fluidSourceLost();
		this.phase = Phase.DONE;
	}

	private void tickMovingToIgnite() {
		IgnitionPlan plan = this.ignitionPlan;
		if (plan == null || !isEligibleTarget(plan.target()) || plan.target().isOnFire()) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getLookControl().setLookAt(plan.target(), 30.0F, 30.0F);
		if (this.isWithinIgnitionReach(plan.target()) && this.zombie.getSensing().hasLineOfSight(plan.target())) {
			this.zombie.getNavigation().stop();
			this.beginIgnition(this.zombie.level().getGameTime());
			return;
		}
		long now = this.zombie.level().getGameTime();
		if (now >= this.nextPathRefreshAt) {
			this.beginIgnitionNavigation();
			this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
		}
	}

	private void beginIgnition(final long now) {
		this.phase = Phase.IGNITING_TARGET;
		this.phaseDeadline = now + IGNITION_WINDUP_TICKS;
	}

	private void tickIgnitingTarget() {
		IgnitionPlan plan = this.ignitionPlan;
		if (plan == null || !(this.zombie.level() instanceof ServerLevel level)
			|| !isEligibleTarget(plan.target()) || plan.target().isOnFire()) {
			this.phase = Phase.DONE;
			return;
		}
		LivingEntity target = plan.target();
		this.zombie.getNavigation().stop();
		this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		if (!this.isWithinIgnitionReach(target) || !this.zombie.getSensing().hasLineOfSight(target)) {
			this.phase = Phase.MOVING_TO_IGNITE;
			this.nextPathRefreshAt = level.getGameTime();
			return;
		}
		if (level.getGameTime() < this.phaseDeadline) {
			return;
		}

		this.zombie.swing(this.visibleHand);
		level.playSound(null, target, SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0F,
			0.9F + this.zombie.getRandom().nextFloat() * 0.2F);
		target.igniteForSeconds(IGNITION_DURATION_SECONDS);
		level.sendParticles(
			ParticleTypes.FLAME,
			target.getX(),
			target.getY() + target.getBbHeight() * 0.45,
			target.getZ(),
			10,
			0.28,
			0.35,
			0.28,
			0.02
		);
		SmartZombieMetrics.engineerIgnition();
		this.phase = Phase.DONE;
	}

	private boolean preparePersistedFluidTransaction(final ZombieFluidCarrierState state) {
		BlockPos source = state.source();
		if (source == null || (state.utility() != UtilityClass.WATER && state.utility() != UtilityClass.LAVA)) {
			return false;
		}
		this.clearPreparedSkill();
		this.selectedSkill = state.utility() == UtilityClass.WATER ? Skill.WATER_CONTROL : Skill.LAVA_CONTROL;
		this.combatTarget = isEligibleTarget(this.zombie.getTarget()) ? this.zombie.getTarget() : null;
		this.fluidPlan = new FluidPlan(state.utility(), source.immutable(), null);
		return true;
	}

	private @Nullable TntPlan findTntPlan(final ServerLevel level, final LivingEntity target) {
		BlockPos center = target.blockPosition();
		int start = this.zombie.getRandom().nextInt(TNT_OFFSETS.length);
		for (int index = 0; index < TNT_OFFSETS.length; index++) {
			int[] offset = TNT_OFFSETS[(start + index) % TNT_OFFSETS.length];
			BlockPos candidate = center.offset(offset[0], 0, offset[1]);
			if (!this.isValidTntSite(level, candidate)) {
				continue;
			}
			if (this.isWithinTntReach(candidate)) {
				return new TntPlan(candidate.immutable(), null);
			}
			Path path = this.zombie.getNavigation().createPath(candidate, 1);
			if (path != null && path.canReach()) {
				return new TntPlan(candidate.immutable(), path);
			}
		}
		return null;
	}

	private @Nullable FluidPlan findFluidPlan(
		final ServerLevel level,
		final LivingEntity target,
		final UtilityClass utility
	) {
		BlockPos center = target.blockPosition();
		int start = this.zombie.getRandom().nextInt(FLUID_OFFSETS.length);
		for (int index = 0; index < FLUID_OFFSETS.length; index++) {
			int[] offset = FLUID_OFFSETS[(start + index) % FLUID_OFFSETS.length];
			BlockPos candidate = center.offset(offset[0], 0, offset[1]);
			if (!this.isValidFluidSite(level, utility, candidate)) {
				continue;
			}
			if (this.isWithinFluidReach(candidate)) {
				return new FluidPlan(utility, candidate.immutable(), null);
			}
			Path path = this.zombie.getNavigation().createPath(candidate, 1);
			if (path != null && path.canReach()) {
				return new FluidPlan(utility, candidate.immutable(), path);
			}
		}
		return null;
	}

	private @Nullable IgnitionPlan findIgnitionPlan(final LivingEntity target) {
		if (this.isWithinIgnitionReach(target) && this.zombie.getSensing().hasLineOfSight(target)) {
			return new IgnitionPlan(target);
		}
		Path path = this.zombie.getNavigation().createPath(target, 1);
		return path != null && path.canReach() ? new IgnitionPlan(target) : null;
	}

	private boolean isValidTntSite(final ServerLevel level, final BlockPos pos) {
		if (!isLoadedAndInsideBorder(level, pos)) {
			return false;
		}
		BlockState current = level.getBlockState(pos);
		BlockPos support = pos.below();
		if (!current.canBeReplaced()
			|| !current.getFluidState().isEmpty()
			|| level.getBlockEntity(pos) != null
			|| !level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)) {
			return false;
		}
		if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) {
			return false;
		}
		return level.getEntitiesOfClass(
			Zombie.class,
			new AABB(pos).inflate(TNT_ALLY_SAFETY_RADIUS, 1.5, TNT_ALLY_SAFETY_RADIUS),
			candidate -> candidate != this.zombie && candidate.isAlive()
		).isEmpty();
	}

	private boolean isValidFluidSite(
		final ServerLevel level,
		final UtilityClass utility,
		final BlockPos pos
	) {
		return isLoadedAndInsideBorder(level, pos)
			&& ZombieFluidActions.canDeployAt(level, this.zombie, utility, pos);
	}

	private boolean placeTnt(final ServerLevel level, final BlockPos pos) {
		if (!this.isValidTntSite(level, pos)) {
			return false;
		}
		BlockState tnt = Blocks.TNT.defaultBlockState();
		if (!level.setBlock(pos, tnt, Block.UPDATE_ALL)) {
			return false;
		}
		this.placedTnt = true;
		SoundType sound = tnt.getSoundType();
		level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
			(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.zombie, tnt));
		this.zombie.swing(this.visibleHand);
		return true;
	}

	private boolean primeTnt(final ServerLevel level, final BlockPos pos) {
		PrimedTnt primed = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, this.zombie);
		primed.setFuse(TNT_FUSE_TICKS);
		if (!level.addFreshEntity(primed)) {
			return false;
		}
		if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
			primed.discard();
			return false;
		}
		this.placedTnt = false;
		this.primedTnt = true;
		level.playSound(null, primed, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.gameEvent(this.zombie, GameEvent.PRIME_FUSE, pos);
		return true;
	}

	private void cleanupUnprimedTnt() {
		if (!this.placedTnt || this.primedTnt || this.tntPlan == null
			|| !(this.zombie.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = this.tntPlan.position();
		if (level.getBlockState(pos).is(Blocks.TNT)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}
		this.placedTnt = false;
	}

	private void beginTntNavigation() {
		TntPlan plan = this.tntPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		boolean moving;
		if (plan.initialPath() != null) {
			moving = this.zombie.getNavigation().moveTo(plan.initialPath(), 1.12);
			this.tntPlan = new TntPlan(plan.position(), null);
		} else {
			moving = this.zombie.getNavigation().moveTo(
				plan.position().getX() + 0.5, plan.position().getY(), plan.position().getZ() + 0.5, 1.12
			);
		}
		if (!moving && this.zombie.getNavigation().isDone()) {
			this.phase = Phase.DONE;
		}
	}

	private void beginFluidNavigation() {
		FluidPlan plan = this.fluidPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		boolean moving;
		if (plan.initialPath() != null) {
			moving = this.zombie.getNavigation().moveTo(plan.initialPath(), FLUID_APPROACH_SPEED);
			this.fluidPlan = new FluidPlan(plan.utility(), plan.position(), null);
		} else {
			moving = this.zombie.getNavigation().moveTo(
				plan.position().getX() + 0.5,
				plan.position().getY(),
				plan.position().getZ() + 0.5,
				FLUID_APPROACH_SPEED
			);
		}
		if (!moving && this.zombie.getNavigation().isDone()) {
			this.phase = Phase.DONE;
		}
	}

	private void beginIgnitionNavigation() {
		IgnitionPlan plan = this.ignitionPlan;
		if (plan == null) {
			this.phase = Phase.DONE;
			return;
		}
		// 目标可以移动；初始 Path 只用于 canUse 的可达性证明，运行时始终让导航追踪当前实体位置。
		boolean moving = this.zombie.getNavigation().moveTo(plan.target(), IGNITION_APPROACH_SPEED);
		if (!moving && this.zombie.getNavigation().isDone()) {
			this.phase = Phase.DONE;
		}
	}

	private void moveAwayFrom(
		final Vec3 danger,
		final double speed,
		final double horizontalRange,
		final double verticalRange
	) {
		long now = this.zombie.level().getGameTime();
		boolean reached = this.retreatDestination == null
			|| this.zombie.position().distanceToSqr(this.retreatDestination) <= 2.25;
		if (now < this.nextPathRefreshAt && !reached && !this.zombie.getNavigation().isDone()) {
			return;
		}
		Vec3 destination = LandRandomPos.getPosAway(this.zombie, horizontalRange, verticalRange, 4, danger);
		if (destination == null) {
			Vec3 away = horizontalUnit(this.zombie.position().subtract(danger));
			destination = this.zombie.position().add(away.scale(horizontalRange));
		}
		boolean moving = this.zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
		this.retreatDestination = moving ? destination : null;
		this.nextPathRefreshAt = now + (moving ? 8L : 2L);
		if (moving) {
			this.zombie.getLookControl().setLookAt(destination.add(0.0, 1.0, 0.0));
		}
	}

	private void lookAt(final BlockPos pos) {
		this.zombie.getLookControl().setLookAt(Vec3.atCenterOf(pos));
	}

	private boolean isWithinTntReach(final BlockPos pos) {
		return this.zombie.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= TNT_INTERACTION_DISTANCE_SQUARED;
	}

	private boolean isWithinFluidReach(final BlockPos pos) {
		return this.zombie.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= FLUID_INTERACTION_DISTANCE_SQUARED;
	}

	private boolean isWithinIgnitionReach(final LivingEntity target) {
		return this.zombie.distanceToSqr(target) <= IGNITION_REACH_SQUARED;
	}

	private boolean isTntPhase() {
		return this.phase == Phase.MOVING_TO_TNT_SITE
			|| this.phase == Phase.PLACING_TNT
			|| this.phase == Phase.ARMING_TNT
			|| this.phase == Phase.TNT_RETREAT;
	}

	private boolean isUncommittedFluidPhase() {
		return this.phase == Phase.MOVING_TO_FLUID_SITE || this.phase == Phase.DEPLOYING_FLUID;
	}

	private boolean isIgnitionPhase() {
		return this.phase == Phase.MOVING_TO_IGNITE || this.phase == Phase.IGNITING_TARGET;
	}

	private void clearPreparedSkill() {
		this.selectedSkill = null;
		this.combatTarget = null;
		this.tntPlan = null;
		this.fluidPlan = null;
		this.ignitionPlan = null;
	}

	static int skillDelayTicks(final double roll) {
		double bounded = Double.isFinite(roll) ? Math.max(0.0, Math.min(1.0, roll)) : 0.0;
		int offset = Math.min(SKILL_DELAY_RANGE, (int)Math.floor(bounded * (SKILL_DELAY_RANGE + 1)));
		return MINIMUM_SKILL_DELAY_TICKS + offset;
	}

	static int fluidHoldTicks(final UtilityClass utility, final int entityId) {
		return utility == UtilityClass.WATER
			? WATER_HOLD_TICKS + Math.floorMod(entityId, 16)
			: LAVA_HOLD_TICKS + Math.floorMod(entityId, 10);
	}

	static float ignitionDurationSeconds() {
		return IGNITION_DURATION_SECONDS;
	}

	static boolean isVisualTool(final ItemStack stack) {
		return stack.is(Items.TNT)
			|| stack.is(Items.FLINT_AND_STEEL)
			|| stack.is(Items.WATER_BUCKET)
			|| stack.is(Items.LAVA_BUCKET)
			|| stack.is(Items.BUCKET);
	}

	private static boolean isEnabled(final Zombie zombie, final MobsThinkNowConfig config) {
		return config.enabled
			&& config.zombieAiEnabled
			&& config.engineerSkills
			&& zombie.getType() == EntityType.ZOMBIE
			&& zombie.isAlive()
			&& !zombie.isBaby()
			&& ZombieEngineerProfile.isEngineer(zombie)
			&& !ZombieAirAssault.isAirAssaultLoadout(zombie);
	}

	private static boolean isEligibleTarget(final @Nullable LivingEntity target) {
		return target != null
			&& target.isAlive()
			&& !(target instanceof Zombie)
			&& (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	private static boolean isLoadedAndInsideBorder(final ServerLevel level, final BlockPos pos) {
		return Level.isInSpawnableBounds(pos)
			&& isChunkLoaded(level, pos)
			&& level.getWorldBorder().isWithinBounds(new AABB(pos));
	}

	private static boolean isChunkLoaded(final ServerLevel level, final BlockPos pos) {
		return level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ())
		);
	}

	private static boolean isMatchingSource(
		final ServerLevel level,
		final BlockPos source,
		final UtilityClass utility
	) {
		return level.getFluidState(source).isSource()
			&& switch (utility) {
				case WATER -> level.getFluidState(source).is(FluidTags.WATER);
				case LAVA -> level.getFluidState(source).is(FluidTags.LAVA);
				case NONE -> false;
			};
	}

	private static boolean isExpectedBucket(final ItemStack stack, final UtilityClass utility) {
		return utility == UtilityClass.WATER ? stack.is(Items.WATER_BUCKET)
			: utility == UtilityClass.LAVA && stack.is(Items.LAVA_BUCKET);
	}

	private static Vec3 horizontalUnit(final Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.horizontalDistanceSqr() < 1.0E-6
			? new Vec3(0.0, 0.0, 1.0)
			: horizontal.normalize();
	}

	enum Skill {
		DEMOLITION_CHARGE,
		WATER_CONTROL,
		LAVA_CONTROL,
		IGNITE_TARGET
	}

	private enum Phase {
		IDLE,
		MOVING_TO_TNT_SITE,
		PLACING_TNT,
		ARMING_TNT,
		TNT_RETREAT,
		MOVING_TO_FLUID_SITE,
		DEPLOYING_FLUID,
		FLUID_HOLD,
		RETRIEVING_FLUID,
		MOVING_TO_IGNITE,
		IGNITING_TARGET,
		DONE
	}

	@FunctionalInterface
	interface SkillDecision {
		@Nullable Skill choose(Zombie zombie, List<Skill> available);
	}

	private record TntPlan(BlockPos position, @Nullable Path initialPath) {
	}

	private record FluidPlan(UtilityClass utility, BlockPos position, @Nullable Path initialPath) {
	}

	private record IgnitionPlan(LivingEntity target) {
	}
}

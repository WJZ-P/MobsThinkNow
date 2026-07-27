package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * 高智力僵尸的受限地形战术状态机。
 *
 * <p>只在当前锁定铁傀儡，或任意存活目标占据高处且原版地面路径确实不可达时启动，
 * 避免僵尸为了普通巡逻、平地追击或可正常绕行的山坡无目的地改造地形。
 * 铁傀儡分支的完整流程为：</p>
 * <ol>
 *     <li>若内部材料不足三块，在五格内按近到远搜索泥土、沙、泥、沙砾或黏土；</li>
 *     <li>走到可达采集位，按空手硬度显示裂纹并逐块收进受限背包；</li>
 *     <li>选择距铁傀儡约三格、地基完整且整列无阻挡的位置；</li>
 *     <li>像玩家垫脚一样逐次起跳，仅在碰撞箱已经高于待放方块时放置；</li>
 *     <li>站上三格立柱后保持位置，以受冷却约束的俯身攻击打击下方目标。</li>
 * </ol>
 * 高处目标分支会选择目标附近的一格紧凑落点，按真实高差只采集所需材料，再像玩家一样
 * 一次跳起、向脚下放置一格，直到与目标同高。撤退行为不会再放置任何方块。
 *
 * <p>所有扫描、寻路、方块修改和攻击都由服务器主线程执行。搜索只发生在启动或完成一次采集后，
 * 带实体 ID 错峰冷却，并限制原始方块检查和寻路候选数量；不存在邻近僵尸两两扫描。</p>
 */
public final class ZombieTerrainTacticsGoal extends Goal {
	static final int PILLAR_HEIGHT = 3;
	static final int MAX_ELEVATION_PILLAR_HEIGHT = 4;
	private static final int MINIMUM_RETRY_DELAY_TICKS = 30;
	private static final int RETRY_DELAY_VARIANCE_TICKS = 20;
	private static final int ACTIVE_SETUP_TIMEOUT_TICKS = 500;
	private static final int PATH_REFRESH_TICKS = 10;
	private static final int MAXIMUM_RAW_BLOCK_CHECKS = 320;
	private static final int MAXIMUM_HARVEST_PATH_CHECKS = 4;
	private static final int MAXIMUM_PILLAR_PATH_CHECKS = 8;
	private static final int HARVEST_RADIUS = 5;
	private static final double DIG_REACH_SQUARED = 3.1 * 3.1;
	private static final double PILLAR_CENTER_REACHED_SQUARED = 0.42 * 0.42;
	private static final double MOVE_SPEED_MODIFIER = 1.10;
	private static final int JUMP_PLACEMENT_TIMEOUT_TICKS = 30;
	private static final int PERCHED_TARGET_WAIT_TICKS = 120;
	private static final double TARGET_MAXIMUM_DISTANCE_SQUARED = 18.0 * 18.0;
	private static final double PERCHED_MAXIMUM_HORIZONTAL_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double DOWNWARD_ATTACK_HORIZONTAL_DISTANCE_SQUARED = 3.25 * 3.25;
	private static final double DOWNWARD_ATTACK_MAXIMUM_VERTICAL_DISTANCE = 4.5;
	private static final double MINIMUM_ELEVATION_ADVANTAGE = 2.0;
	private static final double ELEVATED_TARGET_TRIGGER_HORIZONTAL_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double ELEVATION_PILLAR_MINIMUM_HORIZONTAL_DISTANCE_SQUARED = 1.15 * 1.15;
	private static final double ELEVATION_PILLAR_MAXIMUM_HORIZONTAL_DISTANCE_SQUARED = 3.25 * 3.25;
	private static final int[][] PILLAR_OFFSETS = {
		{3, 0}, {-3, 0}, {0, 3}, {0, -3},
		{2, 2}, {2, -2}, {-2, 2}, {-2, -2}
	};

	private final Zombie zombie;
	private Phase phase = Phase.IDLE;
	private Phase preparedPhase = Phase.IDLE;
	private @Nullable LivingEntity target;
	private @Nullable BlockPos harvestPos;
	private @Nullable BlockPos harvestStandPos;
	private @Nullable BlockPos pillarBase;
	private @Nullable Path preparedPath;
	private @Nullable BlockState pillarMaterial;
	private PillarPurpose pillarPurpose = PillarPurpose.NONE;
	private int pillarHeight;
	private long nextAttemptAt;
	private long setupDeadline;
	private long nextPathRefreshAt;
	private long jumpStartedAt;
	private long nextPerchedAttackAt;
	private long perchedTargetWaitDeadline;
	private int breakTicks;
	private int breakDurationTicks;
	private int lastBreakStage = -1;
	private int placedBlocks;
	private boolean jumpRequested;

	public ZombieTerrainTacticsGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!canUseTerrainTactics(this.zombie, config)
			|| !(this.zombie.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}

		long now = level.getGameTime();
		if (now < this.nextAttemptAt) {
			return false;
		}
		// 每只僵尸按实体 ID 和自己的随机源错开昂贵检查，避免同一刷怪批次在同一 tick 集中寻路。
		this.nextAttemptAt = now
			+ MINIMUM_RETRY_DELAY_TICKS
			+ Math.floorMod(this.zombie.getId(), 7)
			+ this.zombie.getRandom().nextInt(RETRY_DELAY_VARIANCE_TICKS + 1);

		LivingEntity currentTarget = this.zombie.getTarget();
		if (!isEligibleTarget(currentTarget)
			|| this.zombie.distanceToSqr(currentTarget) > TARGET_MAXIMUM_DISTANCE_SQUARED) {
			return false;
		}
		this.target = currentTarget;
		this.clearPreparedAction();

		PillarPlan plan = this.selectPillarPlan(level, currentTarget, config);
		if (plan == null) {
			// 保留原有“铁傀儡战先备料、材料齐后再找最终柱位”的容错；高处目标则必须先有紧凑柱位。
			if (!(currentTarget instanceof IronGolem)
				|| ZombieBuilderInventory.count(this.zombie) >= PILLAR_HEIGHT) {
				return false;
			}
			this.pillarPurpose = PillarPurpose.GOLEM_PERCH;
			this.pillarHeight = PILLAR_HEIGHT;
		} else {
			this.pillarPurpose = plan.purpose();
			this.pillarHeight = plan.height();
			if (ZombieBuilderInventory.count(this.zombie) >= plan.height()) {
				this.preparePillar(plan);
				return true;
			}
		}

		HarvestPlan harvest = this.findReachableHarvest(level, config);
		if (harvest == null) {
			return false;
		}
		this.prepareHarvest(harvest);
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!canUseTerrainTactics(this.zombie, config)
			|| !(this.zombie.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| this.phase == Phase.IDLE
			|| this.phase == Phase.DONE) {
			return false;
		}

		LivingEntity combatTarget = this.target;
		if (combatTarget == null || !combatTarget.isAlive() || this.zombie.getTarget() != combatTarget) {
			return false;
		}
		if (this.phase == Phase.PERCHED) {
			return this.pillarPurpose == PillarPurpose.GOLEM_PERCH
				&& combatTarget instanceof IronGolem
				&& horizontalDistanceSquared(this.zombie.position(), combatTarget.position())
				<= PERCHED_MAXIMUM_HORIZONTAL_DISTANCE_SQUARED;
		}
		if (this.pillarPurpose == PillarPurpose.ELEVATED_TARGET && this.pillarBase != null) {
			boolean heightStillUseful = requiredElevationPillarHeight(
				this.pillarBase.getY(),
				combatTarget.getBoundingBox().minY,
				MAX_ELEVATION_PILLAR_HEIGHT
			) > 0;
			boolean targetStillNearby = horizontalDistanceSquared(
				Vec3.atBottomCenterOf(this.pillarBase),
				combatTarget.position()
			) <= ELEVATED_TARGET_TRIGGER_HORIZONTAL_DISTANCE_SQUARED;
			if (!heightStillUseful || !targetStillNearby) {
				// 目标主动跳下、被击落或远离柱位时，立即让 stop() 回收未完成立柱。
				return false;
			}
		}
		return level.getGameTime() < this.setupDeadline
			&& this.zombie.distanceToSqr(combatTarget) <= TARGET_MAXIMUM_DISTANCE_SQUARED;
	}

	@Override
	public void start() {
		long now = this.zombie.level().getGameTime();
		this.phase = this.preparedPhase;
		this.setupDeadline = now + ACTIVE_SETUP_TIMEOUT_TICKS;
		this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
		this.breakTicks = 0;
		this.breakDurationTicks = 0;
		this.lastBreakStage = -1;
		this.placedBlocks = 0;
		this.jumpRequested = false;
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);

		if (this.preparedPath != null) {
			this.zombie.getNavigation().moveTo(this.preparedPath, MOVE_SPEED_MODIFIER);
		}
		if (this.phase == Phase.DIGGING) {
			this.beginDigging();
		}
	}

	@Override
	public void tick() {
		this.zombie.setAggressive(false);
		switch (this.phase) {
			case MOVING_TO_BLOCK -> this.tickMovingToBlock();
			case DIGGING -> this.tickDigging();
			case MOVING_TO_PILLAR -> this.tickMovingToPillar();
			case PILLARING -> this.tickPillaring();
			case PERCHED -> this.tickPerched();
			case IDLE, DONE -> {
			}
		}
	}

	@Override
	public void stop() {
		this.clearBreakProgress();
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);

		// 垫脚中途被更高优先级撤退打断时，立即回收自己刚放的半成品，避免地面留下大量一两格残柱。
		if (this.placedBlocks > 0 && this.placedBlocks < this.pillarHeight) {
			this.reclaimPartialPillar();
		}

		this.phase = Phase.IDLE;
		this.preparedPhase = Phase.IDLE;
		this.harvestPos = null;
		this.harvestStandPos = null;
		this.preparedPath = null;
		this.pillarMaterial = null;
		this.pillarPurpose = PillarPurpose.NONE;
		this.pillarHeight = 0;
		this.placedBlocks = 0;
		this.jumpRequested = false;
		this.nextAttemptAt = Math.max(this.nextAttemptAt, this.zombie.level().getGameTime() + MINIMUM_RETRY_DELAY_TICKS);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void tickMovingToBlock() {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos block = this.harvestPos;
		BlockPos stand = this.harvestStandPos;
		MobsThinkNowConfig config = ConfigManager.get();
		if (block == null
			|| stand == null
			|| !ZombieBuilderInventory.isHarvestable(
				level,
				block,
				level.getBlockState(block),
				this.zombie,
				config.terrainBlockInventoryLimit
			)) {
			this.selectNextAction(level, config);
			return;
		}

		this.lookAt(block);
		if (this.isWithinDigReach(level, block)) {
			this.beginDigging();
			return;
		}

		long now = level.getGameTime();
		if (now >= this.nextPathRefreshAt) {
			boolean moving = this.zombie.getNavigation().moveTo(
				stand.getX() + 0.5,
				stand.getY(),
				stand.getZ() + 0.5,
				MOVE_SPEED_MODIFIER
			);
			this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
			if (!moving && this.zombie.getNavigation().isDone()) {
				this.selectNextAction(level, config);
			}
		}
	}

	private void beginDigging() {
		BlockPos block = this.harvestPos;
		if (block == null || !(this.zombie.level() instanceof ServerLevel level)) {
			this.phase = Phase.DONE;
			return;
		}
		this.zombie.getNavigation().stop();
		this.breakTicks = 0;
		this.lastBreakStage = -1;
		this.breakDurationTicks = ZombieBuilderInventory.emptyHandBreakTicks(
			level.getBlockState(block).getDestroySpeed(level, block)
		);
		this.phase = Phase.DIGGING;
	}

	private void tickDigging() {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos block = this.harvestPos;
		MobsThinkNowConfig config = ConfigManager.get();
		if (block == null) {
			this.phase = Phase.DONE;
			return;
		}
		BlockState state = level.getBlockState(block);
		if (!ZombieBuilderInventory.isHarvestable(
			level,
			block,
			state,
			this.zombie,
			config.terrainBlockInventoryLimit
		)) {
			this.clearBreakProgress();
			this.selectNextAction(level, config);
			return;
		}
		if (!this.isWithinDigReach(level, block)) {
			this.clearBreakProgress();
			this.phase = Phase.MOVING_TO_BLOCK;
			this.nextPathRefreshAt = 0L;
			return;
		}

		this.zombie.getNavigation().stop();
		this.lookAt(block);
		this.breakTicks++;
		int stage = Math.min(9, this.breakTicks * 10 / Math.max(1, this.breakDurationTicks));
		if (stage != this.lastBreakStage) {
			level.destroyBlockProgress(this.zombie.getId(), block, stage);
			this.lastBreakStage = stage;
		}
		if (this.breakTicks == 1 || this.breakTicks % 5 == 0) {
			this.playMiningFeedback(level, block, state);
		}
		if (this.breakTicks < this.breakDurationTicks) {
			return;
		}

		ItemStack result = ZombieBuilderInventory.harvestResult(state);
		this.clearBreakProgress();
		// 先验证槽位再破坏；服务器主线程内两步之间没有并发写入，因此不会出现方块消失而材料丢失。
		// Level.destroyBlock 会广播 2001 原版破坏事件与 BLOCK_DESTROY，因此结束瞬间还有碎屑和破坏声。
		if (ZombieBuilderInventory.canAccept(this.zombie, result, config.terrainBlockInventoryLimit)
			&& level.destroyBlock(block, false, this.zombie, 512)) {
			ZombieBuilderInventory.addOne(this.zombie, result, config.terrainBlockInventoryLimit);
			SmartZombieMetrics.terrainBlockHarvested();
		}
		this.selectNextAction(level, config);
	}

	private void selectNextAction(final ServerLevel level, final MobsThinkNowConfig config) {
		LivingEntity combatTarget = this.target;
		if (combatTarget == null) {
			this.phase = Phase.DONE;
			return;
		}
		PillarPlan plan = this.selectPillarPlan(level, combatTarget, config);
		if (plan == null) {
			if (!(combatTarget instanceof IronGolem)
				|| ZombieBuilderInventory.count(this.zombie) >= PILLAR_HEIGHT) {
				this.phase = Phase.DONE;
				return;
			}
			this.pillarPurpose = PillarPurpose.GOLEM_PERCH;
			this.pillarHeight = PILLAR_HEIGHT;
		} else {
			this.pillarPurpose = plan.purpose();
			this.pillarHeight = plan.height();
			if (ZombieBuilderInventory.count(this.zombie) >= plan.height()) {
				this.preparePillar(plan);
				this.phase = this.preparedPhase;
				this.beginPreparedNavigation();
				return;
			}
		}

		HarvestPlan next = this.findReachableHarvest(level, config);
		if (next == null) {
			this.phase = Phase.DONE;
			return;
		}
		this.prepareHarvest(next);
		this.phase = this.preparedPhase;
		this.beginPreparedNavigation();
		if (this.phase == Phase.DIGGING) {
			this.beginDigging();
		}
	}

	private void tickMovingToPillar() {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos base = this.pillarBase;
		if (base == null || this.pillarHeight <= 0 || !this.isClearPillarColumn(level, base, this.pillarHeight)) {
			this.phase = Phase.DONE;
			return;
		}

		LivingEntity combatTarget = this.target;
		if (combatTarget != null) {
			this.zombie.getLookControl().setLookAt(combatTarget, 30.0F, 30.0F);
		}
		if (horizontalDistanceSquared(this.zombie.position(), Vec3.atBottomCenterOf(base))
			<= PILLAR_CENTER_REACHED_SQUARED
			&& Math.abs(this.zombie.getY() - base.getY()) <= 0.35) {
			this.zombie.getNavigation().stop();
			this.phase = Phase.PILLARING;
			this.placedBlocks = 0;
			this.jumpRequested = false;
			this.pillarMaterial = ZombieBuilderInventory.placementState(this.zombie);
			return;
		}

		long now = level.getGameTime();
		if (now >= this.nextPathRefreshAt) {
			boolean moving = this.zombie.getNavigation().moveTo(
				base.getX() + 0.5,
				base.getY(),
				base.getZ() + 0.5,
				MOVE_SPEED_MODIFIER
			);
			this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
			if (!moving && this.zombie.getNavigation().isDone()) {
				this.phase = Phase.DONE;
			}
		}
	}

	private void tickPillaring() {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos base = this.pillarBase;
		BlockState material = this.pillarMaterial;
		if (base == null || material == null || material.isAir() || this.pillarHeight <= 0) {
			this.phase = Phase.DONE;
			return;
		}
		LivingEntity combatTarget = this.target;
		if (combatTarget != null) {
			this.zombie.getLookControl().setLookAt(combatTarget, 30.0F, 30.0F);
		}
		this.centerOverPillar(base);

		if (this.placedBlocks >= this.pillarHeight) {
			if (this.zombie.onGround() && this.zombie.getY() >= base.getY() + this.pillarHeight - 0.15) {
				if (this.pillarPurpose == PillarPurpose.GOLEM_PERCH) {
					this.phase = Phase.PERCHED;
					this.nextPerchedAttackAt = level.getGameTime();
					this.perchedTargetWaitDeadline = level.getGameTime() + PERCHED_TARGET_WAIT_TICKS;
				} else {
					// 已和高处目标站到同一战斗层；下一 tick 交还 MOVE/LOOK，让普通武器战斗接管。
					this.phase = Phase.DONE;
				}
				this.jumpRequested = false;
			}
			return;
		}

		BlockPos placementPos = base.above(this.placedBlocks);
		if (!canReplaceForPillar(level, placementPos)
			|| !material.canSurvive(level, placementPos)
			|| !Block.canSupportCenter(level, placementPos.below(), Direction.UP)) {
			this.phase = Phase.DONE;
			return;
		}

		long now = level.getGameTime();
		if (!this.jumpRequested) {
			double expectedFeetY = base.getY() + this.placedBlocks;
			if (this.zombie.onGround() && Math.abs(this.zombie.getY() - expectedFeetY) <= 0.20) {
				this.zombie.getJumpControl().jump();
				this.jumpRequested = true;
				this.jumpStartedAt = now;
			}
			return;
		}

		// 必须等实体脚底完整越过方块顶面；这与玩家跳起后向脚下放块的碰撞边界一致。
		if (this.zombie.getBoundingBox().minY >= placementPos.getY() + 0.999
			&& level.isUnobstructed(material, placementPos, CollisionContext.of(this.zombie))) {
			if (level.setBlock(placementPos, material, Block.UPDATE_ALL)) {
				ZombieBuilderInventory.consumeOne(this.zombie);
				SmartZombieMetrics.terrainBlockPlaced();
				this.playPlacementFeedback(level, placementPos, material);
				this.placedBlocks++;
				this.jumpRequested = false;
				return;
			}
			this.phase = Phase.DONE;
			return;
		}

		if (now - this.jumpStartedAt > JUMP_PLACEMENT_TIMEOUT_TICKS
			|| (this.zombie.onGround() && now - this.jumpStartedAt > 3L)) {
			this.phase = Phase.DONE;
		}
	}

	private void tickPerched() {
		ServerLevel level = (ServerLevel)this.zombie.level();
		BlockPos base = this.pillarBase;
		LivingEntity combatTarget = this.target;
		if (base == null
			|| !(combatTarget instanceof IronGolem golem)
			|| !this.isStandingOnCompletePillar(level, base)) {
			this.phase = Phase.DONE;
			return;
		}

		this.zombie.getNavigation().stop();
		this.centerOverPillar(base);
		this.zombie.getLookControl().setLookAt(golem, 35.0F, 45.0F);
		long now = level.getGameTime();
		if (!this.canStrikeDownward(golem)) {
			if (now >= this.perchedTargetWaitDeadline) {
				this.phase = Phase.DONE;
			}
			return;
		}

		this.perchedTargetWaitDeadline = now + PERCHED_TARGET_WAIT_TICKS;
		if (now < this.nextPerchedAttackAt) {
			return;
		}
		this.zombie.setAggressive(true);
		this.zombie.swing(InteractionHand.MAIN_HAND);
		if (this.zombie.doHurtTarget(level, golem)) {
			SmartZombieMetrics.perchedAttack();
		}
		this.nextPerchedAttackAt = now + ZombieWeaponCombat.attackCooldownTicks(this.zombie.getMainHandItem());
	}

	private boolean canStrikeDownward(final IronGolem golem) {
		double verticalDistance = this.zombie.getY() - golem.getY();
		return verticalDistance > 0.0
			&& verticalDistance <= DOWNWARD_ATTACK_MAXIMUM_VERTICAL_DISTANCE
			&& horizontalDistanceSquared(this.zombie.position(), golem.position())
				<= DOWNWARD_ATTACK_HORIZONTAL_DISTANCE_SQUARED
			// 两个碰撞箱在竖直方向已经分离，正是铁傀儡原版近战范围失效而俯击生效的战术窗口。
			&& this.zombie.getBoundingBox().minY >= golem.getBoundingBox().maxY + 0.02
			&& this.zombie.getSensing().hasLineOfSight(golem);
	}

	private @Nullable HarvestPlan findReachableHarvest(
		final ServerLevel level,
		final MobsThinkNowConfig config
	) {
		BlockPos origin = this.zombie.blockPosition();
		List<BlockPos> candidates = new ArrayList<>();
		int rawChecks = 0;

		outer:
		for (int radius = 1; radius <= HARVEST_RADIUS; radius++) {
			for (int dy : new int[] {0, -1, 1}) {
				for (int dz = -radius; dz <= radius; dz++) {
					for (int dx = -radius; dx <= radius; dx++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
							continue;
						}
						if (rawChecks++ >= MAXIMUM_RAW_BLOCK_CHECKS) {
							break outer;
						}
						BlockPos pos = origin.offset(dx, dy, dz);
						if (this.isProtectedSupportBlock(pos)) {
							continue;
						}
						BlockState state = level.getBlockState(pos);
						if (ZombieBuilderInventory.isHarvestable(
							level,
							pos,
							state,
							this.zombie,
							config.terrainBlockInventoryLimit
						)) {
							candidates.add(pos.immutable());
						}
					}
				}
			}
		}

		candidates.sort(
			Comparator.comparingDouble((BlockPos pos) -> Vec3.atCenterOf(pos).distanceToSqr(this.zombie.position()))
				.thenComparingLong(BlockPos::asLong)
		);
		int[] pathChecks = {0};
		for (BlockPos block : candidates) {
			HarvestPlan plan = this.findReachableStandForBlock(level, block, pathChecks);
			if (plan == null) {
				continue;
			}
			return plan;
		}
		return null;
	}

	private @Nullable HarvestPlan findReachableStandForBlock(
		final ServerLevel level,
		final BlockPos block,
		final int[] pathChecks
	) {
		if (this.isWithinDigReach(level, block)) {
			return new HarvestPlan(block, this.zombie.blockPosition(), null);
		}
		if (pathChecks[0] >= MAXIMUM_HARVEST_PATH_CHECKS) {
			return null;
		}

		PathNavigation navigation = this.zombie.getNavigation();
		for (int yOffset : new int[] {0, 1, -1}) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos stand = block.relative(direction).offset(0, yOffset, 0);
				if (!this.canStandAt(level, stand)) {
					continue;
				}
				if (pathChecks[0]++ >= MAXIMUM_HARVEST_PATH_CHECKS) {
					return null;
				}
				Path path = navigation.createPath(stand, 0);
				if (path != null && path.canReach()) {
					return new HarvestPlan(block, stand.immutable(), path);
				}
			}
		}
		return null;
	}

	/**
	 * 先判断高差：只要目标在两格以上、水平距离足够近且原版路径真的到不了，就采用按实际高差
	 * 计算的追高立柱；否则仅保留原有的铁傀儡三格俯击策略。这样自然山坡仍走导航，只有玩家
	 * 垫柱等垂直障碍才触发方块改造。
	 */
	private @Nullable PillarPlan selectPillarPlan(
		final ServerLevel level,
		final LivingEntity combatTarget,
		final MobsThinkNowConfig config
	) {
		int maximumElevationHeight = Math.min(MAX_ELEVATION_PILLAR_HEIGHT, config.terrainBlockInventoryLimit);
		int elevationHeight = requiredElevationPillarHeight(
			this.zombie.getY(),
			combatTarget.getBoundingBox().minY,
			maximumElevationHeight
		);
		if (elevationHeight > 0
			&& horizontalDistanceSquared(this.zombie.position(), combatTarget.position())
				<= ELEVATED_TARGET_TRIGGER_HORIZONTAL_DISTANCE_SQUARED
			&& !this.hasReachableGroundPath(combatTarget)) {
			PillarPlan elevationPlan = this.findReachableElevationPillarBase(
				level,
				combatTarget,
				maximumElevationHeight
			);
			if (elevationPlan != null) {
				return elevationPlan;
			}
		}

		return combatTarget instanceof IronGolem golem
			? this.findReachableGolemPillarBase(level, golem)
			: null;
	}

	private boolean hasReachableGroundPath(final LivingEntity combatTarget) {
		Path path = this.zombie.getNavigation().createPath(combatTarget, 0);
		if (path == null || !path.canReach() || path.getEndNode() == null) {
			return false;
		}
		// 高处实体的路径可能只“到达”柱脚附近；终点还需进入目标脚底下一格，才算真实的战斗层可达。
		return path.getEndNode().y >= Math.floor(combatTarget.getBoundingBox().minY) - 1.0;
	}

	/**
	 * 候选只覆盖僵尸脚边三格的固定 7x7 区域，并最多做八次寻路；这不是实体扫描，数量也不随
	 * 附近僵尸数量增长。优先使用当前位置，只有当前位置离目标太远或柱体受阻时才走到邻格。
	 */
	private @Nullable PillarPlan findReachableElevationPillarBase(
		final ServerLevel level,
		final LivingEntity combatTarget,
		final int maximumHeight
	) {
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos zombieFeet = this.zombie.blockPosition();
		for (int radius = 0; radius <= 3; radius++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dx = -radius; dx <= radius; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					for (int yOffset : new int[] {0, -1, 1}) {
						candidates.add(zombieFeet.offset(dx, yOffset, dz).immutable());
					}
				}
			}
		}
		candidates.sort(
			Comparator.comparingDouble((BlockPos pos) -> Vec3.atBottomCenterOf(pos).distanceToSqr(this.zombie.position()))
				.thenComparingLong(BlockPos::asLong)
		);

		int pathChecks = 0;
		for (BlockPos base : candidates) {
			int height = requiredElevationPillarHeight(
				base.getY(),
				combatTarget.getBoundingBox().minY,
				maximumHeight
			);
			if (height == 0
				|| !isElevationPillarDistanceUseful(base, combatTarget)
				|| !this.isClearPillarColumn(level, base, height)) {
				continue;
			}
			if (horizontalDistanceSquared(this.zombie.position(), Vec3.atBottomCenterOf(base))
				<= PILLAR_CENTER_REACHED_SQUARED
				&& Math.abs(this.zombie.getY() - base.getY()) <= 0.35) {
				return new PillarPlan(base, null, height, PillarPurpose.ELEVATED_TARGET);
			}
			if (pathChecks++ >= MAXIMUM_PILLAR_PATH_CHECKS) {
				break;
			}
			Path path = this.zombie.getNavigation().createPath(base, 0);
			if (path != null && path.canReach()) {
				return new PillarPlan(base, path, height, PillarPurpose.ELEVATED_TARGET);
			}
		}
		return null;
	}

	private @Nullable PillarPlan findReachableGolemPillarBase(final ServerLevel level, final IronGolem golem) {
		List<BlockPos> candidates = new ArrayList<>();
		BlockPos zombieFeet = this.zombie.blockPosition();
		if (isPillarDistanceUseful(zombieFeet, golem)) {
			candidates.add(zombieFeet.immutable());
		}

		BlockPos targetFeet = golem.blockPosition();
		for (int[] offset : PILLAR_OFFSETS) {
			for (int yOffset : new int[] {0, 1, -1}) {
				candidates.add(targetFeet.offset(offset[0], yOffset, offset[1]).immutable());
			}
		}
		candidates.sort(
			Comparator.comparingDouble((BlockPos pos) -> Vec3.atBottomCenterOf(pos).distanceToSqr(this.zombie.position()))
				.thenComparingLong(BlockPos::asLong)
		);

		int pathChecks = 0;
		for (BlockPos base : candidates) {
			if (!isPillarDistanceUseful(base, golem)
				|| !isPillarHeightSafe(base, golem)
				|| !this.isClearPillarColumn(level, base, PILLAR_HEIGHT)) {
				continue;
			}
			if (horizontalDistanceSquared(this.zombie.position(), Vec3.atBottomCenterOf(base))
				<= PILLAR_CENTER_REACHED_SQUARED
				&& Math.abs(this.zombie.getY() - base.getY()) <= 0.35) {
				return new PillarPlan(base, null, PILLAR_HEIGHT, PillarPurpose.GOLEM_PERCH);
			}
			if (pathChecks++ >= MAXIMUM_PILLAR_PATH_CHECKS) {
				break;
			}
			Path path = this.zombie.getNavigation().createPath(base, 0);
			if (path != null && path.canReach()) {
				return new PillarPlan(base, path, PILLAR_HEIGHT, PillarPurpose.GOLEM_PERCH);
			}
		}
		return null;
	}

	private boolean isClearPillarColumn(final ServerLevel level, final BlockPos base, final int height) {
		BlockState foundation = level.getBlockState(base.below());
		if (!foundation.isCollisionShapeFullBlock(level, base.below())) {
			return false;
		}
		for (int dy = 0; dy <= height + 2; dy++) {
			if (!canReplaceForPillar(level, base.above(dy))) {
				return false;
			}
		}

		AABB column = new AABB(
			base.getX(),
			base.getY(),
			base.getZ(),
			base.getX() + 1.0,
			base.getY() + height + 2.0,
			base.getZ() + 1.0
		).deflate(0.02);
		return level.getEntitiesOfClass(
			LivingEntity.class,
			column,
			entity -> entity.isAlive() && entity != this.zombie
		).isEmpty();
	}

	private boolean canStandAt(final ServerLevel level, final BlockPos feet) {
		if (!canReplaceForPillar(level, feet)
			|| !canReplaceForPillar(level, feet.above())
			|| !level.getBlockState(feet.below()).isCollisionShapeFullBlock(level, feet.below())) {
			return false;
		}
		Vec3 destination = Vec3.atBottomCenterOf(feet);
		AABB destinationBox = this.zombie.getBoundingBox().move(
			destination.x - this.zombie.getX(),
			destination.y - this.zombie.getY(),
			destination.z - this.zombie.getZ()
		);
		return level.noCollision(this.zombie, destinationBox);
	}

	private boolean isProtectedSupportBlock(final BlockPos pos) {
		if (pos.equals(this.zombie.blockPosition().below())) {
			return true;
		}
		LivingEntity combatTarget = this.target;
		return combatTarget != null && pos.equals(combatTarget.blockPosition().below());
	}

	private boolean isWithinDigReach(final ServerLevel level, final BlockPos block) {
		Vec3 eye = this.zombie.getEyePosition();
		Vec3 center = Vec3.atCenterOf(block);
		if (eye.distanceToSqr(center) > DIG_REACH_SQUARED) {
			return false;
		}
		// 距离足够仍需真实射线首先命中该方块，避免隔墙或隔地板“透视挖掘”。
		BlockHitResult hit = level.clip(new ClipContext(
			eye,
			center,
			ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE,
			this.zombie
		));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(block);
	}

	private void prepareHarvest(final HarvestPlan plan) {
		this.harvestPos = plan.block();
		this.harvestStandPos = plan.stand();
		this.preparedPath = plan.path();
		this.preparedPhase = plan.path() == null ? Phase.DIGGING : Phase.MOVING_TO_BLOCK;
	}

	private void preparePillar(final PillarPlan plan) {
		this.pillarBase = plan.base();
		this.preparedPath = plan.path();
		this.pillarHeight = plan.height();
		this.pillarPurpose = plan.purpose();
		this.preparedPhase = plan.path() == null ? Phase.PILLARING : Phase.MOVING_TO_PILLAR;
		this.pillarMaterial = ZombieBuilderInventory.placementState(this.zombie);
	}

	private void beginPreparedNavigation() {
		this.nextPathRefreshAt = this.zombie.level().getGameTime() + PATH_REFRESH_TICKS;
		if (this.preparedPath != null) {
			this.zombie.getNavigation().moveTo(this.preparedPath, MOVE_SPEED_MODIFIER);
		}
	}

	private void clearPreparedAction() {
		this.preparedPhase = Phase.IDLE;
		this.harvestPos = null;
		this.harvestStandPos = null;
		this.pillarBase = null;
		this.preparedPath = null;
		this.pillarMaterial = null;
		this.pillarPurpose = PillarPurpose.NONE;
		this.pillarHeight = 0;
	}

	private void clearBreakProgress() {
		if (this.harvestPos != null && this.zombie.level() instanceof ServerLevel level && this.lastBreakStage >= 0) {
			level.destroyBlockProgress(this.zombie.getId(), this.harvestPos, -1);
		}
		this.lastBreakStage = -1;
		this.breakTicks = 0;
	}

	private void centerOverPillar(final BlockPos base) {
		double wantedX = base.getX() + 0.5;
		double wantedZ = base.getZ() + 0.5;
		double correctionX = clamp((wantedX - this.zombie.getX()) * 0.20, -0.08, 0.08);
		double correctionZ = clamp((wantedZ - this.zombie.getZ()) * 0.20, -0.08, 0.08);
		Vec3 movement = this.zombie.getDeltaMovement();
		this.zombie.setDeltaMovement(
			movement.x * 0.55 + correctionX,
			movement.y,
			movement.z * 0.55 + correctionZ
		);
	}

	/** 每五 tick 同步一次挥臂、方块命中声和裂纹进度，让采集过程而非只有结果可被玩家读懂。 */
	private void playMiningFeedback(final ServerLevel level, final BlockPos pos, final BlockState state) {
		this.zombie.swing(InteractionHand.MAIN_HAND);
		SoundType sound = state.getSoundType();
		level.playSound(
			null,
			pos,
			sound.getHitSound(),
			SoundSource.BLOCKS,
			sound.getVolume() * 0.35F,
			sound.getPitch() * 0.75F
		);
	}

	/** 一格只播放一次挥臂、材质放置声和方块事件；服务器会把三种反馈同步给附近客户端。 */
	private void playPlacementFeedback(final ServerLevel level, final BlockPos pos, final BlockState state) {
		SoundType sound = state.getSoundType();
		level.playSound(
			null,
			pos,
			sound.getPlaceSound(),
			SoundSource.BLOCKS,
			(sound.getVolume() + 1.0F) / 2.0F,
			sound.getPitch() * 0.8F
		);
		level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.zombie, state));
		this.zombie.swing(InteractionHand.MAIN_HAND);
	}

	private boolean isStandingOnCompletePillar(final ServerLevel level, final BlockPos base) {
		if (this.pillarHeight <= 0
			|| !this.zombie.onGround()
			|| this.zombie.getY() < base.getY() + this.pillarHeight - 0.20) {
			return false;
		}
		for (int dy = 0; dy < this.pillarHeight; dy++) {
			if (!level.getBlockState(base.above(dy)).isCollisionShapeFullBlock(level, base.above(dy))) {
				return false;
			}
		}
		return true;
	}

	private void reclaimPartialPillar() {
		if (!(this.zombie.level() instanceof ServerLevel level) || this.pillarBase == null || this.pillarMaterial == null) {
			return;
		}
		MobsThinkNowConfig config = ConfigManager.get();
		ItemStack material = this.pillarMaterial.getBlock().asItem().getDefaultInstance();
		for (int dy = this.placedBlocks - 1; dy >= 0; dy--) {
			BlockPos pos = this.pillarBase.above(dy);
			if (level.getBlockState(pos) != this.pillarMaterial
				|| !ZombieBuilderInventory.canAccept(this.zombie, material, config.terrainBlockInventoryLimit)) {
				continue;
			}
			if (level.destroyBlock(pos, false, this.zombie, 512)) {
				ZombieBuilderInventory.addOne(this.zombie, material, config.terrainBlockInventoryLimit);
			}
		}
	}

	private void lookAt(final BlockPos pos) {
		this.zombie.getLookControl().setLookAt(
			pos.getX() + 0.5,
			pos.getY() + 0.5,
			pos.getZ() + 0.5,
			30.0F,
			45.0F
		);
	}

	static boolean canUseTerrainTactics(final Zombie zombie, final MobsThinkNowConfig config) {
		return config.enabled
			&& config.zombieAiEnabled
			&& config.terrainTactics
			&& zombie.getType() == EntityType.ZOMBIE
			&& zombie.isAlive()
			&& ZombieIntelligence.get(zombie) >= config.terrainMinimumIntelligence;
	}

	static boolean isEligibleTarget(final @Nullable LivingEntity target) {
		return target != null
			&& target.isAlive()
			&& (!(target instanceof Player player) || (!player.isCreative() && !player.isSpectator()));
	}

	/**
	 * 目标脚底高出柱基至少两格才值得建造；高度按整格向上取整，且超过受控上限时整项放弃，
	 * 避免用连续短柱追逐极端高空目标。
	 */
	static int requiredElevationPillarHeight(
		final double baseY,
		final double targetFeetY,
		final int maximumHeight
	) {
		double elevation = targetFeetY - baseY;
		if (maximumHeight <= 0 || elevation + 1.0E-3 < MINIMUM_ELEVATION_ADVANTAGE) {
			return 0;
		}
		int required = (int)Math.ceil(elevation - 1.0E-3);
		return required <= maximumHeight ? required : 0;
	}

	static boolean isElevationPillarDistanceUseful(final BlockPos base, final LivingEntity target) {
		double x = base.getX() + 0.5 - target.getX();
		double z = base.getZ() + 0.5 - target.getZ();
		double squared = x * x + z * z;
		return squared >= ELEVATION_PILLAR_MINIMUM_HORIZONTAL_DISTANCE_SQUARED
			&& squared <= ELEVATION_PILLAR_MAXIMUM_HORIZONTAL_DISTANCE_SQUARED;
	}

	static boolean isPillarDistanceUseful(final BlockPos base, final LivingEntity target) {
		double x = base.getX() + 0.5 - target.getX();
		double z = base.getZ() + 0.5 - target.getZ();
		double squared = x * x + z * z;
		return squared >= 2.4 * 2.4 && squared <= 4.25 * 4.25;
	}

	/** 预估完成后的脚底必须高于铁傀儡碰撞箱，同时仍处在俯击的最大垂直距离内。 */
	static boolean isPillarHeightSafe(final BlockPos base, final LivingEntity target) {
		double futureFeetY = base.getY() + PILLAR_HEIGHT;
		double verticalDistance = futureFeetY - target.getY();
		return futureFeetY >= target.getBoundingBox().maxY + 0.02
			&& verticalDistance > 0.0
			&& verticalDistance <= DOWNWARD_ATTACK_MAXIMUM_VERTICAL_DISTANCE;
	}

	static boolean canReplaceForPillar(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private enum Phase {
		IDLE,
		MOVING_TO_BLOCK,
		DIGGING,
		MOVING_TO_PILLAR,
		PILLARING,
		PERCHED,
		DONE
	}

	private enum PillarPurpose {
		NONE,
		GOLEM_PERCH,
		ELEVATED_TARGET
	}

	private record HarvestPlan(BlockPos block, BlockPos stand, @Nullable Path path) {
	}

	private record PillarPlan(BlockPos base, @Nullable Path path, int height, PillarPurpose purpose) {
	}
}

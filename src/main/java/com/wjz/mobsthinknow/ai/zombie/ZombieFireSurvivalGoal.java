package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 僵尸的火焰与日光生存状态机。
 *
 * <p>真正着火时，行为优先级高于战斗：水桶兵先在脚下制造真实水源，其他僵尸只做有界的
 * 水体搜索，寻路到可站立的岸边后再用 MoveControl 跨进水里。若附近没有可达水体，则只向
 * 已存在的小队发出一次有界救火请求，不会每 tick 全局扫描实体；日晒但尚未着火时仍沿用
 * “放水争取时间，再寻找阴影”的生存逻辑。火焰熄灭后，近期受击会立即把 MOVE/LOOK 交还
 * 撤退或攻击 Goal。</p>
 */
public final class ZombieFireSurvivalGoal extends Goal {
	private static final double SHADE_SPEED = 1.18;
	private static final double WATER_ENTRY_SPEED = 1.28;
	private static final double WATER_ENTRY_REACHED_SQUARED = 1.75 * 1.75;
	private static final int SHADE_SEARCH_INTERVAL_TICKS = 20;
	private static final int WATER_SEARCH_INTERVAL_TICKS = 20;
	private static final int FAILED_SEARCH_RETRY_TICKS = 10;
	private static final int SUPPORT_ALERT_INTERVAL_TICKS = 80;
	private static final int PATH_REFRESH_TICKS = 12;
	private static final int MAXIMUM_SHADE_PATH_CHECKS = 6;
	private static final int MAXIMUM_WATER_PATH_CHECKS = 8;
	private static final int WATER_SEARCH_RADIUS = 12;
	private static final int SURVIVAL_WATER_MINIMUM_HOLD_TICKS = 60;
	private static final int[] SHADE_VERTICAL_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
	private static final int[] WATER_VERTICAL_OFFSETS = {0, -1, 1, -2, 2};
	private static final int[] ENTRY_VERTICAL_OFFSETS = {0, 1, -1};
	private static final Direction[] HORIZONTAL_DIRECTIONS = {
		Direction.NORTH,
		Direction.EAST,
		Direction.SOUTH,
		Direction.WEST
	};

	private final Zombie zombie;
	private final Activation activation;
	private EscapeMode mode = EscapeMode.NONE;
	private @Nullable Path shadePath;
	private @Nullable Path waterEntryPath;
	private @Nullable BlockPos waterEntry;
	private @Nullable BlockPos waterDestination;
	private long nextShadeSearchAt;
	private long nextWaterSearchAt;
	private long nextPathRefreshAt;
	private long nextSupportAlertAt;
	private boolean waterDeployed;

	public ZombieFireSurvivalGoal(final Zombie zombie) {
		this.zombie = zombie;
		this.activation = Activation.BOTH;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	/** priority 0 传 {@code true} 仅处理真实着火；priority 1 传 {@code false} 仅处理日晒。 */
	public ZombieFireSurvivalGoal(final Zombie zombie, final boolean activeFireOnly) {
		this.zombie = zombie;
		this.activation = activeFireOnly ? Activation.FIRE_ONLY : Activation.SUNLIGHT_ONLY;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!(this.zombie.level() instanceof ServerLevel level)
			|| !this.zombie.isAlive()
			|| !isEnabled(ConfigManager.get())) {
			return false;
		}

		boolean burning = this.zombie.isOnFire();
		if (burning && this.zombie.isInWaterOrRain()) {
			this.zombie.extinguishFire();
			burning = false;
		}
		if ((this.activation == Activation.FIRE_ONLY && !burning)
			|| (this.activation == Activation.SUNLIGHT_ONLY && burning)) {
			return false;
		}
		if (burning) {
			this.alertWaterSupport(level);
		}

		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		boolean sunlightDanger = ZombieSunlightRules.isExposed(this.zombie, level)
			|| (state.isDeployed()
				&& state.isSurvivalProtection()
				&& ZombieSunlightRules.requiresEscape(this.zombie, level));
		if (!burning && (!sunlightDanger || ZombieCombatUrgency.wasRecentlyAttacked(this.zombie))) {
			return false;
		}

		if (this.canDeployOwnWater(level)) {
			this.mode = EscapeMode.SELF_WATER;
			return true;
		}
		if (burning && this.prepareWaterEscape(level, false)) {
			this.mode = EscapeMode.WATER;
			return true;
		}
		if (sunlightDanger) {
			this.mode = EscapeMode.SHADE;
			this.prepareShadeEscape(level, false);
			return true;
		}

		// 夜间等非日晒火焰若找不到水，不霸占 MOVE/LOOK；小队水桶兵仍可依据上面的求援赶来。
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		if (!(this.zombie.level() instanceof ServerLevel level)
			|| !this.zombie.isAlive()
			|| !isEnabled(ConfigManager.get())) {
			return false;
		}
		if (this.zombie.isOnFire()) {
			return this.activation != Activation.SUNLIGHT_ONLY;
		}
		if (this.activation == Activation.FIRE_ONLY) {
			return false;
		}
		return !ZombieCombatUrgency.wasRecentlyAttacked(this.zombie)
			&& ZombieSunlightRules.requiresEscape(this.zombie, level);
	}

	@Override
	public void start() {
		this.zombie.getNavigation().stop();
		this.zombie.stopUsingItem();
		this.zombie.setAggressive(false);
		ZombieFluidCarrierState state = ZombieSpecialEquipment.state(this.zombie);
		this.waterDeployed = state.isDeployed() && state.isSurvivalProtection();
		this.nextPathRefreshAt = this.zombie.level().getGameTime();

		ServerLevel level = (ServerLevel)this.zombie.level();
		if (this.mode == EscapeMode.SELF_WATER) {
			this.tryDeployOwnWater(level);
		}
		if (this.zombie.isOnFire()) {
			this.prepareWaterEscape(level, true);
			this.tickWaterMovement(level);
		} else {
			this.mode = EscapeMode.SHADE;
			this.prepareShadeEscape(level, true);
			this.startShadeMovement();
		}
	}

	@Override
	public void tick() {
		this.zombie.setAggressive(false);
		ServerLevel level = (ServerLevel)this.zombie.level();
		if (this.zombie.isOnFire() && this.zombie.isInWaterOrRain()) {
			this.zombie.extinguishFire();
		}

		if (this.zombie.isOnFire()) {
			this.alertWaterSupport(level);
			if (!this.waterDeployed && this.tryDeployOwnWater(level)) {
				return;
			}
			if (this.prepareWaterEscape(level, false)) {
				this.tickWaterMovement(level);
				return;
			}
			// 找不到水但正处于日晒危险时，至少先移动到阴影，避免火焰被反复续上。
			if (ZombieSunlightRules.requiresEscape(this.zombie, level)) {
				this.mode = EscapeMode.SHADE;
				this.tickShadeMovement(level);
			}
			return;
		}

		this.clearWaterPlan();
		if (!ZombieSunlightRules.requiresEscape(this.zombie, level)
			|| ZombieSunlightRules.isShaded(this.zombie, level)) {
			this.zombie.getNavigation().stop();
			return;
		}
		this.mode = EscapeMode.SHADE;
		this.tickShadeMovement(level);
	}

	@Override
	public void stop() {
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.mode = EscapeMode.NONE;
		this.shadePath = null;
		this.clearWaterPlan();
		this.nextPathRefreshAt = 0L;
		this.waterDeployed = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private boolean canDeployOwnWater(final ServerLevel level) {
		BlockPos placement = feetPosition(this.zombie);
		return !ZombieSpecialEquipment.state(this.zombie).isDeployed()
			&& ZombieSpecialEquipment.hasFullBucket(this.zombie, UtilityClass.WATER)
			&& ZombieFluidActions.canDeployAt(level, this.zombie, UtilityClass.WATER, placement);
	}

	private boolean tryDeployOwnWater(final ServerLevel level) {
		if (this.waterDeployed) {
			return false;
		}
		BlockPos placement = feetPosition(this.zombie);
		long now = level.getGameTime();
		if (!ZombieFluidActions.tryDeploy(
			level,
			this.zombie,
			UtilityClass.WATER,
			placement,
			now + SURVIVAL_WATER_MINIMUM_HOLD_TICKS,
			FluidDeploymentPurpose.SURVIVAL
		)) {
			return false;
		}

		// 水确实落在碰撞箱脚部才立即结算灭火；声音、游戏事件和挥手由真实桶管线负责。
		this.zombie.extinguishFire();
		this.waterDeployed = true;
		this.mode = EscapeMode.SHADE;
		this.clearWaterPlan();
		return true;
	}

	private void alertWaterSupport(final ServerLevel level) {
		long now = level.getGameTime();
		if (now < this.nextSupportAlertAt) {
			return;
		}
		ZombieSquadCoordinator.onSquadMemberBurning(this.zombie);
		this.nextSupportAlertAt = now + SUPPORT_ALERT_INTERVAL_TICKS;
	}

	private boolean prepareWaterEscape(final ServerLevel level, final boolean force) {
		if (this.hasValidWaterPlan(level)) {
			this.mode = EscapeMode.WATER;
			return true;
		}
		long now = level.getGameTime();
		if (!force && now < this.nextWaterSearchAt) {
			return false;
		}

		WaterEscapePlan plan = this.findWaterEscape(level);
		this.nextWaterSearchAt = now + (plan == null ? FAILED_SEARCH_RETRY_TICKS : WATER_SEARCH_INTERVAL_TICKS);
		if (plan == null) {
			this.clearWaterPlan();
			return false;
		}
		this.waterDestination = plan.water();
		this.waterEntry = plan.entry();
		this.waterEntryPath = plan.path();
		this.mode = EscapeMode.WATER;
		this.nextPathRefreshAt = now;
		return true;
	}

	private void tickWaterMovement(final ServerLevel level) {
		if (!this.hasValidWaterPlan(level)) {
			return;
		}
		BlockPos entry = this.waterEntry;
		BlockPos water = this.waterDestination;
		if (entry == null || water == null) {
			return;
		}

		this.zombie.getLookControl().setLookAt(Vec3.atCenterOf(water));
		double entryDistance = this.zombie.position().distanceToSqr(Vec3.atBottomCenterOf(entry));
		if (entryDistance <= WATER_ENTRY_REACHED_SQUARED) {
			// GroundPathNavigation 会把 WATER 判为不可站立；到岸后直接跨最后一步才能真正进入灭火体积。
			this.zombie.getNavigation().stop();
			this.zombie.getMoveControl().setWantedPosition(
				water.getX() + 0.5,
				water.getY() + 0.1,
				water.getZ() + 0.5,
				WATER_ENTRY_SPEED
			);
			return;
		}

		long now = level.getGameTime();
		if (now < this.nextPathRefreshAt && !this.zombie.getNavigation().isDone()) {
			return;
		}
		Path refreshed = this.waterEntryPath;
		this.waterEntryPath = null;
		if (refreshed == null) {
			refreshed = this.zombie.getNavigation().createPath(entry, 0);
		}
		if (refreshed != null && refreshed.canReach()) {
			this.waterEntryPath = refreshed;
			this.zombie.getNavigation().moveTo(refreshed, WATER_ENTRY_SPEED);
		} else {
			this.clearWaterPlan();
		}
		this.nextPathRefreshAt = now + PATH_REFRESH_TICKS;
	}

	private boolean hasValidWaterPlan(final ServerLevel level) {
		return this.waterDestination != null
			&& this.waterEntry != null
			&& isEnterableWater(level, this.waterDestination)
			&& ZombieTraversalRules.canStandAt(level, this.waterEntry);
	}

	private void clearWaterPlan() {
		this.waterEntryPath = null;
		this.waterEntry = null;
		this.waterDestination = null;
	}

	private @Nullable WaterEscapePlan findWaterEscape(final ServerLevel level) {
		BlockPos origin = this.zombie.blockPosition();
		int[] pathChecks = {0};
		for (int radius = 1; radius <= WATER_SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				WaterEscapePlan north = this.findWaterInColumn(
					level, origin.offset(dx, 0, -radius), pathChecks
				);
				if (north != null) {
					return north;
				}
				WaterEscapePlan south = this.findWaterInColumn(
					level, origin.offset(dx, 0, radius), pathChecks
				);
				if (south != null) {
					return south;
				}
				if (pathChecks[0] >= MAXIMUM_WATER_PATH_CHECKS) {
					return null;
				}
			}
			for (int dz = -radius + 1; dz < radius; dz++) {
				WaterEscapePlan west = this.findWaterInColumn(
					level, origin.offset(-radius, 0, dz), pathChecks
				);
				if (west != null) {
					return west;
				}
				WaterEscapePlan east = this.findWaterInColumn(
					level, origin.offset(radius, 0, dz), pathChecks
				);
				if (east != null) {
					return east;
				}
				if (pathChecks[0] >= MAXIMUM_WATER_PATH_CHECKS) {
					return null;
				}
			}
		}
		return null;
	}

	private @Nullable WaterEscapePlan findWaterInColumn(
		final ServerLevel level,
		final BlockPos column,
		final int[] pathChecks
	) {
		if (!level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(column.getX()),
			SectionPos.blockToSectionCoord(column.getZ())
		)) {
			return null;
		}
		for (int dy : WATER_VERTICAL_OFFSETS) {
			BlockPos water = column.offset(0, dy, 0);
			if (!isEnterableWater(level, water)) {
				continue;
			}
			for (Direction direction : HORIZONTAL_DIRECTIONS) {
				for (int entryDy : ENTRY_VERTICAL_OFFSETS) {
					BlockPos entry = water.relative(direction).offset(0, entryDy, 0);
					if (!ZombieTraversalRules.canStandAt(level, entry)) {
						continue;
					}
					if (this.zombie.blockPosition().equals(entry)) {
						return new WaterEscapePlan(water.immutable(), entry.immutable(), null);
					}
					if (pathChecks[0] >= MAXIMUM_WATER_PATH_CHECKS) {
						return null;
					}
					Path path = this.zombie.getNavigation().createPath(entry, 0);
					pathChecks[0]++;
					if (path != null && path.canReach()) {
						return new WaterEscapePlan(water.immutable(), entry.immutable(), path);
					}
				}
			}
		}
		return null;
	}

	private static boolean isEnterableWater(final ServerLevel level, final BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		BlockState above = level.getBlockState(pos.above());
		return state.getFluidState().is(FluidTags.WATER)
			&& state.getCollisionShape(level, pos).isEmpty()
			&& above.getCollisionShape(level, pos.above()).isEmpty();
	}

	private void tickShadeMovement(final ServerLevel level) {
		if (ZombieSunlightRules.isShaded(this.zombie, level)) {
			this.zombie.getNavigation().stop();
			return;
		}
		if (this.prepareShadeEscape(level, false)) {
			this.startShadeMovement();
		}
	}

	private boolean prepareShadeEscape(final ServerLevel level, final boolean force) {
		long now = level.getGameTime();
		if (!force
			&& this.shadePath != null
			&& !this.zombie.getNavigation().isDone()
			&& now < this.nextShadeSearchAt) {
			return true;
		}
		if (!force && now < this.nextShadeSearchAt) {
			return this.shadePath != null;
		}

		this.shadePath = this.findShadePath(level);
		this.nextShadeSearchAt = now + (this.shadePath == null
			? FAILED_SEARCH_RETRY_TICKS
			: SHADE_SEARCH_INTERVAL_TICKS);
		return this.shadePath != null;
	}

	private void startShadeMovement() {
		if (this.shadePath != null) {
			this.zombie.getNavigation().moveTo(this.shadePath, SHADE_SPEED);
		}
	}

	private @Nullable Path findShadePath(final ServerLevel level) {
		BlockPos origin = this.zombie.blockPosition();
		int phase = this.zombie.getRandom().nextInt(16);
		int pathChecks = 0;
		for (int ring = 1; ring <= 4; ring++) {
			int radius = ring * 3;
			for (int step = 0; step < 16; step++) {
				double angle = (phase + step) * Math.PI * 2.0 / 16.0;
				int dx = (int)Math.round(Math.cos(angle) * radius);
				int dz = (int)Math.round(Math.sin(angle) * radius);
				for (int dy : SHADE_VERTICAL_OFFSETS) {
					BlockPos candidate = origin.offset(dx, dy, dz);
					if (!level.getChunkSource().hasChunk(
						SectionPos.blockToSectionCoord(candidate.getX()),
						SectionPos.blockToSectionCoord(candidate.getZ())
					)
						|| level.canSeeSky(candidate.above())
						|| !ZombieTraversalRules.canStandAt(level, candidate)) {
						continue;
					}

					Path path = this.zombie.getNavigation().createPath(candidate, 0);
					if (path != null && path.canReach()) {
						return path;
					}
					if (++pathChecks >= MAXIMUM_SHADE_PATH_CHECKS) {
						return null;
					}
				}
			}
		}
		return null;
	}

	private static BlockPos feetPosition(final Zombie zombie) {
		return ZombieFluidActions.feetPosition(zombie);
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.zombieAiEnabled && config.sunlightSurvival;
	}

	private enum EscapeMode {
		NONE,
		SELF_WATER,
		WATER,
		SHADE
	}

	private enum Activation {
		BOTH,
		FIRE_ONLY,
		SUNLIGHT_ONLY
	}

	private record WaterEscapePlan(BlockPos water, BlockPos entry, @Nullable Path path) {
	}
}

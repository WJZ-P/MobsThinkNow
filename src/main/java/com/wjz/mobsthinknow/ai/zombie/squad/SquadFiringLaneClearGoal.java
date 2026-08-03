package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
import com.wjz.mobsthinknow.ai.utility.EscapePathing;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 挡住同队骷髅弹道的地面成员执行一次短侧移。
 *
 * <p>每次只检查走廊两侧各一个落点；射手租约过期、爆破提交、乘坐载具或更高优先级活动开始时
 * 立即结束。它不会把射击安全变成新的附近实体扫描。</p>
 */
public final class SquadFiringLaneClearGoal extends Goal {
	private static final int MAXIMUM_CLEAR_TICKS = 24;
	private static final double CLEAR_STEP = 1.75;

	private final PathfinderMob mob;
	private final double speedModifier;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.FIRING_LANE_CLEAR);
	private SquadFiringLaneRegistry.@Nullable Reservation lane;
	private @Nullable Vec3 destination;
	private int clearTicks;
	private int retryCooldown;
	private boolean active;

	public SquadFiringLaneClearGoal(final PathfinderMob mob, final double speedModifier) {
		this.mob = mob;
		this.speedModifier = speedModifier;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.retryCooldown-- > 0 || !enabled()) {
			return false;
		}
		if (this.mob.isPassenger()
			|| (this.mob instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0))
			|| !(this.mob.level() instanceof ServerLevel level)
			|| !this.activityLease.canAcquire(this.mob, this.mob.level().getGameTime())) {
			return false;
		}
		this.lane = ZombieSquadCoordinator.forLevel(level).blockingFiringLaneFor(this.mob);
		return this.lane != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.active
			|| !enabled()
			|| ++this.clearTicks > MAXIMUM_CLEAR_TICKS
			|| !this.activityLease.owns(this.mob, this.mob.level().getGameTime())
			|| !(this.mob.level() instanceof ServerLevel level)) {
			return false;
		}
		this.lane = ZombieSquadCoordinator.forLevel(level).blockingFiringLaneFor(this.mob);
		return this.lane != null && this.destination != null;
	}

	@Override
	public void start() {
		this.active = this.activityLease.acquire(this.mob, this.mob.level().getGameTime());
		this.clearTicks = 0;
		this.destination = null;
		if (!this.active || this.lane == null || !(this.mob.level() instanceof ServerLevel level)) {
			return;
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		this.destination = this.planClearStep(coordinator, this.lane);
		if (this.destination == null) {
			this.active = false;
			this.retryCooldown = 10;
		}
	}

	@Override
	public void tick() {
		if (!this.active || !this.activityLease.renew(this.mob, this.mob.level().getGameTime())) {
			return;
		}
		Vec3 currentDestination = this.destination;
		if (currentDestination != null) {
			EscapePathing.faceCurrentPathOrDestination(this.mob, currentDestination);
		}
	}

	@Override
	public void stop() {
		boolean cleared = this.active && this.lane == null;
		this.mob.getNavigation().stop();
		this.activityLease.release(this.mob);
		this.lane = null;
		this.destination = null;
		this.clearTicks = 0;
		this.active = false;
		if (cleared) {
			SmartSkeletonMetrics.allyClearedFiringLane();
		}
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	public @Nullable Vec3 destination() {
		return this.destination;
	}

	private @Nullable Vec3 planClearStep(
		final ZombieSquadCoordinator coordinator,
		final SquadFiringLaneRegistry.Reservation reservation
	) {
		Vec3 corridor = new Vec3(
			reservation.end().x - reservation.start().x,
			0.0,
			reservation.end().z - reservation.start().z
		);
		if (corridor.horizontalDistanceSqr() < 1.0E-6) {
			return null;
		}
		corridor = corridor.normalize();
		Vec3 lateral = new Vec3(-corridor.z, 0.0, corridor.x);
		Vec3 fromLineOrigin = this.mob.position().subtract(reservation.start());
		double preferredSide = fromLineOrigin.dot(lateral) >= 0.0 ? 1.0 : -1.0;
		Vec3 lastCandidate = null;
		for (double side : new double[]{preferredSide, -preferredSide}) {
			Vec3 candidate = this.mob.position()
				.add(lateral.scale(side * CLEAR_STEP))
				.subtract(corridor.scale(0.35));
			lastCandidate = candidate;
			if (coordinator.isSharedDangerNear(this.mob, candidate)) {
				continue;
			}
			Path path = this.mob.getNavigation().createPath(BlockPos.containing(candidate), 0);
			if (path != null && path.canReach() && this.mob.getNavigation().moveTo(path, this.speedModifier)) {
				return candidate;
			}
		}
		if (lastCandidate != null) {
			coordinator.reportTraversalDanger(this.mob, lastCandidate, SquadDangerKind.ROUTE_BLOCKED, 1);
		}
		return null;
	}

	private static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.packSurrounding && config.squadFiringLaneReservations;
	}
}

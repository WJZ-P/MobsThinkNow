package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.ai.creeper.CreeperBlastEvacuationMath;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperMetrics;
import com.wjz.mobsthinknow.ai.utility.EscapePathing;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 混编小队成员听见同队苦力怕引信后，从真实爆炸伤害候选范围内撤离。
 *
 * <p>威胁发现使用协调器维护的活动爆点索引，不执行附近实体扫描。正在引信的苦力怕不会中断自己的
 * 提交，蜘蛛也不会逃离背上的爆破载荷；其他地面成员则暂时交出攻击、集合和射击控制权。</p>
 */
public final class SquadCreeperEvadeGoal extends Goal {
	private static final double DESTINATION_REACHED_DISTANCE_SQUARED = 2.25;
	private static final long PATH_REFRESH_TICKS = 4L;
	private static final long THREAT_REFRESH_TICKS = 3L;
	private static final int VERTICAL_SEARCH = 4;
	private static final int PATH_CANDIDATE_BUDGET = 6;
	private static final double MINIMUM_PARTIAL_PATH_STEP = 3.0;
	private static final double MINIMUM_DISTANCE_GAIN_SQUARED = 1.0;

	private final PathfinderMob mob;
	private @Nullable Creeper threat;
	private ZombieSquadCoordinator.@Nullable SquadBlastThreat blastThreat;
	private @Nullable Vec3 evacuationDestination;
	private long nextPathAt;
	private long nextThreatRefreshAt;

	public SquadCreeperEvadeGoal(final PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(config)
			|| !isEnabledForSpecies(config)
			|| this.mob.isPassenger()
			|| hasCommittedBomb(this.mob)
			|| !(this.mob.level() instanceof ServerLevel level)) {
			this.threat = null;
			this.blastThreat = null;
			return false;
		}
		this.blastThreat = ZombieSquadCoordinator.forLevel(level).nearestBlastThreatFor(this.mob);
		this.threat = this.blastThreat == null ? null : this.blastThreat.creeper();
		return this.blastThreat != null;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquadCoordinator.SquadBlastThreat current = this.blastThreat;
		return isEnabled(config)
			&& this.isEnabledForSpecies(config)
			&& this.mob.isAlive()
			&& !this.mob.isPassenger()
			&& !hasCommittedBomb(this.mob)
			&& current != null
			&& isPrimed(current.creeper())
			&& CreeperBlastEvacuationMath.shouldContinue(
				this.mob.position().distanceToSqr(current.center()),
				current.powered()
			);
	}

	@Override
	public void start() {
		long now = this.mob.level().getGameTime();
		this.evacuationDestination = null;
		this.nextPathAt = now;
		this.nextThreatRefreshAt = now;
		this.mob.stopUsingItem();
		this.mob.getNavigation().stop();
		this.mob.setAggressive(false);
		SmartCreeperMetrics.squadEvacuationStarted();
		this.updateEscapePath(now);
	}

	@Override
	public void tick() {
		long now = this.mob.level().getGameTime();
		if (now >= this.nextThreatRefreshAt && this.mob.level() instanceof ServerLevel level) {
			this.nextThreatRefreshAt = now + THREAT_REFRESH_TICKS;
			ZombieSquadCoordinator.SquadBlastThreat refreshed = ZombieSquadCoordinator.forLevel(level)
				.nearestBlastThreatFor(this.mob);
			if (refreshed == null) {
				this.blastThreat = null;
				this.threat = null;
			} else if (this.blastThreat == null
				|| refreshed.creeper() != this.blastThreat.creeper()
				|| refreshed.center().distanceToSqr(this.blastThreat.center()) > 1.0) {
				this.blastThreat = refreshed;
				this.threat = refreshed.creeper();
				this.evacuationDestination = null;
				this.nextPathAt = now;
				this.mob.getNavigation().stop();
			}
		}
		this.updateEscapePath(now);
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
		this.mob.setAggressive(this.mob.getTarget() != null && this.mob.getTarget().isAlive());
		this.threat = null;
		this.blastThreat = null;
		this.evacuationDestination = null;
		this.nextPathAt = 0L;
		this.nextThreatRefreshAt = 0L;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	/** GameTest 与诊断界面使用的只读路径目标。 */
	public @Nullable Vec3 evacuationDestination() {
		return this.evacuationDestination;
	}

	/** GameTest 与诊断界面使用的当前爆点。 */
	public @Nullable Creeper threat() {
		return this.threat;
	}

	private void updateEscapePath(final long now) {
		ZombieSquadCoordinator.SquadBlastThreat current = this.blastThreat;
		if (current == null || !isPrimed(current.creeper())) {
			return;
		}
		Creeper creeper = current.creeper();
		Vec3 blastCenter = current.center();
		boolean reachedDestination = this.evacuationDestination == null
			|| this.mob.position().distanceToSqr(this.evacuationDestination) <= DESTINATION_REACHED_DISTANCE_SQUARED;
		if (now < this.nextPathAt && !reachedDestination && !this.mob.getNavigation().isDone()) {
			return;
		}

		double currentDistance = this.mob.position().distanceTo(blastCenter);
		double pathStep = CreeperBlastEvacuationMath.pathStep(currentDistance, current.powered());
		double speed = CreeperBlastEvacuationMath.evacuationSpeed(creeper.getSwelling(1.0F));
		double currentDistanceSquared = this.mob.position().distanceToSqr(blastCenter);
		Vec3 selectedDestination = null;
		for (int attempt = 0; attempt < PATH_CANDIDATE_BUDGET; attempt++) {
			// 先找能一次跑出安全圈的远点；空间不足时逐级缩短，先获得一段真实可走的逃生路径。
			double partialStep = Math.max(MINIMUM_PARTIAL_PATH_STEP, pathStep - attempt * 1.25);
			Vec3 candidate = EscapePathing.findDestinationAwayFrom(
				this.mob,
				blastCenter,
				creeper.getLookAngle(),
				partialStep,
				partialStep + 4.0,
				VERTICAL_SEARCH
			);
			if (candidate.distanceToSqr(blastCenter)
				<= currentDistanceSquared + MINIMUM_DISTANCE_GAIN_SQUARED) {
				continue;
			}
			if (this.mob.getNavigation().moveTo(candidate.x, candidate.y, candidate.z, speed)) {
				selectedDestination = candidate;
				break;
			}
		}
		boolean foundPath = selectedDestination != null;
		this.evacuationDestination = selectedDestination;
		this.nextPathAt = now + (foundPath ? PATH_REFRESH_TICKS : 1L);
		if (foundPath) {
			EscapePathing.faceCurrentPathOrDestination(this.mob, selectedDestination);
		}
	}

	private boolean isEnabledForSpecies(final MobsThinkNowConfig config) {
		if (this.mob instanceof Zombie) {
			return config.zombieAiEnabled;
		}
		if (this.mob instanceof AbstractSkeleton) {
			return config.skeletonAiEnabled;
		}
		if (this.mob instanceof Creeper) {
			return config.creeperAiEnabled;
		}
		return this.mob instanceof Spider && config.spiderAiEnabled;
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled
			&& config.packSurrounding
			&& config.creeperAiEnabled
			&& config.creeperSquadEvacuation;
	}

	private static boolean hasCommittedBomb(final PathfinderMob mob) {
		if (mob instanceof Creeper creeper && isPrimed(creeper)) {
			return true;
		}
		Entity passenger = mob.getFirstPassenger();
		return passenger instanceof Creeper creeper && isPrimed(creeper);
	}

	private static boolean isPrimed(final @Nullable Creeper creeper) {
		return creeper != null
			&& creeper.isAlive()
			&& (creeper.isIgnited() || creeper.getSwellDir() > 0);
	}
}

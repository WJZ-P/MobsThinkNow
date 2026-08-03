package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.utility.EscapePathing;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 远程骷髅被当前目标贴脸时使用的最高移动优先级脱离行为。
 *
 * <p>普通弓箭 Goal 位于优先级 4，原版避日和避狼行为位于 2～3；如果把后撤只写在弓箭 Goal
 * 内部，任何正在运行的高优先级行为都可能让骷髅继续贴脸挨打。本 Goal 以优先级 1 独立占用
 * {@link Flag#MOVE} 与 {@link Flag#LOOK}：玩家、铁傀儡等当前目标进入触发距离便取消蓄力并强制脱离，
 * 身体和头部都朝逃生路径正向全速奔跑；达到更远的安全距离后才把控制权交还给远程 Goal。</p>
 *
 * <p>路径每六 tick 至多重算一次，并与僵尸受击撤退共用 {@link EscapePathing} 的陆地落点规划。
 * 狭窄地形找不到完整路径时也会面向威胁反方向向前冲刺，而不是背对行进方向举弓后退；八十 tick
 * 仍未脱困则短暂释放控制权，让弓箭 Goal 以保持瞄准的拉扯步伐反击，避免永久占用战斗状态机。</p>
 */
public final class SkeletonEmergencyDisengageGoal extends Goal {
	private static final double MINIMUM_ESCAPE_PATH_DISTANCE = 6.0;
	private static final double MAXIMUM_ESCAPE_PATH_DISTANCE = 12.0;
	private static final int VERTICAL_PATH_SEARCH = 5;
	private static final int MAXIMUM_DISENGAGE_TICKS = 80;
	private static final int TIMEOUT_COOLDOWN_TICKS = 20;
	private static final float FALLBACK_FORWARD = 1.0F;
	private static final float FALLBACK_SIDEWAYS = 0.35F;

	private final AbstractSkeleton skeleton;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.EMERGENCY_DISENGAGE);
	private @Nullable LivingEntity threat;
	private @Nullable Vec3 escapeDestination;
	private int elapsedTicks;
	private int pathRefreshCooldown;
	private int sideDirection = 1;
	private int sideSwitchTicks;
	private long nextAllowedStartAt;

	public SkeletonEmergencyDisengageGoal(final AbstractSkeleton skeleton) {
		this.skeleton = skeleton;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!isEnabled(config)
			|| !this.skeleton.isAlive()
			|| MountedSkeletonCombat.isManagedRider(this.skeleton)
			|| !this.isHoldingRangedWeapon()
			|| this.skeleton.level().getGameTime() < this.nextAllowedStartAt) {
			return false;
		}

		LivingEntity target = this.skeleton.getTarget();
		if (target == null || !isValidThreat(target)) {
			return false;
		}
		int intelligence = SkeletonIntelligence.get(this.skeleton);
		if (!SkeletonCombatMath.shouldStartEmergencyDisengage(
			horizontalDistanceSquared(this.skeleton.position(), target.position()),
			config.skeletonPreferredRange,
			intelligence
		)) {
			return false;
		}

		this.threat = target;
		return this.activityLease.canAcquire(this.skeleton, this.skeleton.level().getGameTime());
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		LivingEntity currentThreat = this.threat;
		int intelligence = SkeletonIntelligence.get(this.skeleton);
		return isEnabled(config)
			&& this.activityLease.owns(this.skeleton, this.skeleton.level().getGameTime())
			&& this.elapsedTicks < MAXIMUM_DISENGAGE_TICKS
			&& this.skeleton.isAlive()
			&& !MountedSkeletonCombat.isManagedRider(this.skeleton)
			&& this.isHoldingRangedWeapon()
			&& currentThreat != null
			&& isValidThreat(currentThreat)
			&& this.skeleton.getTarget() == currentThreat
			&& SkeletonCombatMath.shouldContinueEmergencyDisengage(
				horizontalDistanceSquared(this.skeleton.position(), currentThreat.position()),
				config.skeletonPreferredRange,
				intelligence
			);
	}

	@Override
	public void start() {
		this.activityLease.acquire(this.skeleton, this.skeleton.level().getGameTime());
		this.elapsedTicks = 0;
		this.pathRefreshCooldown = 0;
		this.sideDirection = this.skeleton.getRandom().nextBoolean() ? 1 : -1;
		this.sideSwitchTicks = nextSideSwitchTicks();
		this.escapeDestination = null;
		// 脱离优先于蓄力：明确取消拉弓，避免模型后退时还保持即将射击的姿势。
		this.skeleton.stopUsingItem();
		this.skeleton.getNavigation().stop();
		SmartSkeletonMetrics.emergencyDisengageStarted();
	}

	@Override
	public void tick() {
		if (!this.activityLease.renew(this.skeleton, this.skeleton.level().getGameTime())) {
			return;
		}
		LivingEntity currentThreat = this.threat;
		if (currentThreat == null) {
			return;
		}

		this.elapsedTicks++;
		// 真正逃跑期间绝不举弓；与持弓拉扯的姿态和攻击能力彻底分离。
		this.skeleton.stopUsingItem();
		if (--this.sideSwitchTicks <= 0) {
			this.sideDirection = -this.sideDirection;
			this.sideSwitchTicks = nextSideSwitchTicks();
		}

		boolean pathFinished = this.escapeDestination != null
			&& (this.skeleton.position().distanceToSqr(this.escapeDestination) <= 2.25
				|| this.skeleton.getNavigation().isDone());
		if (--this.pathRefreshCooldown <= 0
			|| pathFinished) {
			this.updateEscapePath(currentThreat);
		}

		if (this.skeleton.getNavigation().isDone()) {
			Vec3 away = this.skeleton.position().add(
				EscapePathing.horizontalAwayDirection(
					this.skeleton.position(),
					currentThreat.position(),
					currentThreat.getLookAngle()
				).scale(MINIMUM_ESCAPE_PATH_DISTANCE)
			);
			EscapePathing.faceTravelPoint(this.skeleton, away);
			// 面向逃离方向后使用正向输入；左右扰动只用于绕开堵路，不再出现“背身举弓倒跑”。
			this.skeleton.getMoveControl().strafe(
				FALLBACK_FORWARD,
				FALLBACK_SIDEWAYS * this.sideDirection
			);
		} else if (this.escapeDestination != null) {
			EscapePathing.faceCurrentPathOrDestination(this.skeleton, this.escapeDestination);
		}
	}

	@Override
	public void stop() {
		MobsThinkNowConfig config = ConfigManager.get();
		LivingEntity currentThreat = this.threat;
		int intelligence = SkeletonIntelligence.get(this.skeleton);
		boolean timedOutWhileUnsafe = this.elapsedTicks >= MAXIMUM_DISENGAGE_TICKS
			&& currentThreat != null
			&& isValidThreat(currentThreat)
			&& this.skeleton.getTarget() == currentThreat
			&& SkeletonCombatMath.shouldContinueEmergencyDisengage(
				horizontalDistanceSquared(this.skeleton.position(), currentThreat.position()),
				config.skeletonPreferredRange,
				intelligence
			);
		if (timedOutWhileUnsafe) {
			this.nextAllowedStartAt = this.skeleton.level().getGameTime() + TIMEOUT_COOLDOWN_TICKS;
		}

		this.skeleton.getNavigation().stop();
		this.threat = null;
		this.escapeDestination = null;
		this.elapsedTicks = 0;
		this.pathRefreshCooldown = 0;
		this.activityLease.release(this.skeleton);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void updateEscapePath(final LivingEntity currentThreat) {
		Vec3 candidate = EscapePathing.findDestinationAwayFrom(
			this.skeleton,
			currentThreat,
			MINIMUM_ESCAPE_PATH_DISTANCE,
			MAXIMUM_ESCAPE_PATH_DISTANCE,
			VERTICAL_PATH_SEARCH
		);

		boolean foundPath = this.skeleton.getNavigation().moveTo(
			candidate.x,
			candidate.y,
			candidate.z,
			SkeletonEscapeSpeedProfile.pathSpeed(this.skeleton)
		);
		this.escapeDestination = foundPath ? candidate : null;
		this.pathRefreshCooldown = foundPath
			? SkeletonCombatMath.disengagePathRefreshTicks(SkeletonIntelligence.get(this.skeleton))
			: 2;
		if (foundPath) {
			EscapePathing.faceCurrentPathOrDestination(this.skeleton, candidate);
		}
	}

	private int nextSideSwitchTicks() {
		return 14 + this.skeleton.getRandom().nextInt(11);
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.skeletonAiEnabled && config.skeletonEmergencyDisengage;
	}

	private boolean isHoldingRangedWeapon() {
		return this.skeleton.isHolding(Items.BOW) || this.skeleton.isHolding(Items.CROSSBOW);
	}

	/** 当前仇恨目标无论是玩家、铁傀儡还是其他生物，都服从同一近身风险判定。 */
	private static boolean isValidThreat(final LivingEntity target) {
		return target.isAlive()
			&& (!(target instanceof net.minecraft.world.entity.player.Player player)
				|| (!player.isCreative() && !player.isSpectator()));
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}
}

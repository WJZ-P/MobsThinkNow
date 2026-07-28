package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 持弓骷髅被玩家贴脸时使用的最高移动优先级脱离行为。
 *
 * <p>普通弓箭 Goal 位于优先级 4，原版避日和避狼行为位于 2～3；如果把后撤只写在弓箭 Goal
 * 内部，任何正在运行的高优先级行为都可能让骷髅继续贴脸挨打。本 Goal 以优先级 1 独立占用
 * {@link Flag#MOVE} 与 {@link Flag#LOOK}：玩家进入触发距离便取消拉弓并强制脱离，达到更远的
 * 安全距离后才把控制权交还给弓箭 Goal。</p>
 *
 * <p>路径每六 tick 至多重算一次，不会随玩家每一步都请求寻路。狭窄地形找不到路径时，骷髅
 * 会保持面向玩家并左右交替后撤；八十 tick 仍未脱困则短暂释放控制权，让弓箭 Goal 一边射击
 * 一边使用自己的普通撤退逻辑，避免永久占用战斗状态机。</p>
 */
public final class SkeletonEmergencyDisengageGoal extends Goal {
	private static final double PATH_SPEED_MODIFIER = 1.40;
	private static final double MINIMUM_ESCAPE_PATH_DISTANCE = 6.0;
	private static final double MAXIMUM_ESCAPE_PATH_DISTANCE = 12.0;
	private static final int VERTICAL_PATH_SEARCH = 5;
	private static final int PATH_REFRESH_TICKS = 6;
	private static final int MAXIMUM_DISENGAGE_TICKS = 80;
	private static final int TIMEOUT_COOLDOWN_TICKS = 20;
	private static final float FALLBACK_BACKWARDS = -1.0F;
	private static final float FALLBACK_SIDEWAYS = 0.65F;

	private final AbstractSkeleton skeleton;
	private @Nullable Player threat;
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
			|| !this.skeleton.isHolding(Items.BOW)
			|| this.skeleton.level().getGameTime() < this.nextAllowedStartAt) {
			return false;
		}

		LivingEntity target = this.skeleton.getTarget();
		if (!(target instanceof Player player) || !isValidThreat(player)) {
			return false;
		}
		if (!SkeletonCombatMath.shouldStartEmergencyDisengage(
			horizontalDistanceSquared(this.skeleton.position(), player.position()),
			config.skeletonPreferredRange
		)) {
			return false;
		}

		this.threat = player;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		Player currentThreat = this.threat;
		return isEnabled(config)
			&& this.elapsedTicks < MAXIMUM_DISENGAGE_TICKS
			&& this.skeleton.isAlive()
			&& this.skeleton.isHolding(Items.BOW)
			&& currentThreat != null
			&& isValidThreat(currentThreat)
			&& this.skeleton.getTarget() == currentThreat
			&& SkeletonCombatMath.shouldContinueEmergencyDisengage(
				horizontalDistanceSquared(this.skeleton.position(), currentThreat.position()),
				config.skeletonPreferredRange
			);
	}

	@Override
	public void start() {
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
		Player currentThreat = this.threat;
		if (currentThreat == null) {
			return;
		}

		this.elapsedTicks++;
		this.skeleton.getLookControl().setLookAt(currentThreat, 35.0F, 35.0F);
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
			// 后退输入以“正看玩家”为基准，因此 -1 是远离玩家；横向换向让堵路时也不会木桩站立。
			this.skeleton.getMoveControl().strafe(
				FALLBACK_BACKWARDS,
				FALLBACK_SIDEWAYS * this.sideDirection
			);
		}
	}

	@Override
	public void stop() {
		MobsThinkNowConfig config = ConfigManager.get();
		Player currentThreat = this.threat;
		boolean timedOutWhileUnsafe = this.elapsedTicks >= MAXIMUM_DISENGAGE_TICKS
			&& currentThreat != null
			&& isValidThreat(currentThreat)
			&& this.skeleton.getTarget() == currentThreat
			&& SkeletonCombatMath.shouldContinueEmergencyDisengage(
				horizontalDistanceSquared(this.skeleton.position(), currentThreat.position()),
				config.skeletonPreferredRange
			);
		if (timedOutWhileUnsafe) {
			this.nextAllowedStartAt = this.skeleton.level().getGameTime() + TIMEOUT_COOLDOWN_TICKS;
		}

		this.skeleton.getNavigation().stop();
		this.threat = null;
		this.escapeDestination = null;
		this.elapsedTicks = 0;
		this.pathRefreshCooldown = 0;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void updateEscapePath(final Player currentThreat) {
		Vec3 candidate = LandRandomPos.getPosAway(
			this.skeleton,
			MINIMUM_ESCAPE_PATH_DISTANCE,
			MAXIMUM_ESCAPE_PATH_DISTANCE,
			VERTICAL_PATH_SEARCH,
			currentThreat.position()
		);
		double currentDistanceSquared = horizontalDistanceSquared(
			this.skeleton.position(),
			currentThreat.position()
		);
		if (candidate == null
			|| horizontalDistanceSquared(candidate, currentThreat.position()) <= currentDistanceSquared + 1.0) {
			this.escapeDestination = null;
			this.skeleton.getNavigation().stop();
			this.pathRefreshCooldown = 2;
			return;
		}

		boolean foundPath = this.skeleton.getNavigation().moveTo(
			candidate.x,
			candidate.y,
			candidate.z,
			PATH_SPEED_MODIFIER
		);
		this.escapeDestination = foundPath ? candidate : null;
		this.pathRefreshCooldown = foundPath ? PATH_REFRESH_TICKS : 2;
	}

	private int nextSideSwitchTicks() {
		return 14 + this.skeleton.getRandom().nextInt(11);
	}

	private static boolean isEnabled(final MobsThinkNowConfig config) {
		return config.enabled && config.skeletonAiEnabled && config.skeletonEmergencyDisengage;
	}

	private static boolean isValidThreat(final Player player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static double horizontalDistanceSquared(final Vec3 first, final Vec3 second) {
		double x = first.x - second.x;
		double z = first.z - second.z;
		return x * x + z * z;
	}
}

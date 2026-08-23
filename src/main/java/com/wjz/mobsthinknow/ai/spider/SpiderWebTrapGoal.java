package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import com.wjz.mobsthinknow.shared.ai.SpiderWebTrapPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * IQ 7～10 蜘蛛的可读伏击动作：观察目标运动，短暂抬身吐丝，再把限时蛛网投到预计落脚点。
 *
 * <p>它不会凭空远程扫描方块。一次决策固定检查五个水平候选、每个最多四层高度；同维度登记表会阻止
 * 相邻蜘蛛叠放。蛛网服从 mobGriefing、到期恢复空气，并在关服前强制清理。</p>
 */
public final class SpiderWebTrapGoal extends Goal {
	private static final int WINDUP_SOUND_TICK = 3;
	private static final int SILK_RELEASE_TICK = 8;
	private static final int ACTION_FINISH_TICK = 12;
	private static final int[] VERTICAL_SEARCH_ORDER = {0, -1, 1, -2};

	private final Spider spider;
	private final int stableSide;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.WEB_TRAP);
	private @Nullable LivingEntity target;
	private @Nullable BlockPos plannedPosition;
	private long nextTrapTick;
	private int actionTicks;
	private boolean placed;
	private boolean acquired;
	private boolean blastContainment;
	private @Nullable UUID plannedCreeperId;
	private @Nullable UUID lastSupportedCreeperId;

	public SpiderWebTrapGoal(final Spider spider) {
		this.spider = spider;
		this.stableSide = (spider.getUUID().getMostSignificantBits() & 1L) == 0L ? -1 : 1;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!enabled(config)
			|| !(this.spider.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		LivingEntity currentTarget = this.spider.getTarget();
		int intelligence = SpiderIntelligence.get(this.spider);
		if (!isValidTarget(currentTarget)
			|| !SpiderWebTrapPlanner.canPlan(
				intelligence,
				this.spider.getSensing().hasLineOfSight(currentTarget),
				this.spider.onGround(),
				this.spider.isVehicle(),
				this.spider.distanceToSqr(currentTarget)
			)
			|| !this.activityLease.canAcquire(this.spider, level.getGameTime())) {
			return false;
		}

		ZombieSquadCoordinator.SquadBlastThreat activeBlast = config.squadCreeperWebContainment
			? ZombieSquadCoordinator.forLevel(level).activeBlastForContainment(this.spider, currentTarget)
			: null;
		UUID activeCreeperId = activeBlast == null ? null : activeBlast.creeper().getUUID();
		boolean containmentOpportunity = activeCreeperId != null && !activeCreeperId.equals(this.lastSupportedCreeperId);
		if (!SpiderWebTrapPlanner.mayBypassCooldownForBlast(
			level.getGameTime() >= this.nextTrapTick,
			containmentOpportunity
		)) {
			return false;
		}

		BlockPos candidate = this.findPlacement(
			level,
			currentTarget,
			intelligence,
			containmentOpportunity ? activeBlast : null
		);
		if (candidate == null) {
			// 不可用地形只短暂退避重新评估，避免 canUse 每 tick 重复做 20 次方块检查。
			this.nextTrapTick = level.getGameTime() + 20L;
			return false;
		}
		this.target = currentTarget;
		this.plannedPosition = candidate;
		this.blastContainment = containmentOpportunity;
		this.plannedCreeperId = containmentOpportunity ? activeCreeperId : null;
		if (containmentOpportunity) {
			// 预订发生在 canUse 成功时，防止 GoalSelector 多次试探同一引信并绕过冷却。
			this.lastSupportedCreeperId = activeCreeperId;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		return this.acquired
			&& enabled(config)
			&& this.actionTicks <= ACTION_FINISH_TICK
			&& isValidTarget(this.target)
			&& this.plannedPosition != null
			&& this.activityLease.owns(this.spider, this.spider.level().getGameTime());
	}

	@Override
	public void start() {
		long now = this.spider.level().getGameTime();
		this.acquired = this.activityLease.acquire(this.spider, now);
		this.actionTicks = 0;
		this.placed = false;
		if (this.acquired) {
			this.spider.getNavigation().stop();
			this.spider.setAggressive(true);
			SmartSpiderMetrics.webTrapWindup();
		}
	}

	@Override
	public void tick() {
		if (!this.acquired || !this.activityLease.renew(this.spider, this.spider.level().getGameTime())) {
			this.actionTicks = ACTION_FINISH_TICK + 1;
			return;
		}
		this.actionTicks++;
		BlockPos placement = this.plannedPosition;
		LivingEntity currentTarget = this.target;
		if (placement == null || !isValidTarget(currentTarget)) {
			return;
		}
		this.spider.getLookControl().setLookAt(
			placement.getX() + 0.5,
			placement.getY() + 0.35,
			placement.getZ() + 0.5,
			60.0F,
			45.0F
		);

		if (this.actionTicks == WINDUP_SOUND_TICK && this.spider.level() instanceof ServerLevel level) {
			level.playSound(
				null,
				this.spider,
				SoundEvents.SPIDER_AMBIENT,
				SoundSource.HOSTILE,
				0.75F,
				1.20F + this.spider.getRandom().nextFloat() * 0.18F
			);
		}
		if (this.actionTicks == SILK_RELEASE_TICK && this.spider.level() instanceof ServerLevel level) {
			this.spider.swing(InteractionHand.MAIN_HAND);
			Vec3 movement = this.spider.getDeltaMovement();
			if (this.spider.onGround()) {
				// 很轻的抬身让玩家能读懂“吐丝”而不是方块无提示出现，不改变水平位置或触发跳扑。
				this.spider.setDeltaMovement(movement.x * 0.35, Math.max(movement.y, 0.16), movement.z * 0.35);
			}
			MobsThinkNowConfig config = ConfigManager.get();
			this.placed = SpiderWebTrapRegistry.tryPlace(
				level,
				placement,
				this.spider.getUUID(),
				level.getGameTime(),
				config.spiderWebTrapLifetimeTicks
			);
			if (this.placed && this.blastContainment) {
				SmartSpiderMetrics.blastContainmentWeb();
			}
			this.nextTrapTick = level.getGameTime() + SpiderWebTrapPlanner.cooldownTicks(
				config.spiderWebTrapCooldownTicks,
				SpiderIntelligence.get(this.spider),
				level.getDifficulty().getId(),
				this.spider.getRandom().nextInt(41)
			);
		}
	}

	@Override
	public void stop() {
		if (!this.placed) {
			this.nextTrapTick = Math.max(this.nextTrapTick, this.spider.level().getGameTime() + 30L);
		}
		this.spider.getNavigation().stop();
		this.target = null;
		this.plannedPosition = null;
		this.actionTicks = 0;
		this.placed = false;
		this.acquired = false;
		this.blastContainment = false;
		this.plannedCreeperId = null;
		this.activityLease.release(this.spider);
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private @Nullable BlockPos findPlacement(
		final ServerLevel level,
		final LivingEntity currentTarget,
		final int intelligence,
		final ZombieSquadCoordinator.@Nullable SquadBlastThreat activeBlast
	) {
		Iterable<Vec3d> centers;
		if (activeBlast != null) {
			centers = SpiderWebTrapPlanner.blastEscapeCandidateCenters(
				toShared(currentTarget.position()),
				toShared(currentTarget.getDeltaMovement()),
				toShared(activeBlast.center()),
				this.stableSide
			);
		} else {
			Vec3d predicted = SpiderWebTrapPlanner.predictedPosition(
				toShared(currentTarget.position()),
				toShared(currentTarget.getDeltaMovement()),
				toShared(currentTarget.getLookAngle()),
				intelligence
			);
			centers = SpiderWebTrapPlanner.candidateCenters(
				toShared(currentTarget.position()),
				predicted,
				toShared(currentTarget.getLookAngle()),
				this.stableSide
			);
		}
		for (Vec3d center : centers) {
			for (int yOffset : VERTICAL_SEARCH_ORDER) {
				BlockPos pos = BlockPos.containing(
					center.x(),
					currentTarget.getBoundingBox().minY + yOffset,
					center.z()
				);
				if (this.isUsefulPlacement(level, pos, currentTarget)) {
					return pos.immutable();
				}
			}
		}
		return null;
	}

	/** GameTest/诊断只读：返回本轮已经通过地形校验的绝对落点。 */
	public @Nullable BlockPos plannedPosition() {
		return this.plannedPosition;
	}

	public boolean isBlastContainmentPlan() {
		return this.blastContainment && this.plannedCreeperId != null;
	}

	private boolean isUsefulPlacement(
		final ServerLevel level,
		final BlockPos pos,
		final LivingEntity currentTarget
	) {
		Vec3 center = Vec3.atCenterOf(pos);
		if (this.spider.position().distanceToSqr(center) > 10.0 * 10.0
			|| currentTarget.position().distanceToSqr(center) < 0.65 * 0.65
			|| new AABB(pos).inflate(0.05).intersects(this.spider.getBoundingBox())
			|| !SpiderWebTrapRegistry.canPlace(level, pos)) {
			return false;
		}
		// 不挤进已有天然/玩家蛛网群；临时登记表自身还会执行更严格的一格半预约。
		for (BlockPos nearby : BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))) {
			if (!nearby.equals(pos) && level.getBlockState(nearby).is(net.minecraft.world.level.block.Blocks.COBWEB)) {
				return false;
			}
		}
		return true;
	}

	private static boolean enabled(final MobsThinkNowConfig config) {
		return config.enabled && config.spiderAiEnabled && config.spiderWebTraps;
	}

	private static boolean isValidTarget(final @Nullable LivingEntity target) {
		return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target);
	}

	private static Vec3d toShared(final Vec3 vector) {
		return new Vec3d(vector.x, vector.y, vector.z);
	}
}

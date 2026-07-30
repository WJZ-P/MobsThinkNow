package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadDirective;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadRole;
import com.wjz.mobsthinknow.ai.zombie.squad.SquadState;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 单只僵尸的执行器：负责感知、执行协调器命令，以及没有小队时的轻量单体战术。
 *
 * <p>它不会再查询附近的全部僵尸。小队发现、选举和任务分配统一交给
 * {@link ZombieSquadCoordinator}，从结构上消除原先的逐僵尸范围扫描。</p>
 */
final class ZombieTacticalController {
	private static final double MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED = 1.0E-6;
	private static final double FRONT_ARC_DOT_PRODUCT = 0.2;
	private static final double DESTINATION_REACHED_DISTANCE_SQUARED = 2.25;
	/** 目标水平视线与"目标→僵尸"方向夹角小于约 60° 时视为"被盯着"。 */
	private static final double WATCHED_VIEW_DOT = 0.5;
	private final Zombie zombie;
	private final ZombieShieldCombat shieldCombat;
	private ZombieTactic tactic = ZombieTactic.PRESSURE;
	private @Nullable Vec3 lastSeenPosition;
	private long lastSeenAt = Long.MIN_VALUE;
	private @Nullable Vec3 destination;
	private @Nullable SquadDirective squadDirective;
	private int observedTargetId = Integer.MIN_VALUE;
	private long nextDecisionAt;
	private long nextPathUpdateAt;
	private long nextProgressCheckAt;
	private Vec3 lastProgressPosition;
	private boolean alternateFlank;
	private boolean hasLineOfSight;

	ZombieTacticalController(final Zombie zombie) {
		this.zombie = zombie;
		this.shieldCombat = new ZombieShieldCombat(zombie);
		this.lastProgressPosition = zombie.position();
	}

	/**
	 * 记录这只僵尸亲眼看到的信息，再把“有限感知”作为心跳提交给小队黑板。
	 * 协调器共享的是最后目击位置，而不是无条件读取墙后目标的实时坐标。
	 */
	void observe(final LivingEntity target) {
		if (this.observedTargetId != target.getId()) {
			// 换目标时不能把上一名玩家的最后目击位置错误地套到新目标身上。
			this.observedTargetId = target.getId();
			this.lastSeenPosition = null;
			this.lastSeenAt = Long.MIN_VALUE;
		}
		this.hasLineOfSight = this.zombie.getSensing().hasLineOfSight(target);
		if (this.hasLineOfSight) {
			this.lastSeenPosition = target.position();
			this.lastSeenAt = this.zombie.level().getGameTime();
		}

		if (this.zombie.level() instanceof ServerLevel serverLevel && ConfigManager.get().packSurrounding) {
			ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(serverLevel);
			coordinator.heartbeat(
				this.zombie,
				target,
				this.hasLineOfSight,
				this.lastSeenPosition,
				this.lastSeenAt
			);
			this.squadDirective = coordinator.directiveFor(this.zombie);
		} else {
			this.squadDirective = null;
		}
	}

	boolean hasTrackableTarget() {
		MobsThinkNowConfig config = ConfigManager.get();
		return this.hasLineOfSight
			|| this.hasRecentLastSeenPosition(config)
			|| (this.squadDirective != null && this.squadDirective.hasSharedTargetMemory());
	}

	boolean hasTacticalIntent() {
		return this.shieldCombat.hasIntent()
			|| this.squadDirective != null
			|| (this.tactic != ZombieTactic.PRESSURE && this.destination != null);
	}

	boolean shouldRunVanillaCombat(final LivingEntity target) {
		if (this.shieldCombat.holdsPosition()) {
			return false;
		}

		if (this.squadDirective != null) {
			SquadState state = this.squadDirective.state();
			if (state == SquadState.ENGAGING) {
				SquadRole role = this.squadDirective.role();
				return role == SquadRole.LEADER
					|| role == SquadRole.PRESSURER
					|| (this.hasLineOfSight
						&& this.zombie.isWithinMeleeAttackRange(target)
						&& !this.shouldHoldFrontalAttack(target));
			}

			// 集结和部署时仍允许贴身自卫；协调器会在本 tick 末尾切到紧急交战状态。
			return this.hasLineOfSight
				&& this.zombie.isWithinMeleeAttackRange(target)
				&& !this.shouldHoldFrontalAttack(target);
		}

		return this.tactic == ZombieTactic.PRESSURE
			|| (this.hasLineOfSight && this.zombie.isWithinMeleeAttackRange(target) && !this.shouldHoldFrontalAttack(target));
	}

	boolean shouldHoldFrontalAttack(final LivingEntity target) {
		if (!ConfigManager.get().shieldFlanking) {
			return false;
		}

		return (this.tactic == ZombieTactic.FLANK_LEFT || this.tactic == ZombieTactic.FLANK_RIGHT)
			&& target.isBlocking()
			&& isInFrontArc(target);
	}

	/** 守势阶段禁止提前挥击；攻击窗口打开后只放行一次真实近战结算。 */
	boolean shouldHoldAttack(final LivingEntity target) {
		return this.shieldCombat.blocksAttack() || this.shouldHoldFrontalAttack(target);
	}

	boolean hasShieldCombatIntent() {
		return this.shieldCombat.hasIntent();
	}

	boolean isShieldStrikeWindow() {
		return this.shieldCombat.isStrikeWindow();
	}

	void onAttackPerformed(final LivingEntity target) {
		this.shieldCombat.onAttackPerformed(target);
	}

	void tick(final LivingEntity target) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled || !config.zombieAiEnabled) {
			this.tactic = ZombieTactic.PRESSURE;
			this.destination = null;
			this.squadDirective = null;
			this.shieldCombat.stop();
			return;
		}

		long now = this.zombie.level().getGameTime();
		this.shieldCombat.tick(target, config, this.hasLineOfSight);

		if (this.squadDirective != null) {
			this.executeSquadDirective(target, config, now);
			return;
		}

		if (now >= this.nextDecisionAt) {
			this.decideSolo(target, config, now);
		}
		this.executeDestination(target, config, now);
	}

	void stop() {
		this.tactic = ZombieTactic.PRESSURE;
		this.destination = null;
		this.squadDirective = null;
		this.shieldCombat.stop();

		LivingEntity target = this.zombie.getTarget();
		if ((target == null || !target.isAlive()) && this.zombie.level() instanceof ServerLevel serverLevel) {
			ZombieSquadCoordinator.forLevel(serverLevel).unregister(this.zombie);
		}
	}

	private void executeSquadDirective(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now
	) {
		SquadDirective directive = this.squadDirective;
		if (directive == null) {
			return;
		}

		this.tactic = tacticFor(directive.role());
		this.destination = directive.destination();

		if (directive.isCombatPhase()
			&& isAmbushRole(directive.role())
			&& directive.state() == SquadState.ENGAGING
			&& this.hasLineOfSight
			&& this.destination != null
			&& !this.isWatchedByTarget(target)
			// 目标举盾且自己在其正面弧内时保持绕后弧线；直冲会撞在盾上又打不出手。
			&& !(target.isBlocking() && this.isInFrontArc(target))) {
			// 没被目标盯住的包抄手放弃保守弧线，直接突袭目标当前位置。
			this.destination = target.position();
		}

		if (directive.isMeetingPhase() && directive.focusPosition() != null && directive.role() != SquadRole.LEADER) {
			// 非首领在会议阶段面向首领，让玩家能从动作上读懂“它们正在交流”。
			this.zombie.getLookControl().setLookAt(directive.focusPosition());
		} else if (this.hasLineOfSight) {
			this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}

		this.executeDestination(target, config, now);
	}

	private void executeDestination(final LivingEntity target, final MobsThinkNowConfig config, final long now) {
		if (this.shieldCombat.holdsPosition()) {
			return;
		}
		if (this.destination == null || (this.squadDirective == null && this.tactic == ZombieTactic.PRESSURE)) {
			return;
		}

		if (this.tactic == ZombieTactic.SEARCH_LAST_SEEN) {
			this.zombie.getLookControl().setLookAt(this.destination.add(0.0, 1.0, 0.0));
		} else if (this.hasLineOfSight && (this.squadDirective == null || !this.squadDirective.isMeetingPhase())) {
			this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}

		boolean shouldKeepFlankingShield = this.shouldHoldFrontalAttack(target);
		if (this.zombie.isWithinMeleeAttackRange(target) && !shouldKeepFlankingShield) {
			return;
		}

		if (this.zombie.position().distanceToSqr(this.destination) <= DESTINATION_REACHED_DISTANCE_SQUARED) {
			if (this.tactic == ZombieTactic.SEARCH_LAST_SEEN) {
				this.lastSeenPosition = null;
				this.tactic = ZombieTactic.PRESSURE;
			}
			return;
		}

		this.checkProgress(now);
		if (now >= this.nextPathUpdateAt && this.navigationTargetsDifferentPosition(this.destination)) {
			boolean foundPath = this.zombie
				.getNavigation()
				.moveTo(this.destination.x, this.destination.y, this.destination.z, this.tacticalSpeed(config));
			this.nextPathUpdateAt = now + config.decisionIntervalTicks;
			if (!foundPath) {
				if (this.squadDirective == null) {
					this.alternateFlank = !this.alternateFlank;
					this.nextDecisionAt = now + 2L;
				}
				SmartZombieMetrics.failedPath();
			}
		}
	}

	private void decideSolo(final LivingEntity target, final MobsThinkNowConfig config, final long now) {
		boolean prefersLeft = ((this.zombie.getId() & 1) == 0) != this.alternateFlank;
		ZombieDecisionContext context = new ZombieDecisionContext(
			this.hasLineOfSight,
			this.hasRecentLastSeenPosition(config),
			target.isBlocking(),
			this.isInFrontArc(target),
			1,
			0,
			prefersLeft
		);

		this.tactic = ZombieTacticEvaluator.select(context, this.tactic);
		this.destination = this.calculateSoloDestination(target, config);
		this.nextDecisionAt = now + config.decisionIntervalTicks + Math.floorMod(this.zombie.getId(), 3);
		SmartZombieMetrics.decision(this.tactic);
	}

	private @Nullable Vec3 calculateSoloDestination(final LivingEntity target, final MobsThinkNowConfig config) {
		return switch (this.tactic) {
			case PRESSURE -> null;
			case SEARCH_LAST_SEEN -> this.lastSeenPosition;
			case FLANK_LEFT -> this.calculateShieldFlank(target, config, 1.0);
			case FLANK_RIGHT -> this.calculateShieldFlank(target, config, -1.0);
			case SURROUND -> this.calculateFallbackFormationSlot(target, config);
		};
	}

	private Vec3 calculateShieldFlank(final LivingEntity target, final MobsThinkNowConfig config, final double side) {
		Vec3 forward = horizontalUnit(target.getLookAngle(), target.position().subtract(this.zombie.position()));
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		return target.position()
			.subtract(forward.scale(config.flankBehindDistance))
			.add(lateral.scale(config.flankSideDistance * side));
	}

	private Vec3 calculateFallbackFormationSlot(final LivingEntity target, final MobsThinkNowConfig config) {
		double angle = Math.toRadians(target.getYRot()) + Math.PI * 2.0 * Math.floorMod(this.zombie.getId(), 8) / 8.0;
		return target.position().add(Math.cos(angle) * config.formationRadius, 0.0, Math.sin(angle) * config.formationRadius);
	}

	private boolean isInFrontArc(final LivingEntity target) {
		Vec3 fallback = target.position().subtract(this.zombie.position());
		Vec3 targetForward = horizontalUnit(target.getLookAngle(), fallback);
		Vec3 targetToZombie = horizontalUnit(this.zombie.position().subtract(target.position()), targetForward);
		return targetForward.dot(targetToZombie) > FRONT_ARC_DOT_PRODUCT;
	}

	private boolean hasRecentLastSeenPosition(final MobsThinkNowConfig config) {
		if (this.lastSeenPosition == null) {
			return false;
		}

		long elapsed = this.zombie.level().getGameTime() - this.lastSeenAt;
		return elapsed >= 0L && elapsed <= config.targetMemoryTicks;
	}

	private boolean navigationTargetsDifferentPosition(final Vec3 wanted) {
		Path path = this.zombie.getNavigation().getPath();
		if (path == null || path.isDone()) {
			return true;
		}

		BlockPos currentTarget = path.getTarget();
		double dx = currentTarget.getX() + 0.5 - wanted.x;
		double dy = currentTarget.getY() - wanted.y;
		double dz = currentTarget.getZ() + 0.5 - wanted.z;
		return dx * dx + dy * dy + dz * dz > 2.25;
	}

	private void checkProgress(final long now) {
		if (now < this.nextProgressCheckAt) {
			return;
		}

		Vec3 currentPosition = this.zombie.position();
		if (currentPosition.distanceToSqr(this.lastProgressPosition) < 0.04 && !this.zombie.getNavigation().isDone()) {
			// 先停止旧路径，下一次更新才会真的重新寻路，而不是继续复用一个已经卡住的 Path。
			this.zombie.getNavigation().stop();
			this.nextPathUpdateAt = now;
			if (this.squadDirective == null) {
				this.alternateFlank = !this.alternateFlank;
				this.nextDecisionAt = now + 1L;
			}
		}

		this.lastProgressPosition = currentPosition;
		this.nextProgressCheckAt = now + 20L;
	}

	/** 武装小队中，两翼和截断位在机动时获得少量额外速度，让包抄能真正抢到位置。 */
	private double tacticalSpeed(final MobsThinkNowConfig config) {
		if (!config.armedSquads || this.squadDirective == null) {
			return config.tacticalSpeedModifier;
		}

		SquadRole role = this.squadDirective.role();
		if (role == SquadRole.FLANK_LEFT
			|| role == SquadRole.FLANK_RIGHT
			|| role == SquadRole.CUTOFF
			|| role == SquadRole.SUPPORT) {
			return Math.min(1.5, config.tacticalSpeedModifier + config.armedFlankSpeedBonus);
		}
		return config.tacticalSpeedModifier;
	}

	private static boolean isAmbushRole(final SquadRole role) {
		return role == SquadRole.FLANK_LEFT || role == SquadRole.FLANK_RIGHT || role == SquadRole.CUTOFF;
	}

	/** 目标的水平视线是否落在自己身上；包抄成员据此决定继续绕行还是直接突袭。 */
	private boolean isWatchedByTarget(final LivingEntity target) {
		Vec3 toZombie = new Vec3(
			this.zombie.getX() - target.getX(),
			0.0,
			this.zombie.getZ() - target.getZ()
		);
		if (toZombie.horizontalDistanceSqr() < MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED) {
			return true;
		}
		Vec3 view = target.getViewVector(1.0F);
		Vec3 horizontalView = new Vec3(view.x, 0.0, view.z);
		if (horizontalView.horizontalDistanceSqr() < MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED) {
			return false;
		}
		return horizontalView.normalize().dot(toZombie.normalize()) > WATCHED_VIEW_DOT;
	}

	private static ZombieTactic tacticFor(final SquadRole role) {
		return switch (role) {
			case LEADER, PRESSURER -> ZombieTactic.PRESSURE;
			case FLANK_LEFT -> ZombieTactic.FLANK_LEFT;
			case FLANK_RIGHT -> ZombieTactic.FLANK_RIGHT;
			case CUTOFF, SUPPORT, RANGED, CARRIER -> ZombieTactic.SURROUND;
			case BREACHER -> ZombieTactic.PRESSURE;
		};
	}

	private static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(preferred.x, 0.0, preferred.z);
		if (horizontal.horizontalDistanceSqr() < MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}

		if (horizontal.horizontalDistanceSqr() < MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED) {
			return new Vec3(0.0, 0.0, 1.0);
		}

		return horizontal.normalize();
	}
}

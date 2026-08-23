package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.creeper.CreeperBlastEvacuationMath;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperMetrics;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonShotSafety;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import com.wjz.mobsthinknow.ai.spider.SmartSpiderMetrics;
import com.wjz.mobsthinknow.ai.spider.SpiderSquadTransportAccess;
import com.wjz.mobsthinknow.ai.spider.SpiderTransportRouteEvaluator;
import com.wjz.mobsthinknow.ai.spider.SpiderWebTrapRegistry;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyAction;
import com.wjz.mobsthinknow.ai.zombie.ZombieBodyLanguage;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieFireSupportMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidThreatMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import com.wjz.mobsthinknow.shared.spatial.BoundedSpatialIndex;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 每个 {@link ServerLevel} 唯一的僵尸、骷髅、苦力怕与蜘蛛混编小队协调器。
 *
 * <p>敌对生物 AI 和 {@code END_LEVEL_TICK} 都在服务器主线程执行，所以这里故意不加锁。导航、视线和
 * 实体状态仍只在主线程读取或修改；未来如果把纯数学评分搬到工作线程，也只能传不可变快照，不能把
 * Minecraft 实体对象交给子线程。</p>
 *
 * <p>性能上，每名候选成员每 tick 只做一次 O(1) 心跳。组队时先按目标和空间格分桶，每个种子最多检查
 * {@code maxSquadSize * 16} 条桶记录。加上为确定性结果所做的种子排序，密集场景上界是
 * O(N log N + N * K)，其中 K 有硬上限，不会退化为每只僵尸查询全部同伴的 O(N²)。</p>
 */
public final class ZombieSquadCoordinator {
	private static final int MINIMUM_SURVIVING_SQUAD_SIZE = 2;
	private static final double ORDER_REACHED_DISTANCE_SQUARED = 4.0;
	private static final double MINIMUM_HORIZONTAL_LENGTH_SQUARED = 1.0E-6;
	private static final long ROUTE_DANGER_MEMORY_TICKS = 120L;
	private static final int MAXIMUM_DANGER_PATH_SAMPLES = 16;
	private static final long BLAST_RESERVATION_TICKS = 8L;
	private static final long FIRING_LANE_RESERVATION_TICKS = 5L;
	/** 防止实体卸载或 Goal 异常切换后遗留跳扑令牌；正常落地会更早主动释放。 */
	private static final int MAXIMUM_SPIDER_POUNCE_RESERVATION_TICKS = 30;
	private static final double FIRING_LANE_CLEARANCE = 0.35;
	private static final Direction[] TACTIC_CARDINAL_DIRECTIONS = {
		Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
	};
	private static final Identifier SQUAD_SPEED_MODIFIER_ID =
		Identifier.fromNamespaceAndPath(MobsThinkNow.MOD_ID, "squad_speed_bonus");
	private static final Map<ServerLevel, ZombieSquadCoordinator> COORDINATORS = new IdentityHashMap<>();

	private final Map<Integer, MemberRecord> members = new HashMap<>();
	private final Map<Long, ZombieSquad> squads = new LinkedHashMap<>();
	private final SquadTheatrics theatrics = new SquadTheatrics();
	private long nextSquadId = 1L;
	private long lastTickAt = Long.MIN_VALUE;

	private ZombieSquadCoordinator() {
	}

	public static ZombieSquadCoordinator forLevel(final ServerLevel level) {
		return COORDINATORS.computeIfAbsent(level, ignored -> new ZombieSquadCoordinator());
	}

	/** Fabric 的世界末 tick 回调入口。 */
	public static void tickLevel(final ServerLevel level) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquadCoordinator existing = COORDINATORS.get(level);
		if (!squadsEnabled(config)) {
			if (existing != null) {
				existing.reset();
				COORDINATORS.remove(level, existing);
			}
			return;
		}

		forLevel(level).tick(level, config);
	}

	/** 服务器停止时主动释放实体引用，避免同一 JVM 内切换存档后保留旧世界。 */
	public static void clearAll() {
		for (ZombieSquadCoordinator coordinator : COORDINATORS.values()) {
			coordinator.reset();
		}
		COORDINATORS.clear();
	}

	public static void unloadLevel(final ServerLevel level) {
		ZombieSquadCoordinator coordinator = COORDINATORS.remove(level);
		if (coordinator != null) {
			coordinator.reset();
		}
	}

	/** Entity unload callbacks must not create an otherwise disabled/empty coordinator just to remove one member. */
	public static void unregisterIfTracked(final ServerLevel level, final Mob mob) {
		ZombieSquadCoordinator coordinator = COORDINATORS.get(level);
		if (coordinator != null) {
			coordinator.unregister(mob);
		}
	}

	/** 命令诊断使用；调用方同样位于服务器主线程。 */
	public static int activeSquadCount() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> coordinator.squads.size()).sum();
	}

	/** Test/status visibility into the bounded per-level heartbeat registry. */
	public int trackedMemberCount() {
		return this.members.size();
	}

	public static int activeDangerMemoryCount() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> coordinator.squads.values().stream()
			.mapToInt(squad -> squad.dangerMemory.activeEntryCount(coordinator.lastTickAt))
			.sum()).sum();
	}

	public static int activeBlastReservationCount() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> coordinator.squads.values().stream()
			.mapToInt(squad -> squad.blastReservations.activeCount(coordinator.lastTickAt))
			.sum()).sum();
	}

	public static int activeFiringLaneCount() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> coordinator.squads.values().stream()
			.mapToInt(squad -> squad.firingLanes.activeCount(coordinator.lastTickAt))
			.sum()).sum();
	}

	public static int activeSecondaryTargetAssignments() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> coordinator.squads.values().stream()
			.mapToInt(squad -> (int)squad.targetAssignments.values().stream()
				.filter(targetId -> targetId != squad.target.getId())
				.count())
			.sum()).sum();
	}

	public static int activeCasualtyResponses() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> (int)coordinator.squads.values().stream()
			.filter(squad -> !squad.casualtyDirectives.isEmpty())
			.count()).sum();
	}

	public static int activeCasualtyTransports() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> (int)coordinator.squads.values().stream()
			.filter(squad -> {
				MemberRecord casualty = coordinator.members.get(squad.casualtyId);
				MemberRecord escort = coordinator.members.get(squad.casualtyEscortId);
				return casualty != null
					&& escort != null
					&& escort.mob instanceof Spider
					&& casualty.mob.getVehicle() == escort.mob;
			})
			.count()).sum();
	}

	public static int activeWebAmbushes() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> (int)coordinator.squads.values().stream()
			.filter(squad -> squad.webAmbushStartedAt != Long.MIN_VALUE)
			.count()).sum();
	}

	public static int activeShieldWalls() {
		if (!ConfigManager.get().squadShieldWallRotation) {
			return 0;
		}
		return COORDINATORS.values().stream().mapToInt(coordinator -> (int)coordinator.squads.values().stream()
			.filter(squad -> squad.shieldWallMemberIds.size() >= 2
				&& (squad.state == SquadState.DEPLOYING || squad.state == SquadState.ENGAGING))
			.count()).sum();
	}

	public static int activeSpiderPounceReservations() {
		if (!ConfigManager.get().squadSpiderPounceStaggering) {
			return 0;
		}
		return COORDINATORS.values().stream().mapToInt(coordinator -> (int)coordinator.squads.values().stream()
			.filter(squad -> squad.spiderPounceCadence.isActive(coordinator.lastTickAt))
			.count()).sum();
	}

	/**
	 * 死亡结算前恢复职业名牌，避免每只小队僵尸阵亡都触发原版
	 * “Named entity ... died” 的 INFO 日志（那是给玩家命名牌实体保留的行为）。
	 */
	public static void onMemberDying(final Mob mob) {
		if (!(mob.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		ZombieSquadCoordinator coordinator = COORDINATORS.get(serverLevel);
		if (coordinator != null) {
			coordinator.theatrics.restoreName(mob);
			removeSquadSpeedBonus(mob);
		}
	}

	/** 保留旧调用名，避免其他模组或测试源码在本次跨物种扩展后立即断裂。 */
	public static void onZombieDying(final Zombie zombie) {
		onMemberDying(zombie);
	}

	/** 供仇恨 Goal 判断“攻击者是不是同队队友”；两个实体都登记在同一支五类混编小队才算。 */
	public static boolean areSquadmates(final Mob first, final Mob second) {
		if (first.level() != second.level() || !(first.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		ZombieSquadCoordinator coordinator = COORDINATORS.get(serverLevel);
		if (coordinator == null) {
			return false;
		}
		MemberRecord a = coordinator.memberFor(first);
		MemberRecord b = coordinator.memberFor(second);
		return a != null && b != null && a.squadId != 0L && a.squadId == b.squadId;
	}

	/**
	 * 真实受击事件触发的有界求援广播。只遍历受害者所在小队（默认最多 20），且只给水桶辅助兵写信号；
	 * 不在每 tick 做邻居查询，因此不会形成“每只僵尸扫描每只僵尸”的 N² 热点。
	 */
	public static void onSquadMemberAttacked(final Mob victim, final LivingEntity attacker) {
		if (!(victim.level() instanceof ServerLevel serverLevel)
			|| !attacker.isAlive()
			|| (attacker instanceof Player player && (player.isCreative() || player.isSpectator()))) {
			return;
		}

		if (victim instanceof Zombie zombie
			&& ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.WATER) {
			ZombieFluidThreatMemory.record(zombie, attacker, zombie.position());
		}
		ZombieSquadCoordinator coordinator = COORDINATORS.get(serverLevel);
		if (coordinator == null) {
			return;
		}
		MemberRecord member = coordinator.memberFor(victim);
		ZombieSquad squad = member == null ? null : coordinator.squads.get(member.squadId);
		if (squad == null) {
			return;
		}
		coordinator.observeThreat(squad, attacker, SquadThreatMemory.Evidence.DIRECT_ATTACK, victim.level().getGameTime());
		for (int memberId : squad.memberIds) {
			MemberRecord helper = coordinator.members.get(memberId);
			if (helper != null
				&& helper.mob != victim
				&& helper.mob instanceof Zombie helperZombie
				&& ZombieSpecialEquipment.utilityClassOf(helperZombie) == UtilityClass.WATER) {
				ZombieFluidThreatMemory.record(helperZombie, attacker, victim.position());
			}
		}
	}

	/**
	 * 一次着火阶段只做一次 O(K) 小队内选择（K 为该队人数上限），把请求交给最近的满水桶队友；
	 * 后续接近、投放与回收都由该水桶兵自己的 Goal 完成，不产生全局实体扫描。
	 */
	public static void onSquadMemberBurning(final Zombie victim) {
		if (!(victim.level() instanceof ServerLevel serverLevel) || !victim.isAlive() || !victim.isOnFire()) {
			return;
		}
		ZombieSquadCoordinator coordinator = COORDINATORS.get(serverLevel);
		if (coordinator == null) {
			return;
		}
		MemberRecord member = coordinator.memberFor(victim);
		ZombieSquad squad = member == null ? null : coordinator.squads.get(member.squadId);
		if (squad == null) {
			return;
		}

		Zombie selected = null;
		double bestDistance = Double.POSITIVE_INFINITY;
		for (int memberId : squad.memberIds) {
			MemberRecord candidate = coordinator.members.get(memberId);
			if (candidate == null
				|| candidate.mob == victim
				|| !candidate.mob.isAlive()
				|| !(candidate.mob instanceof Zombie candidateZombie)
				|| !ZombieSpecialEquipment.hasFullBucket(candidateZombie, UtilityClass.WATER)) {
				continue;
			}
			double distance = candidate.mob.distanceToSqr(victim);
			if (distance < bestDistance) {
				bestDistance = distance;
				selected = candidateZombie;
			}
		}
		if (selected != null) {
			ZombieFireSupportMemory.record(selected, victim);
		}
	}

	/**
	 * 单个混编成员提交自己的观察结果。只有直接视线会刷新时间戳；旧的最后目击位置可以继续上报，
	 * 但不会被误当成一条更新鲜的情报。
	 */
	public void heartbeat(
		final Mob mob,
		final LivingEntity target,
		final boolean hasLineOfSight,
		final @Nullable Vec3 lastSeenPosition,
		final long lastSeenAt
	) {
		if (!isSupportedMember(mob) || !mob.isAlive() || !target.isAlive()) {
			return;
		}

		long now = mob.level().getGameTime();
		MemberRecord member = this.memberFor(mob);
		if (member == null) {
			MemberRecord stale = this.members.remove(mob.getId());
			if (stale != null) {
				this.detachFromSquad(stale);
			}
			// 首次注册时剥掉上次异常退出可能残留在存档里的职业名牌。
			SquadTheatrics.stripLeftoverRoleTag(mob);
			member = new MemberRecord(mob);
			this.members.put(mob.getId(), member);
		}
		if (member.target != target) {
			ZombieSquad currentSquad = this.squads.get(member.squadId);
			if (currentSquad == null) {
				this.detachFromSquad(member);
			}
			member.target = target;
			member.lastSeenPosition = null;
			member.lastSeenFacing = null;
			member.lastSeenVelocity = null;
			member.lastSeenAt = Long.MIN_VALUE;
			if (currentSquad != null && hasLineOfSight && target != currentSquad.target) {
				this.observeThreat(currentSquad, target, SquadThreatMemory.Evidence.VISIBLE_TARGET, now);
			}
		}

		member.lastHeartbeatAt = now;
		member.hasLineOfSight = hasLineOfSight;
		if (lastSeenPosition != null && lastSeenAt >= member.lastSeenAt) {
			member.lastSeenPosition = lastSeenPosition;
			member.lastSeenAt = lastSeenAt;
			if (hasLineOfSight) {
				member.lastSeenFacing = target.getLookAngle();
				member.lastSeenVelocity = target.getDeltaMovement();
			}
		}

		ZombieSquad squad = this.squads.get(member.squadId);
		if (squad != null) {
			this.updatePrimedCreeperIndex(squad, member);
			if (member.target == squad.target) {
				this.mergeObservation(squad, member);
			} else if (hasLineOfSight) {
				this.observeThreat(squad, target, SquadThreatMemory.Evidence.VISIBLE_TARGET, now);
			}
		}
	}

	/** 返回当前命令快照；未组队时返回 {@code null}，由单体战术继续接管。 */
	public @Nullable SquadDirective directiveFor(final Mob mob) {
		MemberRecord member = this.memberFor(mob);
		if (member == null || member.squadId == 0L) {
			return null;
		}

		ZombieSquad squad = this.squads.get(member.squadId);
		if (squad == null) {
			member.squadId = 0L;
			return null;
		}

		SquadOrder order = squad.orders.get(mob.getId());
		SquadRole role = order == null
			? squad.roles.getOrDefault(mob.getId(), defaultRole(mob))
			: order.role;
		Vec3 destination = order == null ? null : order.destination;
		if (role == SquadRole.SUPPORT
			&& (!(mob instanceof Zombie zombie)
				|| ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.NONE)) {
			// 源方块被玩家移走后手里只剩空桶：同一 tick 起按普通施压手执行，不等待下一轮重编队。
			role = SquadRole.PRESSURER;
			if (squad.state == SquadState.ENGAGING) {
				destination = null;
			}
		}
		MemberRecord leader = this.members.get(squad.leaderId);
		Vec3 focusPosition = this.socialFocusPosition(squad, member, leader);
		long now = mob.level().getGameTime();
		SquadCombatCadence.Window combatWindow = this.combatWindowFor(squad, now);
		boolean sharedMemoryIsFresh = isMemoryFresh(
			squad.sharedLastSeenPosition,
			squad.sharedLastSeenAt,
			now,
			ConfigManager.get().targetMemoryTicks
		);
		SquadShieldOrder shieldOrder = this.shieldOrderFor(
			squad,
			mob.getId(),
			combatWindow,
			now,
			ConfigManager.get()
		);

		return new SquadDirective(
			squad.id,
			squad.term,
			squad.planEpoch,
			squad.combatEpoch,
			combatWindow.cycle(),
			combatWindow.beat(),
			combatWindow.executeAt(),
			combatWindow.endsAt(),
			squad.state,
			squad.assaultPlan,
			squad.observedTargetTactic,
			role,
			destination,
			focusPosition,
			shieldOrder,
			sharedMemoryIsFresh
		);
	}

	/**
	 * 成员在执行当前战斗阵位时上报真实的 {@code Navigation#moveTo=false}。同一计划必须连续失败
	 * 两次才触发有界改令，且每名成员有 40～80 tick 的确定性冷却，避免坏地形造成 A* 风暴。
	 */
	public boolean reportRouteFailure(
		final Mob mob,
		final int observedPlanEpoch,
		final Vec3 failedDestination
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.dynamicSquadReplanning) {
			return false;
		}
		MemberRecord member = this.memberFor(mob);
		ZombieSquad squad = member == null ? null : this.squads.get(member.squadId);
		if (squad == null || squad.state != SquadState.ENGAGING || squad.planEpoch != observedPlanEpoch) {
			return false;
		}
		SquadOrder order = squad.orders.get(mob.getId());
		if (order == null
			|| order.destination == null
			|| order.destination.distanceToSqr(failedDestination) > 1.0
			|| mob.position().distanceToSqr(failedDestination) <= ORDER_REACHED_DISTANCE_SQUARED) {
			return false;
		}

		long now = mob.level().getGameTime();
		int cooldown = 40 + Math.floorMod((int)(squad.id ^ mob.getId() ^ observedPlanEpoch), 41);
		SquadRouteFailureTracker.Decision decision = member.routeFailures.recordFailure(
			observedPlanEpoch,
			failedDestination,
			now,
			cooldown
		);
		SmartZombieMetrics.combatRouteFailure();
		if (decision != SquadRouteFailureTracker.Decision.REPLAN) {
			if (decision == SquadRouteFailureTracker.Decision.COOLDOWN) {
				SmartZombieMetrics.combatReplanSuppressed();
			}
			return false;
		}
		if (config.squadSharedDangerMemory) {
			squad.dangerMemory.report(
				BlockPos.containing(failedDestination),
				SquadDangerKind.ROUTE_BLOCKED,
				2,
				now,
				ROUTE_DANGER_MEMORY_TICKS
			);
			SmartZombieMetrics.sharedDangerReported();
		}

		Vec3 targetPosition = squad.sharedLastSeenPosition;
		if (targetPosition == null) {
			return false;
		}
		Vec3 squadCentroid = this.memberCentroid(squad);
		Vec3 fallback = targetPosition.subtract(squadCentroid);
		Vec3 forward = horizontalUnit(squad.sharedTargetFacing, fallback);
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		List<SquadBriefingRoutePlanner.Candidate> alternatives = this.briefingFallbacks(
			squad,
			member,
			order.role,
			null,
			targetPosition,
			forward,
			lateral,
			config
		);
		SquadBriefingRoutePlanner.Result reroute = SquadBriefingRoutePlanner.resolve(
			order.role,
			null,
			alternatives,
			destination -> this.canReachBriefingDestination(squad, mob, destination, now)
		);
		SmartZombieMetrics.combatRouteChecks(reroute.pathChecks());

		SquadRole assignedRole = reroute.resolvedDestination() == null
			? SquadRole.PRESSURER
			: reroute.assignedRole();
		Vec3 assignedDestination = reroute.resolvedDestination();
		squad.roles.put(mob.getId(), assignedRole);
		squad.orders.put(mob.getId(), new SquadOrder(assignedRole, assignedDestination));
		squad.planEpoch++;
		member.routeFailures.reset();
		this.publishFieldRecommand(squad, member, order.role);
		SmartZombieMetrics.combatReplan();
		return true;
	}

	/** 成员只把自己真实遇到的危险位置写入所属小队的短期黑板。 */
	public void reportTraversalDanger(
		final Mob mob,
		final Vec3 position,
		final SquadDangerKind kind,
		final int severity
	) {
		if (!ConfigManager.get().squadSharedDangerMemory) {
			return;
		}
		ZombieSquad squad = this.squadFor(mob);
		if (squad == null) {
			return;
		}
		long now = mob.level().getGameTime();
		squad.dangerMemory.report(
			BlockPos.containing(position),
			kind,
			severity,
			now,
			kind == SquadDangerKind.ROUTE_BLOCKED ? ROUTE_DANGER_MEMORY_TICKS : ROUTE_DANGER_MEMORY_TICKS * 2L
		);
		SmartZombieMetrics.sharedDangerReported();
	}

	public boolean isSharedDangerNear(final Mob mob, final Vec3 position) {
		if (!ConfigManager.get().squadSharedDangerMemory) {
			return false;
		}
		ZombieSquad squad = this.squadFor(mob);
		return squad != null && squad.dangerMemory.isDangerousNear(
			BlockPos.containing(position),
			1,
			1,
			mob.level().getGameTime()
		);
	}

	/** 无副作用检查：同队已有相同目标或重叠爆点时，让候补苦力怕继续绕后等待。 */
	public boolean canReserveBlast(
		final Creeper creeper,
		final LivingEntity target,
		final Vec3 predictedCenter
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(creeper);
		if (!config.creeperBlastReservations || squad == null) {
			return true;
		}
		return squad.blastReservations.canReserve(
			creeper.getId(),
			target.getId(),
			predictedCenter,
			CreeperBlastEvacuationMath.dangerRadius(creeper.isPowered()),
			creeper.level().getGameTime()
		);
	}

	/** start 阶段真正提交爆点；外部点燃属于不可撤销事实，可以与已有预约并存但会阻止后续普通提交。 */
	public boolean tryReserveBlast(
		final Creeper creeper,
		final LivingEntity target,
		final Vec3 predictedCenter,
		final boolean forced
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(creeper);
		if (!config.creeperBlastReservations || squad == null) {
			return true;
		}
		SquadBlastReservationBook.Decision decision = squad.blastReservations.reserve(
			creeper.getId(),
			target.getId(),
			predictedCenter,
			CreeperBlastEvacuationMath.dangerRadius(creeper.isPowered()),
			forced,
			creeper.level().getGameTime(),
			BLAST_RESERVATION_TICKS
		);
		if (decision == SquadBlastReservationBook.Decision.CONFLICT) {
			SmartCreeperMetrics.blastReservationConflict();
			return false;
		}
		if (decision == SquadBlastReservationBook.Decision.ACQUIRED) {
			SmartCreeperMetrics.blastReservationAcquired();
		}
		return true;
	}

	public boolean renewBlastReservation(final Creeper creeper, final Vec3 predictedCenter) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(creeper);
		return !config.creeperBlastReservations
			|| squad == null
			|| squad.blastReservations.renew(
				creeper.getId(),
				predictedCenter,
				creeper.level().getGameTime(),
				BLAST_RESERVATION_TICKS
			);
	}

	public void releaseBlastReservation(final Creeper creeper) {
		ZombieSquad squad = this.squadFor(creeper);
		if (squad != null && squad.blastReservations.reservationFor(
			creeper.getId(),
			creeper.level().getGameTime()
		) != null) {
			squad.blastReservations.release(creeper.getId());
			SmartCreeperMetrics.blastReservationReleased();
		}
	}

	/** 候补爆破手在当前预约爆点外侧等待，避免自己被第一枚爆炸提前清场。 */
	public @Nullable Vec3 blastQueueStagingPointFor(
		final Creeper creeper,
		final LivingEntity target,
		final Vec3 predictedCenter
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(creeper);
		if (!config.creeperBlastReservations || squad == null) {
			return null;
		}
		double radius = CreeperBlastEvacuationMath.dangerRadius(creeper.isPowered());
		SquadBlastReservationBook.Reservation conflict = squad.blastReservations.conflictingReservation(
			creeper.getId(),
			target.getId(),
			predictedCenter,
			radius,
			creeper.level().getGameTime()
		);
		if (conflict == null) {
			return null;
		}
		Vec3 forward = horizontalUnit(target.getLookAngle(), target.position().subtract(creeper.position()));
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		double side = (creeper.getId() & 1) == 0 ? 1.0 : -1.0;
		return conflict.center()
			.subtract(forward.scale(2.0))
			.add(lateral.scale(side * (Math.min(radius, 8.0) + 1.0)));
	}

	/** 射手从开始蓄力起短期广播弹道；只有同队正式交战成员会收到。 */
	public void reserveFiringLane(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final boolean explosive
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(shooter);
		if (!config.squadFiringLaneReservations || squad == null || squad.state != SquadState.ENGAGING) {
			return;
		}
		List<Vec3> trajectory = SkeletonShotSafety.trajectorySamples(shooter.getEyePosition(), target);
		if (trajectory.isEmpty()) {
			return;
		}
		boolean created = squad.firingLanes.reserve(
			shooter.getId(),
			target.getId(),
			trajectory,
			FIRING_LANE_CLEARANCE,
			explosive,
			shooter.level().getGameTime(),
			FIRING_LANE_RESERVATION_TICKS
		);
		if (created) {
			SmartSkeletonMetrics.firingLaneReserved();
		}
	}

	public void releaseFiringLane(final AbstractSkeleton shooter) {
		ZombieSquad squad = this.squadFor(shooter);
		if (squad != null) {
			squad.firingLanes.release(shooter.getId());
		}
	}

	public SquadFiringLaneRegistry.@Nullable Reservation blockingFiringLaneFor(final PathfinderMob member) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(member);
		if (!config.squadFiringLaneReservations || squad == null || squad.state != SquadState.ENGAGING) {
			return null;
		}
		return squad.firingLanes.blockingLane(
			member.getId(),
			member.getBoundingBox(),
			member.level().getGameTime()
		);
	}

	/** 当前分配目标只读入口；没有次要威胁时始终返回小队主目标。 */
	public @Nullable LivingEntity assignedTargetFor(final Mob mob) {
		ZombieSquad squad = this.squadFor(mob);
		if (squad == null) {
			return null;
		}
		int targetId = squad.targetAssignments.getOrDefault(mob.getId(), squad.target.getId());
		return targetId == squad.target.getId() ? squad.target : squad.threatEntities.get(targetId);
	}

	/** 每只成员只读取自己的预计算伤员命令；此入口不会扫描队友。 */
	public @Nullable SquadCasualtyDirective casualtyDirectiveFor(final Mob mob) {
		MobsThinkNowConfig config = ConfigManager.get();
		ZombieSquad squad = this.squadFor(mob);
		if (!config.squadCasualtyExtraction
			|| squad == null
			|| squad.state != SquadState.ENGAGING
			|| mob.level().getGameTime() >= squad.casualtyResponseEndsAt) {
			return null;
		}
		return squad.casualtyDirectives.get(mob.getId());
	}

	private Vec3 socialFocusPosition(
		final ZombieSquad squad,
		final MemberRecord member,
		final @Nullable MemberRecord leader
	) {
		SquadSocialChoreography.Focus focus = member.mob.getId() == squad.leaderId
			? squad.socialAttention.leaderFocus()
			: squad.socialAttention.audienceFocus();
		Vec3 resolved = this.resolveSocialFocus(squad, focus);
		if (resolved.distanceToSqr(member.mob.position()) > 0.25) {
			return resolved;
		}
		if (member.mob.getId() != squad.leaderId && leader != null) {
			return eyePosition(leader.mob);
		}
		return eyePosition(squad.target);
	}

	private Vec3 resolveSocialFocus(
		final ZombieSquad squad,
		final SquadSocialChoreography.Focus focus
	) {
		return switch (focus.kind()) {
			case TARGET -> eyePosition(squad.target);
			case ACTOR -> {
				int actorId = focus.actorEntityId() == SquadSocialChoreography.LEADER_ACTOR_ID
					? squad.leaderId
					: focus.actorEntityId();
				MemberRecord actor = this.members.get(actorId);
				yield actor == null ? eyePosition(squad.target) : eyePosition(actor.mob);
			}
			case ROLE_DESTINATION -> this.briefingDestinationForRole(squad, focus.role());
		};
	}

	private Vec3 briefingDestinationForRole(final ZombieSquad squad, final @Nullable SquadRole role) {
		if (role != null) {
			for (Map.Entry<Integer, SquadBriefingRoutePlanner.Result> entry : squad.briefingReports.entrySet()) {
				SquadBriefingRoutePlanner.Result report = entry.getValue();
				if (report.requestedRole() != role) {
					continue;
				}
				Vec3 resolved = report.resolvedDestination();
				if (resolved != null) {
					return resolved.add(0.0, 1.0, 0.0);
				}
				Vec3 requested = report.requestedDestination();
				if (requested != null) {
					return requested.add(0.0, 1.0, 0.0);
				}
			}
		}
		Vec3 target = squad.sharedLastSeenPosition == null ? squad.target.position() : squad.sharedLastSeenPosition;
		return target.add(0.0, 1.0, 0.0);
	}

	private static Vec3 eyePosition(final LivingEntity entity) {
		return new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
	}

	/** 测试和诊断使用的只读小队摘要。 */
	public @Nullable SquadView viewFor(final Mob mob) {
		MemberRecord member = this.memberFor(mob);
		ZombieSquad squad = member == null ? null : this.squads.get(member.squadId);
		if (squad == null) {
			return null;
		}
		SquadCombatCadence.Window combatWindow = this.combatWindowFor(squad, mob.level().getGameTime());
		return new SquadView(
			squad.id,
			squad.state,
			squad.assaultPlan,
			squad.observedTargetTactic,
			squad.leaderId,
			squad.term,
			squad.planEpoch,
			squad.combatEpoch,
			combatWindow.cycle(),
			combatWindow.beat(),
			combatWindow.executeAt(),
			squad.webAmbushStartedAt != Long.MIN_VALUE,
			squad.shieldWallMemberIds.size() >= 2,
			squad.deploymentReadyFraction,
			squad.memberIds.size()
		);
	}

	private SquadCombatCadence.Window combatWindowFor(final ZombieSquad squad, final long now) {
		if (squad.state == SquadState.ENGAGING && squad.firstCommitAt != Long.MAX_VALUE) {
			SquadCombatCadence.Window normal = SquadCombatCadence.combatWindow(squad.firstCommitAt, now);
			SquadCombatCadence.Window ambush = SquadWebAmbushTiming.window(
				squad.webAmbushStartedAt,
				squad.webAmbushLastConfirmedAt,
				now,
				normal.cycle()
			);
			return ambush == null ? normal : ambush;
		}
		if (squad.state == SquadState.DEPLOYING && squad.firstCommitAt != Long.MAX_VALUE) {
			return SquadCombatCadence.deploymentWindow(squad.commitArmedAt, squad.firstCommitAt, now);
		}
		return SquadCombatCadence.waiting();
	}

	private void updateObservedTargetTactics(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		if (!config.observableTargetTactics || !(squad.target instanceof Player player)) {
			squad.tacticMemory.clear();
			if (squad.observedTargetTactic != ObservedTargetTactic.NONE) {
				this.applyObservedTactic(squad, ObservedTargetTactic.NONE);
			}
			return;
		}

		boolean visible = false;
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null
				&& member.target == squad.target
				&& member.hasLineOfSight
				&& member.lastSeenAt >= now - config.decisionIntervalTicks * 2L) {
				visible = true;
				break;
			}
		}
		SquadTacticMemory.Update update;
		if (!visible) {
			update = squad.tacticMemory.age(now);
		} else {
			EnumSet<ObservedTargetTactic> signals = EnumSet.noneOf(ObservedTargetTactic.class);
			Vec3 centroid = this.memberCentroid(squad);
			Vec3 targetPosition = player.position();
			if (targetPosition.y - centroid.y >= 2.5) {
				signals.add(ObservedTargetTactic.HIGH_GROUND);
			}
			if (player.isBlocking()) {
				signals.add(ObservedTargetTactic.SHIELDING);
			}
			Vec3 away = new Vec3(targetPosition.x - centroid.x, 0.0, targetPosition.z - centroid.z);
			Vec3 velocity = clampHorizontal(player.getDeltaMovement(), 0.5);
			if (away.horizontalDistanceSqr() >= 36.0
				&& away.horizontalDistanceSqr() > MINIMUM_HORIZONTAL_LENGTH_SQUARED
				&& velocity.dot(away.normalize()) >= 0.045) {
				signals.add(ObservedTargetTactic.KITING);
			}

			BlockPos feet = player.blockPosition();
			int blockedSides = 0;
			for (Direction direction : TACTIC_CARDINAL_DIRECTIONS) {
				BlockPos side = feet.relative(direction);
				if (!player.level().getBlockState(side).getCollisionShape(player.level(), side).isEmpty()
					|| !player.level().getBlockState(side.above())
						.getCollisionShape(player.level(), side.above()).isEmpty()) {
					blockedSides++;
				}
			}
			if (blockedSides >= 2) {
				signals.add(ObservedTargetTactic.CHOKEPOINT);
			}
			if (player.level().getFluidState(feet).is(FluidTags.WATER)) {
				signals.add(ObservedTargetTactic.WATER_DEFENSE);
			}
			update = squad.tacticMemory.observe(signals, now);
		}

		if (update.changed()) {
			this.applyObservedTactic(squad, update.primary());
		}
	}

	private void applyObservedTactic(
		final ZombieSquad squad,
		final ObservedTargetTactic tactic
	) {
		squad.observedTargetTactic = tactic;
		// 从基础职位重新规划，确保切入/退出盾楔时盾卫会同步加入/离开正面，而不是遗留旧职位。
		this.rebuildRoles(squad);
		squad.planEpoch++;
		SmartZombieMetrics.targetTacticChanged();
	}

	private void observeThreat(
		final ZombieSquad squad,
		final LivingEntity target,
		final SquadThreatMemory.Evidence evidence,
		final long now
	) {
		if (target == squad.target || !this.isValidSecondaryThreat(squad, target)) {
			return;
		}
		boolean firstObservation = !squad.threatEntities.containsKey(target.getId());
		SquadThreatMemory.ObservationResult result = squad.threatMemory.observe(target.getId(), evidence, now);
		if (result.evictedTargetId() >= 0) {
			squad.threatEntities.remove(result.evictedTargetId());
		}
		if (!result.retained()) {
			return;
		}
		squad.threatEntities.put(target.getId(), target);
		if (firstObservation) {
			SmartZombieMetrics.secondaryThreatObserved();
		}
	}

	/**
	 * 每个决策周期只处理最多八个有证据目标，并只把最多 40% 的合格成员分给其中两个次要威胁。
	 */
	private void refreshThreatAssignments(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		if (!config.squadThreatDistribution) {
			squad.threatMemory.clear();
			squad.threatEntities.clear();
			this.applyTargetAssignments(squad, Map.of());
			return;
		}

		List<SquadThreatMemory.ThreatScore> remembered = squad.threatMemory.snapshot(now);
		Set<Integer> activeThreatIds = new LinkedHashSet<>();
		for (SquadThreatMemory.ThreatScore score : remembered) {
			activeThreatIds.add(score.targetId());
		}
		squad.threatEntities.entrySet().removeIf(entry -> !activeThreatIds.contains(entry.getKey())
			|| !this.isValidSecondaryThreat(squad, entry.getValue()));

		Vec3 centroid = this.memberCentroid(squad);
		List<SquadThreatAllocator.Threat> threats = new ArrayList<>();
		threats.add(new SquadThreatAllocator.Threat(squad.target.getId(), 100.0));
		for (SquadThreatMemory.ThreatScore score : remembered) {
			LivingEntity target = squad.threatEntities.get(score.targetId());
			if (target == null) {
				squad.threatMemory.remove(score.targetId());
				continue;
			}
			double distance = Math.sqrt(Math.max(0.0, centroid.distanceToSqr(target.position())));
			double proximityBonus = Math.max(0.0, 20.0 - distance * 0.8);
			boolean witnessed = false;
			for (int memberId : squad.memberIds) {
				MemberRecord member = this.members.get(memberId);
				if (member != null && member.target == target && member.hasLineOfSight) {
					witnessed = true;
					break;
				}
			}
			threats.add(new SquadThreatAllocator.Threat(
				target.getId(),
				Math.min(100.0, score.score() + proximityBonus + (witnessed ? 10.0 : 0.0))
			));
		}

		List<SquadThreatAllocator.Member> candidates = new ArrayList<>(squad.memberIds.size());
		for (int memberId : this.orderedMemberIds(squad)) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			Map<Integer, Double> distances = new HashMap<>();
			distances.put(squad.target.getId(), member.mob.distanceToSqr(squad.target));
			for (LivingEntity threat : squad.threatEntities.values()) {
				distances.put(threat.getId(), member.mob.distanceToSqr(threat));
			}
			SquadRole role = squad.roles.getOrDefault(memberId, defaultRole(member.mob));
			boolean eligible = !member.mob.isPassenger()
				&& !member.mob.isVehicle()
				&& !(member.mob instanceof Creeper creeper && isPrimedCreeper(creeper));
			candidates.add(new SquadThreatAllocator.Member(memberId, role, eligible, Map.copyOf(distances)));
		}

		Map<Integer, Integer> assignments = SquadThreatAllocator.assign(
			candidates,
			squad.target.getId(),
			threats
		);
		this.applyTargetAssignments(squad, assignments);
	}

	private void applyTargetAssignments(
		final ZombieSquad squad,
		final Map<Integer, Integer> requested
	) {
		int changed = 0;
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			int requestedTargetId = requested.getOrDefault(memberId, squad.target.getId());
			LivingEntity assigned = requestedTargetId == squad.target.getId()
				? squad.target
				: squad.threatEntities.get(requestedTargetId);
			if (assigned == null || !assigned.isAlive()) {
				assigned = squad.target;
				requestedTargetId = assigned.getId();
			}
			int previousTargetId = squad.targetAssignments.getOrDefault(memberId, squad.target.getId());
			squad.targetAssignments.put(memberId, requestedTargetId);
			boolean assignmentChanged = previousTargetId != requestedTargetId;
			if (assignmentChanged) {
				changed++;
			}
			if (member.mob.getTarget() != assigned || member.target != assigned) {
				member.mob.setTarget(assigned);
				member.target = assigned;
				member.lastSeenPosition = null;
				member.lastSeenFacing = null;
				member.lastSeenVelocity = null;
				member.lastSeenAt = Long.MIN_VALUE;
			}
		}
		if (changed > 0) {
			squad.planEpoch++;
			SmartZombieMetrics.threatAssignmentsChanged(changed);
		}
	}

	private boolean isValidSecondaryThreat(final ZombieSquad squad, final LivingEntity target) {
		if (!target.isAlive()
			|| target.level() != squad.target.level()
			|| !EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)
			|| squad.memberIds.contains(target.getId())) {
			return false;
		}
		MemberRecord leader = this.members.get(squad.leaderId);
		return leader == null || !leader.mob.isAlliedTo(target);
	}

	private void publishFieldRecommand(
		final ZombieSquad squad,
		final MemberRecord reporter,
		final SquadRole failedRole
	) {
		if (reporter.mob instanceof Zombie zombie) {
			ZombieBodyLanguage.play(zombie, ZombieBodyAction.SHAKE_HEAD);
		}
		MemberRecord leader = this.members.get(squad.leaderId);
		if (leader == null || !(leader.mob instanceof Zombie zombieLeader)) {
			return;
		}
		ZombieBodyAction command = failedRole == SquadRole.FLANK_LEFT
			? ZombieBodyAction.COMMAND_LEFT
			: failedRole == SquadRole.FLANK_RIGHT
				? ZombieBodyAction.COMMAND_RIGHT
				: ZombieBodyAction.COMMAND;
		ZombieBodyLanguage.play(zombieLeader, command);
	}

	/**
	 * 返回当前成员最危险的已引信同队苦力怕。
	 *
	 * <p>每支小队只维护正在引信的实体 ID，查询复杂度是 O(P)，P 为同时活动的爆点数量，而不是
	 * O(K) 全员扫描。蜘蛛不会把自己背上的苦力怕误判成应当逃离的外部爆点。</p>
	 */
	public @Nullable Creeper nearestPrimedCreeperThreatFor(final Mob mob) {
		SquadBlastThreat threat = this.nearestBlastThreatFor(mob);
		return threat == null ? null : threat.creeper;
	}

	/**
	 * 供空闲蜘蛛读取同队正在追击同一目标的引信。与撤离查询不同，这里不要求蜘蛛已经进入爆炸圈；
	 * 目标是提前封住逃生线。每队只遍历已引信 ID 集合 O(P)，并顺手剔除失效记录。
	 */
	public @Nullable SquadBlastThreat activeBlastForContainment(final Mob mob, final LivingEntity target) {
		ZombieSquad squad = this.squadFor(mob);
		if (squad == null || squad.primedCreeperIds.isEmpty()) {
			return null;
		}
		SquadBlastThreat selected = null;
		double nearestToTarget = Double.POSITIVE_INFINITY;
		long now = mob.level().getGameTime();
		Iterator<Integer> iterator = squad.primedCreeperIds.iterator();
		while (iterator.hasNext()) {
			int candidateId = iterator.next();
			MemberRecord candidate = this.members.get(candidateId);
			if (candidate == null
				|| candidate.squadId != squad.id
				|| !(candidate.mob instanceof Creeper creeper)
				|| !isPrimedCreeper(creeper)) {
				iterator.remove();
				continue;
			}
			SquadBlastReservationBook.Reservation reservation = squad.blastReservations.reservationFor(candidateId, now);
			boolean sameTarget = reservation == null
				? creeper.getTarget() == target
				: reservation.targetId() == target.getId();
			if (!sameTarget || creeper.getVehicle() == mob || mob.getVehicle() == creeper) {
				continue;
			}
			double distanceSquared = target.distanceToSqr(creeper);
			if (distanceSquared < nearestToTarget) {
				nearestToTarget = distanceSquared;
				// 玩家最容易读懂并躲避的是苦力怕当前来向；蛛网因此封住其反方向，而非不可见预测点。
				selected = new SquadBlastThreat(creeper, creeper.position(), creeper.isPowered());
			}
		}
		return selected;
	}

	/**
	 * 无副作用检查同队跳扑令牌。脱队蜘蛛、关闭功能或攻击不同世界目标时保持原来的单体节奏；
	 * 已组队蜘蛛只做一次 O(1) 状态读取，不扫描附近实体或全队成员。
	 */
	public boolean canStartSpiderPounce(final Spider spider, final LivingEntity target) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.squadSpiderPounceStaggering || target.level() != spider.level()) {
			return true;
		}
		ZombieSquad squad = this.squadFor(spider);
		if (squad == null || this.assignedTargetEntity(squad, spider.getId()) != target) {
			return true;
		}
		return squad.spiderPounceCadence.canReserve(
			spider.getId(),
			target.getId(),
			spider.level().getGameTime()
		);
	}

	/**
	 * 在真正起跳帧提交令牌。GoalSelector 同处服务端主线程，因此第一个成功提交者会让同 tick
	 * 后续蜘蛛立刻等待；租约同时受最小起跳间隔与最长空中时间双重约束。
	 */
	public boolean tryStartSpiderPounce(final Spider spider, final LivingEntity target) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.squadSpiderPounceStaggering || target.level() != spider.level()) {
			return true;
		}
		ZombieSquad squad = this.squadFor(spider);
		if (squad == null || this.assignedTargetEntity(squad, spider.getId()) != target) {
			return true;
		}
		boolean reserved = squad.spiderPounceCadence.tryReserve(
			spider.getId(),
			target.getId(),
			spider.level().getGameTime(),
			config.squadSpiderPounceIntervalTicks,
			MAXIMUM_SPIDER_POUNCE_RESERVATION_TICKS
		);
		if (reserved) {
			SmartSpiderMetrics.coordinatedPounceStarted();
		}
		return reserved;
	}

	/** 落地或被更高优先级活动打断时释放空中所有权；最小起跳间隔仍然保留。 */
	public void releaseSpiderPounce(final Spider spider) {
		ZombieSquad squad = this.squadFor(spider);
		if (squad != null) {
			squad.spiderPounceCadence.release(spider.getId());
		}
	}

	/** GameTest 与状态诊断只读：检查该蜘蛛是否正持有自己小队的空中令牌。 */
	public boolean ownsSpiderPounceReservation(final Spider spider) {
		ZombieSquad squad = this.squadFor(spider);
		return squad != null
			&& squad.spiderPounceCadence.ownerId(spider.level().getGameTime()) == spider.getId();
	}

	/** 返回真实引信实体和预约的预测爆点，使队友不是只会远离苦力怕当前脚下位置。 */
	public @Nullable SquadBlastThreat nearestBlastThreatFor(final Mob mob) {
		MemberRecord member = this.memberFor(mob);
		ZombieSquad squad = member == null ? null : this.squads.get(member.squadId);
		if (squad == null || squad.primedCreeperIds.isEmpty()) {
			return null;
		}

		SquadBlastThreat selected = null;
		double bestRelativeDanger = Double.POSITIVE_INFINITY;
		long now = mob.level().getGameTime();
		Iterator<Integer> iterator = squad.primedCreeperIds.iterator();
		while (iterator.hasNext()) {
			int candidateId = iterator.next();
			MemberRecord candidate = this.members.get(candidateId);
			if (candidate == null
				|| candidate.squadId != squad.id
				|| !(candidate.mob instanceof Creeper creeper)
				|| !isPrimedCreeper(creeper)) {
				iterator.remove();
				continue;
			}
			if (creeper == mob || creeper.getVehicle() == mob || mob.getVehicle() == creeper) {
				continue;
			}
			SquadBlastReservationBook.Reservation reservation = squad.blastReservations.reservationFor(
				creeper.getId(),
				now
			);
			Vec3 currentCenter = creeper.position();
			Vec3 predictedCenter = reservation == null ? currentCenter : reservation.center();
			double currentDistanceSquared = mob.position().distanceToSqr(currentCenter);
			double predictedDistanceSquared = mob.position().distanceToSqr(predictedCenter);
			Vec3 center = predictedDistanceSquared < currentDistanceSquared ? predictedCenter : currentCenter;
			double distanceSquared = Math.min(currentDistanceSquared, predictedDistanceSquared);
			if (!CreeperBlastEvacuationMath.isInsideDanger(distanceSquared, creeper.isPowered())) {
				continue;
			}
			double radius = CreeperBlastEvacuationMath.dangerRadius(creeper.isPowered());
			double relativeDanger = distanceSquared / (radius * radius);
			if (relativeDanger < bestRelativeDanger) {
				bestRelativeDanger = relativeDanger;
				selected = new SquadBlastThreat(creeper, center, creeper.isPowered());
			}
		}
		return selected;
	}

	/**
	 * 返回协调器给蜘蛛固定分配的乘员。形成/开会阶段也保留此关系，供其他运输 Goal
	 * 判断自己是否应该让路；真正开始接送则由 {@link #activeTransportPartnerFor(Spider)} 控制。
	 */
	public @Nullable Mob assignedTransportPartnerFor(final Spider spider) {
		MemberRecord carrier = this.memberFor(spider);
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		if (squad == null) {
			return null;
		}
		Integer passengerId = squad.transportPartners.get(spider.getId());
		MemberRecord passenger = passengerId == null ? null : this.members.get(passengerId);
		return passenger != null
			&& passenger.squadId == squad.id
			&& passenger.mob.isAlive()
			? passenger.mob
			: null;
	}

	/** 只有小队正式交战后，蜘蛛才离开阵位去接取乘员。 */
	public @Nullable Mob activeTransportPartnerFor(final Spider spider) {
		MemberRecord carrier = this.memberFor(spider);
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		return squad != null && squad.state == SquadState.ENGAGING
			? this.assignedTransportPartnerFor(spider)
			: null;
	}

	/**
	 * 正式交战后给已装载蜘蛛计算实时 staging point。这里只做 O(1) Map 读取和向量数学：
	 * 苦力怕从侧后方提交爆破，骷髅则停在交叉射界，不让远程乘员被载具送去贴脸。
	 */
	public @Nullable Vec3 carrierStagingPointFor(final Spider spider) {
		MemberRecord carrier = this.memberFor(spider);
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		if (squad == null || squad.state != SquadState.ENGAGING || !squad.target.isAlive()) {
			return null;
		}
		Integer passengerId = squad.transportPartners.get(spider.getId());
		MemberRecord passenger = passengerId == null ? null : this.members.get(passengerId);
		if (passenger == null || passenger.squadId != squad.id || !passenger.mob.isAlive()) {
			return null;
		}

		Vec3 targetPosition = squad.target.position();
		Vec3 targetFacing = squad.target.getLookAngle();
		Vec3 fallback = targetPosition.subtract(spider.position());
		int sideSeed = spider.getId() ^ Long.hashCode(squad.id);
		if (passenger.mob instanceof Creeper && squad.assaultPlan.usesMountedBreach()) {
			return SquadAssaultGeometry.mountedBreachStaging(
				targetPosition,
				targetFacing,
				fallback,
				sideSeed
			);
		}
		if (passenger.mob instanceof AbstractSkeleton skeleton
			&& squad.assaultPlan != SquadAssaultPlan.SWARM) {
			double range = SkeletonCombatMath.intelligenceAdjustedPreferredRange(
				ConfigManager.get().skeletonPreferredRange,
				SkeletonIntelligence.get(skeleton)
			);
			return SquadAssaultGeometry.mobileFireSupportStaging(
				targetPosition,
				targetFacing,
				fallback,
				range,
				sideSeed
			);
		}
		return null;
	}

	/** 返回分给巨人头顶火力位的射手；集结阶段同样保留预约关系。 */
	public @Nullable AbstractSkeleton assignedGiantHeadRiderFor(final Giant giant) {
		MemberRecord carrier = this.memberFor(giant);
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		Integer riderId = squad == null ? null : squad.giantHeadRiders.get(giant.getId());
		MemberRecord rider = riderId == null ? null : this.members.get(riderId);
		return rider != null
			&& rider.squadId == squad.id
			&& rider.mob.isAlive()
			&& rider.mob instanceof AbstractSkeleton skeleton
			? skeleton
			: null;
	}

	/** 射手反向查询自己的巨人火力平台。 */
	public @Nullable Giant assignedGiantMountFor(final AbstractSkeleton skeleton) {
		MemberRecord rider = this.memberFor(skeleton);
		ZombieSquad squad = rider == null ? null : this.squads.get(rider.squadId);
		if (squad == null) {
			return null;
		}
		for (Map.Entry<Integer, Integer> assignment : squad.giantHeadRiders.entrySet()) {
			if (assignment.getValue() != skeleton.getId()) {
				continue;
			}
			MemberRecord giant = this.members.get(assignment.getKey());
			return giant != null && giant.squadId == squad.id && giant.mob.isAlive() && giant.mob instanceof Giant value
				? value
				: null;
		}
		return null;
	}

	/** 只有正式交战时，射手才从阵位跳上巨人头顶。 */
	public @Nullable Giant activeGiantMountFor(final AbstractSkeleton skeleton) {
		MemberRecord rider = this.memberFor(skeleton);
		ZombieSquad squad = rider == null ? null : this.squads.get(rider.squadId);
		return squad != null && squad.state == SquadState.ENGAGING
			? this.assignedGiantMountFor(skeleton)
			: null;
	}

	/** 返回巨人左右手的固定载荷预约，顺序即右手、左手。 */
	public List<Mob> assignedGiantPayloadsFor(final Giant giant) {
		MemberRecord carrier = this.memberFor(giant);
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		List<Integer> payloadIds = squad == null ? null : squad.giantHandPayloads.get(giant.getId());
		if (payloadIds == null || payloadIds.isEmpty()) {
			return List.of();
		}
		List<Mob> result = new ArrayList<>(payloadIds.size());
		for (int payloadId : payloadIds) {
			MemberRecord payload = this.members.get(payloadId);
			if (payload != null && payload.squadId == squad.id && payload.mob.isAlive()) {
				result.add(payload.mob);
			}
		}
		return List.copyOf(result);
	}

	/** 巨人只在正式交战阶段离开阵位收取双手载荷。 */
	public List<Mob> activeGiantPayloadsFor(final Giant giant) {
		MemberRecord carrier = this.memberFor(giant);
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		return squad != null && squad.state == SquadState.ENGAGING
			? this.assignedGiantPayloadsFor(giant)
			: List.of();
	}

	/** 目标失效时立即注销；正常 Goal 切换则交给心跳超时，避免频繁退队又入队。 */
	public void unregister(final Mob mob) {
		MemberRecord member = this.memberFor(mob);
		if (member != null && this.members.remove(mob.getId(), member)) {
			this.detachFromSquad(member);
		}
	}

	private void tick(final ServerLevel level, final MobsThinkNowConfig config) {
		long now = level.getGameTime();
		if (now == this.lastTickAt) {
			return;
		}
		this.lastTickAt = now;

		if (!squadsEnabled(config)) {
			this.reset();
			return;
		}

		this.pruneMembers(level, config, now);
		if (Math.floorMod(now, config.squadFormationIntervalTicks) == 0L) {
			this.formNewSquads(config, now);
		}

		// 状态转换可能解散小队，因此在快照上迭代。
		for (ZombieSquad squad : new ArrayList<>(this.squads.values())) {
			if (this.squads.containsKey(squad.id)) {
				this.updateSquad(squad, config, now);
			}
			if (this.squads.containsKey(squad.id)) {
				this.presentSquad(level, squad, config, now);
				this.applySquadMobility(squad, config, now);
			}
		}
		SmartZombieMetrics.coordinatorTick();
	}

	private void pruneMembers(final ServerLevel level, final MobsThinkNowConfig config, final long now) {
		Iterator<MemberRecord> iterator = this.members.values().iterator();
		while (iterator.hasNext()) {
			MemberRecord member = iterator.next();
			boolean invalid = member.mob.level() != level
				|| member.mob.isRemoved()
				|| !member.mob.isAlive()
				|| now - member.lastHeartbeatAt > config.memberHeartbeatTimeoutTicks;
			ZombieSquad squad = member.squadId == 0L ? null : this.squads.get(member.squadId);
			if (!invalid && member.squadId != 0L) {
				if (squad == null || !squad.target.isAlive()) {
					invalid = true;
				} else {
					LivingEntity current = member.mob.getTarget();
					if (current == null || !current.isAlive()) {
						LivingEntity fallback = this.assignedTargetEntity(squad, member.mob.getId());
						if (fallback == null || !fallback.isAlive()) {
							fallback = squad.target;
						}
						member.mob.setTarget(fallback);
						member.target = fallback;
					} else {
						member.target = current;
					}
				}
			} else if (!invalid) {
				invalid = member.target == null
					|| !member.target.isAlive()
					|| member.mob.getTarget() != member.target;
			}
			if (invalid) {
				this.detachFromSquad(member);
				iterator.remove();
			}
		}
	}

	private void formNewSquads(final MobsThinkNowConfig config, final long now) {
		BoundedSpatialIndex<Integer, MemberRecord> spatialIndex = new BoundedSpatialIndex<>(
			config.coordinationRadius,
			member -> member.target == null ? Integer.MIN_VALUE : member.target.getId(),
			member -> member.mob.getX(),
			member -> member.mob.getZ()
		);
		List<MemberRecord> seeds = new ArrayList<>();
		for (MemberRecord member : this.members.values()) {
			if (member.squadId != 0L
				|| member.target == null
				|| !isMemoryFresh(member.lastSeenPosition, member.lastSeenAt, now, config.targetMemoryTicks)) {
				continue;
			}
			seeds.add(member);
			spatialIndex.add(member);
		}
		this.recruitExistingSquads(spatialIndex, config, now);

		seeds.sort(Comparator.comparingInt(member -> member.mob.getId()));
		for (MemberRecord seed : seeds) {
			if (seed.squadId != 0L || seed.target == null) {
				continue;
			}

			// shared 返回只读快照；排序属于 Fabric 适配层的局部需求，先复制以免改写共享查询结果。
			List<MemberRecord> nearby = new ArrayList<>(this.collectBoundedNearby(seed, spatialIndex, config));
			if (nearby.size() < config.minimumSquadSize) {
				continue;
			}

			nearby.sort(
				Comparator.comparingDouble((MemberRecord member) -> member.mob.distanceToSqr(seed.mob))
					.thenComparingInt(member -> member.mob.getId())
			);
			if (nearby.size() > config.maximumCoordinatedZombies) {
				nearby = new ArrayList<>(nearby.subList(0, config.maximumCoordinatedZombies));
			}
			this.createSquad(nearby, config, now);
		}
	}

	/**
	 * Heartbeats from different species need not arrive on the same tick. During each bounded formation pass,
	 * existing squads first absorb nearby unassigned members sharing their exact live target; otherwise a late
	 * Parched/Drowned heartbeat can permanently fragment one encounter into adjacent squads.
	 */
	private void recruitExistingSquads(
		final BoundedSpatialIndex<Integer, MemberRecord> unassigned,
		final MobsThinkNowConfig config,
		final long now
	) {
		for (ZombieSquad squad : this.squads.values()) {
			int capacity = config.maximumCoordinatedZombies - squad.memberIds.size();
			MemberRecord leader = this.members.get(squad.leaderId);
			if (capacity <= 0 || leader == null || !squad.target.isAlive()) {
				continue;
			}
			MemberRecord anchor = leader;
			if (leader.target != squad.target) {
				anchor = new MemberRecord(leader.mob);
				anchor.target = squad.target;
			}
			double radiusSquared = config.coordinationRadius * config.coordinationRadius;
			BoundedSpatialIndex.ScanResult<MemberRecord> scan = unassigned.collectNearby(
				anchor,
				candidate -> candidate.squadId == 0L && candidate.target == squad.target,
				(first, second) -> first.mob.distanceToSqr(second.mob),
				radiusSquared,
				capacity,
				config.maximumCoordinatedZombies * 16,
				false
			);
			SmartZombieMetrics.squadCandidateChecks(scan.rawChecks());
			if (scan.candidates().isEmpty()) {
				continue;
			}
			List<MemberRecord> recruits = new ArrayList<>(scan.candidates());
			recruits.sort(
				Comparator.comparingDouble((MemberRecord member) -> member.mob.distanceToSqr(leader.mob))
					.thenComparingInt(member -> member.mob.getId())
			);
			for (MemberRecord member : recruits) {
				if (member.squadId != 0L || squad.memberIds.size() >= config.maximumCoordinatedZombies) {
					continue;
				}
				squad.memberIds.add(member.mob.getId());
				squad.targetAssignments.put(member.mob.getId(), squad.target.getId());
				member.squadId = squad.id;
				this.updatePrimedCreeperIndex(squad, member);
				this.mergeObservation(squad, member);
			}
			boolean leadershipTransitioned = false;
			if (squad.state == SquadState.FORMING) {
				this.electInitialLeader(squad);
			} else {
				int currentLeaderIntelligence = intelligenceOf(leader.mob);
				boolean smarterRecruitArrived = recruits.stream()
					.anyMatch(member -> intelligenceOf(member.mob) > currentLeaderIntelligence);
				if (smarterRecruitArrived) {
					leadershipTransitioned = this.electReplacementLeader(squad, config, now);
				}
			}
			if (!leadershipTransitioned) {
				this.rebuildRoles(squad);
				this.refreshOrdersAfterRecruitment(squad, config, now);
			}
		}
	}

	private void refreshOrdersAfterRecruitment(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		switch (squad.state) {
			case FORMING, RALLYING, REORGANIZING -> this.assignRallyOrders(squad, squad.rallyPoint, config);
			case BRIEFING -> {
				this.assignRallyOrders(squad, squad.rallyPoint, config);
				this.prepareBriefingRoutes(squad, config);
			}
			case DEPLOYING -> {
				this.prepareBriefingRoutes(squad, config);
				this.refreshCombatOrders(squad, config, false, true, true, now);
			}
			case ENGAGING -> {
				SquadCombatCadence.Window window = this.combatWindowFor(squad, now);
				this.refreshThreatAssignments(squad, config, now);
				this.refreshCombatOrders(squad, config, !window.beat().holdsFormation(), false, true, now);
			}
		}
	}

	private List<MemberRecord> collectBoundedNearby(
		final MemberRecord seed,
		final BoundedSpatialIndex<Integer, MemberRecord> spatialIndex,
		final MobsThinkNowConfig config
	) {
		double radiusSquared = config.coordinationRadius * config.coordinationRadius;
		int acceptedBudget = config.maximumCoordinatedZombies * 4;
		int rawScanBudget = config.maximumCoordinatedZombies * 16;
		BoundedSpatialIndex.ScanResult<MemberRecord> scan = spatialIndex.collectNearby(
			seed,
			candidate -> candidate.squadId == 0L,
			(first, second) -> first.mob.distanceToSqr(second.mob),
			radiusSquared,
			acceptedBudget,
			rawScanBudget
		);
		SmartZombieMetrics.squadCandidateChecks(scan.rawChecks());
		return scan.candidates();
	}

	private void createSquad(final List<MemberRecord> selected, final MobsThinkNowConfig config, final long now) {
		MemberRecord first = selected.getFirst();
		if (first.target == null) {
			return;
		}

		ZombieSquad squad = new ZombieSquad(this.nextSquadId++, first.target);
		for (MemberRecord member : selected) {
			if (member.squadId == 0L && member.target == first.target) {
				squad.memberIds.add(member.mob.getId());
				squad.targetAssignments.put(member.mob.getId(), first.target.getId());
				member.squadId = squad.id;
				this.updatePrimedCreeperIndex(squad, member);
				this.mergeObservation(squad, member);
			}
		}
		if (squad.memberIds.size() < config.minimumSquadSize || !this.electInitialLeader(squad)) {
			this.releaseMembers(squad);
			return;
		}

		squad.rallyPoint = this.calculateRallyPoint(squad, config);
		squad.state = SquadState.FORMING;
		squad.stateStartedAt = now;
		squad.stateDeadline = now + config.squadFormationTicks;
		this.rebuildRoles(squad);
		this.assignRallyOrders(squad, squad.rallyPoint, config);
		this.squads.put(squad.id, squad);
		SmartZombieMetrics.squadFormed();
		this.debug(config, squad, "formed");
	}

	private void updateSquad(final ZombieSquad squad, final MobsThinkNowConfig config, final long now) {
		if (!squad.target.isAlive() || squad.memberIds.size() < MINIMUM_SURVIVING_SQUAD_SIZE) {
			this.disband(squad, config, "target lost or too few members");
			return;
		}

		if (!squad.memberIds.contains(squad.leaderId)) {
			if (!this.electReplacementLeader(squad, config, now)) {
				this.disband(squad, config, "leader lost and no replacement");
			}
			return;
		}

		if (!isMemoryFresh(squad.sharedLastSeenPosition, squad.sharedLastSeenAt, now, config.targetMemoryTicks)) {
			this.disband(squad, config, "shared target memory expired");
			return;
		}

		if (squad.state != SquadState.ENGAGING && this.shouldEmergencyEngage(squad, config)) {
			this.enterEngaging(squad, config, now, "emergency contact");
			return;
		}

		switch (squad.state) {
			case FORMING -> {
				if (now >= squad.stateDeadline) {
					this.enterState(squad, SquadState.RALLYING, now, now + config.rallyTimeoutTicks, config, "formation window closed");
				}
			}
			case RALLYING -> {
				if (this.hasReachedQuorum(squad, config.rallyQuorum) || now >= squad.stateDeadline) {
					this.prepareBriefingRoutes(squad, config);
					this.enterState(squad, SquadState.BRIEFING, now, now + config.briefingTicks, config, "rally complete");
				}
			}
			case BRIEFING -> {
				if (now >= squad.stateDeadline) {
					this.enterDeploying(squad, config, now);
				}
			}
			case DEPLOYING -> {
				SquadReadinessBarrier.Result readiness = this.deploymentReadiness(squad, config.deploymentQuorum);
				squad.deploymentReadyFraction = readiness.readyFraction();
				if (squad.firstCommitAt != Long.MAX_VALUE) {
					if (now >= squad.firstCommitAt) {
						this.enterEngaging(squad, config, now, "synchronized assault committed");
					}
				} else if (readiness.canCommit()) {
					this.armCommit(squad, config, now, false);
				} else if (now >= squad.stateDeadline) {
					this.armCommit(squad, config, now, true);
				}
			}
			case ENGAGING -> {
				this.refreshWebAmbushOpportunity(squad, config, now);
				SquadCombatCadence.Window combatWindow = this.combatWindowFor(squad, now);
				this.refreshShieldWallRotation(squad, config, combatWindow, now);
				if (combatWindow.beat() != squad.lastCombatBeat) {
					this.transitionCombatBeat(squad, config, combatWindow.beat(), now);
				}
				if (now >= squad.nextPlanRefreshAt) {
					this.refreshThreatAssignments(squad, config, now);
					this.updateObservedTargetTactics(squad, config, now);
					this.refreshCasualtyResponse(squad, config, now);
					this.refreshCombatOrders(
						squad,
						config,
						!combatWindow.beat().holdsFormation(),
						false,
						false,
						now
					);
					squad.nextPlanRefreshAt = now + config.decisionIntervalTicks;
				}
			}
			case REORGANIZING -> {
				if (now >= squad.stateDeadline) {
					this.enterDeploying(squad, config, now);
				}
			}
		}
	}

	private boolean electInitialLeader(final ZombieSquad squad) {
		OptionalInt winner = SquadLeaderElection.elect(this.electionCandidates(squad));
		if (winner.isEmpty()) {
			return false;
		}
		squad.leaderId = winner.getAsInt();
		squad.term = 1;
		SmartZombieMetrics.leaderElection(false);
		return true;
	}

	private boolean electReplacementLeader(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		OptionalInt winner = SquadLeaderElection.elect(this.electionCandidates(squad));
		if (winner.isEmpty()) {
			return false;
		}

		squad.leaderId = winner.getAsInt();
		squad.term++;
		this.rebuildRoles(squad);
		MemberRecord leader = this.members.get(squad.leaderId);
		squad.rallyPoint = leader == null ? squad.rallyPoint : leader.mob.position();
		this.assignRallyOrders(squad, squad.rallyPoint, config);
		this.prepareBriefingRoutes(squad, config);
		this.enterState(squad, SquadState.REORGANIZING, now, now + config.regroupTicks, config, "leader re-elected");
		SmartZombieMetrics.leaderElection(true);
		return true;
	}

	private Collection<SquadLeaderCandidate> electionCandidates(final ZombieSquad squad) {
		List<SquadLeaderCandidate> candidates = new ArrayList<>();
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null && member.mob.isAlive()) {
				candidates.add(
					new SquadLeaderCandidate(memberId, intelligenceOf(member.mob), randomElectionTicket(member.mob))
				);
			}
		}
		return candidates;
	}

	/**
	 * 每队每 tick 只查目标脚下、上下三格的蛛网登记；只有 O(1) 命中后才做一次有上限的 O(K)
	 * 成员所有权确认。这样跨物种联动不会把临时蛛网表或世界实体变成新的扫描热点。
	 */
	private void refreshWebAmbushOpportunity(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		if (!config.squadWebAmbushFollowup || !(squad.target.level() instanceof ServerLevel level)) {
			if (squad.webAmbushStartedAt != Long.MIN_VALUE) {
				this.clearWebAmbush(squad, now);
			}
			return;
		}

		UUID trapOwner = this.webTrapOwnerAtTarget(level, squad.target);
		int ownerId = trapOwner == null ? 0 : this.squadSpiderId(squad, trapOwner);
		if (squad.webAmbushStartedAt != Long.MIN_VALUE) {
			if (ownerId != 0) {
				squad.webAmbushOwnerId = ownerId;
				squad.webAmbushLastConfirmedAt = now;
			}
			long normalCycle = squad.firstCommitAt == Long.MAX_VALUE
				? 0L
				: SquadCombatCadence.combatWindow(squad.firstCommitAt, now).cycle();
			SquadCombatCadence.Window active = SquadWebAmbushTiming.window(
				squad.webAmbushStartedAt,
				squad.webAmbushLastConfirmedAt,
				now,
				normalCycle
			);
			if (active == null) {
				this.clearWebAmbush(squad, now);
				return;
			}
			if (active.beat() == SquadCombatBeat.COMMIT && !squad.webAmbushCommitAnnounced) {
				squad.webAmbushCommitAnnounced = true;
				SmartZombieMetrics.webAmbushCommitted();
				this.announceWebAmbushCommit(level, squad);
			}
			return;
		}

		if (ownerId == 0 || now < squad.nextWebAmbushAt) {
			return;
		}
		squad.webAmbushStartedAt = now;
		squad.webAmbushLastConfirmedAt = now;
		squad.webAmbushOwnerId = ownerId;
		squad.webAmbushCommitAnnounced = false;
		squad.combatEpoch++;
		SmartZombieMetrics.webAmbushStarted();
		this.announceWebAmbushStart(level, squad, ownerId);
	}

	private @Nullable UUID webTrapOwnerAtTarget(final ServerLevel level, final LivingEntity target) {
		BlockPos feet = target.blockPosition();
		UUID owner = SpiderWebTrapRegistry.ownerAt(level, feet);
		if (owner == null) {
			owner = SpiderWebTrapRegistry.ownerAt(level, feet.below());
		}
		return owner == null ? SpiderWebTrapRegistry.ownerAt(level, feet.above()) : owner;
	}

	private int squadSpiderId(final ZombieSquad squad, final UUID owner) {
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null
				&& member.mob instanceof Spider
				&& member.mob.isAlive()
				&& member.mob.getUUID().equals(owner)) {
				return memberId;
			}
		}
		return 0;
	}

	private void announceWebAmbushStart(
		final ServerLevel level,
		final ZombieSquad squad,
		final int ownerId
	) {
		MemberRecord owner = this.members.get(ownerId);
		if (owner == null || !(owner.mob instanceof Spider spider)) {
			return;
		}
		spider.getLookControl().setLookAt(squad.target, 65.0F, 45.0F);
		if (spider.onGround()) {
			Vec3 movement = spider.getDeltaMovement();
			spider.setDeltaMovement(movement.x * 0.40, Math.max(movement.y, 0.18), movement.z * 0.40);
		}
		level.playSound(null, spider, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 0.95F, 1.38F);
	}

	private void announceWebAmbushCommit(final ServerLevel level, final ZombieSquad squad) {
		MemberRecord leader = this.members.get(squad.leaderId);
		if (leader == null || !leader.mob.isAlive()) {
			return;
		}
		leader.mob.getLookControl().setLookAt(squad.target, 65.0F, 45.0F);
		leader.mob.setAggressive(true);
		if (leader.mob instanceof Zombie zombie) {
			ZombieBodyLanguage.play(zombie, ZombieBodyAction.WAR_CRY);
		}
		var sound = leader.mob instanceof AbstractSkeleton
			? SoundEvents.SKELETON_AMBIENT
			: leader.mob instanceof Spider
				? SoundEvents.SPIDER_AMBIENT
				: leader.mob instanceof Creeper
					? SoundEvents.CREEPER_HURT
					: SoundEvents.ZOMBIE_AMBIENT;
		level.playSound(null, leader.mob, sound, SoundSource.HOSTILE, 1.0F, 0.82F);
	}

	private void clearWebAmbush(final ZombieSquad squad, final long now) {
		if (squad.webAmbushStartedAt == Long.MIN_VALUE) {
			return;
		}
		if (squad.webAmbushCommitAnnounced) {
			SmartZombieMetrics.webAmbushFinished();
		} else {
			SmartZombieMetrics.webAmbushEscaped();
		}
		squad.nextWebAmbushAt = Math.max(squad.nextWebAmbushAt, now + 120L);
		squad.webAmbushStartedAt = Long.MIN_VALUE;
		squad.webAmbushLastConfirmedAt = Long.MIN_VALUE;
		squad.webAmbushOwnerId = 0;
		squad.webAmbushCommitAnnounced = false;
	}

	/**
	 * 每个决策周期在单支队伍内做一次 O(K) 快照：最多冻结一名伤员和一名护卫，随后两个 Goal 都只读 Map。
	 */
	private void refreshCasualtyResponse(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		if (!config.squadCasualtyExtraction) {
			this.clearCasualtyResponse(squad, now, false);
			return;
		}
		MemberRecord activeCasualty = this.members.get(squad.casualtyId);
		MemberRecord activeEscort = this.members.get(squad.casualtyEscortId);
		if (squad.casualtyId != 0) {
			boolean invalid = activeCasualty == null
				|| activeEscort == null
				|| !squad.memberIds.contains(squad.casualtyId)
				|| !squad.memberIds.contains(squad.casualtyEscortId)
				|| !activeCasualty.mob.isAlive()
				|| !activeEscort.mob.isAlive();
			double recoveryThreshold = Math.min(0.80, config.squadCasualtyHealthThreshold + 0.20);
			boolean recovered = !invalid && healthFraction(activeCasualty.mob) > recoveryThreshold;
			boolean safe = !invalid && SquadCasualtyPlanner.isSafe(activeCasualty.mob.position(), squad.target.position());
			if (invalid || recovered || safe || now >= squad.casualtyResponseEndsAt) {
				this.clearCasualtyResponse(squad, now, !invalid);
			} else {
				this.publishCasualtyDirectives(squad, activeCasualty, activeEscort, config);
				return;
			}
		}
		if (now < squad.nextCasualtyResponseAt) {
			return;
		}

		List<SquadCasualtyPlanner.MemberSnapshot> snapshots = new ArrayList<>(squad.memberIds.size());
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null && member.mob.isAlive()) {
				snapshots.add(this.casualtySnapshot(member.mob, config.squadSpiderCasualtyTransport));
			}
		}
		SquadCasualtyPlanner.Response selected = SquadCasualtyPlanner.select(
			snapshots,
			squad.target.position(),
			config.squadCasualtyHealthThreshold
		);
		if (selected == null) {
			return;
		}
		MemberRecord casualty = this.members.get(selected.casualtyId());
		MemberRecord escort = this.members.get(selected.escortId());
		if (casualty == null || escort == null) {
			return;
		}
		squad.casualtyId = selected.casualtyId();
		squad.casualtyEscortId = selected.escortId();
		squad.casualtyResponseEndsAt = now + config.squadCasualtyResponseTicks;
		this.publishCasualtyDirectives(squad, casualty, escort, config);
		SmartZombieMetrics.casualtyResponseStarted();
	}

	private void publishCasualtyDirectives(
		final ZombieSquad squad,
		final MemberRecord casualty,
		final MemberRecord escort,
		final MobsThinkNowConfig config
	) {
		SquadCasualtyPlanner.Response response = SquadCasualtyPlanner.responseForPair(
			this.casualtySnapshot(casualty.mob, false),
			this.casualtySnapshot(escort.mob, false),
			squad.target.position()
		);
		boolean spiderCarrier = config.squadSpiderCasualtyTransport
			&& escort.mob instanceof Spider
			&& (OverworldUndeadFamilies.isZombieFamily(casualty.mob)
				|| OverworldUndeadFamilies.isSkeletonFamily(casualty.mob));
		if (!spiderCarrier) {
			this.releaseCasualtyMount(squad);
		}
		squad.casualtyDirectives.clear();
		squad.casualtyDirectives.put(casualty.mob.getId(), new SquadCasualtyDirective(
			squad.id,
			casualty.mob.getId(),
			escort.mob.getId(),
			SquadCasualtyDirective.Role.EVACUEE,
			response.casualtyDestination(),
			response.focusPosition(),
			squad.casualtyResponseEndsAt
		));
		squad.casualtyDirectives.put(escort.mob.getId(), new SquadCasualtyDirective(
			squad.id,
			casualty.mob.getId(),
			escort.mob.getId(),
			spiderCarrier ? SquadCasualtyDirective.Role.CARRIER : SquadCasualtyDirective.Role.ESCORT,
			spiderCarrier ? response.casualtyDestination() : response.escortDestination(),
			response.focusPosition(),
			squad.casualtyResponseEndsAt
		));
	}

	private SquadCasualtyPlanner.MemberSnapshot casualtySnapshot(
		final Mob mob,
		final boolean spiderTransportEnabled
	) {
		boolean unavailable = mob.isPassenger() || mob.isVehicle() || mob.isFallFlying();
		boolean casualtyEligible = !unavailable && !isCreeperMember(mob) && !isGiantMember(mob);
		boolean escortEligible = !unavailable
			&& (OverworldUndeadFamilies.isZombieFamily(mob) || isSpiderMember(mob));
		return new SquadCasualtyPlanner.MemberSnapshot(
			mob.getId(),
			mob.position(),
			healthFraction(mob),
			intelligenceOf(mob),
			casualtyEligible,
			escortEligible,
			mob instanceof Zombie zombie && ZombieArmory.hasShield(zombie),
			spiderTransportEnabled && mob instanceof Spider spider && SpiderIntelligence.get(spider) >= 6
		);
	}

	private void clearCasualtyResponse(
		final ZombieSquad squad,
		final long now,
		final boolean finished
	) {
		boolean hadResponse = squad.casualtyId != 0 || !squad.casualtyDirectives.isEmpty();
		this.releaseCasualtyMount(squad);
		squad.casualtyId = 0;
		squad.casualtyEscortId = 0;
		squad.casualtyResponseEndsAt = Long.MIN_VALUE;
		squad.casualtyDirectives.clear();
		if (hadResponse) {
			squad.nextCasualtyResponseAt = now + 80L;
			if (finished) {
				SmartZombieMetrics.casualtyResponseFinished();
			}
		}
	}

	/** 结束、换图或功能热关闭时精确拆除本轮救护骑乘，避免伤员永久留在蛛背。 */
	private void releaseCasualtyMount(final ZombieSquad squad) {
		MemberRecord casualty = this.members.get(squad.casualtyId);
		MemberRecord escort = this.members.get(squad.casualtyEscortId);
		if (casualty == null
			|| escort == null
			|| !(escort.mob instanceof Spider spider)
			|| casualty.mob.getVehicle() != spider) {
			return;
		}
		Vec3 safeDismount = SpiderTransportRouteEvaluator.findSafeDismount(spider, casualty.mob);
		casualty.mob.stopRiding();
		if (safeDismount != null) {
			casualty.mob.setPos(safeDismount.x, safeDismount.y, safeDismount.z);
		}
		((SpiderSquadTransportAccess)spider).mobsthinknow$clearSquadPassenger();
		SmartSpiderMetrics.casualtyDropoff();
	}

	private static double healthFraction(final LivingEntity entity) {
		return entity.getMaxHealth() <= 0.0F ? 0.0 : entity.getHealth() / entity.getMaxHealth();
	}

	private void rebuildRoles(final ZombieSquad squad) {
		List<Integer> ordered = this.orderedMemberIds(squad);
		MemberRecord leader = this.members.get(squad.leaderId);
		int intelligence = leader == null ? 1 : intelligenceOf(leader.mob);
		squad.roles.clear();
		squad.roles.putAll(SquadRolePlanner.planLoadouts(
			ordered,
			squad.leaderId,
			intelligence,
			this.memberLoadouts(ordered)
		));
		// 远程成员不会被编成贴脸施压/包夹位；首领仍保留 LEADER 身份，但目的地按远程成员计算。
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			if (memberId != squad.leaderId && member != null && isRangedMember(member.mob)) {
				squad.roles.put(memberId, SquadRole.RANGED);
			} else if (memberId != squad.leaderId && member != null && isCreeperMember(member.mob)) {
				squad.roles.put(memberId, SquadRole.BREACHER);
			} else if (memberId != squad.leaderId && member != null && isGiantMember(member.mob)) {
				squad.roles.put(memberId, SquadRole.PRESSURER);
			}
		}
		this.rebuildTransportAssignments(squad, ordered);
		SquadAssaultPlan previousPlan = squad.assaultPlan;
		squad.composition = this.compositionOf(ordered);
		squad.baseAssaultPlan = SquadAssaultPlanner.choose(squad.composition, intelligence);
		squad.assaultPlan = SquadAdaptiveAssaultPlanner.adapt(
			squad.baseAssaultPlan,
			squad.observedTargetTactic,
			squad.composition,
			intelligence
		);
		this.rebuildShieldWallAssignments(squad, ordered);
		if (squad.assaultPlan != previousPlan || squad.planEpoch == 0) {
			SmartZombieMetrics.assaultPlanChosen(squad.assaultPlan);
		}
	}

	/**
	 * 只在重编职位或总攻方案切换时构造一次稳定盾墙顺序；每名成员读取命令时均为 O(1)。
	 * 辅助兵和载具不被强行拉回盾墙，避免水桶救火、蜘蛛运输与正面阵位互相争抢。
	 * 其余盾卫统一改为 PRESSURER，让三名以上盾卫也能真正组成完整多排阵线。
	 */
	private void rebuildShieldWallAssignments(final ZombieSquad squad, final List<Integer> ordered) {
		squad.shieldWallMemberIds.clear();
		squad.shieldWallRanks.clear();
		squad.shieldWallStrikerId = 0;
		if (!SquadShieldWallPlanner.supports(squad.assaultPlan)) {
			return;
		}
		Set<Integer> reservedPassengers = new LinkedHashSet<>(squad.transportPartners.values());
		for (List<Integer> payloadIds : squad.giantHandPayloads.values()) {
			reservedPassengers.addAll(payloadIds);
		}
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			SquadRole role = squad.roles.getOrDefault(memberId, SquadRole.PRESSURER);
			if (member == null
				|| !(member.mob instanceof Zombie zombie)
				|| !ZombieArmory.hasShield(zombie)
				|| role == SquadRole.SUPPORT
				|| role == SquadRole.CARRIER
				|| reservedPassengers.contains(memberId)) {
				continue;
			}
			if (memberId != squad.leaderId) {
				squad.roles.put(memberId, SquadRole.PRESSURER);
			}
			int rank = squad.shieldWallMemberIds.size();
			squad.shieldWallMemberIds.add(memberId);
			squad.shieldWallRanks.put(memberId, rank);
		}
	}

	private SquadShieldOrder shieldOrderFor(
		final ZombieSquad squad,
		final int memberId,
		final SquadCombatCadence.Window combatWindow,
		final long now,
		final MobsThinkNowConfig config
	) {
		Integer rank = squad.shieldWallRanks.get(memberId);
		int memberCount = squad.shieldWallMemberIds.size();
		if (!config.squadShieldWallRotation
			|| rank == null
			|| memberCount < 2
			|| (squad.state != SquadState.DEPLOYING && squad.state != SquadState.ENGAGING)) {
			return SquadShieldOrder.NONE;
		}
		int strikerRank = squad.state == SquadState.ENGAGING
			? SquadShieldWallPlanner.strikerRank(
				combatWindow.beat(),
				combatWindow.cycle(),
				now - combatWindow.startedAt(),
				memberCount
			)
			: -1;
		return SquadShieldWallPlanner.orderFor(rank, strikerRank, memberCount);
	}

	/** 只在轮换人选真正变化时发出一次轻量盾击声，避免每名成员各自播放形成噪声风暴。 */
	private void refreshShieldWallRotation(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final SquadCombatCadence.Window combatWindow,
		final long now
	) {
		int memberCount = squad.shieldWallMemberIds.size();
		int strikerRank = config.squadShieldWallRotation
			? SquadShieldWallPlanner.strikerRank(
				combatWindow.beat(),
				combatWindow.cycle(),
				now - combatWindow.startedAt(),
				memberCount
			)
			: -1;
		int nextStrikerId = strikerRank < 0 ? 0 : squad.shieldWallMemberIds.get(strikerRank);
		if (nextStrikerId == squad.shieldWallStrikerId) {
			return;
		}
		int previousStrikerId = squad.shieldWallStrikerId;
		squad.shieldWallStrikerId = nextStrikerId;
		if (nextStrikerId == 0) {
			return;
		}
		SmartZombieMetrics.shieldWallRotation();
		MemberRecord striker = this.members.get(nextStrikerId);
		if (striker != null && striker.mob.level() instanceof ServerLevel level) {
			level.playSound(
				null,
				striker.mob.getX(),
				striker.mob.getY(),
				striker.mob.getZ(),
				SoundEvents.SHIELD_BLOCK.value(),
				SoundSource.HOSTILE,
				previousStrikerId == 0 ? 0.48F : 0.36F,
				0.72F + striker.mob.getRandom().nextFloat() * 0.10F
			);
		}
	}

	/** 提取阵容时只遍历本队 K 名成员；仅在组建、换届或成员变化时执行。 */
	private SquadComposition compositionOf(final List<Integer> ordered) {
		int meleeMembers = 0;
		int rangedMembers = 0;
		int creepers = 0;
		int spiders = 0;
		int shieldFrontliners = 0;
		int supportMembers = 0;
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			if (OverworldUndeadFamilies.isZombieFamily(member.mob) && member.mob instanceof Zombie zombie) {
				meleeMembers++;
				if (ZombieArmory.hasShield(zombie)) {
					shieldFrontliners++;
				}
				if (ZombieSpecialEquipment.utilityClassOf(zombie) != UtilityClass.NONE) {
					supportMembers++;
				}
			} else if (isRangedMember(member.mob)) {
				rangedMembers++;
			} else if (isCreeperMember(member.mob)) {
				creepers++;
			} else if (isSpiderMember(member.mob)) {
				spiders++;
			}
		}
		return new SquadComposition(
			meleeMembers,
			rangedMembers,
			creepers,
			spiders,
			shieldFrontliners,
			supportMembers
		);
	}

	/**
	 * 运输资源按固定层级一次性分配，保证同一成员不会被两种载具争抢：先给每个巨人一名头顶射手，
	 * 再以轮转方式给每个巨人至多两枚苦力怕/僵尸载荷，最后才把剩余成员交给蜘蛛运输。
	 */
	private void rebuildTransportAssignments(final ZombieSquad squad, final List<Integer> ordered) {
		squad.transportPartners.clear();
		squad.giantHeadRiders.clear();
		squad.giantHandPayloads.clear();
		List<MemberRecord> giants = new ArrayList<>();
		List<MemberRecord> spiders = new ArrayList<>();
		List<MemberRecord> available = new ArrayList<>();
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			if (isGiantMember(member.mob)) {
				giants.add(member);
			} else if (isSpiderMember(member.mob)) {
				spiders.add(member);
			} else {
				available.add(member);
			}
		}

		List<MemberRecord> ranged = available.stream()
			.filter(member -> isRangedMember(member.mob))
			.sorted(Comparator.comparingInt((MemberRecord member) -> intelligenceOf(member.mob)).reversed()
				.thenComparingInt(member -> member.mob.getId()))
			.toList();
		Set<Integer> reserved = new LinkedHashSet<>();
		for (int index = 0; index < Math.min(giants.size(), ranged.size()); index++) {
			MemberRecord giant = giants.get(index);
			MemberRecord rider = ranged.get(index);
			squad.giantHeadRiders.put(giant.mob.getId(), rider.mob.getId());
			reserved.add(rider.mob.getId());
			if (giant.mob.getId() != squad.leaderId) {
				squad.roles.put(giant.mob.getId(), SquadRole.CARRIER);
			}
		}

		List<MemberRecord> payloads = available.stream()
			.filter(member -> !reserved.contains(member.mob.getId()))
			.filter(member -> isCreeperMember(member.mob) || OverworldUndeadFamilies.isZombieFamily(member.mob))
			.sorted(Comparator.comparingInt((MemberRecord member) -> giantPayloadPriority(member.mob))
				.thenComparing(Comparator.comparingInt((MemberRecord member) -> intelligenceOf(member.mob)).reversed())
				.thenComparingInt(member -> member.mob.getId()))
			.toList();
		int payloadIndex = 0;
		for (int hand = 0; hand < 2 && payloadIndex < payloads.size(); hand++) {
			for (MemberRecord giant : giants) {
				if (payloadIndex >= payloads.size()) {
					break;
				}
				MemberRecord payload = payloads.get(payloadIndex++);
				squad.giantHandPayloads.computeIfAbsent(giant.mob.getId(), ignored -> new ArrayList<>(2))
					.add(payload.mob.getId());
				reserved.add(payload.mob.getId());
				if (giant.mob.getId() != squad.leaderId) {
					squad.roles.put(giant.mob.getId(), SquadRole.CARRIER);
				}
			}
		}

		List<MemberRecord> spiderPassengers = available.stream()
			.filter(member -> !reserved.contains(member.mob.getId()))
			.sorted(
			Comparator.comparingInt((MemberRecord member) -> transportPriority(member.mob))
				.thenComparing(Comparator.comparingInt((MemberRecord member) -> intelligenceOf(member.mob)).reversed())
				.thenComparingInt(member -> member.mob.getId())
			)
			.toList();
		int pairCount = Math.min(spiders.size(), spiderPassengers.size());
		for (int index = 0; index < pairCount; index++) {
			MemberRecord carrier = spiders.get(index);
			MemberRecord passenger = spiderPassengers.get(index);
			squad.transportPartners.put(carrier.mob.getId(), passenger.mob.getId());
			if (carrier.mob.getId() != squad.leaderId) {
				squad.roles.put(carrier.mob.getId(), SquadRole.CARRIER);
			}
		}
	}

	/** 武装或特殊装备开启时读取完整负载；两者都关时空 Map 保持旧版分配。 */
	private Map<Integer, SquadLoadout> memberLoadouts(final List<Integer> memberIds) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.armedSquads && !config.specialEquipment) {
			return Map.of();
		}

		Map<Integer, SquadLoadout> loadouts = new HashMap<>();
		for (int memberId : memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null && member.mob instanceof Zombie zombie) {
				loadouts.put(memberId, new SquadLoadout(
					config.armedSquads
						? ZombieArmory.weaponClassOf(zombie.getMainHandItem())
						: WeaponClass.NONE,
					config.armedSquads && ZombieArmory.hasShield(zombie),
					config.specialEquipment
						? ZombieSpecialEquipment.utilityClassOf(zombie)
						: UtilityClass.NONE
				));
			}
		}
		return loadouts;
	}

	private List<Integer> orderedMemberIds(final ZombieSquad squad) {
		List<MemberRecord> ordered = new ArrayList<>();
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null) {
				ordered.add(member);
			}
		}
		ordered.sort(
			Comparator.comparingInt((MemberRecord member) -> intelligenceOf(member.mob)).reversed()
				.thenComparing(Comparator.comparingDouble((MemberRecord member) -> member.mob.getHealth()).reversed())
				.thenComparingInt(member -> member.mob.getId())
		);
		return ordered.stream().map(member -> member.mob.getId()).toList();
	}

	private void assignRallyOrders(final ZombieSquad squad, final Vec3 center, final MobsThinkNowConfig config) {
		squad.planEpoch++;
		squad.orders.clear();
		squad.briefingReports.clear();
		squad.briefingDestinations.clear();
		List<Integer> ordered = this.orderedMemberIds(squad);
		List<Integer> followers = ordered.stream().filter(memberId -> memberId != squad.leaderId).toList();
		List<SquadRole> followerRoles = followers.stream()
			.map(memberId -> squad.roles.getOrDefault(memberId, SquadRole.PRESSURER))
			.toList();
		double widestFollower = followers.stream()
			.map(this.members::get)
			.filter(java.util.Objects::nonNull)
			.mapToDouble(member -> member.mob.getBbWidth())
			.max()
			.orElse(0.6);
		double effectiveRadius = Math.max(config.rallyRadius, Math.min(5.5, widestFollower * 1.15 + 0.9));
		Vec3 targetPosition = squad.sharedLastSeenPosition == null ? squad.target.position() : squad.sharedLastSeenPosition;
		List<Vec3> meetingPositions = SquadMeetingFormation.arrange(
			center,
			targetPosition.subtract(center),
			followerRoles,
			effectiveRadius
		);

		SquadRole leaderRole = squad.roles.getOrDefault(squad.leaderId, SquadRole.LEADER);
		squad.orders.put(squad.leaderId, new SquadOrder(leaderRole, center));
		for (int index = 0; index < followers.size(); index++) {
			int memberId = followers.get(index);
			squad.orders.put(memberId, new SquadOrder(followerRoles.get(index), meetingPositions.get(index)));
		}
	}

	/**
	 * 会议开始前只为左右翼各做一次有界真实寻路评估。原路线失败时依次尝试缩短侧翼、截断位和
	 * 正面施压位；结果会同时驱动点头/摇头表现与下一版部署命令，而不是只演一段固定动画。
	 */
	private void prepareBriefingRoutes(final ZombieSquad squad, final MobsThinkNowConfig config) {
		squad.briefingReports.clear();
		squad.briefingDestinations.clear();
		Vec3 targetPosition = squad.sharedLastSeenPosition;
		if (targetPosition == null) {
			return;
		}
		if (squad.observedTargetTactic == ObservedTargetTactic.KITING
			&& squad.sharedTargetVelocity != null) {
			// 只使用队员最后一次有视线时写入的速度；墙后的实时移动不会被读取。
			targetPosition = targetPosition.add(clampHorizontal(squad.sharedTargetVelocity, 0.35).scale(3.0));
		}
		Vec3 squadCentroid = this.memberCentroid(squad);
		Vec3 fallback = targetPosition.subtract(squadCentroid);
		Vec3 forward = horizontalUnit(squad.sharedTargetFacing, fallback);
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		boolean changed = false;

		for (SquadRole requestedRole : List.of(SquadRole.FLANK_LEFT, SquadRole.FLANK_RIGHT)) {
			int memberId = this.firstMemberWithRole(squad, requestedRole);
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			Vec3 requested = this.combatDestination(
				squad,
				member,
				requestedRole,
				targetPosition,
				forward,
				lateral,
				config,
				false,
				0,
				0
			);
			List<SquadBriefingRoutePlanner.Candidate> alternatives = this.briefingFallbacks(
				squad,
				member,
				requestedRole,
				requested,
				targetPosition,
				forward,
				lateral,
				config
			);
			SquadBriefingRoutePlanner.Result report = SquadBriefingRoutePlanner.resolve(
				requestedRole,
				requested,
				alternatives,
				destination -> this.canReachBriefingDestination(
					squad,
					member.mob,
					destination,
					member.mob.level().getGameTime()
				)
			);
			SmartZombieMetrics.briefingRouteChecks(report.pathChecks());
			squad.briefingReports.put(memberId, report);
			if (report.resolvedDestination() != null) {
				squad.briefingDestinations.put(memberId, report.resolvedDestination());
			}
			if (!report.outcome().isObjection()) {
				continue;
			}

			SmartZombieMetrics.briefingRouteObjection();
			changed = true;
			squad.roles.put(memberId, report.assignedRole());
			SquadOrder meetingOrder = squad.orders.get(memberId);
			if (meetingOrder != null) {
				squad.orders.put(memberId, new SquadOrder(report.assignedRole(), meetingOrder.destination));
			}
		}
		if (changed) {
			squad.planEpoch++;
			SmartZombieMetrics.briefingReplan();
		}
	}

	private List<SquadBriefingRoutePlanner.Candidate> briefingFallbacks(
		final ZombieSquad squad,
		final MemberRecord member,
		final SquadRole requestedRole,
		final @Nullable Vec3 requested,
		final Vec3 targetPosition,
		final Vec3 forward,
		final Vec3 lateral,
		final MobsThinkNowConfig config
	) {
		List<SquadBriefingRoutePlanner.Candidate> candidates = new ArrayList<>(3);
		Vec3 pressure = this.combatDestination(
			squad,
			member,
			SquadRole.PRESSURER,
			targetPosition,
			forward,
			lateral,
			config,
			false,
			0,
			0
		);
		if (requested != null && pressure != null) {
			candidates.add(new SquadBriefingRoutePlanner.Candidate(
				requestedRole,
				requested.scale(0.55).add(pressure.scale(0.45))
			));
		}
		Vec3 cutoff = this.combatDestination(
			squad,
			member,
			SquadRole.CUTOFF,
			targetPosition,
			forward,
			lateral,
			config,
			false,
			0,
			0
		);
		if (cutoff != null) {
			candidates.add(new SquadBriefingRoutePlanner.Candidate(SquadRole.CUTOFF, cutoff));
		}
		if (pressure != null) {
			candidates.add(new SquadBriefingRoutePlanner.Candidate(SquadRole.PRESSURER, pressure));
		}
		return List.copyOf(candidates);
	}

	private boolean canReachBriefingDestination(
		final ZombieSquad squad,
		final Mob mob,
		final Vec3 destination,
		final long now
	) {
		if (ConfigManager.get().squadSharedDangerMemory
			&& squad.dangerMemory.isDangerousNear(BlockPos.containing(destination), 1, 1, now)) {
			SmartZombieMetrics.sharedDangerAvoided();
			return false;
		}
		if (mob.position().distanceToSqr(destination) <= ORDER_REACHED_DISTANCE_SQUARED) {
			return true;
		}
		Path path = mob.getNavigation().createPath(BlockPos.containing(destination), 0);
		if (path == null || !path.canReach()) {
			return false;
		}
		if (!ConfigManager.get().squadSharedDangerMemory || path.getNodeCount() == 0) {
			return true;
		}

		int samples = Math.min(MAXIMUM_DANGER_PATH_SAMPLES, path.getNodeCount());
		for (int sample = 0; sample < samples; sample++) {
			int index = samples == 1
				? 0
				: (int)Math.round(sample * (path.getNodeCount() - 1.0) / (samples - 1.0));
			Vec3 node = path.getEntityPosAtNode(mob, index);
			if (squad.dangerMemory.isDangerousNear(BlockPos.containing(node), 1, 1, now)) {
				SmartZombieMetrics.sharedDangerAvoided();
				return false;
			}
		}
		return true;
	}

	private int firstMemberWithRole(final ZombieSquad squad, final SquadRole role) {
		for (int memberId : this.orderedMemberIds(squad)) {
			if (squad.roles.get(memberId) == role) {
				return memberId;
			}
		}
		return 0;
	}

	private void enterDeploying(final ZombieSquad squad, final MobsThinkNowConfig config, final long now) {
		squad.commitArmedAt = Long.MIN_VALUE;
		squad.firstCommitAt = Long.MAX_VALUE;
		squad.deploymentReadyFraction = 0.0;
		squad.lastCombatBeat = SquadCombatBeat.PREPARE;
		this.refreshCombatOrders(squad, config, false, true, true, now);
		this.enterState(
			squad,
			SquadState.DEPLOYING,
			now,
			now + config.deploymentTimeoutTicks,
			config,
			"orders assigned"
		);
	}

	private void enterEngaging(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now,
		final String reason
	) {
		// 紧急接敌会抢占尚未执行的口令；正常部署则保留全队已经共享的执行 tick。
		if (squad.firstCommitAt == Long.MAX_VALUE || squad.firstCommitAt > now) {
			squad.commitArmedAt = now;
			squad.firstCommitAt = now;
			squad.combatEpoch++;
		}
		this.refreshCombatOrders(squad, config, true, false, true, now);
		squad.lastCombatBeat = SquadCombatBeat.COMMIT;
		squad.nextPlanRefreshAt = now + config.decisionIntervalTicks;
		this.enterState(squad, SquadState.ENGAGING, now, Long.MAX_VALUE, config, reason);
	}

	private void transitionCombatBeat(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final SquadCombatBeat nextBeat,
		final long now
	) {
		SquadCombatBeat previousBeat = squad.lastCombatBeat;
		squad.lastCombatBeat = nextBeat;
		if (previousBeat.holdsFormation() == nextBeat.holdsFormation()) {
			return;
		}

		boolean attacking = !nextBeat.holdsFormation();
		this.refreshThreatAssignments(squad, config, now);
		this.updateObservedTargetTactics(squad, config, now);
		this.refreshCombatOrders(squad, config, attacking, false, true, now);
		squad.nextPlanRefreshAt = now + config.decisionIntervalTicks;
		this.debug(config, squad, "combat beat " + previousBeat + " -> " + nextBeat);
	}

	/**
	 * 刷新当前战斗节拍的阵位。简报寻路结果只服务于首次部署；进入交战循环后，每次重整都必须围绕
	 * 最新目标快照重新锚定，否则目标移动后成员会折返最初的开会位置。
	 *
	 * @param attacking 当前节拍是否释放成员追击和近战
	 * @param reuseBriefingDestinations 是否复用首次简报中已经验证可达的阵位
	 */
	private void refreshCombatOrders(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final boolean attacking,
		final boolean reuseBriefingDestinations,
		final boolean bumpPlanEpoch,
		final long now
	) {
		if (bumpPlanEpoch) {
			squad.planEpoch++;
		}
		Vec3 targetPosition = squad.sharedLastSeenPosition;
		if (targetPosition == null) {
			return;
		}
		if (!attacking && !reuseBriefingDestinations) {
			MemberRecord leader = this.members.get(squad.leaderId);
			int leaderIntelligence = leader == null ? 1 : intelligenceOf(leader.mob);
			targetPosition = SquadFormationAnchor.predict(
				targetPosition,
				squad.sharedTargetVelocity,
				leaderIntelligence,
				now - squad.sharedLastSeenAt
			);
		}

		Vec3 squadCentroid = this.memberCentroid(squad);
		Vec3 fallback = targetPosition.subtract(squadCentroid);
		Vec3 forward = horizontalUnit(squad.sharedTargetFacing, fallback);
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		List<Integer> ordered = this.orderedMemberIds(squad);
		squad.orders.clear();
		int pressureIndex = 0;
		int rangedIndex = 0;
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			SquadRole role = squad.roles.getOrDefault(memberId, member == null ? SquadRole.PRESSURER : defaultRole(member.mob));
			LivingEntity assignedTarget = attacking ? this.assignedTargetEntity(squad, memberId) : squad.target;
			Vec3 orderTargetPosition = assignedTarget == null || assignedTarget == squad.target
				? targetPosition
				: assignedTarget.position();
			Vec3 orderForward = assignedTarget == null || assignedTarget == squad.target
				? forward
				: horizontalUnit(assignedTarget.getLookAngle(), orderTargetPosition.subtract(squadCentroid));
			Vec3 orderLateral = new Vec3(-orderForward.z, 0.0, orderForward.x);
			// 部署阶段让蜘蛛乘员原地等载具靠近，避免乘员和载具同时追逐造成“永远差一格”的会合。
			Vec3 destination = reuseBriefingDestinations
				&& member != null
				&& this.assignedSpiderCarrierId(squad, memberId) != null
				? member.mob.position()
				: reuseBriefingDestinations && squad.briefingDestinations.containsKey(memberId)
					? squad.briefingDestinations.get(memberId)
					: this.combatDestination(
					squad,
					member,
					role,
					orderTargetPosition,
					orderForward,
					orderLateral,
					config,
					attacking,
					pressureIndex,
					rangedIndex
					);
			if (isRangedMember(member == null ? null : member.mob)) {
				rangedIndex++;
			}
			if (!isRangedMember(member == null ? null : member.mob)
				&& (role == SquadRole.LEADER || role == SquadRole.PRESSURER)) {
				pressureIndex++;
			}
			squad.orders.put(memberId, new SquadOrder(role, destination));
		}
	}

	private @Nullable Vec3 combatDestination(
		final ZombieSquad squad,
		final @Nullable MemberRecord member,
		final SquadRole role,
		final Vec3 targetPosition,
		final Vec3 forward,
		final Vec3 lateral,
		final MobsThinkNowConfig config,
		final boolean engaging,
		final int pressureIndex,
		final int rangedIndex
	) {
		if (member != null && isRangedMember(member.mob)) {
			double range = SkeletonCombatMath.intelligenceAdjustedPreferredRange(
				config.skeletonPreferredRange,
				intelligenceOf(member.mob)
			);
			if (squad.assaultPlan.usesCrossfire()) {
				return SquadAssaultGeometry.crossfirePosition(
					targetPosition,
					forward,
					member.mob.position().subtract(targetPosition),
					range,
					rangedIndex
				);
			}
			if (squad.assaultPlan == SquadAssaultPlan.PIN_AND_FLANK
				|| squad.assaultPlan == SquadAssaultPlan.SHIELD_WEDGE
				|| squad.assaultPlan == SquadAssaultPlan.MOUNTED_BREACH) {
				double side = (rangedIndex & 1) == 0 ? 1.75 : -1.75;
				return targetPosition.add(forward.scale(range)).add(lateral.scale(side));
			}
			Vec3 outward = horizontalUnit(member.mob.position().subtract(targetPosition), forward.scale(-1.0));
			Vec3 rangedLateral = new Vec3(-outward.z, 0.0, outward.x);
			double side = (member.mob.getId() & 1) == 0 ? 1.5 : -1.5;
			return targetPosition.add(outward.scale(range)).add(rangedLateral.scale(side));
		}
		return switch (role) {
			case LEADER, PRESSURER -> {
				if (engaging) {
					yield null; // 交战阶段让原版 MeleeAttackGoal 接手最后几格的追击与挥击。
				}
				Integer shieldRank = member == null ? null : squad.shieldWallRanks.get(member.mob.getId());
				SquadShieldWallPlanner.Slot shieldSlot = shieldRank == null
					? null
					: SquadShieldWallPlanner.slotFor(shieldRank, squad.shieldWallMemberIds.size());
				double side = shieldSlot == null
					? pressureIndex == 0 ? 0.0 : (pressureIndex % 2 == 0 ? 0.8 : -0.8)
					: shieldSlot.lateralOffset();
				double depth = shieldSlot == null
					? config.formationRadius + 0.35
					: config.formationRadius - 0.75 + shieldSlot.depthOffset();
				yield targetPosition.add(forward.scale(depth)).add(lateral.scale(side));
			}
			case FLANK_LEFT -> targetPosition
				.subtract(forward.scale(config.flankBehindDistance))
				.add(lateral.scale(config.flankSideDistance));
			case FLANK_RIGHT -> targetPosition
				.subtract(forward.scale(config.flankBehindDistance))
				.subtract(lateral.scale(config.flankSideDistance));
			case CUTOFF -> targetPosition.subtract(forward.scale(config.formationRadius + 1.5));
			case SUPPORT -> targetPosition
				.add(forward.scale(config.formationRadius + 2.5))
				.add(lateral.scale((pressureIndex & 1) == 0 ? 2.0 : -2.0));
			case RANGED -> targetPosition.add(forward.scale(config.skeletonPreferredRange));
			case BREACHER -> {
				if (engaging) {
					yield null;
				}
				if (squad.assaultPlan.usesMountedBreach() && member != null) {
					double side = (member.mob.getId() & 1) == 0 ? 2.75 : -2.75;
					yield targetPosition.subtract(forward.scale(config.formationRadius + 1.5)).add(lateral.scale(side));
				}
				yield targetPosition.add(forward.scale(config.formationRadius + 0.75));
			}
			case CARRIER -> {
				if (member == null) {
					yield targetPosition.add(forward.scale(config.formationRadius + 1.5));
				}
				ZombieSquad memberSquad = member.squadId == 0L ? null : this.squads.get(member.squadId);
				Integer passengerId = this.firstTransportPartner(memberSquad, member.mob.getId());
				MemberRecord passenger = passengerId == null ? null : this.members.get(passengerId);
				yield passenger == null
					? targetPosition.add(forward.scale(config.formationRadius + 1.5))
					: passenger.mob.position();
			}
		};
	}

	private @Nullable ZombieSquad squadFor(final Mob mob) {
		MemberRecord member = this.memberFor(mob);
		return member == null || member.squadId == 0L ? null : this.squads.get(member.squadId);
	}

	/** Entity ids are fast local keys but may eventually be reused; external lookups confirm object identity. */
	private @Nullable MemberRecord memberFor(final Mob mob) {
		MemberRecord member = this.members.get(mob.getId());
		return member != null && member.mob == mob ? member : null;
	}

	private @Nullable LivingEntity assignedTargetEntity(final ZombieSquad squad, final int memberId) {
		int targetId = squad.targetAssignments.getOrDefault(memberId, squad.target.getId());
		LivingEntity target = targetId == squad.target.getId() ? squad.target : squad.threatEntities.get(targetId);
		return target != null && target.isAlive() ? target : squad.target;
	}

	private @Nullable Integer assignedSpiderCarrierId(final ZombieSquad squad, final int passengerId) {
		for (Map.Entry<Integer, Integer> assignment : squad.transportPartners.entrySet()) {
			if (assignment.getValue() == passengerId) {
				return assignment.getKey();
			}
		}
		return null;
	}

	/** 返回尚未登上对应载具的第一名预约成员，避免 CARRIER 阵位永久追着自己身上的乘员。 */
	private @Nullable Integer firstTransportPartner(
		final @Nullable ZombieSquad squad,
		final int carrierId
	) {
		if (squad == null) {
			return null;
		}
		List<Integer> giantPayloads = squad.giantHandPayloads.get(carrierId);
		if (giantPayloads != null) {
			for (int payloadId : giantPayloads) {
				MemberRecord payload = this.members.get(payloadId);
				if (payload != null && (payload.mob.getVehicle() == null || payload.mob.getVehicle().getId() != carrierId)) {
					return payloadId;
				}
			}
		}
		Integer headRider = squad.giantHeadRiders.get(carrierId);
		MemberRecord rider = headRider == null ? null : this.members.get(headRider);
		if (rider != null && (rider.mob.getVehicle() == null || rider.mob.getVehicle().getId() != carrierId)) {
			return headRider;
		}
		Integer spiderPassenger = squad.transportPartners.get(carrierId);
		MemberRecord passenger = spiderPassenger == null ? null : this.members.get(spiderPassenger);
		return passenger != null && (passenger.mob.getVehicle() == null || passenger.mob.getVehicle().getId() != carrierId)
			? spiderPassenger
			: null;
	}

	private boolean hasReachedQuorum(final ZombieSquad squad, final double requiredFraction) {
		int arrived = 0;
		int total = 0;
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			SquadOrder order = squad.orders.get(memberId);
			if (member == null || order == null || order.destination == null) {
				continue;
			}
			total++;
			if (member.mob.position().distanceToSqr(order.destination) <= ORDER_REACHED_DISTANCE_SQUARED) {
				arrived++;
			}
		}
		return total > 0 && arrived >= Math.ceil(total * requiredFraction);
	}

	private SquadReadinessBarrier.Result deploymentReadiness(
		final ZombieSquad squad,
		final double requiredFraction
	) {
		List<SquadReadinessBarrier.MemberStatus> statuses = new ArrayList<>(squad.memberIds.size());
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			SquadOrder order = squad.orders.get(memberId);
			boolean assigned = member != null && order != null && order.destination != null;
			boolean arrived = assigned
				&& member.mob.position().distanceToSqr(order.destination) <= ORDER_REACHED_DISTANCE_SQUARED;
			boolean roleReady = member != null && assigned && this.isRoleReadyForCommit(member);
			statuses.add(new SquadReadinessBarrier.MemberStatus(assigned, arrived, roleReady));
		}
		return SquadReadinessBarrier.evaluate(statuses, requiredFraction);
	}

	private boolean isRoleReadyForCommit(final MemberRecord member) {
		// 射手至少要重新获得共享目标的直视线；近战和载具在抵达阵位后即可响应总攻。
		return !isRangedMember(member.mob) || member.hasLineOfSight;
	}

	private void armCommit(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now,
		final boolean forced
	) {
		MemberRecord leader = this.members.get(squad.leaderId);
		int leaderIntelligence = leader == null ? 1 : intelligenceOf(leader.mob);
		int delay = forced
			? SquadCombatCadence.forcedCommitDelay()
			: SquadCombatCadence.initialCommitDelay(leaderIntelligence, squad.id);
		squad.commitArmedAt = now;
		squad.firstCommitAt = now + delay;
		squad.combatEpoch++;
		this.debug(
			config,
			squad,
			(forced ? "forced" : "ready")
				+ " commit armed at " + squad.firstCommitAt
				+ " readiness=" + String.format(java.util.Locale.ROOT, "%.2f", squad.deploymentReadyFraction)
		);
	}

	private boolean shouldEmergencyEngage(final ZombieSquad squad, final MobsThinkNowConfig config) {
		double emergencyDistanceSquared = config.emergencyEngageDistance * config.emergencyEngageDistance;
		boolean deploymentIsHoldingForCommit = squad.state == SquadState.DEPLOYING;
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			// 只有被小队目标本人刚刚打了才算紧急军情；日晒着火、摔落这类无关伤害不应打断会议。
			// hurtTime 对任何伤害源都会置位，而 lastHurtByMob 会保留 100 tick，两者组合会误报；
			// 用"最后一次被生物攻击的时间戳距今 ≤10 tick"才能精确对应目标刚出手这一事件。
			boolean hurtByTarget = member.mob.getLastHurtByMob() == squad.target
				&& member.mob.tickCount - member.mob.getLastHurtByMobTimestamp() <= 10;
			// 成员主动抵达近身部署位不算遭遇战，否则旧的距离旁路会在同一 tick 吞掉就绪屏障。
			// 部署阶段仍允许目标真实出手抢占；其他阶段则保留原有的近距离紧急接敌能力。
			if (hurtByTarget
				|| (!deploymentIsHoldingForCommit
					&& member.hasLineOfSight
					&& member.mob.distanceToSqr(squad.target) <= emergencyDistanceSquared)) {
				return true;
			}
		}
		return false;
	}

	private Vec3 calculateRallyPoint(final ZombieSquad squad, final MobsThinkNowConfig config) {
		Vec3 centroid = this.memberCentroid(squad);
		Vec3 targetPosition = squad.sharedLastSeenPosition == null ? squad.target.position() : squad.sharedLastSeenPosition;
		Vec3 awayFromTarget = horizontalUnit(centroid.subtract(targetPosition), new Vec3(0.0, 0.0, 1.0));
		// 集结点稍微背离战斗中心，让“先聚拢开会、再散开”能被玩家看见。
		return centroid.add(awayFromTarget.scale(Math.min(4.0, config.coordinationRadius * 0.3)));
	}

	private Vec3 memberCentroid(final ZombieSquad squad) {
		double x = 0.0;
		double y = 0.0;
		double z = 0.0;
		int count = 0;
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null) {
				Vec3 position = member.mob.position();
				x += position.x;
				y += position.y;
				z += position.z;
				count++;
			}
		}
		return count == 0 ? Vec3.ZERO : new Vec3(x / count, y / count, z / count);
	}

	private void mergeObservation(final ZombieSquad squad, final MemberRecord member) {
		if (member.lastSeenPosition != null && member.lastSeenAt >= squad.sharedLastSeenAt) {
			squad.sharedLastSeenPosition = member.lastSeenPosition;
			squad.sharedLastSeenAt = member.lastSeenAt;
			if (member.lastSeenFacing != null) {
				squad.sharedTargetFacing = member.lastSeenFacing;
			}
			if (member.lastSeenVelocity != null) {
				squad.sharedTargetVelocity = member.lastSeenVelocity;
			}
		}
	}

	private void updatePrimedCreeperIndex(final ZombieSquad squad, final MemberRecord member) {
		if (member.mob instanceof Creeper creeper && isPrimedCreeper(creeper)) {
			squad.primedCreeperIds.add(member.mob.getId());
		} else {
			squad.primedCreeperIds.remove(member.mob.getId());
		}
	}

	private static boolean isPrimedCreeper(final Creeper creeper) {
		return creeper.isAlive() && (creeper.isIgnited() || creeper.getSwellDir() > 0);
	}

	private void enterState(
		final ZombieSquad squad,
		final SquadState state,
		final long now,
		final long deadline,
		final MobsThinkNowConfig config,
		final String reason
	) {
		if (state != SquadState.ENGAGING && !squad.casualtyDirectives.isEmpty()) {
			this.clearCasualtyResponse(squad, now, false);
		}
		if (state != SquadState.ENGAGING && squad.webAmbushStartedAt != Long.MIN_VALUE) {
			this.clearWebAmbush(squad, now);
		}
		squad.state = state;
		squad.stateStartedAt = now;
		squad.stateDeadline = deadline;
		this.debug(config, squad, reason);
	}

	private void detachFromSquad(final MemberRecord member) {
		if (member.squadId == 0L) {
			return;
		}
		ZombieSquad squad = this.squads.get(member.squadId);
		if (squad != null) {
			squad.memberIds.remove(member.mob.getId());
			squad.primedCreeperIds.remove(member.mob.getId());
			squad.targetAssignments.remove(member.mob.getId());
			squad.firingLanes.release(member.mob.getId());
			squad.blastReservations.release(member.mob.getId());
			squad.roles.remove(member.mob.getId());
			squad.orders.remove(member.mob.getId());
			squad.casualtyDirectives.remove(member.mob.getId());
			if (member.mob.getId() == squad.casualtyId || member.mob.getId() == squad.casualtyEscortId) {
				this.clearCasualtyResponse(squad, member.mob.level().getGameTime(), false);
			}
			squad.briefingReports.remove(member.mob.getId());
			squad.briefingDestinations.remove(member.mob.getId());
			boolean removedCarrier = squad.transportPartners.remove(member.mob.getId()) != null;
			boolean removedPassenger = squad.transportPartners.values()
				.removeIf(passengerId -> passengerId == member.mob.getId());
			boolean removedGiantRider = squad.giantHeadRiders.remove(member.mob.getId()) != null
				|| squad.giantHeadRiders.values().removeIf(riderId -> riderId == member.mob.getId());
			boolean removedGiantPayload = squad.giantHandPayloads.remove(member.mob.getId()) != null;
			for (List<Integer> payloadIds : squad.giantHandPayloads.values()) {
				removedGiantPayload |= payloadIds.removeIf(payloadId -> payloadId == member.mob.getId());
			}
			boolean transportChanged = removedCarrier || removedPassenger || removedGiantRider || removedGiantPayload;
			if (transportChanged && !squad.memberIds.isEmpty()) {
				this.rebuildRoles(squad);
			}
		}
		member.squadId = 0L;
		this.theatrics.restoreName(member.mob);
		removeSquadSpeedBonus(member.mob);
	}

	private void disband(final ZombieSquad squad, final MobsThinkNowConfig config, final String reason) {
		this.releaseCasualtyMount(squad);
		this.releaseMembers(squad);
		this.squads.remove(squad.id);
		SmartZombieMetrics.squadDisbanded();
		this.debug(config, squad, reason);
	}

	private void releaseMembers(final ZombieSquad squad) {
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member != null && member.squadId == squad.id) {
				member.squadId = 0L;
				this.theatrics.restoreName(member.mob);
				removeSquadSpeedBonus(member.mob);
			}
		}
	}

	private void reset() {
		for (ZombieSquad squad : this.squads.values()) {
			this.releaseCasualtyMount(squad);
		}
		for (MemberRecord member : this.members.values()) {
			member.squadId = 0L;
			this.theatrics.restoreName(member.mob);
			removeSquadSpeedBonus(member.mob);
		}
		this.members.clear();
		this.squads.clear();
	}

	/**
	 * 组队期间给全员挂临时移速加成（transient，永不写入存档），离队即移除。
	 * 集结或持阵时，明显落后当前阵位的成员会获得离散、封顶的追赶档位；正式进攻时只保留基础加成。
	 * 仅在数值真正变化时重建修饰符，因此 {@code /mtn reload} 修改数值会立即作用于存量小队。
	 */
	private void applySquadMobility(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final long now
	) {
		boolean cohesionOrderActive = squad.state != SquadState.ENGAGING
			|| this.combatWindowFor(squad, now).beat().holdsFormation();
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			AttributeInstance speed = member.mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed == null) {
				continue;
			}
			// 载具 Goal 已使用独立的 1.10～配置上限速度曲线；再叠通用 +10% 会突破刚收紧的运输峰值。
			if ((isSpiderMember(member.mob) && squad.transportPartners.containsKey(memberId))
				|| (isGiantMember(member.mob)
					&& (squad.giantHeadRiders.containsKey(memberId) || squad.giantHandPayloads.containsKey(memberId)))) {
				speed.removeModifier(SQUAD_SPEED_MODIFIER_ID);
				continue;
			}
			SquadOrder order = squad.orders.get(memberId);
			boolean memberHasCohesionOrder = cohesionOrderActive
				&& order != null
				&& order.destination != null;
			double distanceToDestinationSquared = memberHasCohesionOrder
				? member.mob.position().distanceToSqr(order.destination)
				: 0.0;
			double requestedBonus = SquadCohesionPacing.speedBonus(
				config.squadSpeedBonus,
				memberHasCohesionOrder,
				distanceToDestinationSquared
			);
			if (requestedBonus <= 0.0) {
				speed.removeModifier(SQUAD_SPEED_MODIFIER_ID);
				continue;
			}
			AttributeModifier existing = speed.getModifier(SQUAD_SPEED_MODIFIER_ID);
			if (existing == null || Double.compare(existing.amount(), requestedBonus) != 0) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(
					SQUAD_SPEED_MODIFIER_ID,
					requestedBonus,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
				));
			}
		}
	}

	private static void removeSquadSpeedBonus(final Mob mob) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SQUAD_SPEED_MODIFIER_ID);
		}
	}

	/** 每 tick 的表现层输出：职业名牌、首领光环、会议叫声与部署粒子。 */
	private void presentSquad(final ServerLevel level, final ZombieSquad squad, final MobsThinkNowConfig config, final long now) {
		// 即使两个表现开关都被关掉也要继续调用：tickSquad 的名牌分支负责把已应用的名牌还原。
		List<SquadTheatrics.RoleMember> roleMembers = new ArrayList<>(squad.memberIds.size());
		Mob leader = null;
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			if (memberId == squad.leaderId) {
				leader = member.mob;
			}
			SquadRole presentedRole = squad.roles.getOrDefault(memberId, defaultRole(member.mob));
			if (presentedRole == SquadRole.SUPPORT
				&& (!(member.mob instanceof Zombie zombie)
					|| ZombieSpecialEquipment.utilityClassOf(zombie) == UtilityClass.NONE)) {
				presentedRole = SquadRole.PRESSURER;
			}
			SquadBriefingRoutePlanner.Result report = squad.briefingReports.get(memberId);
			SquadRole briefingRole = report == null ? presentedRole : report.requestedRole();
			SquadRouteOutcome outcome = report == null ? SquadRouteOutcome.UNASSESSED : report.outcome();
			int intelligence = intelligenceOf(member.mob);
			roleMembers.add(new SquadTheatrics.RoleMember(
				member.mob,
				presentedRole,
				briefingRole,
				outcome,
				intelligence,
				socialIdleStyle(member.mob, intelligence)
			));
		}
		Mob resolvedLeader = leader;
		long phase = Math.max(0L, now - squad.stateStartedAt);
		List<SquadTheatrics.RoleMember> followers = roleMembers.stream()
			.filter(member -> member.mob() != resolvedLeader)
			.toList();
		SquadSocialChoreography.Scene scene = SquadSocialChoreography.sceneAt(
			squad.state,
			squad.id,
			phase,
			SquadTheatrics.participantsOf(followers),
			new SquadSocialChoreography.Timing(config.briefingTicks, config.regroupTicks)
		);
		squad.socialAttention = scene.attention();
		this.theatrics.tickSquad(level, squad.id, squad.state, resolvedLeader, roleMembers, config, now, phase, scene);
	}

	private static SquadSocialChoreography.IdleStyle socialIdleStyle(final Mob mob, final int intelligence) {
		if (!(mob instanceof Zombie zombie)) {
			return SquadSocialChoreography.IdleStyle.NONE;
		}
		if (ZombieEngineerProfile.isEngineer(zombie)) {
			return SquadSocialChoreography.IdleStyle.ENGINEER;
		}
		if (ZombieArmory.hasShield(zombie)) {
			return SquadSocialChoreography.IdleStyle.SHIELD;
		}
		return switch (ZombieArmory.weaponClassOf(zombie.getMainHandItem())) {
			case SWORD -> SquadSocialChoreography.IdleStyle.SWORD;
			case AXE -> SquadSocialChoreography.IdleStyle.AXE;
			default -> intelligence <= 4
				? SquadSocialChoreography.IdleStyle.CONFUSED
				: SquadSocialChoreography.IdleStyle.NONE;
		};
	}

	private void debug(final MobsThinkNowConfig config, final ZombieSquad squad, final String reason) {
		if (config.debugLogging) {
			MobsThinkNow.LOGGER.info(
				"Mixed hostile squad {} state={} leader={} term={} members={} ({})",
				squad.id,
				squad.state,
				squad.leaderId,
				squad.term,
				squad.memberIds.size(),
				reason
			);
		}
	}

	private static Vec3 horizontalUnit(final @Nullable Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = preferred == null ? Vec3.ZERO : new Vec3(preferred.x, 0.0, preferred.z);
		if (horizontal.horizontalDistanceSqr() < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}
		if (horizontal.horizontalDistanceSqr() < MINIMUM_HORIZONTAL_LENGTH_SQUARED) {
			return new Vec3(0.0, 0.0, 1.0);
		}
		return horizontal.normalize();
	}

	private static Vec3 clampHorizontal(final Vec3 vector, final double maximumLength) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		double maximumSquared = maximumLength * maximumLength;
		return horizontal.lengthSqr() <= maximumSquared
			? horizontal
			: horizontal.normalize().scale(maximumLength);
	}

	private static boolean isMemoryFresh(
		final @Nullable Vec3 position,
		final long observedAt,
		final long now,
		final int memoryTicks
	) {
		if (position == null) {
			return false;
		}
		long elapsed = now - observedAt;
		return elapsed >= 0L && elapsed <= memoryTicks;
	}

	private static boolean squadsEnabled(final MobsThinkNowConfig config) {
		return config.enabled
			&& config.packSurrounding
			&& (config.zombieAiEnabled
				|| config.skeletonAiEnabled
				|| config.creeperAiEnabled
				|| config.spiderAiEnabled
				|| config.giantZombieAiEnabled);
	}

	private static boolean isSupportedMember(final Mob mob) {
		MobsThinkNowConfig config = ConfigManager.get();
		return (OverworldUndeadFamilies.isZombieFamily(mob) && config.zombieAiEnabled)
			|| (OverworldUndeadFamilies.isSkeletonFamily(mob) && config.skeletonAiEnabled)
			|| (mob.getType() == EntityType.CREEPER && config.creeperAiEnabled)
			|| (mob.getType() == EntityType.SPIDER && config.spiderAiEnabled)
			|| (mob.getType() == EntityType.GIANT && config.giantZombieAiEnabled);
	}

	private static boolean isRangedMember(final @Nullable Mob mob) {
		return mob instanceof AbstractSkeleton && OverworldUndeadFamilies.isSkeletonFamily(mob);
	}

	private static boolean isCreeperMember(final @Nullable Mob mob) {
		return mob instanceof Creeper && mob.getType() == EntityType.CREEPER;
	}

	private static boolean isSpiderMember(final @Nullable Mob mob) {
		return mob instanceof Spider && mob.getType() == EntityType.SPIDER;
	}

	private static boolean isGiantMember(final @Nullable Mob mob) {
		return mob instanceof Giant && mob.getType() == EntityType.GIANT;
	}

	private static SquadRole defaultRole(final Mob mob) {
		if (isRangedMember(mob)) {
			return SquadRole.RANGED;
		}
		if (isGiantMember(mob)) {
			return SquadRole.CARRIER;
		}
		return isCreeperMember(mob) ? SquadRole.BREACHER : SquadRole.PRESSURER;
	}

	private static int intelligenceOf(final Mob mob) {
		if (mob instanceof Zombie zombie) {
			return ZombieIntelligence.get(zombie);
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			return SkeletonIntelligence.get(skeleton);
		}
		if (mob instanceof Creeper creeper) {
			return CreeperIntelligence.get(creeper);
		}
		if (mob instanceof Spider spider) {
			return SpiderIntelligence.get(spider);
		}
		if (mob instanceof Giant giant) {
			return GiantIntelligence.get(giant);
		}
		return 1;
	}

	private static int transportPriority(final Mob mob) {
		if (isCreeperMember(mob)) {
			return 0;
		}
		return isRangedMember(mob) ? 1 : 2;
	}

	private static int giantPayloadPriority(final Mob mob) {
		return isCreeperMember(mob) ? 0 : 1;
	}

	private static long randomElectionTicket(final Mob mob) {
		return mob.getUUID().getMostSignificantBits()
			^ Long.rotateLeft(mob.getUUID().getLeastSignificantBits(), 23);
	}

	public record SquadBlastThreat(Creeper creeper, Vec3 center, boolean powered) {
	}

	public record SquadView(
		long squadId,
		SquadState state,
		SquadAssaultPlan assaultPlan,
		ObservedTargetTactic observedTargetTactic,
		int leaderEntityId,
		int term,
		int planEpoch,
		int combatEpoch,
		long combatCycle,
		SquadCombatBeat combatBeat,
		long combatExecuteAt,
		boolean webAmbushActive,
		boolean shieldWallActive,
		double deploymentReadyFraction,
		int memberCount
	) {
	}

	private static final class MemberRecord {
		private final Mob mob;
		private @Nullable LivingEntity target;
		private long lastHeartbeatAt = Long.MIN_VALUE;
		private boolean hasLineOfSight;
		private @Nullable Vec3 lastSeenPosition;
		private @Nullable Vec3 lastSeenFacing;
		private @Nullable Vec3 lastSeenVelocity;
		private long lastSeenAt = Long.MIN_VALUE;
		private long squadId;
		private final SquadRouteFailureTracker routeFailures = new SquadRouteFailureTracker();

		private MemberRecord(final Mob mob) {
			this.mob = mob;
		}
	}

	private static final class ZombieSquad {
		private final long id;
		private final LivingEntity target;
		private final Set<Integer> memberIds = new LinkedHashSet<>();
		private final Set<Integer> primedCreeperIds = new LinkedHashSet<>();
		private final Map<Integer, SquadRole> roles = new HashMap<>();
		private final Map<Integer, SquadOrder> orders = new HashMap<>();
		private final Map<Integer, SquadBriefingRoutePlanner.Result> briefingReports = new HashMap<>();
		private final Map<Integer, Vec3> briefingDestinations = new HashMap<>();
		private final Map<Integer, Integer> targetAssignments = new HashMap<>();
		private final Map<Integer, LivingEntity> threatEntities = new HashMap<>();
		private final Map<Integer, Integer> transportPartners = new HashMap<>();
		private final Map<Integer, Integer> giantHeadRiders = new HashMap<>();
		private final Map<Integer, List<Integer>> giantHandPayloads = new HashMap<>();
		private final Map<Integer, SquadCasualtyDirective> casualtyDirectives = new HashMap<>();
		private final List<Integer> shieldWallMemberIds = new ArrayList<>();
		private final Map<Integer, Integer> shieldWallRanks = new HashMap<>();
		private int shieldWallStrikerId;
		private int casualtyId;
		private int casualtyEscortId;
		private long casualtyResponseEndsAt = Long.MIN_VALUE;
		private long nextCasualtyResponseAt = Long.MIN_VALUE;
		private long webAmbushStartedAt = Long.MIN_VALUE;
		private long webAmbushLastConfirmedAt = Long.MIN_VALUE;
		private long nextWebAmbushAt = Long.MIN_VALUE;
		private int webAmbushOwnerId;
		private boolean webAmbushCommitAnnounced;
		private int leaderId;
		private int term;
		private int planEpoch;
		private int combatEpoch;
		private long commitArmedAt = Long.MIN_VALUE;
		private long firstCommitAt = Long.MAX_VALUE;
		private double deploymentReadyFraction;
		private SquadCombatBeat lastCombatBeat = SquadCombatBeat.PREPARE;
		private SquadState state = SquadState.FORMING;
		private SquadAssaultPlan baseAssaultPlan = SquadAssaultPlan.SWARM;
		private SquadAssaultPlan assaultPlan = SquadAssaultPlan.SWARM;
		private SquadComposition composition = new SquadComposition(0, 0, 0, 0, 0, 0);
		private final SquadTacticMemory tacticMemory = new SquadTacticMemory();
		private final SquadThreatMemory threatMemory = new SquadThreatMemory();
		private final SquadDangerMemory dangerMemory = new SquadDangerMemory();
		private final SquadBlastReservationBook blastReservations = new SquadBlastReservationBook();
		private final SquadFiringLaneRegistry firingLanes = new SquadFiringLaneRegistry();
		private final SquadPounceCadence spiderPounceCadence = new SquadPounceCadence();
		private ObservedTargetTactic observedTargetTactic = ObservedTargetTactic.NONE;
		private long stateStartedAt;
		private long stateDeadline;
		private long nextPlanRefreshAt;
		private SquadSocialChoreography.Attention socialAttention = SquadSocialChoreography.Attention.FOLLOW_LEADER;
		private Vec3 rallyPoint = Vec3.ZERO;
		private @Nullable Vec3 sharedLastSeenPosition;
		private @Nullable Vec3 sharedTargetFacing;
		private @Nullable Vec3 sharedTargetVelocity;
		private long sharedLastSeenAt = Long.MIN_VALUE;

		private ZombieSquad(final long id, final LivingEntity target) {
			this.id = id;
			this.target = target;
		}
	}

	private record SquadOrder(SquadRole role, @Nullable Vec3 destination) {
	}

}

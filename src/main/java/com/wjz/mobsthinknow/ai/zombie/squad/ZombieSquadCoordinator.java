package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieFireSupportMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidThreatMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 每个 {@link ServerLevel} 唯一的僵尸—骷髅混编小队协调器。
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
		if (existing == null && !squadsEnabled(config)) {
			return;
		}

		forLevel(level).tick(level, config);
	}

	/** 服务器停止时主动释放实体引用，避免同一 JVM 内切换存档后保留旧世界。 */
	public static void clearAll() {
		COORDINATORS.clear();
	}

	public static void unloadLevel(final ServerLevel level) {
		COORDINATORS.remove(level);
	}

	/** 命令诊断使用；调用方同样位于服务器主线程。 */
	public static int activeSquadCount() {
		return COORDINATORS.values().stream().mapToInt(coordinator -> coordinator.squads.size()).sum();
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

	/** 供仇恨 Goal 判断“攻击者是不是同队队友”；两个受支持 Mob 都在同一支小队才算。 */
	public static boolean areSquadmates(final Mob first, final Mob second) {
		if (first.level() != second.level() || !(first.level() instanceof ServerLevel serverLevel)) {
			return false;
		}
		ZombieSquadCoordinator coordinator = COORDINATORS.get(serverLevel);
		if (coordinator == null) {
			return false;
		}
		MemberRecord a = coordinator.members.get(first.getId());
		MemberRecord b = coordinator.members.get(second.getId());
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
		MemberRecord member = coordinator.members.get(victim.getId());
		ZombieSquad squad = member == null ? null : coordinator.squads.get(member.squadId);
		if (squad == null) {
			return;
		}
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
		MemberRecord member = coordinator.members.get(victim.getId());
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
		MemberRecord member = this.members.get(mob.getId());
		if (member == null) {
			// 首次注册时剥掉上次异常退出可能残留在存档里的职业名牌。
			SquadTheatrics.stripLeftoverRoleTag(mob);
			member = new MemberRecord(mob);
			this.members.put(mob.getId(), member);
		}
		if (member.target != target) {
			this.detachFromSquad(member);
			member.target = target;
			member.lastSeenPosition = null;
			member.lastSeenFacing = null;
			member.lastSeenAt = Long.MIN_VALUE;
		}

		member.lastHeartbeatAt = now;
		member.hasLineOfSight = hasLineOfSight;
		if (lastSeenPosition != null && lastSeenAt >= member.lastSeenAt) {
			member.lastSeenPosition = lastSeenPosition;
			member.lastSeenAt = lastSeenAt;
			if (hasLineOfSight) {
				member.lastSeenFacing = target.getLookAngle();
			}
		}

		ZombieSquad squad = this.squads.get(member.squadId);
		if (squad != null) {
			this.mergeObservation(squad, member);
		}
	}

	/** 返回当前命令快照；未组队时返回 {@code null}，由单体战术继续接管。 */
	public @Nullable SquadDirective directiveFor(final Mob mob) {
		MemberRecord member = this.members.get(mob.getId());
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
		Vec3 focusPosition = leader == null ? squad.rallyPoint : leader.mob.position().add(0.0, 1.0, 0.0);
		long now = mob.level().getGameTime();
		boolean sharedMemoryIsFresh = isMemoryFresh(
			squad.sharedLastSeenPosition,
			squad.sharedLastSeenAt,
			now,
			ConfigManager.get().targetMemoryTicks
		);

		return new SquadDirective(
			squad.id,
			squad.term,
			squad.planEpoch,
			squad.state,
			role,
			destination,
			focusPosition,
			sharedMemoryIsFresh
		);
	}

	/** 测试和诊断使用的只读小队摘要。 */
	public @Nullable SquadView viewFor(final Mob mob) {
		MemberRecord member = this.members.get(mob.getId());
		ZombieSquad squad = member == null ? null : this.squads.get(member.squadId);
		if (squad == null) {
			return null;
		}
		return new SquadView(squad.id, squad.state, squad.leaderId, squad.term, squad.planEpoch, squad.memberIds.size());
	}

	/** 目标失效时立即注销；正常 Goal 切换则交给心跳超时，避免频繁退队又入队。 */
	public void unregister(final Mob mob) {
		MemberRecord member = this.members.remove(mob.getId());
		if (member != null) {
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
				this.applySquadMobility(squad, config);
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
				|| member.target == null
				|| !member.target.isAlive()
				|| member.mob.getTarget() != member.target
				|| now - member.lastHeartbeatAt > config.memberHeartbeatTimeoutTicks;
			if (invalid) {
				this.detachFromSquad(member);
				iterator.remove();
			}
		}
	}

	private void formNewSquads(final MobsThinkNowConfig config, final long now) {
		BoundedSpatialIndex<MemberRecord> spatialIndex = new BoundedSpatialIndex<>(
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

		seeds.sort(Comparator.comparingInt(member -> member.mob.getId()));
		for (MemberRecord seed : seeds) {
			if (seed.squadId != 0L || seed.target == null) {
				continue;
			}

			List<MemberRecord> nearby = this.collectBoundedNearby(seed, spatialIndex, config);
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

	private List<MemberRecord> collectBoundedNearby(
		final MemberRecord seed,
		final BoundedSpatialIndex<MemberRecord> spatialIndex,
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
				member.squadId = squad.id;
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
					this.enterState(squad, SquadState.BRIEFING, now, now + config.briefingTicks, config, "rally complete");
				}
			}
			case BRIEFING -> {
				if (now >= squad.stateDeadline) {
					this.enterDeploying(squad, config, now);
				}
			}
			case DEPLOYING -> {
				if (this.hasReachedQuorum(squad, config.deploymentQuorum) || now >= squad.stateDeadline) {
					this.enterEngaging(squad, config, now, "deployment complete");
				}
			}
			case ENGAGING -> {
				if (now >= squad.nextPlanRefreshAt) {
					this.refreshCombatOrders(squad, config, true, false, now);
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
					new SquadLeaderCandidate(memberId, intelligenceOf(member.mob), member.mob.getHealth())
				);
			}
		}
		return candidates;
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
		List<Integer> ordered = this.orderedMemberIds(squad);
		int outerIndex = 0;
		int outerCount = Math.max(1, ordered.size() - 1);
		for (int memberId : ordered) {
			SquadRole role = squad.roles.getOrDefault(memberId, SquadRole.PRESSURER);
			Vec3 destination;
			if (memberId == squad.leaderId) {
				destination = center;
			} else {
				double angle = Math.PI * 2.0 * outerIndex / outerCount;
				destination = center.add(Math.cos(angle) * config.rallyRadius, 0.0, Math.sin(angle) * config.rallyRadius);
				outerIndex++;
			}
			squad.orders.put(memberId, new SquadOrder(role, destination));
		}
	}

	private void enterDeploying(final ZombieSquad squad, final MobsThinkNowConfig config, final long now) {
		this.refreshCombatOrders(squad, config, false, true, now);
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
		this.refreshCombatOrders(squad, config, true, true, now);
		squad.nextPlanRefreshAt = now + config.decisionIntervalTicks;
		this.enterState(squad, SquadState.ENGAGING, now, Long.MAX_VALUE, config, reason);
	}

	private void refreshCombatOrders(
		final ZombieSquad squad,
		final MobsThinkNowConfig config,
		final boolean engaging,
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

		Vec3 fallback = targetPosition.subtract(this.memberCentroid(squad));
		Vec3 forward = horizontalUnit(squad.sharedTargetFacing, fallback);
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		List<Integer> ordered = this.orderedMemberIds(squad);
		squad.orders.clear();
		int pressureIndex = 0;
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			SquadRole role = squad.roles.getOrDefault(memberId, member == null ? SquadRole.PRESSURER : defaultRole(member.mob));
			Vec3 destination = this.combatDestination(
				member,
				role,
				targetPosition,
				forward,
				lateral,
				config,
				engaging,
				pressureIndex
			);
			if (!isRangedMember(member == null ? null : member.mob)
				&& (role == SquadRole.LEADER || role == SquadRole.PRESSURER)) {
				pressureIndex++;
			}
			squad.orders.put(memberId, new SquadOrder(role, destination));
		}
	}

	private @Nullable Vec3 combatDestination(
		final @Nullable MemberRecord member,
		final SquadRole role,
		final Vec3 targetPosition,
		final Vec3 forward,
		final Vec3 lateral,
		final MobsThinkNowConfig config,
		final boolean engaging,
		final int pressureIndex
	) {
		if (member != null && isRangedMember(member.mob)) {
			Vec3 outward = horizontalUnit(member.mob.position().subtract(targetPosition), forward.scale(-1.0));
			Vec3 rangedLateral = new Vec3(-outward.z, 0.0, outward.x);
			double range = SkeletonCombatMath.intelligenceAdjustedPreferredRange(
				config.skeletonPreferredRange,
				intelligenceOf(member.mob)
			);
			double side = (member.mob.getId() & 1) == 0 ? 1.5 : -1.5;
			return targetPosition.add(outward.scale(range)).add(rangedLateral.scale(side));
		}
		return switch (role) {
			case LEADER, PRESSURER -> {
				if (engaging) {
					yield null; // 交战阶段让原版 MeleeAttackGoal 接手最后几格的追击与挥击。
				}
				double side = pressureIndex == 0 ? 0.0 : (pressureIndex % 2 == 0 ? 0.8 : -0.8);
				yield targetPosition.add(forward.scale(config.formationRadius)).add(lateral.scale(side));
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
		};
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

	private boolean shouldEmergencyEngage(final ZombieSquad squad, final MobsThinkNowConfig config) {
		double emergencyDistanceSquared = config.emergencyEngageDistance * config.emergencyEngageDistance;
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
			if (hurtByTarget
				|| (member.hasLineOfSight && member.mob.distanceToSqr(squad.target) <= emergencyDistanceSquared)) {
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
		}
	}

	private void enterState(
		final ZombieSquad squad,
		final SquadState state,
		final long now,
		final long deadline,
		final MobsThinkNowConfig config,
		final String reason
	) {
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
			squad.roles.remove(member.mob.getId());
			squad.orders.remove(member.mob.getId());
		}
		member.squadId = 0L;
		this.theatrics.restoreName(member.mob);
		removeSquadSpeedBonus(member.mob);
	}

	private void disband(final ZombieSquad squad, final MobsThinkNowConfig config, final String reason) {
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
	 * 只在数值真正变化时重建修饰符，因此 {@code /mtn reload} 修改数值会立即作用于存量小队。
	 */
	private void applySquadMobility(final ZombieSquad squad, final MobsThinkNowConfig config) {
		for (int memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			AttributeInstance speed = member.mob.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed == null) {
				continue;
			}
			if (config.squadSpeedBonus <= 0.0) {
				speed.removeModifier(SQUAD_SPEED_MODIFIER_ID);
				continue;
			}
			AttributeModifier existing = speed.getModifier(SQUAD_SPEED_MODIFIER_ID);
			if (existing == null || existing.amount() != config.squadSpeedBonus) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(
					SQUAD_SPEED_MODIFIER_ID,
					config.squadSpeedBonus,
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
			roleMembers.add(new SquadTheatrics.RoleMember(member.mob, presentedRole));
		}
		this.theatrics.tickSquad(level, squad.id, squad.state, squad.stateStartedAt, leader, roleMembers, config, now);
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
			&& (config.zombieAiEnabled || config.skeletonAiEnabled);
	}

	private static boolean isSupportedMember(final Mob mob) {
		return mob.getType() == EntityType.ZOMBIE || mob.getType() == EntityType.SKELETON;
	}

	private static boolean isRangedMember(final @Nullable Mob mob) {
		return mob instanceof AbstractSkeleton && mob.getType() == EntityType.SKELETON;
	}

	private static SquadRole defaultRole(final Mob mob) {
		return isRangedMember(mob) ? SquadRole.RANGED : SquadRole.PRESSURER;
	}

	private static int intelligenceOf(final Mob mob) {
		if (mob instanceof Zombie zombie) {
			return ZombieIntelligence.get(zombie);
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			return SkeletonIntelligence.get(skeleton);
		}
		return 1;
	}

	public record SquadView(
		long squadId,
		SquadState state,
		int leaderEntityId,
		int term,
		int planEpoch,
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
		private long lastSeenAt = Long.MIN_VALUE;
		private long squadId;

		private MemberRecord(final Mob mob) {
			this.mob = mob;
		}
	}

	private static final class ZombieSquad {
		private final long id;
		private final LivingEntity target;
		private final Set<Integer> memberIds = new LinkedHashSet<>();
		private final Map<Integer, SquadRole> roles = new HashMap<>();
		private final Map<Integer, SquadOrder> orders = new HashMap<>();
		private int leaderId;
		private int term;
		private int planEpoch;
		private SquadState state = SquadState.FORMING;
		private long stateStartedAt;
		private long stateDeadline;
		private long nextPlanRefreshAt;
		private Vec3 rallyPoint = Vec3.ZERO;
		private @Nullable Vec3 sharedLastSeenPosition;
		private @Nullable Vec3 sharedTargetFacing;
		private long sharedLastSeenAt = Long.MIN_VALUE;

		private ZombieSquad(final long id, final LivingEntity target) {
			this.id = id;
			this.target = target;
		}
	}

	private record SquadOrder(SquadRole role, @Nullable Vec3 destination) {
	}

}

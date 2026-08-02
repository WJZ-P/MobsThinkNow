package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.MobsThinkNow;
import com.wjz.mobsthinknow.ai.creeper.CreeperBlastEvacuationMath;
import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligence;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonCombatMath;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.utility.OverworldUndeadFamilies;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.ZombieArmory;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieFireSupportMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidThreatMemory;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
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

	/** 供仇恨 Goal 判断“攻击者是不是同队队友”；两个实体都登记在同一支五类混编小队才算。 */
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
			this.updatePrimedCreeperIndex(squad, member);
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
		Vec3 focusPosition = this.socialFocusPosition(squad, member, leader);
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
			squad.assaultPlan,
			role,
			destination,
			focusPosition,
			sharedMemoryIsFresh
		);
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
		MemberRecord member = this.members.get(mob.getId());
		ZombieSquad squad = member == null ? null : this.squads.get(member.squadId);
		if (squad == null) {
			return null;
		}
		return new SquadView(
			squad.id,
			squad.state,
			squad.assaultPlan,
			squad.leaderId,
			squad.term,
			squad.planEpoch,
			squad.memberIds.size()
		);
	}

	/**
	 * 返回当前成员最危险的已引信同队苦力怕。
	 *
	 * <p>每支小队只维护正在引信的实体 ID，查询复杂度是 O(P)，P 为同时活动的爆点数量，而不是
	 * O(K) 全员扫描。蜘蛛不会把自己背上的苦力怕误判成应当逃离的外部爆点。</p>
	 */
	public @Nullable Creeper nearestPrimedCreeperThreatFor(final Mob mob) {
		MemberRecord member = this.members.get(mob.getId());
		ZombieSquad squad = member == null ? null : this.squads.get(member.squadId);
		if (squad == null || squad.primedCreeperIds.isEmpty()) {
			return null;
		}

		Creeper selected = null;
		double bestRelativeDanger = Double.POSITIVE_INFINITY;
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
			double distanceSquared = mob.distanceToSqr(creeper);
			if (!CreeperBlastEvacuationMath.isInsideDanger(distanceSquared, creeper.isPowered())) {
				continue;
			}
			double radius = CreeperBlastEvacuationMath.dangerRadius(creeper.isPowered());
			double relativeDanger = distanceSquared / (radius * radius);
			if (relativeDanger < bestRelativeDanger) {
				bestRelativeDanger = relativeDanger;
				selected = creeper;
			}
		}
		return selected;
	}

	/**
	 * 返回协调器给蜘蛛固定分配的乘员。形成/开会阶段也保留此关系，供其他运输 Goal
	 * 判断自己是否应该让路；真正开始接送则由 {@link #activeTransportPartnerFor(Spider)} 控制。
	 */
	public @Nullable Mob assignedTransportPartnerFor(final Spider spider) {
		MemberRecord carrier = this.members.get(spider.getId());
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
		MemberRecord carrier = this.members.get(spider.getId());
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
		MemberRecord carrier = this.members.get(spider.getId());
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
		MemberRecord carrier = this.members.get(giant.getId());
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
		MemberRecord rider = this.members.get(skeleton.getId());
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
		MemberRecord rider = this.members.get(skeleton.getId());
		ZombieSquad squad = rider == null ? null : this.squads.get(rider.squadId);
		return squad != null && squad.state == SquadState.ENGAGING
			? this.assignedGiantMountFor(skeleton)
			: null;
	}

	/** 返回巨人左右手的固定载荷预约，顺序即右手、左手。 */
	public List<Mob> assignedGiantPayloadsFor(final Giant giant) {
		MemberRecord carrier = this.members.get(giant.getId());
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
		MemberRecord carrier = this.members.get(giant.getId());
		ZombieSquad squad = carrier == null ? null : this.squads.get(carrier.squadId);
		return squad != null && squad.state == SquadState.ENGAGING
			? this.assignedGiantPayloadsFor(giant)
			: List.of();
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
		squad.assaultPlan = SquadAssaultPlanner.choose(this.compositionOf(ordered), intelligence);
		if (squad.assaultPlan != previousPlan || squad.planEpoch == 0) {
			SmartZombieMetrics.assaultPlanChosen(squad.assaultPlan);
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
		Vec3 fallback = targetPosition.subtract(this.memberCentroid(squad));
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
				destination -> this.canReachBriefingDestination(member.mob, destination)
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

	private boolean canReachBriefingDestination(final Mob mob, final Vec3 destination) {
		if (mob.position().distanceToSqr(destination) <= ORDER_REACHED_DISTANCE_SQUARED) {
			return true;
		}
		Path path = mob.getNavigation().createPath(BlockPos.containing(destination), 0);
		return path != null && path.canReach();
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
		int rangedIndex = 0;
		for (int memberId : ordered) {
			MemberRecord member = this.members.get(memberId);
			SquadRole role = squad.roles.getOrDefault(memberId, member == null ? SquadRole.PRESSURER : defaultRole(member.mob));
			// 部署阶段让蜘蛛乘员原地等载具靠近，避免乘员和载具同时追逐造成“永远差一格”的会合。
			Vec3 destination = !engaging && member != null && this.assignedSpiderCarrierId(squad, memberId) != null
				? member.mob.position()
				: !engaging && squad.briefingDestinations.containsKey(memberId)
					? squad.briefingDestinations.get(memberId)
					: this.combatDestination(
					squad,
					member,
					role,
					targetPosition,
					forward,
					lateral,
					config,
					engaging,
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
				boolean shieldWedge = (squad.assaultPlan == SquadAssaultPlan.SHIELD_WEDGE
					|| squad.assaultPlan == SquadAssaultPlan.COMBINED_ARMS)
					&& member != null
					&& member.mob instanceof Zombie zombie
					&& ZombieArmory.hasShield(zombie);
				double side = shieldWedge ? 0.0 : pressureIndex == 0 ? 0.0 : (pressureIndex % 2 == 0 ? 0.8 : -0.8);
				double depth = shieldWedge ? config.formationRadius - 0.75 : config.formationRadius + 0.35;
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
			squad.roles.remove(member.mob.getId());
			squad.orders.remove(member.mob.getId());
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
			// 载具 Goal 已使用独立的 1.10～配置上限速度曲线；再叠通用 +10% 会突破刚收紧的运输峰值。
			if ((isSpiderMember(member.mob) && squad.transportPartners.containsKey(memberId))
				|| (isGiantMember(member.mob)
					&& (squad.giantHeadRiders.containsKey(memberId) || squad.giantHandPayloads.containsKey(memberId)))) {
				speed.removeModifier(SQUAD_SPEED_MODIFIER_ID);
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

	public record SquadView(
		long squadId,
		SquadState state,
		SquadAssaultPlan assaultPlan,
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
		private final Set<Integer> primedCreeperIds = new LinkedHashSet<>();
		private final Map<Integer, SquadRole> roles = new HashMap<>();
		private final Map<Integer, SquadOrder> orders = new HashMap<>();
		private final Map<Integer, SquadBriefingRoutePlanner.Result> briefingReports = new HashMap<>();
		private final Map<Integer, Vec3> briefingDestinations = new HashMap<>();
		private final Map<Integer, Integer> transportPartners = new HashMap<>();
		private final Map<Integer, Integer> giantHeadRiders = new HashMap<>();
		private final Map<Integer, List<Integer>> giantHandPayloads = new HashMap<>();
		private int leaderId;
		private int term;
		private int planEpoch;
		private SquadState state = SquadState.FORMING;
		private SquadAssaultPlan assaultPlan = SquadAssaultPlan.SWARM;
		private long stateStartedAt;
		private long stateDeadline;
		private long nextPlanRefreshAt;
		private SquadSocialChoreography.Attention socialAttention = SquadSocialChoreography.Attention.FOLLOW_LEADER;
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

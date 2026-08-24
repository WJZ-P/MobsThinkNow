package com.wjz.mobsthinknow.paper.squad;

import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperThreats;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.spatial.BoundedSpatialIndex;
import com.wjz.mobsthinknow.shared.squad.MixedSquadGeometry;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPhasePlanner;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlan;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlanner;
import com.wjz.mobsthinknow.shared.squad.MixedSquadRole;
import com.wjz.mobsthinknow.shared.squad.MixedSquadSpecies;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import com.wjz.mobsthinknow.shared.squad.MixedSquadTransportPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Paper 主线程混编小队黑板。实体每次心跳只更新一次空间桶；组队只访问 3x3 桶并受硬扫描预算约束。
 */
public final class PaperSquadCoordinator {
	private static final double DESTINATION_QUORUM_DISTANCE_SQUARED = 2.25 * 2.25;
	private static final double REQUIRED_QUORUM_FRACTION = 0.60;

	private final Plugin plugin;
	private final BooleanSupplier globallyEnabled;
	private final Supplier<PaperSquadSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadMetrics metrics = new PaperSquadMetrics();
	private final Map<UUID, MemberRecord> members = new LinkedHashMap<>();
	private final Map<Long, Squad> squads = new LinkedHashMap<>();
	private final Map<UUID, Long> squadByMember = new HashMap<>();
	private BoundedSpatialIndex<UUID, MemberRecord> spatialIndex;
	private long nextSquadId = 1L;
	private long directiveCacheHits;
	private long directiveComputations;
	private long geometryCacheHits;
	private long geometryComputations;
	private BukkitTask task;

	public PaperSquadCoordinator(
		final Plugin plugin,
		final BooleanSupplier globallyEnabled,
		final Supplier<PaperSquadSettings> settings,
		final PaperIntelligenceService intelligence
	) {
		this.plugin = plugin;
		this.globallyEnabled = globallyEnabled;
		this.settings = settings;
		this.intelligence = intelligence;
		this.spatialIndex = this.newSpatialIndex(settings.get().formationRadius());
	}

	public void start() {
		this.stopTask();
		if (!this.enabled()) {
			return;
		}
		long period = this.settings.get().heartbeatTicks();
		this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, period);
	}

	public void reconfigure() {
		this.stopTask();
		this.resetSquads();
		if (!this.enabled()) {
			this.members.clear();
			this.spatialIndex.clear();
			return;
		}
		this.rebuildSpatialIndex();
		this.start();
	}

	public void stop() {
		this.stopTask();
		this.resetSquads();
		this.members.clear();
		this.spatialIndex.clear();
	}

	public boolean isRunning() {
		return this.task != null;
	}

	public void track(final Mob mob) {
		if (!this.enabled() || !this.intelligence.supports(mob)) {
			return;
		}
		MemberRecord record = this.members.computeIfAbsent(
			mob.getUniqueId(),
			ignored -> new MemberRecord(mob, stableOrder(mob.getUniqueId()))
		);
		if (record.mob != mob) {
			record.clearDirectiveCache();
			Long squadId = this.squadByMember.get(mob.getUniqueId());
			Squad squad = squadId == null ? null : this.squads.get(squadId);
			if (squad != null && mob.getUniqueId().equals(squad.leaderId)) {
				squad.directiveRevision++;
				squad.clearGeometryCache();
			}
		}
		record.mob = mob;
		this.spatialIndex.upsert(record);
	}

	public void untrack(final Mob mob) {
		MemberRecord removed = this.members.remove(mob.getUniqueId());
		if (removed == null) {
			return;
		}
		this.spatialIndex.remove(removed);
		this.detachMember(mob.getUniqueId());
	}

	public boolean areSquadmates(final Mob first, final Mob second) {
		Long firstSquad = this.squadByMember.get(first.getUniqueId());
		return firstSquad != null && firstSquad.equals(this.squadByMember.get(second.getUniqueId()));
	}

	public boolean enabled() {
		return this.globallyEnabled.getAsBoolean() && this.settings.get().enabled();
	}

	public boolean preventsFriendlyFire() {
		PaperSquadSettings config = this.settings.get();
		return this.globallyEnabled.getAsBoolean() && config.enabled() && config.preventFriendlyFire();
	}

	public boolean isHoldingForOrders(final Mob mob) {
		PaperSquadDirective directive = this.directiveFor(mob);
		return directive != null && directive.isHoldingForOrders();
	}

	/** 记录通过目标事件或明确测试入口得到的可观察目标；不覆盖更长久的实体存档。 */
	public void observeTarget(final Mob mob, final LivingEntity target) {
		if (!PaperThreats.isLiveFor(mob, target)) {
			return;
		}
		this.track(mob);
		MemberRecord member = this.members.get(mob.getUniqueId());
		if (member != null) {
			member.rememberedTarget = target;
			member.rememberedTargetUntil = (long)Bukkit.getCurrentTick() + this.settings.get().targetMemoryTicks();
			member.nextTargetPropagationAt = 0L;
		}
	}

	public PaperSquadDirective directiveFor(final Mob mob) {
		Long squadId = this.squadByMember.get(mob.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		if (squad == null || !squad.memberIds.contains(mob.getUniqueId())) {
			return null;
		}
		MemberRecord member = this.members.get(mob.getUniqueId());
		MemberRecord leader = this.members.get(squad.leaderId);
		if (member == null || leader == null) {
			return null;
		}
		long now = Bukkit.getCurrentTick();
		boolean sharedMemory = squad.target != null && mob.getTarget() != squad.target;
		if (member.cachedDirective != null
			&& member.directiveCachedAt == now
			&& member.cachedDirectiveRevision == squad.directiveRevision
			&& member.cachedSharedMemory == sharedMemory) {
			this.directiveCacheHits++;
			return member.cachedDirective;
		}
		SquadGeometry geometry = this.geometryFor(squad, leader, now);
		MixedSquadRole role = squad.roles.getOrDefault(mob.getUniqueId(), MixedSquadRole.FRONTLINE);
		Vec3d destination = this.destinationFor(squad, member, geometry, role);
		PaperSquadDirective directive = new PaperSquadDirective(
			squad.id,
			squad.term,
			squad.state,
			squad.plan,
			role,
			destination,
			geometry.focus(),
			squad.leaderId,
			squad.targetId,
			sharedMemory
		);
		member.cachedDirective = directive;
		member.directiveCachedAt = now;
		member.cachedDirectiveRevision = squad.directiveRevision;
		member.cachedSharedMemory = sharedMemory;
		this.directiveComputations++;
		return directive;
	}

	public LivingEntity sharedTargetFor(final Mob mob) {
		Long squadId = this.squadByMember.get(mob.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		return squad != null && PaperThreats.isLiveFor(mob, squad.target) ? squad.target : null;
	}

	public Mob leaderFor(final Mob mob) {
		Long squadId = this.squadByMember.get(mob.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		MemberRecord leader = squad == null ? null : this.members.get(squad.leaderId);
		return leader == null ? null : leader.mob;
	}

	/** 将至多 maximum-members 个当前同队成员写入调用方复用的缓冲区。 */
	public void copySquadmatesTo(final Mob mob, final List<Mob> destination) {
		Objects.requireNonNull(destination, "destination");
		destination.clear();
		Long squadId = this.squadByMember.get(mob.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		if (squad == null) {
			return;
		}
		for (int index = 0; index < squad.memberIds.size(); index++) {
			MemberRecord member = this.members.get(squad.memberIds.get(index));
			if (member != null && member.mob != mob && member.mob.isValid() && !member.mob.isDead()) {
				destination.add(member.mob);
			}
		}
	}

	/** 返回至多 maximum-members 个当前同队成员的只读副本；热路径应优先复用缓冲区重载。 */
	public List<Mob> squadmatesFor(final Mob mob) {
		List<Mob> result = new ArrayList<>();
		this.copySquadmatesTo(mob, result);
		return List.copyOf(result);
	}

	/** 返回共享配对器给这只蜘蛛分配的苦力怕；查询只访问当前小队快照，不扫描世界实体。 */
	public Creeper assignedTransportPartnerFor(final Spider spider) {
		Long squadId = this.squadByMember.get(spider.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		UUID payloadId = squad == null ? null : squad.transportPairs.get(spider.getUniqueId());
		MemberRecord payload = payloadId == null ? null : this.members.get(payloadId);
		return payload != null && payload.mob instanceof Creeper creeper ? creeper : null;
	}

	/** O(1) reverse lookup used by creeper goals so a delegated payload does not prime or run away independently. */
	public boolean isAssignedTransportPayload(final Creeper creeper) {
		Long squadId = this.squadByMember.get(creeper.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		UUID carrierId = squad == null ? null : squad.transportCarriers.get(creeper.getUniqueId());
		MemberRecord carrier = carrierId == null ? null : this.members.get(carrierId);
		return carrier != null && carrier.mob instanceof Spider && carrier.mob.isValid() && !carrier.mob.isDead();
	}

	public int activeSquadCount() {
		return this.squads.size();
	}

	public int assignedMemberCount() {
		return this.squadByMember.size();
	}

	public int trackedMemberCount() {
		return this.members.size();
	}

	public long directiveCacheHits() {
		return this.directiveCacheHits;
	}

	public long directiveComputations() {
		return this.directiveComputations;
	}

	public long geometryCacheHits() {
		return this.geometryCacheHits;
	}

	public long geometryComputations() {
		return this.geometryComputations;
	}

	public PaperSquadMetrics metrics() {
		return this.metrics;
	}

	private void tick() {
		PaperSquadSettings config = this.settings.get();
		if (!this.globallyEnabled.getAsBoolean() || !config.enabled()) {
			this.resetSquads();
			this.members.clear();
			this.spatialIndex.clear();
			return;
		}
		long now = Bukkit.getCurrentTick();
		this.refreshMembers();
		this.updateSquads(now, config);
		this.formNewSquads(now, config);
	}

	private void refreshMembers() {
		Iterator<Map.Entry<UUID, MemberRecord>> iterator = this.members.entrySet().iterator();
		List<UUID> detached = new ArrayList<>();
		while (iterator.hasNext()) {
			Map.Entry<UUID, MemberRecord> entry = iterator.next();
			MemberRecord member = entry.getValue();
			if (!member.mob.isValid() || member.mob.isDead() || !this.intelligence.supports(member.mob)) {
				this.spatialIndex.remove(member);
				detached.add(entry.getKey());
				iterator.remove();
				continue;
			}
			this.spatialIndex.upsert(member);
		}
		for (UUID memberId : detached) {
			this.detachMember(memberId);
		}
	}

	private void updateSquads(final long now, final PaperSquadSettings config) {
		Iterator<Squad> iterator = this.squads.values().iterator();
		while (iterator.hasNext()) {
			Squad squad = iterator.next();
			squad.directiveRevision++;
			boolean pruned = this.pruneSquadMembers(squad, config);
			if (squad.memberIds.size() < config.minimumMembers()) {
				this.releaseSquadMembers(squad);
				iterator.remove();
				continue;
			}

			boolean leaderChanged = !squad.memberIds.contains(squad.leaderId)
				|| !this.members.containsKey(squad.leaderId);
			if (leaderChanged && !this.electLeader(squad, true)) {
				this.releaseSquadMembers(squad);
				iterator.remove();
				continue;
			}
			this.resolveTarget(squad, now);
			if (squad.target == null && now - squad.lastTargetSeenAt > config.targetMemoryTicks()) {
				this.releaseSquadMembers(squad);
				iterator.remove();
				continue;
			}

			boolean recruited = this.recruitNearby(squad, config);
			boolean structureChanged = pruned || leaderChanged || recruited;
			if (structureChanged) {
				this.rebuildTactics(squad);
			}
			this.propagateTarget(squad, config);
			this.advanceState(squad, now, config, structureChanged);
		}
	}

	private void formNewSquads(final long now, final PaperSquadSettings config) {
		for (MemberRecord seed : this.members.values()) {
			if (this.squadByMember.containsKey(seed.mob.getUniqueId())) {
				continue;
			}
			LivingEntity target = this.targetFor(seed, now);
			if (target == null) {
				continue;
			}
			ScanResult nearby = this.collectNearby(seed, target, config, true);
			if (nearby.members().size() < config.minimumMembers()) {
				continue;
			}
			Squad squad = new Squad(this.nextSquadId++, target, now);
			for (int index = 0; index < nearby.members().size(); index++) {
				MemberRecord member = nearby.members().get(index);
				if (squad.memberIds.size() >= config.maximumMembers()) {
					break;
				}
				UUID memberId = member.mob.getUniqueId();
				if (this.squadByMember.putIfAbsent(memberId, squad.id) == null) {
					squad.memberIds.add(memberId);
				}
			}
			if (squad.memberIds.size() < config.minimumMembers() || !this.electLeader(squad, false)) {
				this.releaseSquadMembers(squad);
				continue;
			}
			this.rebuildTactics(squad);
			this.squads.put(squad.id, squad);
			this.metrics.squadFormed();
			this.propagateTarget(squad, config);
		}
	}

	private boolean recruitNearby(final Squad squad, final PaperSquadSettings config) {
		if (squad.memberIds.size() >= config.maximumMembers() || squad.target == null) {
			return false;
		}
		MemberRecord leader = this.members.get(squad.leaderId);
		if (leader == null) {
			return false;
		}
		ScanResult result = this.collectNearby(leader, squad.target, config, false);
		boolean changed = false;
		for (int index = 0; index < result.members().size(); index++) {
			MemberRecord member = result.members().get(index);
			if (squad.memberIds.size() >= config.maximumMembers()) {
				break;
			}
			UUID memberId = member.mob.getUniqueId();
			if (this.squadByMember.putIfAbsent(memberId, squad.id) == null) {
				squad.memberIds.add(memberId);
				this.metrics.memberRecruited();
				changed = true;
			}
		}
		return changed;
	}

	private ScanResult collectNearby(
		final MemberRecord seed,
		final LivingEntity sharedTarget,
		final PaperSquadSettings config,
		final boolean includeSeed
	) {
		double radiusSquared = config.formationRadius() * config.formationRadius();
		long now = Bukkit.getCurrentTick();
		BoundedSpatialIndex.ScanResult<MemberRecord> result = this.spatialIndex.collectNearby(
			seed,
			candidate -> {
				UUID candidateId = candidate.mob.getUniqueId();
				if (candidate.mob == sharedTarget || this.squadByMember.containsKey(candidateId)) {
					return false;
				}
				LivingEntity ownTarget = this.targetFor(candidate, now);
				return ownTarget == null || ownTarget == sharedTarget;
			},
			(first, second) -> distanceSquared(first.mob, second.mob),
			radiusSquared,
			config.maximumMembers(),
			config.rawScanLimit(),
			includeSeed
		);
		this.metrics.candidateChecks(result.rawChecks());
		return new ScanResult(result.candidates(), result.rawChecks());
	}

	private boolean pruneSquadMembers(final Squad squad, final PaperSquadSettings config) {
		MemberRecord leader = this.members.get(squad.leaderId);
		double maximumSquared = config.maximumSeparation() * config.maximumSeparation();
		boolean changed = false;
		int index = 0;
		while (index < squad.memberIds.size()) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			boolean wrongWorld = leader != null
				&& member != null
				&& member.mob.getWorld() != leader.mob.getWorld();
			boolean tooFar = leader != null
				&& member != null
				&& !wrongWorld
				&& distanceSquared(member.mob, leader.mob) > maximumSquared;
			if (member == null || wrongWorld || tooFar) {
				squad.memberIds.remove(index);
				this.squadByMember.remove(memberId, squad.id);
				changed = true;
				continue;
			}
			index++;
		}
		return changed;
	}

	private boolean electLeader(final Squad squad, final boolean replacement) {
		List<MixedSquadPlanner.Member<UUID>> snapshots = this.snapshots(squad);
		UUID elected = MixedSquadPlanner.electLeader(snapshots).orElse(null);
		if (elected == null) {
			return false;
		}
		squad.leaderId = elected;
		if (replacement) {
			squad.term++;
			this.metrics.leaderReplaced();
		} else {
			this.metrics.leaderElected();
		}
		return true;
	}

	private void rebuildTactics(final Squad squad) {
		squad.directiveRevision++;
		List<MixedSquadPlanner.Member<UUID>> snapshots = this.snapshots(squad);
		MixedSquadPlanner.Composition composition = MixedSquadPlanner.composition(snapshots);
		MemberRecord leader = this.members.get(squad.leaderId);
		int leaderIntelligence = leader == null ? 1 : this.intelligence.get(leader.mob);
		squad.plan = MixedSquadPlanner.choosePlan(composition, leaderIntelligence);
		squad.roles = MixedSquadPlanner.assignRoles(snapshots, squad.leaderId, squad.plan);
		List<MixedSquadTransportPlanner.Member<UUID>> transportMembers = new ArrayList<>(snapshots.size());
		for (int index = 0; index < snapshots.size(); index++) {
			MixedSquadPlanner.Member<UUID> member = snapshots.get(index);
			transportMembers.add(new MixedSquadTransportPlanner.Member<>(
				member.id(),
				member.species(),
				squad.roles.getOrDefault(member.id(), MixedSquadRole.FRONTLINE),
				member.intelligence(),
				member.stableOrder(),
				true
			));
		}
		squad.transportPairs = MixedSquadTransportPlanner.pairCreeperCarriers(squad.plan, transportMembers);
		Map<UUID, UUID> carriers = new HashMap<>();
		for (Map.Entry<UUID, UUID> pair : squad.transportPairs.entrySet()) {
			carriers.put(pair.getValue(), pair.getKey());
		}
		squad.transportCarriers = Map.copyOf(carriers);
	}

	private List<MixedSquadPlanner.Member<UUID>> snapshots(final Squad squad) {
		List<MixedSquadPlanner.Member<UUID>> snapshots = new ArrayList<>(squad.memberIds.size());
		for (int index = 0; index < squad.memberIds.size(); index++) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			EntityEquipment equipment = member.mob.getEquipment();
			boolean shield = equipment != null && (is(equipment.getItemInMainHand(), Material.SHIELD)
				|| is(equipment.getItemInOffHand(), Material.SHIELD));
			boolean utility = equipment != null && (isUtility(equipment.getItemInMainHand())
				|| isUtility(equipment.getItemInOffHand()));
			snapshots.add(new MixedSquadPlanner.Member<>(
				memberId,
				this.intelligence.get(member.mob),
				electionTicket(memberId),
				member.stableOrder,
				speciesOf(member.mob),
				shield,
				utility
			));
		}
		return snapshots;
	}

	private void resolveTarget(final Squad squad, final long now) {
		if (squad.target != null && this.isValidForAnyMember(squad, squad.target)) {
			squad.targetId = squad.target.getUniqueId();
			squad.lastTargetPosition = positionOf(squad.target);
			squad.lastTargetSeenAt = now;
			return;
		}
		for (int index = 0; index < squad.memberIds.size(); index++) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			LivingEntity candidate = member == null ? null : this.targetFor(member, now);
			if (member != null && candidate != null
				&& !(candidate instanceof Mob mob && this.areSquadmates(member.mob, mob))) {
				squad.target = candidate;
				squad.targetId = candidate.getUniqueId();
				squad.lastTargetPosition = positionOf(candidate);
				squad.lastTargetSeenAt = now;
				return;
			}
		}
		squad.target = null;
	}

	private boolean isValidForAnyMember(final Squad squad, final LivingEntity target) {
		if (target instanceof Mob mob && this.squadByMember.get(mob.getUniqueId()) != null
			&& this.squadByMember.get(mob.getUniqueId()).equals(squad.id)) {
			return false;
		}
		for (int index = 0; index < squad.memberIds.size(); index++) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			if (member != null && PaperThreats.isLiveFor(member.mob, target)) {
				return true;
			}
		}
		return false;
	}

	private LivingEntity targetFor(final MemberRecord member, final long now) {
		LivingEntity current = member.mob.getTarget();
		if (PaperThreats.isLiveFor(member.mob, current)
			&& !(current instanceof Mob mob && this.areSquadmates(member.mob, mob))) {
			member.rememberedTarget = current;
			member.rememberedTargetUntil = now + this.settings.get().targetMemoryTicks();
			return current;
		}
		LivingEntity remembered = member.rememberedTarget;
		if (member.rememberedTargetUntil >= now
			&& PaperThreats.isLiveFor(member.mob, remembered)
			&& !(remembered instanceof Mob mob && this.areSquadmates(member.mob, mob))) {
			return remembered;
		}
		member.rememberedTarget = null;
		member.rememberedTargetUntil = 0L;
		return null;
	}

	private void propagateTarget(final Squad squad, final PaperSquadSettings config) {
		if (!config.shareTargets() || squad.target == null) {
			return;
		}
		long now = Bukkit.getCurrentTick();
		for (int index = 0; index < squad.memberIds.size(); index++) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			if (member == null || !PaperThreats.isLiveFor(member.mob, squad.target)) {
				continue;
			}
			LivingEntity current = member.mob.getTarget();
			if (!PaperThreats.isLiveFor(member.mob, current)
				|| (current instanceof Mob mob && this.areSquadmates(member.mob, mob))) {
				if (now < member.nextTargetPropagationAt) {
					continue;
				}
				member.mob.setTarget(squad.target);
				member.nextTargetPropagationAt = now + 20L;
				this.metrics.sharedTarget();
			}
		}
	}

	private void advanceState(
		final Squad squad,
		final long now,
		final PaperSquadSettings config,
		final boolean structureChanged
	) {
		MemberRecord leader = this.members.get(squad.leaderId);
		boolean emergency = leader != null
			&& squad.target != null
			&& leader.mob.getWorld() == squad.target.getWorld()
			&& distanceSquared(leader.mob, squad.target)
				<= config.emergencyDistance() * config.emergencyDistance();
		boolean quorum = this.hasQuorum(squad);
		MixedSquadState next = MixedSquadPhasePlanner.next(
			squad.state,
			now - squad.stateEnteredAt,
			quorum,
			emergency,
			structureChanged,
			new MixedSquadPhasePlanner.Timings(
				config.formingTimeoutTicks(),
				config.briefingTicks(),
				config.deploymentTimeoutTicks(),
				config.reorganizingTicks()
			)
		);
		if (next != squad.state) {
			squad.state = next;
			squad.stateEnteredAt = now;
			squad.directiveRevision++;
			this.metrics.phaseTransition();
			this.announcePhase(squad);
		}
	}

	private boolean hasQuorum(final Squad squad) {
		MemberRecord leader = this.members.get(squad.leaderId);
		if (leader == null || squad.memberIds.isEmpty()) {
			return false;
		}
		SquadGeometry geometry = this.geometryFor(squad, leader, Bukkit.getCurrentTick());
		int ready = 0;
		int total = 0;
		for (int index = 0; index < squad.memberIds.size(); index++) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			total++;
			MixedSquadRole role = squad.roles.getOrDefault(memberId, MixedSquadRole.FRONTLINE);
			Vec3d destination = this.destinationFor(squad, member, geometry, role);
			if (distanceSquared(member.mob, destination) <= DESTINATION_QUORUM_DISTANCE_SQUARED) {
				ready++;
			}
		}
		return total > 0 && (double)ready / total >= REQUIRED_QUORUM_FRACTION;
	}

	private Vec3d destinationFor(
		final Squad squad,
		final MemberRecord member,
		final SquadGeometry geometry,
		final MixedSquadRole role
	) {
		if (squad.state == MixedSquadState.DEPLOYING || squad.state == MixedSquadState.ENGAGING) {
			return MixedSquadGeometry.combatPosition(
				geometry.focus(),
				geometry.targetLookX(),
				geometry.targetLookZ(),
				geometry.targetFromLeaderX(),
				geometry.targetFromLeaderZ(),
				role,
				member.stableOrder,
				10.0
			);
		}
		return MixedSquadGeometry.rallyPosition(
			geometry.leaderPosition(),
			geometry.focus(),
			role,
			member.stableOrder
		);
	}

	private SquadGeometry geometryFor(final Squad squad, final MemberRecord leader, final long now) {
		LivingEntity target = squad.target;
		if (squad.cachedGeometry != null
			&& squad.geometryCachedAt == now
			&& squad.cachedGeometryRevision == squad.directiveRevision
			&& squad.cachedGeometryLeader == leader.mob
			&& squad.cachedGeometryTarget == target) {
			this.geometryCacheHits++;
			return squad.cachedGeometry;
		}
		Vec3d focus = target != null && target.isValid() ? positionOf(target) : squad.lastTargetPosition;
		Vec3d leaderPosition = positionOf(leader.mob);
		boolean combat = squad.state == MixedSquadState.DEPLOYING || squad.state == MixedSquadState.ENGAGING;
		double targetFromLeaderX = combat ? focus.x() - leaderPosition.x() : 0.0;
		double targetFromLeaderZ = combat ? focus.z() - leaderPosition.z() : 0.0;
		double targetLookX = targetFromLeaderX;
		double targetLookZ = targetFromLeaderZ;
		if (combat && target != null) {
			double yaw = Math.toRadians(target.getYaw());
			double horizontalScale = Math.cos(Math.toRadians(target.getPitch()));
			targetLookX = -horizontalScale * Math.sin(yaw);
			targetLookZ = horizontalScale * Math.cos(yaw);
		}
		SquadGeometry geometry = new SquadGeometry(
			focus,
			leaderPosition,
			targetFromLeaderX,
			targetFromLeaderZ,
			targetLookX,
			targetLookZ
		);
		squad.cachedGeometry = geometry;
		squad.geometryCachedAt = now;
		squad.cachedGeometryRevision = squad.directiveRevision;
		squad.cachedGeometryLeader = leader.mob;
		squad.cachedGeometryTarget = target;
		this.geometryComputations++;
		return geometry;
	}

	private void announcePhase(final Squad squad) {
		if (squad.state != MixedSquadState.BRIEFING && squad.state != MixedSquadState.ENGAGING) {
			return;
		}
		MemberRecord leader = this.members.get(squad.leaderId);
		if (leader == null) {
			return;
		}
		leader.mob.swingMainHand();
		float pitch = 0.82F + this.intelligence.get(leader.mob) * 0.025F;
		leader.mob.getWorld().playSound(
			leader.mob.getLocation(),
			commandSound(leader.mob),
			SoundCategory.HOSTILE,
			1.0F,
			pitch
		);
		for (int index = 0; index < squad.memberIds.size(); index++) {
			UUID memberId = squad.memberIds.get(index);
			MemberRecord member = this.members.get(memberId);
			if (member != null && member != leader && Math.floorMod(member.stableOrder, 3) == 0) {
				member.mob.swingMainHand();
			}
		}
	}

	private void pruneSquadReference(final long squadId) {
		Squad squad = this.squads.get(squadId);
		if (squad != null && squad.memberIds.isEmpty()) {
			this.squads.remove(squadId);
		}
	}

	private void detachMember(final UUID memberId) {
		Long squadId = this.squadByMember.remove(memberId);
		if (squadId == null) {
			return;
		}
		Squad squad = this.squads.get(squadId);
		if (squad != null) {
			if (squad.memberIds.remove(memberId)) {
				squad.directiveRevision++;
			}
			this.pruneSquadReference(squadId);
		}
	}

	private void releaseSquadMembers(final Squad squad) {
		for (int index = 0; index < squad.memberIds.size(); index++) {
			this.squadByMember.remove(squad.memberIds.get(index), squad.id);
		}
		squad.memberIds.clear();
	}

	private void resetSquads() {
		this.squads.clear();
		this.squadByMember.clear();
	}

	private void rebuildSpatialIndex() {
		this.spatialIndex = this.newSpatialIndex(this.settings.get().formationRadius());
		for (MemberRecord member : this.members.values()) {
			this.spatialIndex.add(member);
		}
	}

	private BoundedSpatialIndex<UUID, MemberRecord> newSpatialIndex(final double cellSize) {
		return new BoundedSpatialIndex<>(
			cellSize,
			member -> member.mob.getWorld().getUID(),
			member -> member.mob.getX(),
			member -> member.mob.getZ()
		);
	}

	private void stopTask() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
	}

	private static boolean is(final ItemStack stack, final Material material) {
		return stack != null && stack.getType() == material;
	}

	private static boolean isUtility(final ItemStack stack) {
		if (stack == null) {
			return false;
		}
		return switch (stack.getType()) {
			case WATER_BUCKET, LAVA_BUCKET, TNT, FLINT_AND_STEEL -> true;
			default -> false;
		};
	}

	private static MixedSquadSpecies speciesOf(final Mob mob) {
		if (mob instanceof AbstractSkeleton) {
			return MixedSquadSpecies.SKELETON;
		}
		if (mob instanceof Creeper) {
			return MixedSquadSpecies.CREEPER;
		}
		if (mob instanceof Spider) {
			return MixedSquadSpecies.SPIDER;
		}
		if (mob instanceof Zombie) {
			return MixedSquadSpecies.ZOMBIE;
		}
		throw new IllegalArgumentException("unsupported squad member: " + mob.getType());
	}

	private static Sound commandSound(final Mob mob) {
		if (mob instanceof AbstractSkeleton) {
			return Sound.ENTITY_SKELETON_AMBIENT;
		}
		if (mob instanceof Creeper) {
			return Sound.ENTITY_CREEPER_HURT;
		}
		if (mob instanceof Spider) {
			return Sound.ENTITY_SPIDER_AMBIENT;
		}
		return Sound.ENTITY_ZOMBIE_AMBIENT;
	}

	private static int stableOrder(final UUID id) {
		return id.hashCode() & Integer.MAX_VALUE;
	}

	private static long electionTicket(final UUID id) {
		return id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23);
	}

	private static Vec3d positionOf(final Entity entity) {
		return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
	}

	private static double distanceSquared(final Entity first, final Entity second) {
		double x = first.getX() - second.getX();
		double y = first.getY() - second.getY();
		double z = first.getZ() - second.getZ();
		return x * x + y * y + z * z;
	}

	private static double distanceSquared(final Entity entity, final Vec3d point) {
		double x = entity.getX() - point.x();
		double y = entity.getY() - point.y();
		double z = entity.getZ() - point.z();
		return x * x + y * y + z * z;
	}

	private static final class MemberRecord {
		private Mob mob;
		private final int stableOrder;
		private LivingEntity rememberedTarget;
		private long rememberedTargetUntil;
		private long nextTargetPropagationAt;
		private PaperSquadDirective cachedDirective;
		private long directiveCachedAt = Long.MIN_VALUE;
		private long cachedDirectiveRevision = Long.MIN_VALUE;
		private boolean cachedSharedMemory;

		private MemberRecord(final Mob mob, final int stableOrder) {
			this.mob = mob;
			this.stableOrder = stableOrder;
		}

		private void clearDirectiveCache() {
			this.cachedDirective = null;
			this.directiveCachedAt = Long.MIN_VALUE;
			this.cachedDirectiveRevision = Long.MIN_VALUE;
			this.cachedSharedMemory = false;
		}
	}

	private static final class Squad {
		private final long id;
		private final List<UUID> memberIds = new ArrayList<>();
		private UUID leaderId;
		private LivingEntity target;
		private UUID targetId;
		private Vec3d lastTargetPosition;
		private long lastTargetSeenAt;
		private int term = 1;
		private MixedSquadState state = MixedSquadState.FORMING;
		private long stateEnteredAt;
		private MixedSquadPlan plan = MixedSquadPlan.SWARM;
		private Map<UUID, MixedSquadRole> roles = Map.of();
		private Map<UUID, UUID> transportPairs = Map.of();
		private Map<UUID, UUID> transportCarriers = Map.of();
		private long directiveRevision;
		private SquadGeometry cachedGeometry;
		private long geometryCachedAt = Long.MIN_VALUE;
		private long cachedGeometryRevision = Long.MIN_VALUE;
		private Mob cachedGeometryLeader;
		private LivingEntity cachedGeometryTarget;

		private Squad(final long id, final LivingEntity target, final long now) {
			this.id = id;
			this.target = target;
			this.targetId = target.getUniqueId();
			this.lastTargetPosition = positionOf(target);
			this.lastTargetSeenAt = now;
			this.stateEnteredAt = now;
		}

		private void clearGeometryCache() {
			this.cachedGeometry = null;
			this.geometryCachedAt = Long.MIN_VALUE;
			this.cachedGeometryRevision = Long.MIN_VALUE;
			this.cachedGeometryLeader = null;
			this.cachedGeometryTarget = null;
		}
	}

	private record SquadGeometry(
		Vec3d focus,
		Vec3d leaderPosition,
		double targetFromLeaderX,
		double targetFromLeaderZ,
		double targetLookX,
		double targetLookZ
	) {
	}

	private record ScanResult(List<MemberRecord> members, int rawChecks) {
	}
}

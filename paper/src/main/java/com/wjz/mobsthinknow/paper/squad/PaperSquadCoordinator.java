package com.wjz.mobsthinknow.paper.squad;

import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperThreats;
import com.wjz.mobsthinknow.shared.math.Vec3d;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final Map<CellKey, LinkedHashSet<UUID>> cells = new HashMap<>();
	private final Map<Long, Squad> squads = new LinkedHashMap<>();
	private final Map<UUID, Long> squadByMember = new HashMap<>();
	private long nextSquadId = 1L;
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
		this.resetSquads();
		this.rebuildCells();
		this.start();
	}

	public void stop() {
		this.stopTask();
		this.resetSquads();
		this.members.clear();
		this.cells.clear();
	}

	public void track(final Mob mob) {
		if (!this.intelligence.supports(mob)) {
			return;
		}
		MemberRecord record = this.members.computeIfAbsent(
			mob.getUniqueId(),
			ignored -> new MemberRecord(mob, stableOrder(mob.getUniqueId()))
		);
		record.mob = mob;
		this.moveCell(record, this.cellFor(mob.getLocation()));
	}

	public void untrack(final Mob mob) {
		MemberRecord removed = this.members.remove(mob.getUniqueId());
		if (removed == null) {
			return;
		}
		this.removeFromCell(removed);
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
		Vec3d focus = squad.target != null && squad.target.isValid()
			? toVector(squad.target.getLocation())
			: squad.lastTargetPosition;
		MixedSquadRole role = squad.roles.getOrDefault(mob.getUniqueId(), MixedSquadRole.FRONTLINE);
		Vec3d destination = this.destinationFor(squad, member, leader, focus, role);
		boolean sharedMemory = squad.target != null && mob.getTarget() != squad.target;
		return new PaperSquadDirective(
			squad.id,
			squad.term,
			squad.state,
			squad.plan,
			role,
			destination,
			focus,
			squad.leaderId,
			squad.targetId,
			sharedMemory
		);
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

	/** 返回共享配对器给这只蜘蛛分配的苦力怕；查询只访问当前小队快照，不扫描世界实体。 */
	public Creeper assignedTransportPartnerFor(final Spider spider) {
		Long squadId = this.squadByMember.get(spider.getUniqueId());
		Squad squad = squadId == null ? null : this.squads.get(squadId);
		UUID payloadId = squad == null ? null : squad.transportPairs.get(spider.getUniqueId());
		MemberRecord payload = payloadId == null ? null : this.members.get(payloadId);
		return payload != null && payload.mob instanceof Creeper creeper ? creeper : null;
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

	public PaperSquadMetrics metrics() {
		return this.metrics;
	}

	private void tick() {
		PaperSquadSettings config = this.settings.get();
		if (!this.globallyEnabled.getAsBoolean() || !config.enabled()) {
			this.resetSquads();
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
				this.removeFromCell(member);
				detached.add(entry.getKey());
				iterator.remove();
				continue;
			}
			this.moveCell(member, this.cellFor(member.mob.getLocation()));
		}
		for (UUID memberId : detached) {
			this.detachMember(memberId);
		}
	}

	private void updateSquads(final long now, final PaperSquadSettings config) {
		Iterator<Squad> iterator = this.squads.values().iterator();
		while (iterator.hasNext()) {
			Squad squad = iterator.next();
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
			for (MemberRecord member : nearby.members()) {
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
		for (MemberRecord member : result.members()) {
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
		List<MemberRecord> accepted = new ArrayList<>();
		if (includeSeed) {
			accepted.add(seed);
		}
		CellKey center = seed.cell;
		if (center == null) {
			return new ScanResult(accepted, 0);
		}
		double radiusSquared = config.formationRadius() * config.formationRadius();
		long now = Bukkit.getCurrentTick();
		int checks = 0;
		outer:
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				Set<UUID> bucket = this.cells.get(new CellKey(center.worldId(), center.x() + dx, center.z() + dz));
				if (bucket == null) {
					continue;
				}
				for (UUID candidateId : bucket) {
					if (checks >= config.rawScanLimit()) {
						break outer;
					}
					checks++;
					MemberRecord candidate = this.members.get(candidateId);
					if (candidate == null
						|| candidate == seed
						|| candidate.mob == sharedTarget
						|| this.squadByMember.containsKey(candidateId)
						|| candidate.mob.getLocation().distanceSquared(seed.mob.getLocation()) > radiusSquared) {
						continue;
					}
					LivingEntity ownTarget = this.targetFor(candidate, now);
					if (ownTarget != null && ownTarget != sharedTarget) {
						continue;
					}
					accepted.add(candidate);
					if (accepted.size() >= config.maximumMembers()) {
						break outer;
					}
				}
			}
		}
		this.metrics.candidateChecks(checks);
		return new ScanResult(accepted, checks);
	}

	private boolean pruneSquadMembers(final Squad squad, final PaperSquadSettings config) {
		MemberRecord leader = this.members.get(squad.leaderId);
		Location anchor = leader == null ? null : leader.mob.getLocation();
		double maximumSquared = config.maximumSeparation() * config.maximumSeparation();
		Iterator<UUID> iterator = squad.memberIds.iterator();
		boolean changed = false;
		while (iterator.hasNext()) {
			UUID memberId = iterator.next();
			MemberRecord member = this.members.get(memberId);
			boolean tooFar = anchor != null
				&& member != null
				&& member.mob.getWorld() == anchor.getWorld()
				&& member.mob.getLocation().distanceSquared(anchor) > maximumSquared;
			if (member == null || tooFar || member.mob.getWorld() != (anchor == null ? member.mob.getWorld() : anchor.getWorld())) {
				iterator.remove();
				this.squadByMember.remove(memberId, squad.id);
				changed = true;
			}
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
		List<MixedSquadPlanner.Member<UUID>> snapshots = this.snapshots(squad);
		MixedSquadPlanner.Composition composition = MixedSquadPlanner.composition(snapshots);
		MemberRecord leader = this.members.get(squad.leaderId);
		int leaderIntelligence = leader == null ? 1 : this.intelligence.get(leader.mob);
		squad.plan = MixedSquadPlanner.choosePlan(composition, leaderIntelligence);
		squad.roles = MixedSquadPlanner.assignRoles(snapshots, squad.leaderId, squad.plan);
		List<MixedSquadTransportPlanner.Member<UUID>> transportMembers = new ArrayList<>(snapshots.size());
		for (MixedSquadPlanner.Member<UUID> member : snapshots) {
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
	}

	private List<MixedSquadPlanner.Member<UUID>> snapshots(final Squad squad) {
		List<MixedSquadPlanner.Member<UUID>> snapshots = new ArrayList<>(squad.memberIds.size());
		for (UUID memberId : squad.memberIds) {
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
			squad.lastTargetPosition = toVector(squad.target.getLocation());
			squad.lastTargetSeenAt = now;
			return;
		}
		for (UUID memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			LivingEntity candidate = member == null ? null : this.targetFor(member, now);
			if (member != null && candidate != null
				&& !(candidate instanceof Mob mob && this.areSquadmates(member.mob, mob))) {
				squad.target = candidate;
				squad.targetId = candidate.getUniqueId();
				squad.lastTargetPosition = toVector(candidate.getLocation());
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
		for (UUID memberId : squad.memberIds) {
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
		for (UUID memberId : squad.memberIds) {
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
			&& leader.mob.getLocation().distanceSquared(squad.target.getLocation())
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
			this.metrics.phaseTransition();
			this.announcePhase(squad);
		}
	}

	private boolean hasQuorum(final Squad squad) {
		MemberRecord leader = this.members.get(squad.leaderId);
		if (leader == null || squad.memberIds.isEmpty()) {
			return false;
		}
		Vec3d focus = squad.target != null ? toVector(squad.target.getLocation()) : squad.lastTargetPosition;
		int ready = 0;
		int total = 0;
		for (UUID memberId : squad.memberIds) {
			MemberRecord member = this.members.get(memberId);
			if (member == null) {
				continue;
			}
			total++;
			MixedSquadRole role = squad.roles.getOrDefault(memberId, MixedSquadRole.FRONTLINE);
			Vec3d destination = this.destinationFor(squad, member, leader, focus, role);
			if (toVector(member.mob.getLocation()).distanceSquared(destination) <= DESTINATION_QUORUM_DISTANCE_SQUARED) {
				ready++;
			}
		}
		return total > 0 && (double)ready / total >= REQUIRED_QUORUM_FRACTION;
	}

	private Vec3d destinationFor(
		final Squad squad,
		final MemberRecord member,
		final MemberRecord leader,
		final Vec3d focus,
		final MixedSquadRole role
	) {
		if (squad.state == MixedSquadState.DEPLOYING || squad.state == MixedSquadState.ENGAGING) {
			Vec3d targetLook = squad.target == null
				? focus.subtract(toVector(leader.mob.getLocation()))
				: toVector(squad.target.getLocation().getDirection());
			return MixedSquadGeometry.combatPosition(
				focus,
				targetLook,
				focus.subtract(toVector(leader.mob.getLocation())),
				role,
				member.stableOrder,
				10.0
			);
		}
		return MixedSquadGeometry.rallyPosition(
			toVector(leader.mob.getLocation()),
			focus,
			role,
			member.stableOrder
		);
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
		for (UUID memberId : squad.memberIds) {
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
			squad.memberIds.remove(memberId);
			this.pruneSquadReference(squadId);
		}
	}

	private void releaseSquadMembers(final Squad squad) {
		for (UUID memberId : squad.memberIds) {
			this.squadByMember.remove(memberId, squad.id);
		}
		squad.memberIds.clear();
	}

	private void resetSquads() {
		this.squads.clear();
		this.squadByMember.clear();
	}

	private void rebuildCells() {
		this.cells.clear();
		for (MemberRecord member : this.members.values()) {
			member.cell = null;
			this.moveCell(member, this.cellFor(member.mob.getLocation()));
		}
	}

	private void moveCell(final MemberRecord member, final CellKey next) {
		if (next.equals(member.cell)) {
			return;
		}
		this.removeFromCell(member);
		member.cell = next;
		this.cells.computeIfAbsent(next, ignored -> new LinkedHashSet<>()).add(member.mob.getUniqueId());
	}

	private void removeFromCell(final MemberRecord member) {
		if (member.cell == null) {
			return;
		}
		Set<UUID> bucket = this.cells.get(member.cell);
		if (bucket != null) {
			bucket.remove(member.mob.getUniqueId());
			if (bucket.isEmpty()) {
				this.cells.remove(member.cell);
			}
		}
		member.cell = null;
	}

	private CellKey cellFor(final Location location) {
		double size = this.settings.get().formationRadius();
		return new CellKey(
			location.getWorld().getUID(),
			(int)Math.floor(location.getX() / size),
			(int)Math.floor(location.getZ() / size)
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

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toVector(final org.bukkit.util.Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	private static final class MemberRecord {
		private Mob mob;
		private final int stableOrder;
		private CellKey cell;
		private LivingEntity rememberedTarget;
		private long rememberedTargetUntil;
		private long nextTargetPropagationAt;

		private MemberRecord(final Mob mob, final int stableOrder) {
			this.mob = mob;
			this.stableOrder = stableOrder;
		}
	}

	private static final class Squad {
		private final long id;
		private final LinkedHashSet<UUID> memberIds = new LinkedHashSet<>();
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

		private Squad(final long id, final LivingEntity target, final long now) {
			this.id = id;
			this.target = target;
			this.targetId = target.getUniqueId();
			this.lastTargetPosition = toVector(target.getLocation());
			this.lastTargetSeenAt = now;
			this.stateEnteredAt = now;
		}
	}

	private record CellKey(UUID worldId, int x, int z) {
	}

	private record ScanResult(List<MemberRecord> members, int rawChecks) {
	}
}

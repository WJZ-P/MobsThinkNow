package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperCoverSettings;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner;
import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.GridPosition;
import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.Plan;
import com.wjz.mobsthinknow.shared.ai.FiringLanePlanner;
import com.wjz.mobsthinknow.shared.ai.RangedSpacingPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;

/**
 * Paper-only cover state machine backed by the shared bounded geometry planner.
 *
 * <p>Emergency disengage and projectile evasion have higher goal priorities. Coordinated crossfire also keeps its
 * own firing position; this goal fills the standalone archer gap without replacing NMS classes or requiring a
 * client mod.</p>
 */
public final class PaperSkeletonCoverGoal implements Goal<AbstractSkeleton> {
	private static final double POSITION_REACHED_DISTANCE_SQUARED = 0.85 * 0.85;
	private static final int MOVEMENT_TIMEOUT_TICKS = 70;
	private static final int PEEK_MOVEMENT_TIMEOUT_TICKS = 30;
	private static final int RETURN_TIMEOUT_TICKS = 40;
	private static final int REPATH_INTERVAL_TICKS = 6;
	private static final int POST_SHOT_FACING_TICKS = 2;

	private final AbstractSkeleton skeleton;
	private final GoalKey<AbstractSkeleton> key;
	private final Supplier<PaperSettings> settings;
	private final Supplier<PaperCoverSettings> coverSettings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperMetrics metrics;
	private final int stableOrder;
	private final List<Mob> squadmateBuffer = new ArrayList<>();
	private final FiringLanePlanner.AllyBuffer<UUID> friendlyLaneAllies = new FiringLanePlanner.AllyBuffer<>();

	private LivingEntity target;
	private Plan plan;
	private CoverPositionPlanner.Probe geometryProbe;
	private Pathfinder.PathResult pendingPath;
	private Location targetAnchor;
	private Phase phase = Phase.INACTIVE;
	private long phaseStartedAt;
	private long startedAt;
	private long nextSearchAt;
	private long nextRepathAt;
	private int hiddenWaitTicks;
	private int shotsTaken;
	private int peekAttempts;
	private int cycleSequence;
	private boolean finished;
	private boolean completed;

	public PaperSkeletonCoverGoal(
		final AbstractSkeleton skeleton,
		final GoalKey<AbstractSkeleton> key,
		final Supplier<PaperSettings> settings,
		final Supplier<PaperCoverSettings> coverSettings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperMetrics metrics
	) {
		this.skeleton = skeleton;
		this.key = key;
		this.settings = settings;
		this.coverSettings = coverSettings;
		this.intelligence = intelligence;
		this.squads = squads;
		this.metrics = metrics;
		this.stableOrder = skeleton.getUniqueId().hashCode() & Integer.MAX_VALUE;
		this.nextSearchAt = Bukkit.getCurrentTick() + Math.floorMod(this.stableOrder, 20);
	}

	@Override
	public boolean shouldActivate() {
		long now = Bukkit.getCurrentTick();
		PaperSettings root = this.settings.get();
		PaperCoverSettings config = this.coverSettings.get();
		if (!this.eligible(root, config) || now < this.nextSearchAt) {
			return false;
		}
		LivingEntity candidateTarget = this.currentTarget();
		if (!PaperThreats.isLiveFor(this.skeleton, candidateTarget)
			|| !this.skeleton.hasLineOfSight(candidateTarget)
			|| this.crossfireOwnsMovement()
			|| RangedSpacingPlanner.shouldStartEmergencyDisengage(
				PaperEntityMath.horizontalDistanceSquared(this.skeleton, candidateTarget),
				root.skeletonPreferredRange(),
				this.intelligence.get(this.skeleton)
			)
			|| !CoverPositionPlanner.isUsefulRange(
				PaperEntityMath.distanceSquared(this.skeleton, candidateTarget),
				root.skeletonPreferredRange(),
				config.searchLimits()
			)) {
			this.nextSearchAt = now + Math.max(10, config.searchCooldownTicks() / 3);
			return false;
		}

		this.metrics.skeletonCoverSearch();
		CoverPositionPlanner.Probe candidateProbe = this.probe(candidateTarget);
		var result = CoverPositionPlanner.findPlans(
			toGrid(this.skeleton.getLocation()),
			toShared(this.skeleton.getLocation()),
			toShared(candidateTarget.getLocation()),
			root.skeletonPreferredRange(),
			this.stableOrder + this.cycleSequence,
			config.searchLimits(),
			candidateProbe
		);
		this.metrics.skeletonCoverCandidatesChecked(result.rawChecks());
		this.metrics.skeletonCoverPlansFound(result.plans().size());
		this.nextSearchAt = now + config.searchCooldownTicks();
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		for (Plan candidate : result.plans()) {
			Pathfinder.PathResult path = pathfinder.findPath(toLocation(candidate.hide()));
			if (path != null) {
				this.target = candidateTarget;
				this.plan = candidate;
				this.geometryProbe = candidateProbe;
				this.pendingPath = path;
				this.targetAnchor = candidateTarget.getLocation().clone();
				return true;
			}
			this.metrics.skeletonCoverPathFailed();
		}
		return false;
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings root = this.settings.get();
		PaperCoverSettings config = this.coverSettings.get();
		return !this.finished
			&& this.eligible(root, config)
			&& PaperThreats.isLiveFor(this.skeleton, this.target)
			&& this.currentTarget() == this.target
			&& !this.crossfireOwnsMovement()
			&& Bukkit.getCurrentTick() - this.startedAt < config.cycleTimeoutTicks()
			&& this.targetAnchor != null
			&& this.targetAnchor.getWorld() == this.target.getWorld()
			&& PaperEntityMath.distanceSquared(this.target, this.targetAnchor)
				<= config.targetMovementTolerance() * config.targetMovementTolerance();
	}

	@Override
	public void start() {
		long now = Bukkit.getCurrentTick();
		this.startedAt = now;
		this.shotsTaken = 0;
		this.peekAttempts = 0;
		this.finished = false;
		this.completed = false;
		this.skeleton.clearActiveItem();
		this.skeleton.setAggressive(true);
		this.metrics.skeletonCoverCycleStarted();
		this.transition(Phase.MOVING_TO_COVER, now);
		if (this.pendingPath == null
			|| !this.skeleton.getPathfinder().moveTo(this.pendingPath, this.coverSettings.get().movementSpeed())) {
			this.metrics.skeletonCoverPathFailed();
			this.abort();
		}
		this.pendingPath = null;
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.skeleton, current) || this.plan == null || this.geometryProbe == null) {
			this.abort();
			return;
		}
		long now = Bukkit.getCurrentTick();
		this.skeleton.lookAt(current, 50.0F, 45.0F);
		switch (this.phase) {
			case MOVING_TO_COVER -> this.tickMovingToCover(now);
			case HIDDEN -> this.tickHidden(now);
			case MOVING_TO_PEEK -> this.tickMovingToPeek(now);
			case DRAWING -> this.tickDrawing(now);
			case POST_SHOT -> this.tickPostShot(now);
			case RETURNING_TO_COVER -> this.tickReturning(now);
			case INACTIVE -> this.abort();
		}
	}

	@Override
	public void stop() {
		this.skeleton.clearActiveItem();
		this.skeleton.getPathfinder().stopPathfinding();
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		this.nextSearchAt = Math.max(
			this.nextSearchAt,
			Bukkit.getCurrentTick() + this.coverSettings.get().searchCooldownTicks()
		);
		if (this.startedAt != 0L && !this.completed) {
			this.metrics.skeletonCoverCycleAborted();
		}
		this.cycleSequence++;
		this.target = null;
		this.plan = null;
		this.geometryProbe = null;
		this.pendingPath = null;
		this.targetAnchor = null;
		this.phase = Phase.INACTIVE;
		this.phaseStartedAt = 0L;
		this.startedAt = 0L;
		this.nextRepathAt = 0L;
		this.hiddenWaitTicks = 0;
		this.shotsTaken = 0;
		this.peekAttempts = 0;
		this.finished = false;
		this.completed = false;
	}

	@Override
	public GoalKey<AbstractSkeleton> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	public Phase phase() {
		return this.phase;
	}

	private void tickMovingToCover(final long now) {
		this.skeleton.clearActiveItem();
		if (this.reached(this.plan.hide())) {
			this.skeleton.getPathfinder().stopPathfinding();
			if (!this.geometryProbe.isHidden(this.plan.hide())) {
				this.abort();
				return;
			}
			this.hiddenWaitTicks = this.nextHiddenWait();
			this.transition(Phase.HIDDEN, now);
			return;
		}
		if (now - this.phaseStartedAt >= MOVEMENT_TIMEOUT_TICKS) {
			this.abort();
			return;
		}
		this.ensureMovingTo(this.plan.hide(), now, MOVEMENT_TIMEOUT_TICKS);
	}

	private void tickHidden(final long now) {
		this.skeleton.clearActiveItem();
		this.skeleton.getPathfinder().stopPathfinding();
		if (!this.geometryProbe.isHidden(this.plan.hide())) {
			this.abort();
			return;
		}
		if (now - this.phaseStartedAt < this.hiddenWaitTicks) {
			return;
		}
		if (!this.geometryProbe.hasClearShot(this.plan.peek())
			|| !this.moveTo(this.plan.peek(), this.coverSettings.get().movementSpeed(), now)) {
			this.abort();
			return;
		}
		this.transition(Phase.MOVING_TO_PEEK, now);
	}

	private void tickMovingToPeek(final long now) {
		if (this.reached(this.plan.peek())) {
			this.skeleton.getPathfinder().stopPathfinding();
			if (!this.geometryProbe.hasClearShot(this.plan.peek())) {
				this.beginReturn(now);
				return;
			}
			this.skeleton.startUsingItem(EquipmentSlot.HAND);
			this.transition(Phase.DRAWING, now);
			return;
		}
		if (now - this.phaseStartedAt >= PEEK_MOVEMENT_TIMEOUT_TICKS) {
			this.abort();
			return;
		}
		this.ensureMovingTo(this.plan.peek(), now, PEEK_MOVEMENT_TIMEOUT_TICKS);
	}

	private void tickDrawing(final long now) {
		if (!this.geometryProbe.hasClearShot(this.plan.peek())) {
			this.skeleton.clearActiveItem();
			this.beginReturn(now);
			return;
		}
		if (!this.skeleton.hasActiveItem()) {
			this.skeleton.startUsingItem(EquipmentSlot.HAND);
		}
		int elapsed = (int)(now - this.phaseStartedAt);
		if (elapsed < this.coverSettings.get().drawTicks()) {
			return;
		}
		this.skeleton.clearActiveItem();
		this.peekAttempts++;
		if (this.hasClearFriendlyLane(this.target)) {
			float power = Math.clamp(elapsed / 20.0F, 0.1F, 1.0F);
			this.skeleton.rangedAttack(this.target, power);
			this.shotsTaken++;
			this.metrics.skeletonCoverPeekShot();
		}
		this.transition(Phase.POST_SHOT, now);
	}

	private void tickPostShot(final long now) {
		if (now - this.phaseStartedAt >= POST_SHOT_FACING_TICKS) {
			this.beginReturn(now);
		}
	}

	private void tickReturning(final long now) {
		this.skeleton.clearActiveItem();
		if (this.reached(this.plan.hide())) {
			this.skeleton.getPathfinder().stopPathfinding();
			this.metrics.skeletonCoverReturnCompleted();
			int maximumShots = this.coverSettings.get().maximumShotsPerCover();
			if (this.shotsTaken >= maximumShots || this.peekAttempts >= maximumShots + 2) {
				this.completed = true;
				this.finished = true;
				return;
			}
			this.hiddenWaitTicks = this.nextHiddenWait();
			this.transition(Phase.HIDDEN, now);
			return;
		}
		if (now - this.phaseStartedAt >= RETURN_TIMEOUT_TICKS) {
			this.abort();
			return;
		}
		this.ensureMovingTo(this.plan.hide(), now, RETURN_TIMEOUT_TICKS);
	}

	private void beginReturn(final long now) {
		this.skeleton.clearActiveItem();
		if (!this.moveTo(this.plan.hide(), this.coverSettings.get().movementSpeed() * 1.05, now)) {
			this.abort();
			return;
		}
		this.transition(Phase.RETURNING_TO_COVER, now);
	}

	private void ensureMovingTo(final GridPosition destination, final long now, final int timeout) {
		if (this.skeleton.getPathfinder().hasPath() || now < this.nextRepathAt) {
			return;
		}
		if (!this.moveTo(destination, this.coverSettings.get().movementSpeed(), now)) {
			this.abort();
			return;
		}
		this.nextRepathAt = Math.min(now + REPATH_INTERVAL_TICKS, this.phaseStartedAt + timeout);
	}

	private boolean moveTo(final GridPosition destination, final double speed, final long now) {
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(toLocation(destination));
		boolean moving = path != null && pathfinder.moveTo(path, speed);
		this.nextRepathAt = now + REPATH_INTERVAL_TICKS;
		if (!moving) {
			this.metrics.skeletonCoverPathFailed();
		}
		return moving;
	}

	private CoverPositionPlanner.Probe probe(final LivingEntity currentTarget) {
		return new CoverPositionPlanner.Probe() {
			@Override
			public boolean isStandable(final int x, final int y, final int z) {
				return PaperSkeletonCoverGoal.this.isStandable(x, y, z);
			}

			@Override
			public boolean isHidden(final int x, final int y, final int z) {
				return !PaperSkeletonCoverGoal.this.hasClearRay(x, y, z, currentTarget);
			}

			@Override
			public boolean hasClearShot(final int x, final int y, final int z) {
				return PaperSkeletonCoverGoal.this.hasClearRay(x, y, z, currentTarget);
			}
		};
	}

	private boolean isStandable(final int x, final int y, final int z) {
		World world = this.skeleton.getWorld();
		Block feet = world.getBlockAt(x, y, z);
		Block head = world.getBlockAt(x, y + 1, z);
		Block support = world.getBlockAt(x, y - 1, z);
		return support.getType().isSolid()
			&& !isHazard(support.getType())
			&& feet.isPassable()
			&& head.isPassable()
			&& feet.getType() != Material.WATER
			&& feet.getType() != Material.LAVA
			&& head.getType() != Material.WATER
			&& head.getType() != Material.LAVA
			&& world.getWorldBorder().isInside(toLocation(x, y, z));
	}

	private boolean hasClearRay(final int x, final int y, final int z, final LivingEntity currentTarget) {
		Location from = toLocation(x, y, z).add(0.0, this.skeleton.getEyeHeight(), 0.0);
		Location to = currentTarget.getEyeLocation();
		Vector direction = to.toVector().subtract(from.toVector());
		double distance = direction.length();
		return distance <= 1.0E-6 || this.skeleton.getWorld().rayTraceBlocks(
			from,
			direction.normalize(),
			distance,
			FluidCollisionMode.NEVER,
			true
		) == null;
	}

	private boolean hasClearFriendlyLane(final LivingEntity currentTarget) {
		PaperSettings config = this.settings.get();
		this.squads.copySquadmatesTo(this.skeleton, this.squadmateBuffer);
		this.friendlyLaneAllies.clear();
		for (int index = 0; index < this.squadmateBuffer.size(); index++) {
			Mob ally = this.squadmateBuffer.get(index);
			this.friendlyLaneAllies.add(
				ally.getUniqueId(),
				ally.getX(),
				ally.getY() + ally.getHeight() * 0.55,
				ally.getZ(),
				Math.max(config.skeletonFriendlyLaneRadius(), ally.getWidth() * 0.65)
			);
		}
		this.squadmateBuffer.clear();
		var result = FiringLanePlanner.check(
			toShared(this.skeleton.getEyeLocation()),
			toShared(currentTarget.getEyeLocation()),
			this.friendlyLaneAllies,
			config.skeletonFriendlyLaneMaximumChecks()
		);
		if (!result.clear()) {
			this.metrics.friendlyLaneBlocked();
		}
		return result.clear();
	}

	private boolean eligible(final PaperSettings root, final PaperCoverSettings config) {
		return root.enabled()
			&& config.enabled()
			&& this.skeleton.isValid()
			&& !this.skeleton.isDead()
			&& !this.skeleton.isInsideVehicle()
			&& this.intelligence.get(this.skeleton) >= config.minimumIntelligence()
			&& this.skeleton.getEquipment().getItemInMainHand().getType() == Material.BOW;
	}

	private boolean crossfireOwnsMovement() {
		PaperSquadDirective directive = this.squads.directiveFor(this.skeleton);
		return directive != null
			&& directive.state() == MixedSquadState.ENGAGING
			&& directive.role().isRanged()
			&& directive.plan().usesCrossfire();
	}

	private LivingEntity currentTarget() {
		LivingEntity shared = this.squads.sharedTargetFor(this.skeleton);
		return PaperThreats.isLiveFor(this.skeleton, shared) ? shared : this.skeleton.getTarget();
	}

	private boolean reached(final GridPosition position) {
		return PaperEntityMath.distanceSquared(
			this.skeleton,
			position.x() + 0.5,
			position.y(),
			position.z() + 0.5
		) <= POSITION_REACHED_DISTANCE_SQUARED;
	}

	private int nextHiddenWait() {
		PaperCoverSettings config = this.coverSettings.get();
		int span = config.maximumHiddenTicks() - config.minimumHiddenTicks() + 1;
		return config.minimumHiddenTicks() + Math.floorMod(this.stableOrder + this.cycleSequence + this.shotsTaken, span);
	}

	private void transition(final Phase next, final long now) {
		this.phase = next;
		this.phaseStartedAt = now;
		this.nextRepathAt = now;
	}

	private void abort() {
		this.finished = true;
	}

	private Location toLocation(final GridPosition position) {
		return this.toLocation(position.x(), position.y(), position.z());
	}

	private Location toLocation(final int x, final int y, final int z) {
		return new Location(this.skeleton.getWorld(), x + 0.5, y, z + 0.5);
	}

	private static GridPosition toGrid(final Location location) {
		return new GridPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	private static Vec3d toShared(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static boolean isHazard(final Material material) {
		return material == Material.CACTUS
			|| material == Material.MAGMA_BLOCK
			|| material == Material.CAMPFIRE
			|| material == Material.SOUL_CAMPFIRE
			|| material == Material.POWDER_SNOW;
	}

	public enum Phase {
		INACTIVE,
		MOVING_TO_COVER,
		HIDDEN,
		MOVING_TO_PEEK,
		DRAWING,
		POST_SHOT,
		RETURNING_TO_COVER
	}
}

package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.RangedSpacingPlanner;
import com.wjz.mobsthinknow.shared.ai.RetreatPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * 骷髅近身紧急脱离 Goal：放下远程武器、面向逃生路径正向奔跑，抵达迟滞安全线后才恢复射击。
 */
public final class PaperSkeletonDisengageGoal implements Goal<AbstractSkeleton> {
	private static final double MINIMUM_ESCAPE_DISTANCE = 6.0;
	private static final double MAXIMUM_ESCAPE_DISTANCE = 12.0;
	private static final int PATH_FAILURE_RETRY_TICKS = 2;
	private static final double FALLBACK_FORWARD_SPEED = 0.18;
	private static final double FALLBACK_SIDE_SPEED = 0.045;

	private final AbstractSkeleton skeleton;
	private final GoalKey<AbstractSkeleton> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSkeletonProfile profile;
	private final PaperMetrics metrics;
	private final int stableSide;
	private final double distanceSample;

	private LivingEntity threat;
	private long startedAt;
	private long nextPathAt;
	private long nextAllowedAt;

	public PaperSkeletonDisengageGoal(
		final AbstractSkeleton skeleton,
		final GoalKey<AbstractSkeleton> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSkeletonProfile profile,
		final PaperMetrics metrics
	) {
		this.skeleton = skeleton;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.profile = profile;
		this.metrics = metrics;
		int hash = skeleton.getUniqueId().hashCode();
		this.stableSide = (hash & 1) == 0 ? -1 : 1;
		this.distanceSample = Integer.toUnsignedLong(hash) / (double)0xFFFFFFFFL;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		if (!enabled(config) || Bukkit.getCurrentTick() < this.nextAllowedAt || !this.holdsRangedWeapon()) {
			return false;
		}
		int iq = this.intelligence.get(this.skeleton);
		if (iq < config.skeletonSpacingMinimumIntelligence()) {
			return false;
		}
		LivingEntity target = this.skeleton.getTarget();
		if (!PaperThreats.isLiveFor(this.skeleton, target) || !RangedSpacingPlanner.shouldStartEmergencyDisengage(
			horizontalDistanceSquared(this.skeleton.getLocation(), target.getLocation()),
			config.skeletonPreferredRange(),
			iq
		)) {
			return false;
		}
		this.threat = target;
		return true;
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings config = this.settings.get();
		LivingEntity current = this.threat;
		if (!enabled(config)
			|| !this.holdsRangedWeapon()
			|| !PaperThreats.isLiveFor(this.skeleton, current)
			|| this.skeleton.getTarget() != current
			|| Bukkit.getCurrentTick() - this.startedAt >= config.skeletonDisengageMaximumTicks()) {
			return false;
		}
		return RangedSpacingPlanner.shouldContinueEmergencyDisengage(
			horizontalDistanceSquared(this.skeleton.getLocation(), current.getLocation()),
			config.skeletonPreferredRange(),
			this.intelligence.get(this.skeleton)
		);
	}

	@Override
	public void start() {
		this.startedAt = Bukkit.getCurrentTick();
		this.nextPathAt = this.startedAt;
		this.lowerWeapon();
		this.skeleton.getPathfinder().stopPathfinding();
		this.skeleton.setAggressive(false);
		this.metrics.skeletonDisengageStarted();
		this.updateEscapePath(this.startedAt);
	}

	@Override
	public void tick() {
		this.lowerWeapon();
		long now = Bukkit.getCurrentTick();
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		if (now >= this.nextPathAt || !pathfinder.hasPath()) {
			this.updateEscapePath(now);
		}
		if (pathfinder.hasPath()) {
			this.faceCurrentPath(pathfinder);
		} else {
			this.fallbackRunAway();
		}
	}

	@Override
	public void stop() {
		PaperSettings config = this.settings.get();
		LivingEntity current = this.threat;
		boolean timedOutUnsafe = current != null
			&& PaperThreats.isLiveFor(this.skeleton, current)
			&& Bukkit.getCurrentTick() - this.startedAt >= config.skeletonDisengageMaximumTicks()
			&& RangedSpacingPlanner.shouldContinueEmergencyDisengage(
				horizontalDistanceSquared(this.skeleton.getLocation(), current.getLocation()),
				config.skeletonPreferredRange(),
				this.intelligence.get(this.skeleton)
			);
		if (timedOutUnsafe) {
			this.nextAllowedAt = Bukkit.getCurrentTick() + config.skeletonDisengageCooldownTicks();
		}
		this.skeleton.getPathfinder().stopPathfinding();
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		this.threat = null;
		this.startedAt = 0L;
		this.nextPathAt = 0L;
	}

	@Override
	public GoalKey<AbstractSkeleton> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private void updateEscapePath(final long now) {
		LivingEntity current = this.threat;
		if (!PaperThreats.isLiveFor(this.skeleton, current)) {
			return;
		}
		List<Vec3d> candidates = RetreatPlanner.candidateDestinations(
			toVector(this.skeleton.getLocation()),
			toVector(current.getLocation()),
			MINIMUM_ESCAPE_DISTANCE,
			MAXIMUM_ESCAPE_DISTANCE,
			this.distanceSample,
			this.stableSide
		);
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		double speed = this.profile.escapePathSpeed(this.skeleton, this.intelligence.get(this.skeleton));
		for (Vec3d candidate : candidates) {
			Location destination = new Location(
				this.skeleton.getWorld(),
				candidate.x(),
				candidate.y(),
				candidate.z()
			);
			Pathfinder.PathResult path = pathfinder.findPath(destination);
			if (path != null && pathfinder.moveTo(path, speed)) {
				this.nextPathAt = now + RangedSpacingPlanner.pathRefreshTicks(this.intelligence.get(this.skeleton));
				this.faceCurrentPath(pathfinder);
				return;
			}
		}
		this.metrics.skeletonDisengagePathFailed();
		this.nextPathAt = now + PATH_FAILURE_RETRY_TICKS;
	}

	private void faceCurrentPath(final Pathfinder pathfinder) {
		Pathfinder.PathResult path = pathfinder.getCurrentPath();
		if (path == null || path.getNextPoint() == null) {
			return;
		}
		Location eyeLevelPoint = path.getNextPoint().clone();
		eyeLevelPoint.setY(this.skeleton.getEyeLocation().getY());
		this.skeleton.lookAt(eyeLevelPoint, 360.0F, 90.0F);
		this.skeleton.setBodyYaw(yawToward(this.skeleton.getLocation(), eyeLevelPoint));
	}

	private void fallbackRunAway() {
		LivingEntity current = this.threat;
		if (!PaperThreats.isLiveFor(this.skeleton, current)) {
			return;
		}
		Location actor = this.skeleton.getLocation();
		Location attacker = current.getLocation();
		double deltaX = actor.getX() - attacker.getX();
		double deltaZ = actor.getZ() - attacker.getZ();
		double length = Math.hypot(deltaX, deltaZ);
		if (length < 1.0E-6) {
			deltaX = this.stableSide;
			deltaZ = 0.0;
			length = 1.0;
		}
		double awayX = deltaX / length;
		double awayZ = deltaZ / length;
		Location facing = actor.clone().add(awayX * 4.0, 0.0, awayZ * 4.0);
		facing.setY(this.skeleton.getEyeLocation().getY());
		this.skeleton.lookAt(facing, 360.0F, 90.0F);
		this.skeleton.setBodyYaw(yawToward(actor, facing));
		if (this.skeleton.isOnGround()) {
			Vector velocity = this.skeleton.getVelocity();
			double sideX = -awayZ * this.stableSide;
			double sideZ = awayX * this.stableSide;
			velocity.setX(awayX * FALLBACK_FORWARD_SPEED + sideX * FALLBACK_SIDE_SPEED);
			velocity.setZ(awayZ * FALLBACK_FORWARD_SPEED + sideZ * FALLBACK_SIDE_SPEED);
			this.skeleton.setVelocity(velocity);
		}
	}

	private void lowerWeapon() {
		if (this.skeleton.hasActiveItem()) {
			this.skeleton.clearActiveItem();
		}
	}

	private boolean holdsRangedWeapon() {
		Material material = this.skeleton.getEquipment().getItemInMainHand().getType();
		return material == Material.BOW || material == Material.CROSSBOW;
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled()
			&& config.skeletonSpacingEnabled()
			&& this.skeleton.isValid()
			&& !this.skeleton.isDead();
	}

	private static double horizontalDistanceSquared(final Location first, final Location second) {
		double x = first.getX() - second.getX();
		double z = first.getZ() - second.getZ();
		return x * x + z * z;
	}

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static float yawToward(final Location from, final Location to) {
		return (float)Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
	}
}

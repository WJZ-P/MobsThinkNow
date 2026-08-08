package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperProjectileEvasionSettings;
import com.wjz.mobsthinknow.paper.ai.PaperProjectileThreatBoard.Threat;
import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner;
import com.wjz.mobsthinknow.shared.ai.ProjectileEvasionPlanner.ReactionProfile;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/** High-priority, short-lived lateral dodge driven by the centralized projectile board. */
public final class PaperSkeletonProjectileEvasionGoal implements Goal<AbstractSkeleton> {
	private static final double FALLBACK_LATERAL_SPEED = 0.28;

	private final AbstractSkeleton skeleton;
	private final GoalKey<AbstractSkeleton> key;
	private final Supplier<PaperProjectileEvasionSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperProjectileThreatBoard threats;
	private final PaperMetrics metrics;
	private final int stableSide;
	private final double durationSample;

	private Threat pendingThreat;
	private long endsAt;
	private long nextScanAt;
	private long nextAllowedAt;
	private int dodgeSide;
	private boolean usingFallbackVelocity;

	public PaperSkeletonProjectileEvasionGoal(
		final AbstractSkeleton skeleton,
		final GoalKey<AbstractSkeleton> key,
		final Supplier<PaperProjectileEvasionSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperProjectileThreatBoard threats,
		final PaperMetrics metrics
	) {
		this.skeleton = skeleton;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.threats = threats;
		this.metrics = metrics;
		int hash = skeleton.getUniqueId().hashCode();
		this.stableSide = (hash & 1) == 0 ? -1 : 1;
		this.durationSample = Integer.toUnsignedLong(Integer.rotateLeft(hash, 11)) / (double)0xFFFFFFFFL;
	}

	@Override
	public boolean shouldActivate() {
		PaperProjectileEvasionSettings config = this.settings.get();
		long now = Bukkit.getCurrentTick();
		if (!enabled(config)
			|| now < this.nextAllowedAt
			|| now < this.nextScanAt
			|| !this.skeleton.isOnGround()
			|| this.skeleton.isInsideVehicle()
			|| !this.holdsRangedWeapon()) {
			return false;
		}
		int iq = this.intelligence.get(this.skeleton);
		if (iq < config.minimumIntelligence()) {
			return false;
		}

		ReactionProfile reaction = ProjectileEvasionPlanner.reactionProfile(iq);
		this.nextScanAt = now + reaction.scanIntervalTicks();
		Optional<Threat> threat = this.threats.nearestIncoming(
			this.skeleton,
			reaction,
			config.scanRadius(),
			config.maximumCandidateChecks()
		);
		this.pendingThreat = threat.orElse(null);
		return this.pendingThreat != null;
	}

	@Override
	public boolean shouldStayActive() {
		return enabled(this.settings.get())
			&& Bukkit.getCurrentTick() < this.endsAt;
	}

	@Override
	public void start() {
		Threat threat = this.pendingThreat;
		this.pendingThreat = null;
		if (threat == null) {
			this.endsAt = Bukkit.getCurrentTick();
			return;
		}

		int iq = this.intelligence.get(this.skeleton);
		ReactionProfile reaction = ProjectileEvasionPlanner.reactionProfile(iq);
		PaperProjectileEvasionSettings config = this.settings.get();
		double combatYaw = this.combatYaw();
		Location projectile = threat.projectile().getLocation();
		Vector velocity = threat.projectile().getVelocity();
		Vector center = this.skeleton.getBoundingBox().getCenter();
		this.dodgeSide = ProjectileEvasionPlanner.saferSide(
			center.getX(),
			center.getZ(),
			projectile.getX(),
			projectile.getZ(),
			velocity.getX(),
			velocity.getZ(),
			threat.closestApproachTicks(),
			combatYaw,
			this.stableSide
		);
		this.endsAt = Bukkit.getCurrentTick() + ProjectileEvasionPlanner.dodgeTicks(reaction, this.durationSample);
		this.skeleton.getPathfinder().stopPathfinding();
		this.lowerWeapon();
		this.skeleton.setAggressive(false);
		this.usingFallbackVelocity = !this.startLateralPath(combatYaw, this.dodgeSide, config)
			&& !this.startLateralPath(combatYaw, -this.dodgeSide, config);
		if (this.usingFallbackVelocity) {
			this.metrics.skeletonProjectileDodgePathFailed();
			this.applyFallbackVelocity(combatYaw);
		}
		this.metrics.skeletonProjectileDodgeStarted();
	}

	@Override
	public void tick() {
		this.faceCombatTarget();
		if (this.usingFallbackVelocity && this.skeleton.isOnGround()) {
			this.applyFallbackVelocity(this.combatYaw());
		}
	}

	@Override
	public void stop() {
		this.skeleton.getPathfinder().stopPathfinding();
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		this.nextAllowedAt = Bukkit.getCurrentTick() + this.settings.get().cooldownTicks();
		this.endsAt = 0L;
		this.usingFallbackVelocity = false;
		this.pendingThreat = null;
	}

	@Override
	public GoalKey<AbstractSkeleton> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private boolean startLateralPath(
		final double combatYaw,
		final int side,
		final PaperProjectileEvasionSettings config
	) {
		double yaw = Math.toRadians(combatYaw);
		Location destination = this.skeleton.getLocation().add(
			Math.cos(yaw) * side * config.dodgeDistance(),
			0.0,
			Math.sin(yaw) * side * config.dodgeDistance()
		);
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		if (path == null || !pathfinder.moveTo(path, config.movementSpeed())) {
			return false;
		}
		this.dodgeSide = side;
		return true;
	}

	private void applyFallbackVelocity(final double combatYaw) {
		double yaw = Math.toRadians(combatYaw);
		Vector current = this.skeleton.getVelocity();
		current.setX(Math.cos(yaw) * this.dodgeSide * FALLBACK_LATERAL_SPEED);
		current.setZ(Math.sin(yaw) * this.dodgeSide * FALLBACK_LATERAL_SPEED);
		this.skeleton.setVelocity(current);
	}

	private void faceCombatTarget() {
		LivingEntity target = this.skeleton.getTarget();
		if (!PaperThreats.isLiveFor(this.skeleton, target)) {
			return;
		}
		this.skeleton.lookAt(target, 360.0F, 90.0F);
		this.skeleton.setBodyYaw((float)this.combatYaw());
	}

	private double combatYaw() {
		LivingEntity target = this.skeleton.getTarget();
		if (!PaperThreats.isLiveFor(this.skeleton, target)) {
			return this.skeleton.getBodyYaw();
		}
		Location from = this.skeleton.getLocation();
		Location to = target.getLocation();
		return Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
	}

	private void lowerWeapon() {
		if (this.skeleton.hasActiveItem()) {
			this.skeleton.clearActiveItem();
		}
	}

	private boolean holdsRangedWeapon() {
		Material held = this.skeleton.getEquipment().getItemInMainHand().getType();
		return held == Material.BOW || held == Material.CROSSBOW;
	}

	private boolean enabled(final PaperProjectileEvasionSettings config) {
		return config.enabled() && this.skeleton.isValid() && !this.skeleton.isDead();
	}
}

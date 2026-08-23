package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.shared.ai.CreeperTacticalPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * 可移动、可中止并参与爆点预约的引信 Goal。外部打火石强制点燃永不被插件退火。
 */
public final class PaperCreeperFuseGoal implements Goal<Creeper> {
	private static final double QUEUE_STAGING_SPEED = 1.12;

	private final Creeper creeper;
	private final GoalKey<Creeper> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperCreeperFeintMemory feintMemory;
	private final PaperBlastReservationBoard reservations;
	private final PaperSquadCoordinator squads;
	private final PaperMetrics metrics;
	private final int stableSide;

	private LivingEntity target;
	private boolean externallyIgnited;
	private boolean pluginIgnited;
	private boolean reservationHeld;
	private boolean waitingForReservation;
	private boolean abortRequested;
	private long nextRepathAt;

	public PaperCreeperFuseGoal(
		final Creeper creeper,
		final GoalKey<Creeper> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperCreeperFeintMemory feintMemory,
		final PaperBlastReservationBoard reservations,
		final PaperSquadCoordinator squads,
		final PaperMetrics metrics
	) {
		this.creeper = creeper;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.feintMemory = feintMemory;
		this.reservations = reservations;
		this.squads = squads;
		this.metrics = metrics;
		this.stableSide = (creeper.getUniqueId().hashCode() & 1) == 0 ? -1 : 1;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		if (!enabled(config)
			|| this.feintMemory.blocksCombatGoals(this.creeper)
			|| this.delegatedToSquad(this.creeper.isIgnited() && !this.creeper.isInsideVehicle())
			|| this.intelligence.get(this.creeper) < config.creeperMinimumIntelligence()) {
			return false;
		}
		LivingEntity current = this.creeper.getTarget();
		if (!PaperThreats.isLiveFor(this.creeper, current)) {
			return this.creeper.isIgnited();
		}
		return this.creeper.isIgnited() || this.shouldStartFuse(current, config);
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings config = this.settings.get();
		if (!enabled(config)
			|| this.feintMemory.blocksCombatGoals(this.creeper)
			|| this.delegatedToSquad(this.externallyIgnited)
			|| this.abortRequested
			|| !this.creeper.isValid()
			|| this.creeper.isDead()) {
			return false;
		}
		if (this.externallyIgnited && this.creeper.isIgnited()) {
			return true;
		}
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.creeper, current)) {
			return false;
		}
		if (this.creeper.isIgnited()) {
			return true;
		}
		if (!this.waitingForReservation) {
			return this.shouldStartFuse(current, config);
		}
		int iq = this.intelligence.get(this.creeper);
		double startDistance = CreeperTacticalPlanner.fuseStartDistance(
			config.creeperMaximumFuseStartDistance(),
			iq,
			this.creeper.isPowered(),
			PaperDifficultyAdapter.fromBukkit(this.creeper.getWorld().getDifficulty())
		);
		return CreeperTacticalPlanner.shouldContinueFuse(
			this.creeper.getLocation().distanceSquared(current.getLocation()),
			startDistance,
			this.creeper.hasLineOfSight(current),
			false,
			0.0,
			iq
		);
	}

	@Override
	public void start() {
		this.target = this.creeper.getTarget();
		this.externallyIgnited = this.creeper.isIgnited();
		this.pluginIgnited = false;
		this.reservationHeld = false;
		this.waitingForReservation = false;
		this.abortRequested = false;
		this.nextRepathAt = Bukkit.getCurrentTick();
		this.creeper.getPathfinder().stopPathfinding();
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.creeper, current)) {
			if (!this.externallyIgnited) {
				this.abortFuse();
			}
			return;
		}

		PaperSettings config = this.settings.get();
		int iq = this.intelligence.get(this.creeper);
		double progress = this.fuseProgress();
		Vec3d predicted = CreeperTacticalPlanner.fuseDestination(
			toVector(current.getLocation()),
			toVector(current.getVelocity()),
			progress,
			iq
		);
		Location predictedCenter = toLocation(predicted);
		long now = Bukkit.getCurrentTick();
		long predictedDetonation = now + Math.max(1, this.creeper.getMaxFuseTicks() - this.creeper.getFuseTicks());
		boolean reserved = this.reservations.tryReserve(
			this.creeper,
			current,
			predictedCenter,
			predictedDetonation,
			this.externallyIgnited
		);
		if (!reserved) {
			if (!this.waitingForReservation) {
				this.waitingForReservation = true;
				this.metrics.creeperQueueWait();
			}
			this.reservationHeld = false;
			if (!this.externallyIgnited) {
				this.coolFuseDown();
			}
			this.moveTo(
				this.reservations.stagingPoint(this.creeper, current, this.stableSide),
				QUEUE_STAGING_SPEED
			);
			return;
		}

		this.reservationHeld = true;
		this.waitingForReservation = false;
		if (!this.creeper.isIgnited()) {
			this.creeper.setIgnited(true);
			if (!this.creeper.isIgnited()) {
				return;
			}
			this.feintMemory.transferToRealFuse(this.creeper);
			this.pluginIgnited = true;
			this.creeper.getWorld().playSound(
				this.creeper.getLocation(),
				Sound.ENTITY_CREEPER_PRIMED,
				SoundCategory.HOSTILE,
				1.0F,
				1.0F
			);
			this.metrics.creeperFuseStarted();
		}

		boolean visible = this.creeper.hasLineOfSight(current);
		double startDistance = CreeperTacticalPlanner.fuseStartDistance(
			config.creeperMaximumFuseStartDistance(),
			iq,
			this.creeper.isPowered(),
			PaperDifficultyAdapter.fromBukkit(this.creeper.getWorld().getDifficulty())
		);
		boolean committed = this.externallyIgnited || CreeperTacticalPlanner.shouldContinueFuse(
			this.creeper.getLocation().distanceSquared(current.getLocation()),
			startDistance,
			visible,
			false,
			progress,
			iq
		);
		if (!committed) {
			this.abortFuse();
			this.metrics.creeperFuseAborted();
			return;
		}

		this.creeper.lookAt(current, 40.0F, 35.0F);
		if (this.creeper.isInsideVehicle() || !config.creeperMovingFuseEnabled()) {
			this.creeper.getPathfinder().stopPathfinding();
			return;
		}
		if (now < this.nextRepathAt && this.creeper.getPathfinder().hasPath()) {
			return;
		}
		double speed = CreeperTacticalPlanner.movingFuseSpeed(
			config.creeperMaximumFuseMovementSpeed(),
			iq,
			PaperDifficultyAdapter.fromBukkit(this.creeper.getWorld().getDifficulty())
		);
		if (this.moveTo(predictedCenter, speed)) {
			this.metrics.creeperMovingFusePath();
		}
		this.nextRepathAt = now + CreeperTacticalPlanner.repathTicks(iq);
	}

	@Override
	public void stop() {
		if (this.pluginIgnited && !this.externallyIgnited
			&& (this.abortRequested || !PaperThreats.isLiveFor(this.creeper, this.target) || !enabled(this.settings.get()))) {
			this.coolFuseDown();
		}
		this.creeper.getPathfinder().stopPathfinding();
		if (this.reservationHeld) {
			this.reservations.release(this.creeper);
		}
		this.target = null;
		this.externallyIgnited = false;
		this.pluginIgnited = false;
		this.reservationHeld = false;
		this.waitingForReservation = false;
		this.abortRequested = false;
		this.nextRepathAt = 0L;
	}

	@Override
	public GoalKey<Creeper> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private boolean shouldStartFuse(final LivingEntity current, final PaperSettings config) {
		int iq = this.intelligence.get(this.creeper);
		boolean visible = this.creeper.hasLineOfSight(current);
		if (!visible) {
			return false;
		}
		boolean watching = CreeperTacticalPlanner.isTargetWatching(
			toVector(current.getLocation().getDirection()),
			toVector(this.creeper.getLocation()).subtract(toVector(current.getLocation()))
		);
		boolean blocking = current instanceof Player player && player.isBlocking();
		double startDistance = CreeperTacticalPlanner.fuseStartDistance(
			config.creeperMaximumFuseStartDistance(),
			iq,
			this.creeper.isPowered(),
			PaperDifficultyAdapter.fromBukkit(this.creeper.getWorld().getDifficulty())
		);
		return CreeperTacticalPlanner.shouldStartFuse(
			this.creeper.getLocation().distanceSquared(current.getLocation()),
			startDistance,
			true,
			false,
			watching,
			blocking,
			iq
		);
	}

	private boolean moveTo(final Location destination, final double speed) {
		Pathfinder pathfinder = this.creeper.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		return path != null && pathfinder.moveTo(path, speed);
	}

	private void abortFuse() {
		this.abortRequested = true;
		this.coolFuseDown();
		this.reservations.release(this.creeper);
		this.reservationHeld = false;
		this.creeper.getPathfinder().stopPathfinding();
	}

	private void coolFuseDown() {
		if (this.creeper.isIgnited() && !this.externallyIgnited) {
			this.creeper.setIgnited(false);
		}
		this.creeper.setFuseTicks(Math.max(0, this.creeper.getFuseTicks() - 2));
	}

	private double fuseProgress() {
		return this.creeper.getFuseTicks() / (double)Math.max(1, this.creeper.getMaxFuseTicks());
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled() && config.creeperTacticsEnabled();
	}

	private boolean delegatedToSquad(final boolean preserveExternalIgnition) {
		if (preserveExternalIgnition) {
			return false;
		}
		return this.squads.isHoldingForOrders(this.creeper)
			|| this.squads.isAssignedTransportPayload(this.creeper);
	}

	private Location toLocation(final Vec3d vector) {
		return new Location(this.creeper.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toVector(final org.bukkit.util.Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}
}

package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.CreeperTacticalPlanner;
import com.wjz.mobsthinknow.shared.ai.CreeperTacticalPlanner.ApproachMode;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Ocelot;
import org.bukkit.entity.Player;

/** 预测移动目标并在被观察/举盾时选择稳定侧翼点的 Paper 接敌 Goal。 */
public final class PaperCreeperApproachGoal implements Goal<Creeper> {
	private static final int CAT_CHECK_INTERVAL_TICKS = 10;
	private static final double CAT_AVOID_HORIZONTAL = 6.0;
	private static final double CAT_AVOID_VERTICAL = 4.0;

	private final Creeper creeper;
	private final GoalKey<Creeper> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperCreeperFeintMemory feintMemory;
	private final PaperBlastReservationBoard reservations;
	private final PaperMetrics metrics;
	private final int stableSide;

	private long nextRepathAt;
	private long nextCatCheckAt;
	private boolean catNearby;
	private ApproachMode lastMode = ApproachMode.DIRECT;

	public PaperCreeperApproachGoal(
		final Creeper creeper,
		final GoalKey<Creeper> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperCreeperFeintMemory feintMemory,
		final PaperBlastReservationBoard reservations,
		final PaperMetrics metrics
	) {
		this.creeper = creeper;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.feintMemory = feintMemory;
		this.reservations = reservations;
		this.metrics = metrics;
		this.stableSide = (creeper.getUniqueId().hashCode() & 1) == 0 ? -1 : 1;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		return enabled(config)
			&& this.intelligence.get(this.creeper) >= config.creeperMinimumIntelligence()
			&& !this.creeper.isInsideVehicle()
			&& !this.creeper.isIgnited()
			&& !this.feintMemory.isActive(this.creeper)
			&& PaperThreats.isLiveFor(this.creeper, this.creeper.getTarget())
			&& !this.hasNearbyCat();
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings config = this.settings.get();
		return enabled(config)
			&& !this.creeper.isInsideVehicle()
			&& !this.creeper.isIgnited()
			&& !this.feintMemory.isActive(this.creeper)
			&& PaperThreats.isLiveFor(this.creeper, this.creeper.getTarget())
			&& !this.hasNearbyCat();
	}

	@Override
	public void start() {
		this.nextRepathAt = Bukkit.getCurrentTick();
		this.lastMode = ApproachMode.DIRECT;
		this.creeper.setAggressive(true);
	}

	@Override
	public void tick() {
		LivingEntity target = this.creeper.getTarget();
		if (!PaperThreats.isLiveFor(this.creeper, target)) {
			return;
		}
		this.creeper.lookAt(target, 35.0F, 30.0F);
		long now = Bukkit.getCurrentTick();
		if (now < this.nextRepathAt && this.creeper.getPathfinder().hasPath()) {
			return;
		}

		PaperSettings config = this.settings.get();
		int iq = this.intelligence.get(this.creeper);
		boolean visible = this.creeper.hasLineOfSight(target);
		boolean watching = visible && CreeperTacticalPlanner.isTargetWatching(
			toVector(target.getLocation().getDirection()),
			toVector(this.creeper.getLocation()).subtract(toVector(target.getLocation()))
		);
		boolean blocking = target instanceof Player player && player.isBlocking();
		ApproachMode mode = CreeperTacticalPlanner.chooseApproach(
			iq,
			watching,
			blocking,
			visible,
			this.creeper.getLocation().distanceSquared(target.getLocation()),
			config.creeperFlankingEnabled(),
			this.stableSide
		);

		Vec3d targetPosition = toVector(target.getLocation());
		Vec3d targetVelocity = toVector(target.getVelocity());
		Vec3d targetLook = toVector(target.getLocation().getDirection());
		Vec3d predictedCenter = CreeperTacticalPlanner.fuseDestination(
			targetPosition,
			targetVelocity,
			0.0,
			iq
		);
		Location blastCenter = toLocation(predictedCenter);
		long detonationTick = now + Math.max(1, this.creeper.getMaxFuseTicks());
		Location destination;
		if (this.reservations.availability(this.creeper, blastCenter, detonationTick)
			!= PaperBlastReservationBoard.Availability.AVAILABLE) {
			destination = this.reservations.stagingPoint(this.creeper, target, this.stableSide);
			mode = this.stableSide < 0 ? ApproachMode.FLANK_LEFT : ApproachMode.FLANK_RIGHT;
			this.metrics.creeperQueueWait();
		} else {
			destination = toLocation(CreeperTacticalPlanner.approachDestination(
				mode,
				targetPosition,
				targetVelocity,
				targetLook,
				iq
			));
		}

		double speed = CreeperTacticalPlanner.approachSpeed(
			iq,
			PaperDifficultyAdapter.fromBukkit(this.creeper.getWorld().getDifficulty())
		);
		Pathfinder pathfinder = this.creeper.getPathfinder();
		boolean moving = moveTo(pathfinder, destination, speed);
		if (!moving && mode.isFlanking()) {
			mode = ApproachMode.INTERCEPT;
			destination = toLocation(CreeperTacticalPlanner.approachDestination(
				mode,
				targetPosition,
				targetVelocity,
				targetLook,
				iq
			));
			moving = moveTo(pathfinder, destination, speed);
		}
		if (!moving) {
			mode = ApproachMode.DIRECT;
			moveTo(pathfinder, target.getLocation(), speed);
		}
		this.countModeTransition(mode);
		this.nextRepathAt = now + CreeperTacticalPlanner.repathTicks(iq);
	}

	@Override
	public void stop() {
		this.creeper.getPathfinder().stopPathfinding();
		this.creeper.setAggressive(false);
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

	private void countModeTransition(final ApproachMode mode) {
		if (mode == this.lastMode) {
			return;
		}
		this.lastMode = mode;
		if (mode.isFlanking()) {
			this.metrics.creeperFlankStarted();
		} else if (mode == ApproachMode.INTERCEPT) {
			this.metrics.creeperInterceptStarted();
		}
	}

	private boolean hasNearbyCat() {
		long now = Bukkit.getCurrentTick();
		if (now < this.nextCatCheckAt) {
			return this.catNearby;
		}
		this.nextCatCheckAt = now + CAT_CHECK_INTERVAL_TICKS;
		this.catNearby = this.creeper.getNearbyEntities(
			CAT_AVOID_HORIZONTAL,
			CAT_AVOID_VERTICAL,
			CAT_AVOID_HORIZONTAL
		).stream().anyMatch(entity -> entity instanceof Cat || entity instanceof Ocelot);
		return this.catNearby;
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled()
			&& config.creeperTacticsEnabled()
			&& this.creeper.isValid()
			&& !this.creeper.isDead();
	}

	private Location toLocation(final Vec3d vector) {
		return new Location(this.creeper.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static boolean moveTo(final Pathfinder pathfinder, final Location destination, final double speed) {
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		return path != null && pathfinder.moveTo(path, speed);
	}

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toVector(final org.bukkit.util.Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}
}

package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.shared.ai.SpiderTacticalPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Spider;
import org.bukkit.util.Vector;

/** 预测安全落点并按目标 O(1) 租约错峰起跳的蜘蛛跳扑 Goal。 */
public final class PaperSpiderPounceGoal implements Goal<Spider> {
	private static final int WAIT_REPATH_TICKS = 5;

	private final Spider spider;
	private final GoalKey<Spider> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperPounceCoordinator coordinator;
	private final PaperSquadCoordinator squadCoordinator;
	private final PaperMetrics metrics;
	private final int stableSide;

	private LivingEntity target;
	private long startedAt;
	private long nextPounceAt;
	private long nextWaitPathAt;
	private boolean launched;
	private boolean becameAirborne;
	private boolean abortRequested;

	public PaperSpiderPounceGoal(
		final Spider spider,
		final GoalKey<Spider> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperPounceCoordinator coordinator,
		final PaperSquadCoordinator squadCoordinator,
		final PaperMetrics metrics
	) {
		this.spider = spider;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.coordinator = coordinator;
		this.squadCoordinator = squadCoordinator;
		this.metrics = metrics;
		this.stableSide = (spider.getUniqueId().hashCode() & 1) == 0 ? -1 : 1;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		LivingEntity current = this.spider.getTarget();
		int iq = this.intelligence.get(this.spider);
		return enabled(config)
			&& !this.squadCoordinator.isHoldingForOrders(this.spider)
			&& config.spiderPredictivePounceEnabled()
			&& iq >= Math.max(4, config.spiderMinimumIntelligence())
			&& Bukkit.getCurrentTick() >= this.nextPounceAt
			&& this.spider.isEmpty()
			&& !this.spider.isInsideVehicle()
			&& PaperThreats.isLiveFor(this.spider, current)
			&& SpiderTacticalPlanner.canPredictivePounce(
				iq,
				this.spider.hasLineOfSight(current),
				this.spider.isOnGround(),
				PaperEntityMath.distanceSquared(this.spider, current)
			);
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings config = this.settings.get();
		long elapsed = Bukkit.getCurrentTick() - this.startedAt;
		if (!enabled(config)
			|| this.abortRequested
			|| !PaperThreats.isLiveFor(this.spider, this.target)
			|| elapsed >= config.spiderPounceMaximumAirTicks()) {
			return false;
		}
		if (this.launched) {
			return !this.becameAirborne || !this.spider.isOnGround();
		}
		return this.spider.isOnGround()
			&& elapsed < config.spiderPounceLeaseTicks()
			&& PaperEntityMath.distanceSquared(this.spider, this.target) <= 64.0;
	}

	@Override
	public void start() {
		this.target = this.spider.getTarget();
		this.startedAt = Bukkit.getCurrentTick();
		this.nextWaitPathAt = this.startedAt;
		this.launched = false;
		this.becameAirborne = false;
		this.abortRequested = false;
		this.spider.getPathfinder().stopPathfinding();
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.spider, current)) {
			this.abortRequested = true;
			return;
		}
		this.spider.lookAt(current, 55.0F, 45.0F);
		if (this.launched) {
			if (!this.spider.isOnGround()) {
				this.becameAirborne = true;
			}
			return;
		}

		if (!this.coordinator.tryAcquire(this.spider, current)) {
			this.metrics.spiderPounceWait();
			this.orbitWhileWaiting(current);
			return;
		}

		int iq = this.intelligence.get(this.spider);
		Vec3d predicted = SpiderTacticalPlanner.predictedPounceLanding(
			toVector(current.getLocation()),
			toVector(current.getVelocity()),
			iq
		);
		Location safeLanding = PaperLandingSafety.findSafeLanding(this.spider, predicted);
		if (safeLanding == null) {
			this.metrics.spiderUnsafeLandingRejected();
			this.coordinator.release(this.spider, false);
			this.abortRequested = true;
			this.nextPounceAt = Bukkit.getCurrentTick() + 10L;
			return;
		}

		Vec3d velocity = SpiderTacticalPlanner.pounceVelocity(
			toVector(this.spider.getLocation()),
			toVector(this.spider.getVelocity()),
			toVector(safeLanding),
			Vec3d.ZERO,
			iq,
			PaperDifficultyAdapter.fromBukkit(this.spider.getWorld().getDifficulty())
		);
		this.spider.getPathfinder().stopPathfinding();
		this.spider.setVelocity(toVector(velocity));
		this.launched = true;
		this.nextPounceAt = Bukkit.getCurrentTick() + SpiderTacticalPlanner.pounceCooldownTicks(
			iq,
			ThreadLocalRandom.current().nextDouble()
		);
		this.metrics.spiderPounceStarted();
	}

	@Override
	public void stop() {
		boolean completed = this.launched && this.becameAirborne && this.spider.isOnGround();
		this.coordinator.release(this.spider, completed);
		this.spider.getPathfinder().stopPathfinding();
		this.target = null;
		this.startedAt = 0L;
		this.nextWaitPathAt = 0L;
		this.launched = false;
		this.becameAirborne = false;
		this.abortRequested = false;
	}

	@Override
	public GoalKey<Spider> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP);
	}

	private void orbitWhileWaiting(final LivingEntity current) {
		long now = Bukkit.getCurrentTick();
		if (now < this.nextWaitPathAt && this.spider.getPathfinder().hasPath()) {
			return;
		}
		SpiderTacticalPlanner.ApproachMode mode = this.stableSide < 0
			? SpiderTacticalPlanner.ApproachMode.FLANK_LEFT
			: SpiderTacticalPlanner.ApproachMode.FLANK_RIGHT;
		Vec3d point = SpiderTacticalPlanner.approachDestination(
			mode,
			toVector(current.getLocation()),
			toVector(current.getVelocity()),
			toVector(current.getLocation().getDirection()),
			this.intelligence.get(this.spider)
		);
		Pathfinder pathfinder = this.spider.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(toLocation(point));
		if (path != null) {
			pathfinder.moveTo(path, 1.08);
		}
		this.nextWaitPathAt = now + WAIT_REPATH_TICKS;
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled() && config.spiderTacticsEnabled() && this.spider.isValid() && !this.spider.isDead();
	}

	private Location toLocation(final Vec3d vector) {
		return new Location(this.spider.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toVector(final Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	private static Vector toVector(final Vec3d vector) {
		return new Vector(vector.x(), vector.y(), vector.z());
	}
}

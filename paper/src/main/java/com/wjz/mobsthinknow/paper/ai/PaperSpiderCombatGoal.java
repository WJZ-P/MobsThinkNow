package com.wjz.mobsthinknow.paper.ai;

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
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;

/** 蜘蛛预测截击、受观察绕侧和命中后拉开的常规近战 Goal。 */
public final class PaperSpiderCombatGoal implements Goal<Spider> {
	private final Spider spider;
	private final GoalKey<Spider> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperMetrics metrics;
	private final PaperSquadCoordinator squadCoordinator;
	private final int stableSide;

	private long nextRepathAt;
	private long nextAttackAt;
	private int repositionTicks;
	private SpiderTacticalPlanner.ApproachMode lastMode = SpiderTacticalPlanner.ApproachMode.DIRECT;

	public PaperSpiderCombatGoal(
		final Spider spider,
		final GoalKey<Spider> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squadCoordinator,
		final PaperMetrics metrics
	) {
		this.spider = spider;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.squadCoordinator = squadCoordinator;
		this.metrics = metrics;
		this.stableSide = (spider.getUniqueId().hashCode() & 1) == 0 ? -1 : 1;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		return enabled(config)
			&& !this.squadCoordinator.isHoldingForOrders(this.spider)
			&& this.intelligence.get(this.spider) >= config.spiderMinimumIntelligence()
			&& this.spider.isEmpty()
			&& !this.spider.isInsideVehicle()
			&& PaperThreats.isLiveFor(this.spider, this.spider.getTarget());
	}

	@Override
	public boolean shouldStayActive() {
		return this.shouldActivate();
	}

	@Override
	public void start() {
		this.nextRepathAt = Bukkit.getCurrentTick();
		this.nextAttackAt = 0L;
		this.repositionTicks = 0;
		this.lastMode = SpiderTacticalPlanner.ApproachMode.DIRECT;
		this.spider.setAggressive(true);
	}

	@Override
	public void tick() {
		LivingEntity target = this.spider.getTarget();
		if (!PaperThreats.isLiveFor(this.spider, target)) {
			return;
		}
		long now = Bukkit.getCurrentTick();
		this.repositionTicks = Math.max(0, this.repositionTicks - 1);
		this.spider.lookAt(target, 45.0F, 40.0F);
		if (now >= this.nextAttackAt && this.inMeleeRange(target) && this.spider.hasLineOfSight(target)) {
			this.spider.swingMainHand();
			this.spider.attack(target);
			this.nextAttackAt = now + 20L;
			if (this.settings.get().spiderHitAndRunEnabled() && this.intelligence.get(this.spider) >= 5) {
				this.repositionTicks = SpiderTacticalPlanner.repositionTicks(this.intelligence.get(this.spider));
				this.metrics.spiderHitAndRunStarted();
			}
		}
		if (!this.spider.isOnGround() || (now < this.nextRepathAt && this.spider.getPathfinder().hasPath())) {
			return;
		}

		int iq = this.intelligence.get(this.spider);
		boolean visible = this.spider.hasLineOfSight(target);
		double yaw = Math.toRadians(target.getYaw());
		double horizontalScale = Math.cos(Math.toRadians(target.getPitch()));
		double lookX = -horizontalScale * Math.sin(yaw);
		double lookZ = horizontalScale * Math.cos(yaw);
		boolean watching = visible && SpiderTacticalPlanner.isTargetWatching(
			lookX,
			lookZ,
			this.spider.getX() - target.getX(),
			this.spider.getZ() - target.getZ()
		);
		boolean blocking = target instanceof Player player && player.isBlocking();
		SpiderTacticalPlanner.ApproachMode mode = SpiderTacticalPlanner.chooseApproach(
			iq,
			watching,
			blocking,
			visible,
			this.repositionTicks,
			this.stableSide
		);
		Vec3d targetPosition = new Vec3d(target.getX(), target.getY(), target.getZ());
		Vec3d targetVelocity = mode == SpiderTacticalPlanner.ApproachMode.INTERCEPT
			? toVector(target.getVelocity())
			: Vec3d.ZERO;
		Vec3d targetLook = mode.isFlank() || mode.isReposition()
			? new Vec3d(lookX, 0.0, lookZ)
			: Vec3d.ZERO;
		Vec3d destination = SpiderTacticalPlanner.approachDestination(
			mode,
			targetPosition,
			targetVelocity,
			targetLook,
			iq
		);
		double speed = SpiderTacticalPlanner.approachSpeed(
			iq,
			PaperDifficultyAdapter.fromBukkit(this.spider.getWorld().getDifficulty())
		);
		Pathfinder pathfinder = this.spider.getPathfinder();
		if (!moveTo(pathfinder, toLocation(destination), speed) && mode != SpiderTacticalPlanner.ApproachMode.DIRECT) {
			mode = SpiderTacticalPlanner.ApproachMode.DIRECT;
			pathfinder.moveTo(target, speed);
		}
		if (mode != this.lastMode) {
			this.lastMode = mode;
			if (mode.isFlank()) {
				this.metrics.spiderFlankStarted();
			}
		}
		this.nextRepathAt = now + SpiderTacticalPlanner.repathTicks(iq);
	}

	@Override
	public void stop() {
		this.spider.getPathfinder().stopPathfinding();
		this.spider.setAggressive(false);
		this.repositionTicks = 0;
	}

	@Override
	public GoalKey<Spider> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private boolean inMeleeRange(final LivingEntity target) {
		double reach = this.spider.getWidth() * 0.5 + target.getWidth() * 0.5 + 0.9;
		double x = this.spider.getX() - target.getX();
		double y = this.spider.getY() - target.getY();
		double z = this.spider.getZ() - target.getZ();
		return x * x + y * y + z * z <= reach * reach;
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled() && config.spiderTacticsEnabled() && this.spider.isValid() && !this.spider.isDead();
	}

	private Location toLocation(final Vec3d vector) {
		return new Location(this.spider.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static boolean moveTo(final Pathfinder pathfinder, final Location destination, final double speed) {
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		return path != null && pathfinder.moveTo(path, speed);
	}

	private static Vec3d toVector(final org.bukkit.util.Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}
}

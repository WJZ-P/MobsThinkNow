package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.ai.CreeperTacticalPlanner;
import com.wjz.mobsthinknow.shared.ai.RetreatPlanner;
import com.wjz.mobsthinknow.shared.ai.SpiderTacticalPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Spider;
import org.bukkit.util.Vector;

/**
 * Paper 公共 API 版蜘蛛—苦力怕空投：按小队配对会合、让苦力怕跳上背部、投送、提前释放并撤离。
 */
public final class PaperMountedBreachGoal implements Goal<Spider> {
	private static final double BOARDING_TRIGGER_DISTANCE_SQUARED = 2.8 * 2.8;
	private static final double BOARDING_CATCH_DISTANCE_SQUARED = 3.2 * 3.2;
	private static final double MAXIMUM_ASSEMBLY_SEPARATION_SQUARED = 32.0 * 32.0;
	private static final int MINIMUM_BOARDING_TICKS = 3;
	private static final int MAXIMUM_BOARDING_TICKS = 9;
	private static final int BOARDING_RETRY_TICKS = 8;
	private static final int ASSEMBLY_REPATH_TICKS = 5;
	private static final int EVASION_TICKS = 30;

	private final Spider spider;
	private final GoalKey<Spider> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperBlastReservationBoard blastReservations;
	private final PaperMetrics metrics;
	private final int stableSide;
	private final double speedSample;

	private Creeper payload;
	private LivingEntity target;
	private long startedAt;
	private long nextRepathAt;
	private long nextBoardingAt;
	private long nextAllowedAt;
	private int boardingTicks;
	private int evasionTicks;
	private boolean boarding;
	private boolean payloadReleased;
	private boolean abortRequested;
	private boolean waitingForBlastWindow;
	private boolean reservationHeld;
	private boolean pluginIgnited;

	public PaperMountedBreachGoal(
		final Spider spider,
		final GoalKey<Spider> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperBlastReservationBoard blastReservations,
		final PaperMetrics metrics
	) {
		this.spider = spider;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.squads = squads;
		this.blastReservations = blastReservations;
		this.metrics = metrics;
		int hash = spider.getUniqueId().hashCode();
		this.stableSide = (hash & 1) == 0 ? -1 : 1;
		this.speedSample = Integer.toUnsignedLong(hash) / (double)0xFFFFFFFFL;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		if (!enabled(config) || Bukkit.getCurrentTick() < this.nextAllowedAt || this.spider.isInsideVehicle()) {
			return false;
		}
		PaperSquadDirective directive = this.squads.directiveFor(this.spider);
		if (!isActiveTransportOrder(directive)) {
			return false;
		}
		Creeper assigned = this.squads.assignedTransportPartnerFor(this.spider);
		LivingEntity selectedTarget = this.squads.sharedTargetFor(this.spider);
		if (!PaperThreats.isLiveFor(this.spider, selectedTarget)
			|| !this.isAvailable(assigned)
			|| (!this.spider.getPassengers().isEmpty() && assigned.getVehicle() != this.spider)) {
			return false;
		}
		this.payload = assigned;
		this.target = selectedTarget;
		return true;
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings config = this.settings.get();
		if (!enabled(config) || this.abortRequested || !this.spider.isValid() || this.spider.isDead()) {
			return false;
		}
		if (this.payloadReleased) {
			return this.evasionTicks > 0 && PaperThreats.isLiveFor(this.spider, this.target);
		}
		PaperSquadDirective directive = this.squads.directiveFor(this.spider);
		Creeper current = this.payload;
		if (!isActiveTransportOrder(directive)
			|| current == null
			|| !current.isValid()
			|| current.isDead()
			|| this.squads.assignedTransportPartnerFor(this.spider) != current
			|| !PaperThreats.isLiveFor(this.spider, this.target)) {
			return false;
		}
		if (current.getVehicle() == this.spider) {
			return true;
		}
		return !current.isInsideVehicle()
			&& !current.isIgnited()
			&& Bukkit.getCurrentTick() - this.startedAt < config.spiderAssemblyTimeoutTicks()
			&& current.getWorld() == this.spider.getWorld()
			&& PaperEntityMath.distanceSquared(current, this.spider) <= MAXIMUM_ASSEMBLY_SEPARATION_SQUARED;
	}

	@Override
	public void start() {
		this.startedAt = Bukkit.getCurrentTick();
		this.nextRepathAt = this.startedAt;
		this.nextBoardingAt = this.startedAt;
		this.boardingTicks = 0;
		this.evasionTicks = 0;
		this.boarding = false;
		this.payloadReleased = false;
		this.abortRequested = false;
		this.waitingForBlastWindow = false;
		this.reservationHeld = false;
		this.pluginIgnited = false;
		this.spider.setAggressive(true);
		this.metrics.mountedBreachAssemblyStarted();
	}

	@Override
	public void tick() {
		if (this.payloadReleased) {
			this.tickEvasion();
			return;
		}
		Creeper current = this.payload;
		LivingEntity currentTarget = this.target;
		if (current == null || !current.isValid() || current.isDead()
			|| !PaperThreats.isLiveFor(this.spider, currentTarget)) {
			this.abortRequested = true;
			return;
		}
		this.spider.setTarget(currentTarget);
		current.setTarget(currentTarget);
		if (current.getVehicle() == this.spider) {
			this.tickDelivery(current, currentTarget);
		} else {
			this.tickAssembly(current);
		}
	}

	@Override
	public void stop() {
		Creeper current = this.payload;
		if (!this.payloadReleased && current != null && current.getVehicle() == this.spider) {
			current.leaveVehicle();
		}
		if (this.pluginIgnited && !this.payloadReleased && current != null && current.isValid()) {
			current.setIgnited(false);
			current.setFuseTicks(0);
			this.metrics.creeperFuseAborted();
		}
		if (this.reservationHeld && current != null && !current.isIgnited()) {
			this.blastReservations.release(current);
		}
		this.spider.getPathfinder().stopPathfinding();
		this.spider.setAggressive(false);
		if (this.abortRequested) {
			this.metrics.mountedBreachAborted();
		}
		if (this.startedAt != 0L) {
			this.nextAllowedAt = Bukkit.getCurrentTick() + this.settings.get().spiderRemountCooldownTicks();
		}
		this.payload = null;
		this.target = null;
		this.startedAt = 0L;
		this.nextRepathAt = 0L;
		this.nextBoardingAt = 0L;
		this.boardingTicks = 0;
		this.evasionTicks = 0;
		this.boarding = false;
		this.payloadReleased = false;
		this.abortRequested = false;
		this.waitingForBlastWindow = false;
		this.reservationHeld = false;
		this.pluginIgnited = false;
	}

	@Override
	public GoalKey<Spider> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP);
	}

	private void tickAssembly(final Creeper current) {
		long now = Bukkit.getCurrentTick();
		if (now - this.startedAt >= this.settings.get().spiderAssemblyTimeoutTicks()) {
			this.abortRequested = true;
			return;
		}
		this.spider.lookAt(current, 55.0F, 45.0F);
		if (this.boarding) {
			this.boardingTicks++;
			this.spider.getPathfinder().stopPathfinding();
			current.getPathfinder().stopPathfinding();
			this.steerBoardingLeap(current);
			if (this.boardingTicks >= MINIMUM_BOARDING_TICKS
				&& PaperEntityMath.distanceSquared(this.spider, current) <= BOARDING_CATCH_DISTANCE_SQUARED) {
				this.completeBoarding(current);
			} else if (this.boardingTicks >= MAXIMUM_BOARDING_TICKS) {
				this.boarding = false;
				this.boardingTicks = 0;
				this.nextBoardingAt = now + BOARDING_RETRY_TICKS;
			}
			return;
		}

		double distanceSquared = PaperEntityMath.distanceSquared(this.spider, current);
		if (now >= this.nextBoardingAt && distanceSquared <= BOARDING_TRIGGER_DISTANCE_SQUARED) {
			this.beginBoardingLeap(current);
			return;
		}
		if (now >= this.nextRepathAt || !this.spider.getPathfinder().hasPath()) {
			if (!moveTo(this.spider.getPathfinder(), current.getLocation(), 1.20)) {
				this.metrics.mountedBreachPathFailed();
			}
			this.nextRepathAt = now + ASSEMBLY_REPATH_TICKS;
		}
	}

	private void beginBoardingLeap(final Creeper current) {
		this.boarding = true;
		this.boardingTicks = 0;
		this.spider.getPathfinder().stopPathfinding();
		current.getPathfinder().stopPathfinding();
		current.lookAt(this.spider, 70.0F, 55.0F);
		current.setVelocity(toVector(SpiderTacticalPlanner.boardingLeapVelocity(
			toVector(current.getLocation()),
			toVector(this.spider.getLocation())
		)));
		current.getWorld().playSound(
			current.getLocation(),
			Sound.ENTITY_SPIDER_STEP,
			SoundCategory.HOSTILE,
			0.45F,
			1.35F
		);
		current.getWorld().spawnParticle(Particle.CLOUD, current.getLocation(), 4, 0.18, 0.04, 0.18, 0.01);
		this.metrics.mountedBreachBoardingLeap();
	}

	private void steerBoardingLeap(final Creeper current) {
		Vec3d desired = SpiderTacticalPlanner.boardingLeapVelocity(
			toVector(current.getLocation()),
			toVector(this.spider.getLocation())
		);
		Vector movement = current.getVelocity();
		movement.setX(movement.getX() * 0.70 + desired.x() * 0.30);
		movement.setZ(movement.getZ() * 0.70 + desired.z() * 0.30);
		current.setVelocity(movement);
	}

	private void completeBoarding(final Creeper current) {
		if (!this.spider.addPassenger(current)) {
			this.boarding = false;
			this.boardingTicks = 0;
			this.nextBoardingAt = Bukkit.getCurrentTick() + BOARDING_RETRY_TICKS;
			return;
		}
		this.boarding = false;
		this.boardingTicks = 0;
		this.spider.getPathfinder().stopPathfinding();
		current.getPathfinder().stopPathfinding();
		this.spider.getWorld().playSound(
			this.spider.getLocation(),
			Sound.ENTITY_SPIDER_STEP,
			SoundCategory.HOSTILE,
			0.9F,
			0.75F
		);
		this.spider.getWorld().spawnParticle(
			Particle.POOF,
			this.spider.getLocation().add(0.0, this.spider.getHeight(), 0.0),
			8,
			0.35,
			0.25,
			0.35,
			0.02
		);
		this.metrics.mountedBreachMounted();
	}

	private void tickDelivery(final Creeper current, final LivingEntity currentTarget) {
		this.spider.lookAt(currentTarget, 55.0F, 45.0F);
		current.lookAt(currentTarget, 70.0F, 60.0F);
		if (this.prepareFuse(current, currentTarget)) {
			return;
		}
		double fuseProgress = current.getFuseTicks() / (double)Math.max(1, current.getMaxFuseTicks());
		if (current.isIgnited() && fuseProgress >= this.settings.get().spiderPayloadReleaseProgress()) {
			this.releasePayload(current, currentTarget);
			return;
		}

		long now = Bukkit.getCurrentTick();
		if (now < this.nextRepathAt && this.spider.getPathfinder().hasPath()) {
			return;
		}
		int combinedIntelligence = Math.max(this.intelligence.get(this.spider), this.intelligence.get(current));
		Vec3d destination = SpiderTacticalPlanner.carrierDestination(
			toVector(currentTarget.getLocation()),
			toVector(currentTarget.getVelocity()),
			combinedIntelligence
		);
		double randomizedMaximum = SpiderTacticalPlanner.randomizedCarrierMaximum(
			this.settings.get().spiderMaximumCarrierSpeed(),
			this.speedSample
		);
		double speed = SpiderTacticalPlanner.carrierSpeed(
			randomizedMaximum,
			combinedIntelligence,
			PaperDifficultyAdapter.fromBukkit(this.spider.getWorld().getDifficulty())
		);
		if (!moveTo(this.spider.getPathfinder(), toLocation(destination), speed)
			&& !moveTo(this.spider.getPathfinder(), currentTarget.getLocation(), speed)) {
			this.metrics.mountedBreachPathFailed();
		}
		this.nextRepathAt = now + SpiderTacticalPlanner.repathTicks(combinedIntelligence);
	}

	/** 返回 true 表示爆点被占用，本 tick 已改走候场点。 */
	private boolean prepareFuse(final Creeper current, final LivingEntity currentTarget) {
		PaperSettings config = this.settings.get();
		int iq = this.intelligence.get(current);
		double progress = current.getFuseTicks() / (double)Math.max(1, current.getMaxFuseTicks());
		Vec3d predicted = CreeperTacticalPlanner.fuseDestination(
			toVector(currentTarget.getLocation()),
			toVector(currentTarget.getVelocity()),
			progress,
			iq
		);
		Location predictedCenter = toLocation(predicted);
		long now = Bukkit.getCurrentTick();
		long detonationTick = now + Math.max(1, current.getMaxFuseTicks() - current.getFuseTicks());
		if (current.isIgnited()) {
			this.reservationHeld = this.blastReservations.tryReserve(
				current,
				currentTarget,
				predictedCenter,
				detonationTick,
				true
			);
			this.waitingForBlastWindow = false;
			return false;
		}

		double startDistance = CreeperTacticalPlanner.fuseStartDistance(
			config.creeperMaximumFuseStartDistance(),
			iq,
			current.isPowered(),
			PaperDifficultyAdapter.fromBukkit(current.getWorld().getDifficulty())
		);
		if (!this.spider.hasLineOfSight(currentTarget)
			|| PaperEntityMath.distanceSquared(this.spider, currentTarget) > startDistance * startDistance) {
			return false;
		}
		if (!this.blastReservations.tryReserve(
			current,
			currentTarget,
			predictedCenter,
			detonationTick,
			false
		)) {
			if (!this.waitingForBlastWindow) {
				this.waitingForBlastWindow = true;
				this.metrics.creeperQueueWait();
			}
			if (now >= this.nextRepathAt || !this.spider.getPathfinder().hasPath()) {
				moveTo(
					this.spider.getPathfinder(),
					this.blastReservations.stagingPoint(current, currentTarget, this.stableSide),
					1.10
				);
				this.nextRepathAt = now + ASSEMBLY_REPATH_TICKS;
			}
			return true;
		}

		this.waitingForBlastWindow = false;
		this.reservationHeld = true;
		this.pluginIgnited = true;
		current.setIgnited(true);
		current.getWorld().playSound(
			current.getLocation(),
			Sound.ENTITY_CREEPER_PRIMED,
			SoundCategory.HOSTILE,
			1.0F,
			1.0F
		);
		this.metrics.creeperFuseStarted();
		return false;
	}

	private void releasePayload(final Creeper current, final LivingEntity currentTarget) {
		current.leaveVehicle();
		Vec3d toward = toVector(currentTarget.getLocation())
			.subtract(toVector(current.getLocation()))
			.horizontalUnitOr(new Vec3d(this.stableSide, 0.0, 0.0));
		current.setVelocity(new Vector(toward.x() * 0.34, 0.24, toward.z() * 0.34));
		current.lookAt(currentTarget, 90.0F, 80.0F);
		current.getWorld().spawnParticle(Particle.POOF, current.getLocation(), 10, 0.30, 0.22, 0.30, 0.03);
		this.payloadReleased = true;
		this.evasionTicks = EVASION_TICKS;
		this.nextRepathAt = Bukkit.getCurrentTick();
		this.metrics.mountedBreachPayloadReleased();
	}

	private void tickEvasion() {
		LivingEntity currentTarget = this.target;
		if (this.evasionTicks-- <= 0 || !PaperThreats.isLiveFor(this.spider, currentTarget)) {
			return;
		}
		long now = Bukkit.getCurrentTick();
		if (now < this.nextRepathAt && this.spider.getPathfinder().hasPath()) {
			return;
		}
		List<Vec3d> candidates = RetreatPlanner.candidateDestinations(
			toVector(this.spider.getLocation()),
			toVector(currentTarget.getLocation()),
			6.0,
			9.0,
			this.speedSample,
			this.stableSide
		);
		boolean moving = false;
		for (Vec3d candidate : candidates) {
			if (moveTo(this.spider.getPathfinder(), toLocation(candidate), 1.25)) {
				moving = true;
				break;
			}
		}
		if (!moving) {
			this.metrics.mountedBreachPathFailed();
		}
		this.nextRepathAt = now + ASSEMBLY_REPATH_TICKS;
	}

	private boolean isAvailable(final Creeper assigned) {
		if (assigned == null || !assigned.isValid() || assigned.isDead() || assigned.getWorld() != this.spider.getWorld()) {
			return false;
		}
		if (assigned.getVehicle() == this.spider) {
			return true;
		}
		return !assigned.isInsideVehicle()
			&& !assigned.isIgnited()
			&& PaperEntityMath.distanceSquared(assigned, this.spider) <= MAXIMUM_ASSEMBLY_SEPARATION_SQUARED;
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled()
			&& config.spiderTacticsEnabled()
			&& config.spiderMountedBreachEnabled()
			&& this.spider.isValid()
			&& !this.spider.isDead();
	}

	private static boolean isActiveTransportOrder(final PaperSquadDirective directive) {
		return directive != null
			&& directive.state() == MixedSquadState.ENGAGING
			&& directive.plan().usesCarrier();
	}

	private Location toLocation(final Vec3d vector) {
		return new Location(this.spider.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static boolean moveTo(final Pathfinder pathfinder, final Location destination, final double speed) {
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		return path != null && pathfinder.moveTo(path, speed);
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

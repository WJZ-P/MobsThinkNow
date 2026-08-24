package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.PaperCrossbowSettings;
import com.wjz.mobsthinknow.paper.PaperFireworkSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.ai.CrossbowCombatPlanner;
import com.wjz.mobsthinknow.shared.ai.FiringLanePlanner;
import com.wjz.mobsthinknow.shared.ai.SquadVolleyPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadRole;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.util.Vector;

/**
 * 交叉火力阶段的 Paper 射手 Goal：到达左右射位、检查队友胶囊、错峰蓄力并通过公开 rangedAttack 开火。
 */
public final class PaperSquadRangedGoal implements Goal<AbstractSkeleton> {
	private static final double POSITION_REACHED_DISTANCE_SQUARED = 2.0 * 2.0;
	private static final int REPATH_TICKS = 5;
	private static final int LANE_CACHE_TICKS = 2;
	private static final int POSITION_FALLBACK_TICKS = 24;

	private final AbstractSkeleton skeleton;
	private final GoalKey<AbstractSkeleton> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperFireworkBoltService fireworkBolts;
	private final PaperMetrics metrics;
	private final int stableOrder;

	private long nextReleaseAt;
	private long nextRepathAt;
	private long nextLaneCheckAt;
	private long positionFallbackAt;
	private boolean charging;
	private CrossbowPhase crossbowPhase = CrossbowPhase.IDLE;
	private long crossbowPhaseStartedAt;
	private int crossbowAimDelay;
	private long shotSequence;
	private CrossbowPayload crossbowPayload = CrossbowPayload.ARROW;
	private FiringLanePlanner.Result<UUID> cachedLane = new FiringLanePlanner.Result<>(true, null, 0);
	private UUID lastBlocker;

	public PaperSquadRangedGoal(
		final AbstractSkeleton skeleton,
		final GoalKey<AbstractSkeleton> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperFireworkBoltService fireworkBolts,
		final PaperMetrics metrics
	) {
		this.skeleton = skeleton;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.squads = squads;
		this.fireworkBolts = fireworkBolts;
		this.metrics = metrics;
		this.stableOrder = skeleton.getUniqueId().hashCode() & Integer.MAX_VALUE;
	}

	@Override
	public boolean shouldActivate() {
		return this.isEligible();
	}

	@Override
	public boolean shouldStayActive() {
		return this.isEligible();
	}

	@Override
	public void start() {
		long now = Bukkit.getCurrentTick();
		PaperSquadDirective directive = this.squads.directiveFor(this.skeleton);
		int interval = SquadVolleyPlanner.shotIntervalTicks(
			this.settings.get().skeletonCoordinatedFireMinimumShotIntervalTicks(),
			this.intelligence.get(this.skeleton)
		);
		this.nextReleaseAt = SquadVolleyPlanner.nextReleaseTick(
			now,
			directive == null ? MixedSquadRole.RANGED_LEFT : directive.role(),
			this.stableOrder,
			interval
		);
		this.nextRepathAt = now;
		this.nextLaneCheckAt = now;
		this.positionFallbackAt = now + POSITION_FALLBACK_TICKS;
		this.cachedLane = new FiringLanePlanner.Result<>(true, null, 0);
		this.lastBlocker = null;
		this.charging = false;
		this.crossbowPhase = CrossbowPhase.IDLE;
		this.crossbowPhaseStartedAt = 0L;
		this.crossbowPayload = CrossbowPayload.ARROW;
		this.prepareNextCrossbowCycle(now, directive);
		this.skeleton.setAggressive(true);
	}

	@Override
	public void tick() {
		PaperSquadDirective directive = this.squads.directiveFor(this.skeleton);
		LivingEntity target = this.currentTarget();
		if (!PaperThreats.isLiveFor(this.skeleton, target)) {
			return;
		}
		this.skeleton.lookAt(target, 50.0F, 45.0F);
		PaperSettings config = this.settings.get();
		double distanceSquared = distanceSquared(this.skeleton, target);
		boolean visible = this.skeleton.hasLineOfSight(target);
		if (!visible || distanceSquared > config.skeletonCoordinatedFireMaximumRange()
			* config.skeletonCoordinatedFireMaximumRange()) {
			this.cancelCharge();
			if (directive == null) {
				this.moveTo(target, 1.08, Bukkit.getCurrentTick());
			} else {
				this.moveToDirective(directive);
			}
			return;
		}

		long now = Bukkit.getCurrentTick();
		FiringLanePlanner.Result<UUID> lane = this.laneTo(target, now, false);
		if (!lane.clear()) {
			this.handleBlockedLane(directive, target, lane, now);
			return;
		}
		this.lastBlocker = null;
		Vec3d assigned = directive == null ? null : directive.destination();
		if (assigned != null
			&& distanceSquared(this.skeleton, assigned) > POSITION_REACHED_DISTANCE_SQUARED
			&& now < this.positionFallbackAt) {
			this.cancelCharge();
			this.moveTo(assigned, 1.08, now);
			return;
		}
		this.skeleton.getPathfinder().stopPathfinding();

		int iq = this.intelligence.get(this.skeleton);
		if (this.holdsCrossbow()) {
			this.tickCrossbow(target, directive, now, iq);
			return;
		}
		int chargeTicks = SquadVolleyPlanner.chargeTicks(config.skeletonCoordinatedFireChargeTicks(), iq);
		if (now >= this.nextReleaseAt - chargeTicks && !this.charging) {
			this.skeleton.startUsingItem(EquipmentSlot.HAND);
			this.charging = true;
		}
		if (now < this.nextReleaseAt) {
			return;
		}

		FiringLanePlanner.Result<UUID> releaseLane = this.laneTo(target, now, true);
		if (!releaseLane.clear() || !this.skeleton.hasLineOfSight(target)) {
			this.handleBlockedLane(directive, target, releaseLane, now);
			return;
		}
		this.cancelCharge();
		this.skeleton.rangedAttack(target, 1.0F);
		this.metrics.coordinatedShot();
		int interval = SquadVolleyPlanner.shotIntervalTicks(
			config.skeletonCoordinatedFireMinimumShotIntervalTicks(),
			iq
		);
		this.nextReleaseAt = SquadVolleyPlanner.nextReleaseTick(
			now + 1L,
			directive == null ? MixedSquadRole.RANGED_LEFT : directive.role(),
			this.stableOrder,
			interval
		);
	}

	@Override
	public void stop() {
		this.cancelCharge();
		this.skeleton.getPathfinder().stopPathfinding();
		this.skeleton.setAggressive(this.skeleton.getTarget() != null);
		this.nextReleaseAt = 0L;
		this.nextRepathAt = 0L;
		this.nextLaneCheckAt = 0L;
		this.positionFallbackAt = 0L;
		this.crossbowPhase = CrossbowPhase.IDLE;
		this.crossbowPhaseStartedAt = 0L;
		this.crossbowPayload = CrossbowPayload.ARROW;
		this.lastBlocker = null;
	}

	@Override
	public GoalKey<AbstractSkeleton> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private boolean isEligible() {
		PaperSettings config = this.settings.get();
		PaperSquadDirective directive = this.squads.directiveFor(this.skeleton);
		boolean crossbowSpecialist = this.holdsCrossbow()
			&& config.skeletonCrossbowTactics().enabled()
			&& this.intelligence.get(this.skeleton) >= config.skeletonCrossbowTactics().minimumIntelligence()
			&& (directive == null || directive.state() == MixedSquadState.ENGAGING);
		boolean coordinatedArcher = config.skeletonCoordinatedFireEnabled()
			&& this.intelligence.get(this.skeleton) >= config.skeletonCoordinatedFireMinimumIntelligence()
			&& directive != null
			&& directive.state() == MixedSquadState.ENGAGING
			&& directive.plan().usesCrossfire()
			&& directive.role().isRanged();
		return config.enabled()
			&& this.skeleton.isValid()
			&& !this.skeleton.isDead()
			&& this.holdsRangedWeapon()
			&& (crossbowSpecialist || coordinatedArcher)
			&& PaperThreats.isLiveFor(this.skeleton, this.currentTarget());
	}

	private LivingEntity currentTarget() {
		LivingEntity shared = this.squads.sharedTargetFor(this.skeleton);
		return PaperThreats.isLiveFor(this.skeleton, shared) ? shared : this.skeleton.getTarget();
	}

	private FiringLanePlanner.Result<UUID> laneTo(
		final LivingEntity target,
		final long now,
		final boolean forceRefresh
	) {
		if (!forceRefresh && now < this.nextLaneCheckAt) {
			return this.cachedLane;
		}
		PaperSettings config = this.settings.get();
		List<FiringLanePlanner.Ally<UUID>> allies = new ArrayList<>();
		for (Mob ally : this.squads.squadmatesFor(this.skeleton)) {
			double radius = Math.max(config.skeletonFriendlyLaneRadius(), ally.getWidth() * 0.65);
			allies.add(new FiringLanePlanner.Ally<>(
				ally.getUniqueId(),
				ally.getX(),
				ally.getY() + ally.getHeight() * 0.55,
				ally.getZ(),
				radius
			));
		}
		this.cachedLane = FiringLanePlanner.check(
			eyePosition(this.skeleton),
			this.firingEndpoint(target),
			allies,
			config.skeletonFriendlyLaneMaximumChecks()
		);
		this.nextLaneCheckAt = now + LANE_CACHE_TICKS;
		return this.cachedLane;
	}

	private Vec3d firingEndpoint(final LivingEntity target) {
		if (!this.holdsCrossbow()) {
			return eyePosition(target);
		}
		PaperCrossbowSettings config = this.settings.get().skeletonCrossbowTactics();
		boolean firework = this.crossbowPayload == CrossbowPayload.FIREWORK;
		return CrossbowCombatPlanner.intercept(
			eyePosition(this.skeleton),
			eyePosition(target),
			velocityOf(target),
			firework ? config.firework().projectileSpeed() : config.projectileSpeed(),
			firework ? 0.0 : config.gravityPerTickSquared(),
			config.maximumLeadTicks()
		).aimPoint();
	}

	private void handleBlockedLane(
		final PaperSquadDirective directive,
		final LivingEntity target,
		final FiringLanePlanner.Result<UUID> lane,
		final long now
	) {
		this.cancelCharge();
		if (lane.blocker() != null && !lane.blocker().equals(this.lastBlocker)) {
			this.metrics.friendlyLaneBlocked();
			this.lastBlocker = lane.blocker();
		}
		if (now < this.nextRepathAt && this.skeleton.getPathfinder().hasPath()) {
			return;
		}
		int side = directive == null
			? ((this.stableOrder & 1) == 0 ? -1 : 1)
			: directive.role() == MixedSquadRole.RANGED_LEFT ? -1 : 1;
		Vec3d reposition = FiringLanePlanner.lateralReposition(
			positionOf(this.skeleton),
			positionOf(target),
			side,
			this.settings.get().skeletonLaneRepositionDistance()
		);
		if (this.moveTo(reposition, 1.12, now)) {
			this.metrics.firingLaneReposition();
		} else {
			this.nextRepathAt = now;
			if (directive == null) {
				this.moveTo(target, 1.08, now);
			} else {
				this.moveTo(directive.destination(), 1.08, now);
			}
		}
		int interval = SquadVolleyPlanner.shotIntervalTicks(
			this.settings.get().skeletonCoordinatedFireMinimumShotIntervalTicks(),
			this.intelligence.get(this.skeleton)
		);
		this.nextReleaseAt = SquadVolleyPlanner.nextReleaseTick(
			now + 4L,
			directive == null ? MixedSquadRole.RANGED_LEFT : directive.role(),
			this.stableOrder,
			interval
		);
	}

	private void moveToDirective(final PaperSquadDirective directive) {
		this.moveTo(directive.destination(), 1.10, Bukkit.getCurrentTick());
	}

	private boolean moveTo(final Entity destination, final double speed, final long now) {
		if (now < this.nextRepathAt) {
			return this.skeleton.getPathfinder().hasPath();
		}
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		boolean moving = pathfinder.moveTo(destination, speed);
		this.finishRepath(now, moving);
		return moving;
	}

	private boolean moveTo(final Vec3d destination, final double speed, final long now) {
		if (now < this.nextRepathAt) {
			return this.skeleton.getPathfinder().hasPath();
		}
		Pathfinder pathfinder = this.skeleton.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(toLocation(this.skeleton, destination));
		boolean moving = path != null && pathfinder.moveTo(path, speed);
		this.finishRepath(now, moving);
		return moving;
	}

	private void finishRepath(final long now, final boolean moving) {
		this.nextRepathAt = now + REPATH_TICKS;
		if (!moving) {
			this.metrics.firingLanePathFailed();
		}
	}

	private void cancelCharge() {
		if (this.skeleton.hasActiveItem()) {
			this.skeleton.clearActiveItem();
		}
		this.charging = false;
		if (this.crossbowPhase != CrossbowPhase.IDLE) {
			this.setCrossbowLoaded(false, CrossbowPayload.ARROW);
			this.crossbowPhase = CrossbowPhase.IDLE;
			this.crossbowPhaseStartedAt = 0L;
			this.crossbowPayload = CrossbowPayload.ARROW;
		}
	}

	private boolean holdsRangedWeapon() {
		Material material = this.skeleton.getEquipment().getItemInMainHand().getType();
		return material == Material.BOW || material == Material.CROSSBOW;
	}

	private boolean holdsCrossbow() {
		return this.skeleton.getEquipment().getItemInMainHand().getType() == Material.CROSSBOW;
	}

	private void tickCrossbow(
		final LivingEntity target,
		final PaperSquadDirective directive,
		final long now,
		final int intelligence
	) {
		PaperCrossbowSettings config = this.settings.get().skeletonCrossbowTactics();
		int chargeTicks = CrossbowCombatPlanner.chargeTicks(config.chargeTicks(), intelligence);
		switch (this.crossbowPhase) {
			case IDLE -> {
				if (now < this.nextReleaseAt - chargeTicks - this.crossbowAimDelay) {
					return;
				}
				this.crossbowPayload = this.chooseCrossbowPayload(target, config);
				this.setCrossbowLoaded(false, this.crossbowPayload);
				this.skeleton.startUsingItem(EquipmentSlot.HAND);
				this.skeleton.getWorld().playSound(
					this.skeleton.getLocation(),
					Sound.ITEM_CROSSBOW_LOADING_START,
					0.8F,
					1.0F
				);
				this.crossbowPhase = CrossbowPhase.CHARGING;
				this.crossbowPhaseStartedAt = now;
				this.metrics.crossbowChargeStarted();
			}
			case CHARGING -> {
				if (this.skeleton.hasActiveItem()) {
					this.metrics.crossbowChargePoseTick();
				}
				if (now - this.crossbowPhaseStartedAt < chargeTicks) {
					return;
				}
				if (this.skeleton.hasActiveItem()) {
					this.skeleton.clearActiveItem();
				}
				this.setCrossbowLoaded(true, this.crossbowPayload);
				this.skeleton.getWorld().playSound(
					this.skeleton.getLocation(),
					Sound.ITEM_CROSSBOW_LOADING_END,
					0.8F,
					1.0F
				);
				this.crossbowPhase = CrossbowPhase.AIMING;
				this.crossbowPhaseStartedAt = now;
			}
			case AIMING -> {
				if (now < this.nextReleaseAt || now - this.crossbowPhaseStartedAt < this.crossbowAimDelay) {
					return;
				}
				if (this.crossbowPayload == CrossbowPayload.FIREWORK
					&& !this.fireworkSafe(target, config)) {
					this.crossbowPayload = CrossbowPayload.ARROW;
					this.setCrossbowLoaded(true, this.crossbowPayload);
				}
				this.launchCrossbowPayload(target, config);
				this.setCrossbowLoaded(false, CrossbowPayload.ARROW);
				this.crossbowPhase = CrossbowPhase.IDLE;
				this.crossbowPhaseStartedAt = 0L;
				this.metrics.crossbowShot();
				this.metrics.coordinatedShot();
				this.shotSequence++;
				this.prepareNextCrossbowCycle(now + 1L, directive);
			}
		}
	}

	private void prepareNextCrossbowCycle(final long now, final PaperSquadDirective directive) {
		if (!this.holdsCrossbow()) {
			return;
		}
		PaperSettings settings = this.settings.get();
		PaperCrossbowSettings crossbow = settings.skeletonCrossbowTactics();
		this.crossbowAimDelay = CrossbowCombatPlanner.aimDelayTicks(
			crossbow.minimumAimTicks(),
			crossbow.maximumAimTicks(),
			this.intelligence.get(this.skeleton),
			this.stableOrder,
			this.shotSequence
		);
		int interval = SquadVolleyPlanner.shotIntervalTicks(
			settings.skeletonCoordinatedFireMinimumShotIntervalTicks(),
			this.intelligence.get(this.skeleton)
		);
		int preparation = CrossbowCombatPlanner.chargeTicks(
			crossbow.chargeTicks(),
			this.intelligence.get(this.skeleton)
		) + this.crossbowAimDelay;
		this.nextReleaseAt = SquadVolleyPlanner.nextReleaseTick(
			now + preparation,
			directive == null ? MixedSquadRole.RANGED_LEFT : directive.role(),
			this.stableOrder,
			interval
		);
	}

	private void launchCrossbowPayload(final LivingEntity target, final PaperCrossbowSettings config) {
		PaperFireworkSettings firework = config.firework();
		double speed = this.crossbowPayload == CrossbowPayload.FIREWORK
			? firework.projectileSpeed()
			: config.projectileSpeed();
		double gravity = this.crossbowPayload == CrossbowPayload.FIREWORK
			? 0.0
			: config.gravityPerTickSquared();
		CrossbowCombatPlanner.AimSolution aim = CrossbowCombatPlanner.intercept(
			eyePosition(this.skeleton),
			eyePosition(target),
			velocityOf(target),
			speed,
			gravity,
			config.maximumLeadTicks()
		);
		Vector direction = new Vector(aim.direction().x(), aim.direction().y(), aim.direction().z());
		if (this.crossbowPayload == CrossbowPayload.FIREWORK
			&& this.fireworkBolts.launch(this.skeleton, target, direction)) {
			this.consumeFirework(firework);
			this.playCrossbowShotSound();
			return;
		}
		this.launchArrow(direction, config);
		this.playCrossbowShotSound();
	}

	private void launchArrow(final Vector direction, final PaperCrossbowSettings config) {
		Arrow arrow = this.skeleton.getWorld().spawnArrow(
			this.skeleton.getEyeLocation(),
			direction,
			(float)config.projectileSpeed(),
			(float)config.projectileSpread()
		);
		arrow.setShooter(this.skeleton);
		arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
		arrow.setWeapon(this.skeleton.getEquipment().getItemInMainHand().clone());
	}

	private void playCrossbowShotSound() {
		this.skeleton.getWorld().playSound(
			this.skeleton.getLocation(),
			Sound.ITEM_CROSSBOW_SHOOT,
			1.0F,
			0.95F + (this.stableOrder % 7) * 0.015F
		);
	}

	private CrossbowPayload chooseCrossbowPayload(
		final LivingEntity target,
		final PaperCrossbowSettings crossbow
	) {
		PaperFireworkSettings config = crossbow.firework();
		ItemStack ammunition = this.skeleton.getEquipment().getItemInOffHand();
		return config.enabled()
			&& this.intelligence.get(this.skeleton) >= config.minimumIntelligence()
			&& ammunition.getType() == Material.FIREWORK_ROCKET
			&& ammunition.getAmount() > 0
			&& this.fireworkSafe(target, crossbow)
			? CrossbowPayload.FIREWORK
			: CrossbowPayload.ARROW;
	}

	private boolean fireworkSafe(final LivingEntity target, final PaperCrossbowSettings crossbow) {
		PaperFireworkSettings config = crossbow.firework();
		List<CrossbowCombatPlanner.BlastAlly<UUID>> allies = new ArrayList<>();
		for (Mob ally : this.squads.squadmatesFor(this.skeleton)) {
			allies.add(new CrossbowCombatPlanner.BlastAlly<>(
				ally.getUniqueId(),
				ally.getX(),
				ally.getY() + ally.getHeight() * 0.5,
				ally.getZ(),
				ally.getWidth() * 0.65
			));
		}
		Vec3d shooter = eyePosition(this.skeleton);
		Vec3d targetCenter = eyePosition(target);
		Vec3d predictedImpact = CrossbowCombatPlanner.intercept(
			shooter,
			targetCenter,
			velocityOf(target),
			config.projectileSpeed(),
			0.0,
			crossbow.maximumLeadTicks()
		).aimPoint();
		return CrossbowCombatPlanner.assessBlast(
			shooter,
			predictedImpact,
			allies,
			config.minimumRange(),
			config.maximumRange(),
			config.allyDangerRadius(),
			config.maximumAllyChecks()
		).clear();
	}

	private void consumeFirework(final PaperFireworkSettings config) {
		if (!config.consumeAmmunition()) {
			return;
		}
		ItemStack ammunition = this.skeleton.getEquipment().getItemInOffHand();
		if (ammunition.getType() != Material.FIREWORK_ROCKET || ammunition.getAmount() <= 0) {
			return;
		}
		ammunition.setAmount(ammunition.getAmount() - 1);
		this.skeleton.getEquipment().setItemInOffHand(ammunition);
	}

	private void setCrossbowLoaded(final boolean loaded, final CrossbowPayload payload) {
		ItemStack weapon = this.skeleton.getEquipment().getItemInMainHand();
		if (!(weapon.getItemMeta() instanceof CrossbowMeta meta)) {
			return;
		}
		meta.setChargedProjectiles(loaded ? List.of(new ItemStack(payload.material())) : List.of());
		weapon.setItemMeta(meta);
		this.skeleton.getEquipment().setItemInMainHand(weapon);
	}

	private static Location toLocation(final Mob mob, final Vec3d vector) {
		return new Location(mob.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static Vec3d positionOf(final Entity entity) {
		return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
	}

	private static Vec3d eyePosition(final LivingEntity entity) {
		return new Vec3d(entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
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

	private static Vec3d toVector(final Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	private static Vec3d velocityOf(final Entity entity) {
		return toVector(entity.getVelocity());
	}

	private enum CrossbowPhase {
		IDLE,
		CHARGING,
		AIMING
	}

	private enum CrossbowPayload {
		ARROW(Material.ARROW),
		FIREWORK(Material.FIREWORK_ROCKET);

		private final Material material;

		CrossbowPayload(final Material material) {
			this.material = material;
		}

		public Material material() {
			return this.material;
		}
	}
}

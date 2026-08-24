package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.PaperWebTrapSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.shared.ai.CreeperTacticalPlanner;
import com.wjz.mobsthinknow.shared.ai.SpiderWebTrapPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Spider;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** A short, visible wind-up that places one bounded, temporary web at a predicted target lane. */
public final class PaperSpiderWebTrapGoal implements Goal<Spider> {
	private static final int WINDUP_SOUND_TICK = 3;
	private static final int SILK_RELEASE_TICK = 8;
	private static final int ACTION_FINISH_TICK = 12;
	private static final int[] VERTICAL_SEARCH_ORDER = {0, -1, 1, -2};
	private static final double MAXIMUM_PLACEMENT_DISTANCE_SQUARED = 10.0 * 10.0;
	private static final double MINIMUM_TARGET_CLEARANCE_SQUARED = 0.65 * 0.65;

	private final Spider spider;
	private final GoalKey<Spider> key;
	private final Supplier<PaperSettings> settings;
	private final Supplier<PaperWebTrapSettings> webSettings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperCreeperFeintMemory feints;
	private final PaperWebTrapService traps;
	private final PaperMetrics metrics;
	private final int stableSide;
	private final List<Mob> squadmateBuffer = new ArrayList<>();

	private LivingEntity target;
	private Block plannedBlock;
	private long nextTrapAt;
	private int actionTicks;
	private UUID plannedCreeperId;
	private UUID lastSupportedCreeperId;
	private boolean placed;
	private boolean blastContainment;
	private boolean abortRequested;

	public PaperSpiderWebTrapGoal(
		final Spider spider,
		final GoalKey<Spider> key,
		final Supplier<PaperSettings> settings,
		final Supplier<PaperWebTrapSettings> webSettings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperCreeperFeintMemory feints,
		final PaperWebTrapService traps,
		final PaperMetrics metrics
	) {
		this.spider = spider;
		this.key = key;
		this.settings = settings;
		this.webSettings = webSettings;
		this.intelligence = intelligence;
		this.squads = squads;
		this.feints = feints;
		this.traps = traps;
		this.metrics = metrics;
		this.stableSide = (spider.getUniqueId().getMostSignificantBits() & 1L) == 0L ? -1 : 1;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings root = this.settings.get();
		PaperWebTrapSettings config = this.webSettings.get();
		LivingEntity current = this.currentTarget();
		int iq = this.intelligence.get(this.spider);
		if (!this.enabled(root, config)
			|| this.squads.isHoldingForOrders(this.spider)
			|| this.spider.isInsideVehicle()
			|| !PaperThreats.isLiveFor(this.spider, current)
			|| !SpiderWebTrapPlanner.canPlan(
				iq,
				this.spider.hasLineOfSight(current),
				this.spider.isOnGround(),
				!this.spider.getPassengers().isEmpty(),
				PaperEntityMath.distanceSquared(this.spider, current)
			)) {
			return false;
		}

		BlastThreat blast = config.blastContainmentEnabled() ? this.activeBlastFor(current) : null;
		UUID activeCreeperId = blast == null ? null : blast.creeper().getUniqueId();
		boolean newContainment = activeCreeperId != null && !activeCreeperId.equals(this.lastSupportedCreeperId);
		long now = Bukkit.getCurrentTick();
		if (!SpiderWebTrapPlanner.mayBypassCooldownForBlast(
			now >= this.nextTrapAt,
			newContainment
		)) {
			return false;
		}

		Block candidate = this.findPlacement(current, iq, newContainment ? blast : null);
		if (candidate == null) {
			this.nextTrapAt = saturatingAdd(now, 20L);
			return false;
		}
		this.target = current;
		this.plannedBlock = candidate;
		this.blastContainment = newContainment;
		this.plannedCreeperId = newContainment ? activeCreeperId : null;
		if (newContainment) {
			this.lastSupportedCreeperId = activeCreeperId;
		}
		return true;
	}

	@Override
	public boolean shouldStayActive() {
		return !this.abortRequested
			&& this.actionTicks <= ACTION_FINISH_TICK
			&& this.enabled(this.settings.get(), this.webSettings.get())
			&& PaperThreats.isLiveFor(this.spider, this.target)
			&& this.plannedBlock != null
			&& this.plannedBlock.getWorld() == this.spider.getWorld();
	}

	@Override
	public void start() {
		this.actionTicks = 0;
		this.placed = false;
		this.abortRequested = false;
		this.spider.getPathfinder().stopPathfinding();
		this.spider.setAggressive(true);
		this.metrics.spiderWebTrapWindup();
	}

	@Override
	public void tick() {
		this.actionTicks++;
		Block placement = this.plannedBlock;
		LivingEntity current = this.target;
		if (placement == null || !PaperThreats.isLiveFor(this.spider, current)) {
			this.abortRequested = true;
			return;
		}

		Location focus = placement.getLocation().add(0.5, 0.35, 0.5);
		this.spider.lookAt(focus, 60.0F, 45.0F);
		if (this.actionTicks == WINDUP_SOUND_TICK) {
			this.spider.getWorld().playSound(
				this.spider,
				Sound.ENTITY_SPIDER_AMBIENT,
				SoundCategory.HOSTILE,
				0.75F,
				1.20F + ThreadLocalRandom.current().nextFloat() * 0.18F
			);
		}
		if (this.actionTicks != SILK_RELEASE_TICK) {
			return;
		}

		this.spider.swingMainHand();
		if (this.spider.isOnGround()) {
			Vector velocity = this.spider.getVelocity();
			velocity.setX(velocity.getX() * 0.35);
			velocity.setY(Math.max(velocity.getY(), 0.16));
			velocity.setZ(velocity.getZ() * 0.35);
			this.spider.setVelocity(velocity);
		}
		long now = Bukkit.getCurrentTick();
		this.placed = this.traps.tryPlace(this.spider, placement, now);
		if (this.placed && this.blastContainment) {
			this.metrics.spiderBlastContainmentWeb();
		}
		PaperWebTrapSettings config = this.webSettings.get();
		this.nextTrapAt = saturatingAdd(now, SpiderWebTrapPlanner.cooldownTicks(
			config.cooldownTicks(),
			this.intelligence.get(this.spider),
			difficultyId(this.spider.getWorld()),
			ThreadLocalRandom.current().nextInt(41)
		));
	}

	@Override
	public void stop() {
		if (!this.placed) {
			this.nextTrapAt = Math.max(this.nextTrapAt, saturatingAdd(Bukkit.getCurrentTick(), 30L));
		}
		this.spider.getPathfinder().stopPathfinding();
		this.spider.setAggressive(PaperThreats.isLiveFor(this.spider, this.spider.getTarget()));
		this.target = null;
		this.plannedBlock = null;
		this.actionTicks = 0;
		this.plannedCreeperId = null;
		this.placed = false;
		this.blastContainment = false;
		this.abortRequested = false;
	}

	@Override
	public GoalKey<Spider> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	public Block plannedBlock() {
		return this.plannedBlock;
	}

	public boolean isBlastContainmentPlan() {
		return this.blastContainment && this.plannedCreeperId != null;
	}

	private Block findPlacement(final LivingEntity current, final int iq, final BlastThreat blast) {
		Location targetLocation = current.getLocation();
		Vec3d targetPosition = toShared(targetLocation);
		Vec3d targetVelocity = toShared(current.getVelocity());
		Vec3d targetLook = toShared(targetLocation.getDirection());
		List<Vec3d> centers;
		if (blast != null) {
			centers = SpiderWebTrapPlanner.blastEscapeCandidateCenters(
				targetPosition,
				targetVelocity,
				blast.center(),
				this.stableSide
			);
		} else {
			Vec3d predicted = SpiderWebTrapPlanner.predictedPosition(
				targetPosition,
				targetVelocity,
				targetLook,
				iq
			);
			centers = SpiderWebTrapPlanner.candidateCenters(
				targetPosition,
				predicted,
				targetLook,
				this.stableSide
			);
		}

		World world = current.getWorld();
		for (Vec3d center : centers) {
			for (int yOffset : VERTICAL_SEARCH_ORDER) {
				Block block = world.getBlockAt(
					floorToInt(center.x()),
					floorToInt(current.getBoundingBox().getMinY() + yOffset),
					floorToInt(center.z())
				);
				if (this.isUsefulPlacement(block, current)) {
					return block;
				}
			}
		}
		return null;
	}

	private boolean isUsefulPlacement(final Block block, final LivingEntity current) {
		Location center = block.getLocation().add(0.5, 0.5, 0.5);
		BoundingBox placementBox = BoundingBox.of(block).expand(0.05);
		return PaperEntityMath.distanceSquared(this.spider, center) <= MAXIMUM_PLACEMENT_DISTANCE_SQUARED
			&& PaperEntityMath.distanceSquared(current, center) >= MINIMUM_TARGET_CLEARANCE_SQUARED
			&& !placementBox.overlaps(this.spider.getBoundingBox())
			&& this.hasClearSilkPath(center)
			&& this.traps.canPlace(this.spider, block);
	}

	private boolean hasClearSilkPath(final Location destination) {
		Location origin = this.spider.getEyeLocation();
		Vector direction = destination.toVector().subtract(origin.toVector());
		double distance = direction.length();
		return distance <= 1.0E-6 || this.spider.getWorld().rayTraceBlocks(
			origin,
			direction.normalize(),
			distance,
			FluidCollisionMode.NEVER,
			true
		) == null;
	}

	private BlastThreat activeBlastFor(final LivingEntity current) {
		Creeper selected = null;
		double selectedProgress = -1.0;
		this.squads.copySquadmatesTo(this.spider, this.squadmateBuffer);
		for (int index = 0; index < this.squadmateBuffer.size(); index++) {
			Mob squadmate = this.squadmateBuffer.get(index);
			if (!(squadmate instanceof Creeper creeper)
				|| !creeper.isValid()
				|| creeper.isDead()
				|| creeper.getWorld() != current.getWorld()
				|| this.feints.isActive(creeper)
				|| (!creeper.isIgnited() && creeper.getFuseTicks() <= 0)
				|| (creeper.getTarget() != current && this.squads.sharedTargetFor(creeper) != current)) {
				continue;
			}
			double progress = creeper.getFuseTicks() / (double)Math.max(1, creeper.getMaxFuseTicks());
			if (progress > selectedProgress) {
				selected = creeper;
				selectedProgress = progress;
			}
		}
		this.squadmateBuffer.clear();
		if (selected == null) {
			return null;
		}
		Vec3d predicted = CreeperTacticalPlanner.fuseDestination(
			toShared(current.getLocation()),
			toShared(current.getVelocity()),
			selectedProgress,
			this.intelligence.get(selected)
		);
		return new BlastThreat(selected, predicted);
	}

	private LivingEntity currentTarget() {
		LivingEntity shared = this.squads.sharedTargetFor(this.spider);
		return PaperThreats.isLiveFor(this.spider, shared) ? shared : this.spider.getTarget();
	}

	private boolean enabled(final PaperSettings root, final PaperWebTrapSettings config) {
		return root.enabled()
			&& root.spiderTacticsEnabled()
			&& config.enabled()
			&& this.spider.isValid()
			&& !this.spider.isDead()
			&& this.intelligence.get(this.spider) >= config.minimumIntelligence();
	}

	private static int difficultyId(final World world) {
		return switch (world.getDifficulty()) {
			case PEACEFUL -> 0;
			case EASY -> 1;
			case NORMAL -> 2;
			case HARD -> 3;
		};
	}

	private static int floorToInt(final double value) {
		return (int)Math.floor(value);
	}

	private static Vec3d toShared(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toShared(final Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	private record BlastThreat(Creeper creeper, Vec3d center) {
	}
}

package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperCreeperFeintSettings;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.shared.ai.CreeperFeintPlanner;
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
 * Paper 端的可见假引爆：高智力苦力怕在真实起爆圈外短促闪烁，然后退火并移到观察者侧后方。
 *
 * <p>它使用真实的 Creeper 引信姿态，但硬限制为共享内核给出的 6～8 tick；外部打火石点燃会
 * 立即夺回引信所有权，插件绝不替玩家退火。</p>
 */
public final class PaperCreeperFeintGoal implements Goal<Creeper> {
	private static final double DESTINATION_REACHED_SQUARED = 1.35 * 1.35;

	private final Creeper creeper;
	private final GoalKey<Creeper> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperCreeperFeintMemory memory;
	private final PaperSquadCoordinator squads;
	private final PaperMetrics metrics;
	private final int stableSide;

	private LivingEntity target;
	private Phase phase = Phase.IDLE;
	private int phaseTicksRemaining;
	private long nextRepathAt;
	private Location destination;
	private int chosenSide;
	private boolean alternateAttempted;
	private boolean completed;
	private boolean ownsIgnition;

	public PaperCreeperFeintGoal(
		final Creeper creeper,
		final GoalKey<Creeper> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperCreeperFeintMemory memory,
		final PaperSquadCoordinator squads,
		final PaperMetrics metrics
	) {
		this.creeper = creeper;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.memory = memory;
		this.squads = squads;
		this.metrics = metrics;
		this.stableSide = (creeper.getUniqueId().hashCode() & 1) == 0 ? -1 : 1;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings root = this.settings.get();
		PaperCreeperFeintSettings config = root.creeperFeints();
		LivingEntity selected = this.creeper.getTarget();
		long now = Bukkit.getCurrentTick();
		if (!enabled(root, config)
			|| this.delegatedToSquad()
			|| this.creeper.isPowered()
			|| this.creeper.isIgnited()
			|| this.creeper.isInsideVehicle()
			|| !this.memory.canStart(this.creeper, now)
			|| !PaperThreats.isLiveFor(this.creeper, selected)) {
			return false;
		}
		return this.isFeintOpportunity(selected, root, config);
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings root = this.settings.get();
		return enabled(root, root.creeperFeints())
			&& !this.delegatedToSquad()
			&& this.phase != Phase.IDLE
			&& this.phaseTicksRemaining > 0
			&& this.creeper.isValid()
			&& !this.creeper.isDead()
			&& !this.creeper.isInsideVehicle()
			&& this.memory.isActive(this.creeper)
			&& !this.memory.wasExternallyIgnited(this.creeper)
			&& PaperThreats.isLiveFor(this.creeper, this.target);
	}

	@Override
	public void start() {
		long now = Bukkit.getCurrentTick();
		LivingEntity selected = this.creeper.getTarget();
		if (!PaperThreats.isLiveFor(this.creeper, selected) || !this.memory.begin(this.creeper, now)) {
			return;
		}
		this.target = selected;
		this.phase = Phase.PRIMING;
		this.phaseTicksRemaining = CreeperFeintPlanner.primeTicks(this.unitJitter(now, 0x51A7E11DL));
		this.nextRepathAt = now;
		this.destination = null;
		this.chosenSide = this.stableSide;
		this.alternateAttempted = false;
		this.completed = false;
		this.ownsIgnition = true;
		this.creeper.getPathfinder().stopPathfinding();
		this.creeper.setAggressive(true);
		this.creeper.setFuseTicks(0);
		this.setOwnedIgnited(true);
		boolean blocking = selected instanceof Player player && player.isBlocking();
		this.metrics.creeperFeintStarted(blocking);
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.creeper, current)) {
			return;
		}
		this.creeper.lookAt(current, 55.0F, 40.0F);
		if (this.phase == Phase.PRIMING) {
			this.creeper.getPathfinder().stopPathfinding();
			if (--this.phaseTicksRemaining <= 0 || !this.creeper.hasLineOfSight(current)) {
				this.beginReposition(current);
			}
			return;
		}

		if (--this.phaseTicksRemaining <= 0) {
			this.completed = true;
			return;
		}
		// Paper 的 setIgnited(false) 只清强制点燃位；原版 swellDir 仍可能因目标关系继续增长。
		// 假动作仍持有引信所有权时每 tick 压回零，避免侧移阶段偷偷走完真实爆炸线。
		this.creeper.setIgnited(false);
		this.creeper.setFuseTicks(0);
		Location currentDestination = this.destination;
		if (currentDestination == null) {
			this.beginReposition(current);
			currentDestination = this.destination;
		}
		if (currentDestination == null) {
			return;
		}
		if (PaperEntityMath.distanceSquared(this.creeper, currentDestination) <= DESTINATION_REACHED_SQUARED) {
			this.creeper.getPathfinder().stopPathfinding();
			this.completed = true;
			this.phaseTicksRemaining = 0;
			return;
		}
		long now = Bukkit.getCurrentTick();
		if (now < this.nextRepathAt && this.creeper.getPathfinder().hasPath()) {
			return;
		}
		PaperCreeperFeintSettings config = this.settings.get().creeperFeints();
		if (this.moveTo(currentDestination, config.repositionSpeed())) {
			this.nextRepathAt = now + 6L;
			return;
		}
		if (!this.alternateAttempted) {
			this.alternateAttempted = true;
			this.chosenSide = -this.chosenSide;
			this.destination = this.destinationFor(current, this.chosenSide);
			this.nextRepathAt = now + 1L;
			return;
		}
		this.metrics.creeperFeintPathFailed();
		this.phaseTicksRemaining = 0;
	}

	@Override
	public void stop() {
		boolean externalIgnition = this.memory.wasExternallyIgnited(this.creeper);
		long now = Bukkit.getCurrentTick();
		if (this.ownsIgnition && !externalIgnition) {
			this.setOwnedIgnited(false);
			this.creeper.setFuseTicks(0);
			this.memory.beginPostFeintCooling(this.creeper, now, 10);
		} else {
			this.memory.endOwnedIgnition(this.creeper);
		}
		this.creeper.getPathfinder().stopPathfinding();
		PaperCreeperFeintSettings config = this.settings.get().creeperFeints();
		int cooldown = CreeperFeintPlanner.cooldownTicks(
			config.cooldownTicks(),
			this.unitJitter(now, 0xC001D00DL)
		);
		this.memory.finish(
			this.creeper,
			now,
			this.completed ? cooldown : Math.max(40, cooldown / 2)
		);
		if (this.completed) {
			this.memory.markCompleted(this.creeper, now);
			this.metrics.creeperFeintCompleted();
		}
		this.target = null;
		this.phase = Phase.IDLE;
		this.phaseTicksRemaining = 0;
		this.nextRepathAt = 0L;
		this.destination = null;
		this.alternateAttempted = false;
		this.completed = false;
		this.ownsIgnition = false;
	}

	@Override
	public GoalKey<Creeper> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private boolean isFeintOpportunity(
		final LivingEntity selected,
		final PaperSettings root,
		final PaperCreeperFeintSettings config
	) {
		boolean visible = this.creeper.hasLineOfSight(selected);
		boolean watching = visible && CreeperTacticalPlanner.isTargetWatching(
			toVector(selected.getLocation().getDirection()),
			toVector(this.creeper.getLocation()).subtract(toVector(selected.getLocation()))
		);
		boolean blocking = selected instanceof Player player && player.isBlocking();
		return CreeperFeintPlanner.shouldFeint(
			this.intelligence.get(this.creeper),
			config.enabled(),
			visible,
			watching,
			blocking,
			this.creeper.isPowered(),
			this.creeper.getFuseTicks() / (double)Math.max(1, this.creeper.getMaxFuseTicks()),
			PaperEntityMath.distanceSquared(this.creeper, selected),
			root.creeperMaximumFuseStartDistance()
		);
	}

	private void beginReposition(final LivingEntity current) {
		this.phase = Phase.REPOSITIONING;
		this.phaseTicksRemaining = CreeperFeintPlanner.repositionTicks(
			this.unitJitter(Bukkit.getCurrentTick(), 0x7E90517L)
		);
		this.nextRepathAt = Bukkit.getCurrentTick();
		this.setOwnedIgnited(false);
		this.creeper.setFuseTicks(0);
		this.destination = this.destinationFor(current, this.chosenSide);
		this.creeper.getWorld().playSound(
			this.creeper.getLocation(),
			Sound.BLOCK_FIRE_EXTINGUISH,
			SoundCategory.HOSTILE,
			0.45F,
			1.55F
		);
	}

	private Location destinationFor(final LivingEntity current, final int side) {
		Vec3d destinationVector = CreeperFeintPlanner.repositionDestination(
			toVector(current.getLocation()),
			toVector(current.getVelocity()),
			toVector(current.getLocation().getDirection()),
			side,
			this.intelligence.get(this.creeper)
		);
		return new Location(
			this.creeper.getWorld(),
			destinationVector.x(),
			destinationVector.y(),
			destinationVector.z()
		);
	}

	private boolean moveTo(final Location destination, final double speed) {
		Pathfinder pathfinder = this.creeper.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		return path != null && pathfinder.moveTo(path, speed);
	}

	private void setOwnedIgnited(final boolean ignited) {
		if (ignited) {
			this.memory.beginOwnedIgnition(this.creeper);
			this.creeper.setIgnited(true);
			return;
		}
		this.creeper.setIgnited(false);
	}

	private static boolean enabled(
		final PaperSettings root,
		final PaperCreeperFeintSettings config
	) {
		return root.enabled() && root.creeperTacticsEnabled() && config.enabled();
	}

	private boolean delegatedToSquad() {
		return this.squads.isHoldingForOrders(this.creeper)
			|| this.squads.isAssignedTransportPayload(this.creeper);
	}

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toVector(final org.bukkit.util.Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	private double unitJitter(final long tick, final long salt) {
		long mixed = this.creeper.getUniqueId().getMostSignificantBits()
			^ Long.rotateLeft(this.creeper.getUniqueId().getLeastSignificantBits(), 23)
			^ Long.rotateLeft(tick, 41)
			^ salt;
		mixed ^= mixed >>> 30;
		mixed *= 0xBF58476D1CE4E5B9L;
		mixed ^= mixed >>> 27;
		mixed *= 0x94D049BB133111EBL;
		mixed ^= mixed >>> 31;
		return (mixed >>> 11) * 0x1.0p-53;
	}

	private enum Phase {
		IDLE,
		PRIMING,
		REPOSITIONING
	}
}

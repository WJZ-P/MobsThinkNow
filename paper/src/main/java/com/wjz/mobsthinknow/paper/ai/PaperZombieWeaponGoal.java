package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperEntityMath;
import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.PaperWeaponSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.ai.MeleeWeaponPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.EnumSet;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * 仅使用 Paper 公共 API 的持剑/斧僵尸战斗 Goal：真实武器冷却、离身周旋、斧手前摇跳劈与落地降级。
 */
@SuppressWarnings("deprecation")
public final class PaperZombieWeaponGoal implements Goal<Zombie> {
	private static final double MELEE_REACH = 2.45;
	private static final double AXE_MINIMUM_DISTANCE = 1.8;
	private static final double AXE_MAXIMUM_DISTANCE = 3.3;
	private static final double AXE_MAXIMUM_VERTICAL_DIFFERENCE = 1.25;
	private static final double AXE_JUMP_VERTICAL_SPEED = 0.42;
	private static final double AXE_DESCENDING_THRESHOLD = -0.02;
	private static final int AXE_LEAP_TIMEOUT_TICKS = 20;
	private static final int TAKEOFF_GRACE_TICKS = 3;

	private final Zombie zombie;
	private final GoalKey<Zombie> key;
	private final NamespacedKey criticalDamageKey;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperShieldMemory shieldMemory;
	private final PaperMetrics metrics;
	private final int stableSide;

	private LivingEntity target;
	private PaperWeaponProfile.Kind weapon = PaperWeaponProfile.Kind.NONE;
	private long nextAttackAt;
	private long nextRepathAt;
	private long axePreparationDeadline;
	private long axeWindupStartedAt = Long.MIN_VALUE;
	private long axeLeapStartedAt = Long.MIN_VALUE;
	private boolean clockwise;

	public PaperZombieWeaponGoal(
		final Zombie zombie,
		final GoalKey<Zombie> key,
		final NamespacedKey criticalDamageKey,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperShieldMemory shieldMemory,
		final PaperMetrics metrics
	) {
		this.zombie = zombie;
		this.key = key;
		this.criticalDamageKey = criticalDamageKey;
		this.settings = settings;
		this.intelligence = intelligence;
		this.squads = squads;
		this.shieldMemory = shieldMemory;
		this.metrics = metrics;
		this.stableSide = (zombie.getUniqueId().hashCode() & 1) == 0 ? -1 : 1;
		this.clockwise = this.stableSide > 0;
	}

	@Override
	public boolean shouldActivate() {
		return this.refreshEligibility();
	}

	@Override
	public boolean shouldStayActive() {
		return this.refreshEligibility();
	}

	@Override
	public void start() {
		long now = Bukkit.getCurrentTick();
		// Goal 被撤退、会议或其他高优先级行为抢占后会重新 start；武器冷却属于僵尸本身，不能借此刷新。
		if (this.nextAttackAt == 0L) {
			this.nextAttackAt = now;
		}
		this.nextRepathAt = now;
		this.axePreparationDeadline = now + this.settings.get().zombieWeaponTactics().axePreparationTimeoutTicks();
		this.axeWindupStartedAt = Long.MIN_VALUE;
		this.axeLeapStartedAt = Long.MIN_VALUE;
		this.zombie.setAggressive(true);
		this.zombie.setArmsRaised(true);
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.zombie, current)) {
			return;
		}
		PaperWeaponSettings config = this.settings.get().zombieWeaponTactics();
		long now = Bukkit.getCurrentTick();
		this.zombie.lookAt(current, 45.0F, 40.0F);
		if (!this.zombie.hasLineOfSight(current)) {
			this.cancelAxeSequence();
			this.approach(current, config, now);
			return;
		}

		if (now < this.nextAttackAt) {
			this.cancelAxeSequence();
			this.circle(current, config, now);
			return;
		}

		if (this.weapon == PaperWeaponProfile.Kind.SWORD) {
			this.approachAndStrike(current, config, now, false);
			return;
		}
		this.tickAxe(current, config, now);
	}

	@Override
	public void stop() {
		this.cancelAxeSequence();
		this.zombie.getPathfinder().stopPathfinding();
		this.zombie.setJumping(false);
		this.zombie.setArmsRaised(false);
		this.zombie.setAggressive(this.zombie.getTarget() != null);
		this.target = null;
		this.weapon = PaperWeaponProfile.Kind.NONE;
	}

	@Override
	public GoalKey<Zombie> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.JUMP);
	}

	private boolean refreshEligibility() {
		PaperSettings root = this.settings.get();
		PaperWeaponSettings config = root.zombieWeaponTactics();
		PaperWeaponProfile.Kind selected = PaperWeaponProfile.kindOf(
			this.zombie.getEquipment().getItemInMainHand().getType()
		);
		LivingEntity selectedTarget = this.currentTarget();
		PaperSquadDirective directive = this.squads.directiveFor(this.zombie);
		boolean delegatedToShieldGoal = root.zombieShieldTactics().enabled()
			&& this.intelligence.get(this.zombie) >= root.zombieShieldTactics().minimumIntelligence()
			&& !this.shieldMemory.isDisabled(this.zombie, Bukkit.getCurrentTick())
			&& PaperZombieShieldGoal.hasShieldInOffHand(this.zombie);
		boolean eligible = root.enabled()
			&& config.enabled()
			&& this.zombie.isValid()
			&& !this.zombie.isDead()
			&& !this.zombie.isInsideVehicle()
			&& this.intelligence.get(this.zombie) >= config.minimumIntelligence()
			&& !delegatedToShieldGoal
			&& selected != PaperWeaponProfile.Kind.NONE
			&& PaperThreats.isLiveFor(this.zombie, selectedTarget)
			&& (directive == null || directive.state() == MixedSquadState.ENGAGING);
		if (eligible) {
			if (this.target != selectedTarget || this.weapon != selected) {
				this.target = selectedTarget;
				this.weapon = selected;
				long now = Bukkit.getCurrentTick();
				this.nextRepathAt = now;
				this.axePreparationDeadline = now + config.axePreparationTimeoutTicks();
				this.cancelAxeSequence();
			}
		}
		return eligible;
	}

	private LivingEntity currentTarget() {
		LivingEntity shared = this.squads.sharedTargetFor(this.zombie);
		return PaperThreats.isLiveFor(this.zombie, shared) ? shared : this.zombie.getTarget();
	}

	private void tickAxe(final LivingEntity current, final PaperWeaponSettings config, final long now) {
		if (this.axeLeapStartedAt != Long.MIN_VALUE) {
			this.guideLeap(current, config);
			if (!this.zombie.isOnGround()
				&& this.zombie.getVelocity().getY() < AXE_DESCENDING_THRESHOLD
				&& this.canReach(current)) {
				this.performAttack(current, config, now, true);
				return;
			}
			if (now - this.axeLeapStartedAt > AXE_LEAP_TIMEOUT_TICKS
				|| (this.zombie.isOnGround() && now - this.axeLeapStartedAt > TAKEOFF_GRACE_TICKS)
				|| this.zombie.isInWater()) {
				this.axeLeapStartedAt = Long.MIN_VALUE;
				this.axePreparationDeadline = now + config.axePreparationTimeoutTicks();
			}
			return;
		}

		if (this.axeWindupStartedAt != Long.MIN_VALUE) {
			this.zombie.getPathfinder().stopPathfinding();
			this.zombie.setArmsRaised(true);
			if (now - this.axeWindupStartedAt < config.axeWindupTicks()) {
				return;
			}
			this.axeWindupStartedAt = Long.MIN_VALUE;
			if (this.canLaunch(current)) {
				this.launch(current, config, now);
			} else if (now >= this.axePreparationDeadline) {
				this.approachAndStrike(current, config, now, false);
			} else {
				this.circle(current, config, now);
			}
			return;
		}

		if (this.intelligence.get(this.zombie) >= config.axeMinimumIntelligence()
			&& now < this.axePreparationDeadline
			&& this.canLaunch(current)) {
			this.axeWindupStartedAt = now;
			this.zombie.getPathfinder().stopPathfinding();
			this.zombie.getWorld().playSound(
				this.zombie.getLocation(), Sound.ENTITY_ZOMBIE_STEP, SoundCategory.HOSTILE, 0.55F, 0.72F
			);
			this.metrics.axeWindupStarted();
			return;
		}

		if (now >= this.axePreparationDeadline
			|| this.intelligence.get(this.zombie) < config.axeMinimumIntelligence()
			|| Math.abs(current.getY() - this.zombie.getY()) > AXE_MAXIMUM_VERTICAL_DIFFERENCE) {
			this.approachAndStrike(current, config, now, false);
			return;
		}

		double horizontal = MeleeWeaponPlanner.horizontalDistanceSquared(
			toShared(this.zombie.getLocation()), toShared(current.getLocation())
		);
		if (horizontal > AXE_MAXIMUM_DISTANCE * AXE_MAXIMUM_DISTANCE) {
			this.approach(current, config, now);
		} else {
			this.circle(current, config, now);
		}
	}

	private boolean canLaunch(final LivingEntity current) {
		if (!this.zombie.isOnGround() || this.zombie.isInsideVehicle()) {
			this.metrics.axeLaunchAirborneRejected();
			return false;
		}
		if (this.zombie.isInWater()) {
			this.metrics.axeLaunchWaterRejected();
			return false;
		}
		if (!this.zombie.hasLineOfSight(current)) {
			this.metrics.axeLaunchSightRejected();
			return false;
		}
		Vec3d zombiePosition = toShared(this.zombie.getLocation());
		Vec3d targetPosition = toShared(current.getLocation());
		if (!MeleeWeaponPlanner.isAxeLaunchBand(
			zombiePosition,
			targetPosition,
			AXE_MINIMUM_DISTANCE,
			AXE_MAXIMUM_DISTANCE,
			AXE_MAXIMUM_VERTICAL_DIFFERENCE
		)) {
			this.metrics.axeLaunchBandRejected();
			return false;
		}
		Vec3d direction = targetPosition.subtract(zombiePosition).horizontalUnitOr(new Vec3d(this.stableSide, 0.0, 0.0));
		BoundingBox swept = this.zombie.getBoundingBox().clone()
			.expandDirectional(direction.x() * 1.2, 1.1, direction.z() * 1.2)
			.expand(-0.05);
		if (this.zombie.wouldCollideUsing(swept)) {
			this.metrics.axeLaunchCollisionRejected();
			return false;
		}
		return true;
	}

	private void launch(final LivingEntity current, final PaperWeaponSettings config, final long now) {
		this.zombie.getPathfinder().stopPathfinding();
		this.zombie.setJumping(true);
		Vec3d velocity = MeleeWeaponPlanner.axeLeapVelocity(
			toShared(this.zombie.getLocation()),
			toShared(current.getLocation()),
			Math.max(AXE_JUMP_VERTICAL_SPEED, this.zombie.getVelocity().getY()),
			config.axeHorizontalSpeed()
		);
		this.zombie.setVelocity(toBukkit(velocity));
		this.axeLeapStartedAt = now;
		this.metrics.axeLeapStarted();
	}

	private void guideLeap(final LivingEntity current, final PaperWeaponSettings config) {
		this.zombie.lookAt(current, 40.0F, 40.0F);
		Vec3d guided = MeleeWeaponPlanner.guideAxeLeap(
			toShared(this.zombie.getVelocity()),
			toShared(this.zombie.getLocation()),
			toShared(current.getLocation()),
			config.axeHorizontalSpeed(),
			0.20
		);
		this.zombie.setVelocity(toBukkit(guided));
	}

	private void approachAndStrike(
		final LivingEntity current,
		final PaperWeaponSettings config,
		final long now,
		final boolean critical
	) {
		if (this.canReach(current)) {
			this.performAttack(current, config, now, critical);
		} else {
			this.approach(current, config, now);
		}
	}

	private void approach(final LivingEntity current, final PaperWeaponSettings config, final long now) {
		this.zombie.setAggressive(true);
		this.zombie.setArmsRaised(true);
		this.moveTo(current.getLocation(), config.movementSpeed(), config.repathTicks(), now);
	}

	private void circle(final LivingEntity current, final PaperWeaponSettings config, final long now) {
		this.zombie.setAggressive(false);
		this.zombie.setArmsRaised(false);
		if (now < this.nextRepathAt) {
			return;
		}
		Vec3d destination = MeleeWeaponPlanner.spacingDestination(
			toShared(this.zombie.getLocation()),
			toShared(current.getLocation()),
			config.spacingRadius(),
			this.clockwise
		);
		if (!this.moveTo(toLocation(destination), config.movementSpeed(), config.repathTicks(), now)) {
			this.clockwise = !this.clockwise;
			Vec3d fallback = MeleeWeaponPlanner.spacingDestination(
				toShared(this.zombie.getLocation()),
				toShared(current.getLocation()),
				config.spacingRadius(),
				this.clockwise
			);
			this.nextRepathAt = now;
			this.moveTo(toLocation(fallback), config.movementSpeed(), config.repathTicks(), now);
		}
		this.metrics.weaponSpacingMove();
	}

	private boolean moveTo(final Location destination, final double speed, final int repathTicks, final long now) {
		if (now < this.nextRepathAt) {
			return this.zombie.getPathfinder().hasPath();
		}
		Pathfinder pathfinder = this.zombie.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		boolean moving = path != null && pathfinder.moveTo(path, speed);
		this.nextRepathAt = now + repathTicks;
		if (!moving) {
			this.metrics.weaponPathFailed();
		}
		return moving;
	}

	private void performAttack(
		final LivingEntity current,
		final PaperWeaponSettings config,
		final long now,
		final boolean critical
	) {
		this.zombie.getPathfinder().stopPathfinding();
		this.zombie.clearActiveItem();
		this.zombie.swingMainHand();
		AttributeInstance damage = this.zombie.getAttribute(Attribute.ATTACK_DAMAGE);
		AttributeModifier modifier = null;
		if (critical && damage != null && config.axeCriticalDamageMultiplier() > 1.0) {
			AttributeModifier previous = damage.getModifier(this.criticalDamageKey);
			if (previous != null) {
				damage.removeModifier(previous);
			}
			modifier = new AttributeModifier(
				this.criticalDamageKey,
				config.axeCriticalDamageMultiplier() - 1.0,
				AttributeModifier.Operation.MULTIPLY_SCALAR_1,
				EquipmentSlotGroup.MAINHAND
			);
			damage.addTransientModifier(modifier);
		}
		try {
			this.zombie.attack(current);
		} finally {
			if (modifier != null) {
				damage.removeModifier(modifier);
			}
		}
		this.metrics.weaponAttack();
		if (critical) {
			current.getWorld().playSound(current.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT,
				SoundCategory.HOSTILE, 1.0F, 1.0F);
			current.getWorld().spawnParticle(Particle.CRIT, current.getLocation().add(0.0, current.getHeight() * 0.5, 0.0),
				12, current.getWidth() * 0.25, current.getHeight() * 0.2, current.getWidth() * 0.25, 0.2);
			this.metrics.axeCriticalAttack();
		}

		this.nextAttackAt = now + PaperWeaponProfile.cooldownTicks(
			this.zombie.getEquipment().getItemInMainHand().getType()
		);
		this.axePreparationDeadline = now + config.axePreparationTimeoutTicks();
		this.cancelAxeSequence();
		this.clockwise = !this.clockwise;
		this.applyDisengageImpulse(current);
	}

	private void applyDisengageImpulse(final LivingEntity current) {
		Vec3d away = toShared(this.zombie.getLocation()).subtract(toShared(current.getLocation()))
			.horizontalUnitOr(new Vec3d(this.stableSide, 0.0, 0.0));
		Vec3d lateral = this.clockwise
			? new Vec3d(-away.z(), 0.0, away.x())
			: new Vec3d(away.z(), 0.0, -away.x());
		Vector movement = this.zombie.getVelocity();
		movement.setX(movement.getX() * 0.35 + away.x() * 0.24 + lateral.x() * 0.10);
		movement.setZ(movement.getZ() * 0.35 + away.z() * 0.24 + lateral.z() * 0.10);
		this.zombie.setVelocity(movement);
	}

	private boolean canReach(final LivingEntity current) {
		double reach = MELEE_REACH + current.getWidth() * 0.25;
		return PaperEntityMath.distanceSquared(this.zombie, current) <= reach * reach
			&& this.zombie.hasLineOfSight(current);
	}

	private void cancelAxeSequence() {
		this.axeWindupStartedAt = Long.MIN_VALUE;
		this.axeLeapStartedAt = Long.MIN_VALUE;
		this.zombie.setJumping(false);
	}

	private Location toLocation(final Vec3d vector) {
		return new Location(this.zombie.getWorld(), vector.x(), vector.y(), vector.z());
	}

	private static Vec3d toShared(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private static Vec3d toShared(final Vector vector) {
		return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
	}

	private static Vector toBukkit(final Vec3d vector) {
		return new Vector(vector.x(), vector.y(), vector.z());
	}
}

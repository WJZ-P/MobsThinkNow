package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ZombieTacticalController {
	private static final double MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED = 1.0E-6;
	private static final double FRONT_ARC_DOT_PRODUCT = 0.2;
	private static final double DESTINATION_REACHED_DISTANCE_SQUARED = 2.25;

	private final Zombie zombie;
	private ZombieTactic tactic = ZombieTactic.PRESSURE;
	private @Nullable Vec3 lastSeenPosition;
	private long lastSeenAt = Long.MIN_VALUE;
	private @Nullable Vec3 destination;
	private long nextDecisionAt;
	private long nextPathUpdateAt;
	private long nextProgressCheckAt;
	private Vec3 lastProgressPosition;
	private int packSize = 1;
	private int packIndex;
	private boolean alternateFlank;
	private boolean hasLineOfSight;

	ZombieTacticalController(final Zombie zombie) {
		this.zombie = zombie;
		this.lastProgressPosition = zombie.position();
	}

	void observe(final LivingEntity target) {
		this.hasLineOfSight = this.zombie.getSensing().hasLineOfSight(target);
		if (this.hasLineOfSight) {
			this.lastSeenPosition = target.position();
			this.lastSeenAt = this.zombie.level().getGameTime();
		}
	}

	boolean hasTrackableTarget() {
		MobsThinkNowConfig config = ConfigManager.get();
		return this.hasLineOfSight || this.hasRecentLastSeenPosition(config);
	}

	boolean hasTacticalIntent() {
		return this.tactic != ZombieTactic.PRESSURE && this.destination != null;
	}

	boolean shouldRunVanillaCombat(final LivingEntity target) {
		return this.tactic == ZombieTactic.PRESSURE
			|| (this.hasLineOfSight && this.zombie.isWithinMeleeAttackRange(target) && !this.shouldHoldFrontalAttack(target));
	}

	boolean shouldHoldFrontalAttack(final LivingEntity target) {
		if (!ConfigManager.get().shieldFlanking) {
			return false;
		}

		return (this.tactic == ZombieTactic.FLANK_LEFT || this.tactic == ZombieTactic.FLANK_RIGHT)
			&& target.isBlocking()
			&& isInFrontArc(target);
	}

	void tick(final LivingEntity target) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled || !config.zombieAiEnabled) {
			this.tactic = ZombieTactic.PRESSURE;
			this.destination = null;
			return;
		}

		long now = this.zombie.level().getGameTime();
		if (now >= this.nextDecisionAt) {
			this.decide(target, config, now);
		}

		if (this.tactic == ZombieTactic.PRESSURE || this.destination == null) {
			return;
		}

		if (this.tactic == ZombieTactic.SEARCH_LAST_SEEN) {
			this.zombie.getLookControl().setLookAt(this.destination.add(0.0, 1.0, 0.0));
		} else if (this.hasLineOfSight) {
			this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}

		boolean shouldKeepFlankingShield = this.shouldHoldFrontalAttack(target);
		if (this.zombie.isWithinMeleeAttackRange(target) && !shouldKeepFlankingShield) {
			return;
		}

		if (this.zombie.position().distanceToSqr(this.destination) <= DESTINATION_REACHED_DISTANCE_SQUARED) {
			if (this.tactic == ZombieTactic.SEARCH_LAST_SEEN) {
				this.lastSeenPosition = null;
				this.tactic = ZombieTactic.PRESSURE;
			}
			return;
		}

		this.checkProgress(now);
		if (now >= this.nextPathUpdateAt && this.navigationTargetsDifferentPosition(this.destination)) {
			boolean foundPath = this.zombie
				.getNavigation()
				.moveTo(this.destination.x, this.destination.y, this.destination.z, config.tacticalSpeedModifier);
			this.nextPathUpdateAt = now + config.decisionIntervalTicks;
			if (!foundPath) {
				this.alternateFlank = !this.alternateFlank;
				this.nextDecisionAt = now + 2L;
				SmartZombieMetrics.failedPath();
			}
		}
	}

	void stop() {
		this.tactic = ZombieTactic.PRESSURE;
		this.destination = null;
		this.packSize = 1;
		this.packIndex = 0;
	}

	private void decide(final LivingEntity target, final MobsThinkNowConfig config, final long now) {
		this.updatePack(target, config);
		boolean prefersLeft = ((this.zombie.getId() & 1) == 0) != this.alternateFlank;
		ZombieDecisionContext context = new ZombieDecisionContext(
			this.hasLineOfSight,
			this.hasRecentLastSeenPosition(config),
			target.isBlocking(),
			this.isInFrontArc(target),
			this.packSize,
			this.packIndex,
			prefersLeft
		);

		this.tactic = ZombieTacticEvaluator.select(context, this.tactic);
		this.destination = this.calculateDestination(target, config);
		this.nextDecisionAt = now + config.decisionIntervalTicks + Math.floorMod(this.zombie.getId(), 3);
		SmartZombieMetrics.decision(this.tactic);
	}

	private void updatePack(final LivingEntity target, final MobsThinkNowConfig config) {
		if (!config.packSurrounding || !this.hasLineOfSight) {
			this.packSize = 1;
			this.packIndex = 0;
			return;
		}

		List<Zombie> nearby = this.zombie
			.level()
			.getEntitiesOfClass(
				Zombie.class,
				this.zombie.getBoundingBox().inflate(config.coordinationRadius),
				candidate -> candidate.isAlive() && candidate.getTarget() == target
			);
		nearby.sort(Comparator.comparingInt(Zombie::getId));
		if (nearby.size() > config.maximumCoordinatedZombies) {
			nearby = nearby.subList(0, config.maximumCoordinatedZombies);
		}

		int index = nearby.indexOf(this.zombie);
		if (index < 0) {
			this.packSize = 1;
			this.packIndex = 0;
			return;
		}

		this.packSize = nearby.size();
		this.packIndex = index;
	}

	private @Nullable Vec3 calculateDestination(final LivingEntity target, final MobsThinkNowConfig config) {
		return switch (this.tactic) {
			case PRESSURE -> null;
			case SEARCH_LAST_SEEN -> this.lastSeenPosition;
			case FLANK_LEFT -> this.calculateShieldFlank(target, config, 1.0);
			case FLANK_RIGHT -> this.calculateShieldFlank(target, config, -1.0);
			case SURROUND -> this.calculateFormationSlot(target, config);
		};
	}

	private Vec3 calculateShieldFlank(final LivingEntity target, final MobsThinkNowConfig config, final double side) {
		Vec3 forward = horizontalUnit(target.getLookAngle(), target.position().subtract(this.zombie.position()));
		Vec3 lateral = new Vec3(-forward.z, 0.0, forward.x);
		return target.position()
			.subtract(forward.scale(config.flankBehindDistance))
			.add(lateral.scale(config.flankSideDistance * side));
	}

	private Vec3 calculateFormationSlot(final LivingEntity target, final MobsThinkNowConfig config) {
		double angle = Math.toRadians(target.getYRot()) + (Math.PI * 2.0 * this.packIndex) / Math.max(1, this.packSize);
		return target.position().add(Math.cos(angle) * config.formationRadius, 0.0, Math.sin(angle) * config.formationRadius);
	}

	private boolean isInFrontArc(final LivingEntity target) {
		Vec3 fallback = target.position().subtract(this.zombie.position());
		Vec3 targetForward = horizontalUnit(target.getLookAngle(), fallback);
		Vec3 targetToZombie = horizontalUnit(this.zombie.position().subtract(target.position()), targetForward);
		return targetForward.dot(targetToZombie) > FRONT_ARC_DOT_PRODUCT;
	}

	private boolean hasRecentLastSeenPosition(final MobsThinkNowConfig config) {
		if (this.lastSeenPosition == null) {
			return false;
		}

		long elapsed = this.zombie.level().getGameTime() - this.lastSeenAt;
		return elapsed >= 0L && elapsed <= config.targetMemoryTicks;
	}

	private boolean navigationTargetsDifferentPosition(final Vec3 wanted) {
		Path path = this.zombie.getNavigation().getPath();
		if (path == null || path.isDone()) {
			return true;
		}

		BlockPos currentTarget = path.getTarget();
		double dx = currentTarget.getX() + 0.5 - wanted.x;
		double dy = currentTarget.getY() - wanted.y;
		double dz = currentTarget.getZ() + 0.5 - wanted.z;
		return dx * dx + dy * dy + dz * dz > 2.25;
	}

	private void checkProgress(final long now) {
		if (now < this.nextProgressCheckAt) {
			return;
		}

		Vec3 currentPosition = this.zombie.position();
		if (currentPosition.distanceToSqr(this.lastProgressPosition) < 0.04 && !this.zombie.getNavigation().isDone()) {
			this.alternateFlank = !this.alternateFlank;
			this.nextDecisionAt = now + 1L;
		}

		this.lastProgressPosition = currentPosition;
		this.nextProgressCheckAt = now + 20L;
	}

	private static Vec3 horizontalUnit(final Vec3 preferred, final Vec3 fallback) {
		Vec3 horizontal = new Vec3(preferred.x, 0.0, preferred.z);
		if (horizontal.horizontalDistanceSqr() < MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED) {
			horizontal = new Vec3(fallback.x, 0.0, fallback.z);
		}

		if (horizontal.horizontalDistanceSqr() < MIN_HORIZONTAL_VECTOR_LENGTH_SQUARED) {
			return new Vec3(0.0, 0.0, 1.0);
		}

		return horizontal.normalize();
	}
}

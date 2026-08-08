package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.RetreatPlanner;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;

/**
 * 仅使用 Paper 公共 MobGoals/Pathfinder API 的反应式撤退。
 *
 * <p>优先级由注册器设为 1，让原版浮水等生存 Goal 仍可抢占；运行时占用 MOVE/LOOK，因此近战追击
 * 会暂停。结束后明确停路并恢复原目标，绝不反射 NMS 字段。</p>
 */
public final class PaperZombieRetreatGoal implements Goal<Zombie> {
	private static final int PATH_REFRESH_TICKS = 8;
	private static final int RETRY_AFTER_PATH_FAILURE_TICKS = 2;
	private static final int RETREAT_COOLDOWN_TICKS = 40;
	private static final double MINIMUM_DESTINATION_DISTANCE = 5.0;
	private static final double MAXIMUM_DESTINATION_DISTANCE = 9.0;

	private final Zombie zombie;
	private final GoalKey<Zombie> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperDamageMemory damageMemory;
	private final PaperMetrics metrics;
	private final int stableSide;
	private final double distanceSample;

	private LivingEntity attacker;
	private PaperDamageMemory.DamageSnapshot pendingAttack;
	private long startedAt;
	private long nextPathAt;
	private long nextAllowedAt;

	public PaperZombieRetreatGoal(
		final Zombie zombie,
		final GoalKey<Zombie> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperDamageMemory damageMemory,
		final PaperMetrics metrics
	) {
		this.zombie = zombie;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.damageMemory = damageMemory;
		this.metrics = metrics;
		int hash = zombie.getUniqueId().hashCode();
		this.stableSide = (hash & 1) == 0 ? -1 : 1;
		this.distanceSample = Integer.toUnsignedLong(hash) / (double)0xFFFFFFFFL;
	}

	@Override
	public boolean shouldActivate() {
		PaperSettings config = this.settings.get();
		long now = Bukkit.getCurrentTick();
		if (!enabled(config)
			|| now < this.nextAllowedAt
			|| this.intelligence.get(this.zombie) < config.zombieRetreatMinimumIntelligence()) {
			this.damageMemory.discard(this.zombie);
			return false;
		}
		PaperDamageMemory.DamageSnapshot fresh = this.damageMemory.consume(
			this.zombie,
			now,
			config.damageMemoryTicks()
		);
		if (fresh == null) {
			return false;
		}
		RetreatPlanner.Trigger trigger = RetreatPlanner.trigger(
			this.zombie.getHealth(),
			this.maximumHealth(),
			fresh.largestDamage(),
			config.retreatHealthThreshold(),
			config.retreatHeavyHitThreshold()
		);
		if (!trigger.active()) {
			return false;
		}
		LivingEntity selected = this.resolvePreferredAttacker(fresh, trigger.includesHeavyHit());
		if (!isLiveThreat(selected) || this.hasReachedSafeDistance(selected, config.retreatSafeDistance())) {
			return false;
		}
		this.pendingAttack = fresh;
		this.attacker = selected;
		return true;
	}

	@Override
	public boolean shouldStayActive() {
		PaperSettings config = this.settings.get();
		LivingEntity currentAttacker = this.attacker;
		if (!enabled(config) || !isLiveThreat(currentAttacker)) {
			return false;
		}
		long now = Bukkit.getCurrentTick();
		return RetreatPlanner.shouldContinue(
			now - this.startedAt,
			config.retreatMaximumTicks(),
			this.zombie.getLocation().distanceSquared(currentAttacker.getLocation()),
			config.retreatSafeDistance()
		);
	}

	@Override
	public void start() {
		if (this.attacker == null || this.pendingAttack == null) {
			return;
		}
		long now = Bukkit.getCurrentTick();
		this.startedAt = now;
		this.nextPathAt = now;
		this.zombie.getPathfinder().stopPathfinding();
		this.zombie.setAggressive(false);
		this.zombie.getWorld().playSound(
			this.zombie.getLocation(),
			Sound.ENTITY_ZOMBIE_AMBIENT,
			SoundCategory.HOSTILE,
			0.8F,
			1.35F
		);
		this.metrics.retreatStarted();
		this.updateEscapePath(now);
	}

	@Override
	public void tick() {
		long now = Bukkit.getCurrentTick();
		PaperSettings config = this.settings.get();
		PaperDamageMemory.DamageSnapshot fresh = this.damageMemory.consume(
			this.zombie,
			now,
			config.damageMemoryTicks()
		);
		if (fresh != null) {
			RetreatPlanner.Trigger trigger = RetreatPlanner.trigger(
				this.zombie.getHealth(),
				this.maximumHealth(),
				fresh.largestDamage(),
				config.retreatHealthThreshold(),
				config.retreatHeavyHitThreshold()
			);
			LivingEntity updated = this.resolvePreferredAttacker(fresh, trigger.includesHeavyHit());
			if (trigger.active() && isLiveThreat(updated)) {
				this.attacker = updated;
				this.nextPathAt = now;
				this.zombie.getPathfinder().stopPathfinding();
			}
		}
		if (now >= this.nextPathAt || !this.zombie.getPathfinder().hasPath()) {
			this.updateEscapePath(now);
		}
	}

	@Override
	public void stop() {
		this.zombie.getPathfinder().stopPathfinding();
		this.zombie.setAggressive(this.zombie.getTarget() != null);
		this.attacker = null;
		this.pendingAttack = null;
		this.startedAt = 0L;
		this.nextPathAt = 0L;
		this.nextAllowedAt = Bukkit.getCurrentTick() + RETREAT_COOLDOWN_TICKS;
	}

	@Override
	public GoalKey<Zombie> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private void updateEscapePath(final long now) {
		LivingEntity currentAttacker = this.attacker;
		if (!isLiveThreat(currentAttacker)) {
			return;
		}
		Location actorLocation = this.zombie.getLocation();
		Location threatLocation = currentAttacker.getLocation();
		List<Vec3d> candidates = RetreatPlanner.candidateDestinations(
			toVector(actorLocation),
			toVector(threatLocation),
			MINIMUM_DESTINATION_DISTANCE,
			MAXIMUM_DESTINATION_DISTANCE,
			this.distanceSample,
			this.stableSide
		);
		Pathfinder pathfinder = this.zombie.getPathfinder();
		for (Vec3d candidate : candidates) {
			Location destination = new Location(
				this.zombie.getWorld(),
				candidate.x(),
				candidate.y(),
				candidate.z()
			);
			Pathfinder.PathResult path = pathfinder.findPath(destination);
			if (path != null && pathfinder.moveTo(path, this.settings.get().retreatSpeed())) {
				this.nextPathAt = now + PATH_REFRESH_TICKS;
				return;
			}
		}
		this.metrics.retreatPathFailed();
		this.nextPathAt = now + RETRY_AFTER_PATH_FAILURE_TICKS;
	}

	private LivingEntity resolvePreferredAttacker(
		final PaperDamageMemory.DamageSnapshot attack,
		final boolean heavyHit
	) {
		UUID preferredId = heavyHit ? attack.largestDamageAttackerId() : attack.latestAttackerId();
		UUID fallbackId = heavyHit ? attack.latestAttackerId() : attack.largestDamageAttackerId();
		LivingEntity preferred = livingEntity(preferredId);
		return preferred != null ? preferred : livingEntity(fallbackId);
	}

	private boolean hasReachedSafeDistance(final LivingEntity threat, final double safeDistance) {
		return this.zombie.getWorld() != threat.getWorld()
			|| this.zombie.getLocation().distanceSquared(threat.getLocation()) >= safeDistance * safeDistance;
	}

	private boolean isLiveThreat(final LivingEntity threat) {
		return threat != null
			&& threat.isValid()
			&& !threat.isDead()
			&& threat.getWorld() == this.zombie.getWorld()
			&& threat != this.zombie;
	}

	private static LivingEntity livingEntity(final UUID entityId) {
		Entity entity = Bukkit.getEntity(entityId);
		return entity instanceof LivingEntity living ? living : null;
	}

	private boolean enabled(final PaperSettings config) {
		return config.enabled() && config.zombieRetreatEnabled() && this.zombie.isValid() && !this.zombie.isDead();
	}

	private static Vec3d toVector(final Location location) {
		return new Vec3d(location.getX(), location.getY(), location.getZ());
	}

	private double maximumHealth() {
		AttributeInstance attribute = this.zombie.getAttribute(Attribute.MAX_HEALTH);
		return attribute == null ? Math.max(1.0, this.zombie.getHealth()) : Math.max(1.0, attribute.getValue());
	}
}

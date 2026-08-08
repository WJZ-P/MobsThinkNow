package com.wjz.mobsthinknow.paper.ai;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.PaperShieldSettings;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.ai.ShieldCombatPlanner;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;

/** 公开 Paper API 实现的举盾接近、观察、真实格挡延迟反击与放盾恢复状态机。 */
@SuppressWarnings("deprecation")
public final class PaperZombieShieldGoal implements Goal<Zombie> {
	private static final double MELEE_REACH = 2.45;

	private final Zombie zombie;
	private final GoalKey<Zombie> key;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperShieldMemory memory;
	private final PaperMetrics metrics;

	private LivingEntity target;
	private Phase phase = Phase.INACTIVE;
	private long guardDeadline = Long.MIN_VALUE;
	private long counterStrikeAt = Long.MIN_VALUE;
	private long strikeAt = Long.MIN_VALUE;
	private long strikeDeadline = Long.MIN_VALUE;
	private long recoveryDeadline = Long.MIN_VALUE;
	private long nextAttackAt;
	private long nextRepathAt;
	private boolean counterPending;

	public PaperZombieShieldGoal(
		final Zombie zombie,
		final GoalKey<Zombie> key,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperShieldMemory memory,
		final PaperMetrics metrics
	) {
		this.zombie = zombie;
		this.key = key;
		this.settings = settings;
		this.intelligence = intelligence;
		this.squads = squads;
		this.memory = memory;
		this.metrics = metrics;
	}

	@Override
	public boolean shouldActivate() {
		LivingEntity selected = this.currentTarget();
		PaperShieldSettings config = this.settings.get().zombieShieldTactics();
		if (!this.isEligible(selected, config)) {
			return false;
		}
		double maximum = config.raiseDistance();
		if (this.zombie.getLocation().distanceSquared(selected.getLocation()) > maximum * maximum) {
			return false;
		}
		this.target = selected;
		return true;
	}

	@Override
	public boolean shouldStayActive() {
		LivingEntity selected = this.currentTarget();
		PaperShieldSettings config = this.settings.get().zombieShieldTactics();
		if (selected != this.target || !this.isEligible(selected, config)) {
			return false;
		}
		double maximum = config.lowerDistance();
		return this.zombie.getLocation().distanceSquared(selected.getLocation()) <= maximum * maximum;
	}

	@Override
	public void start() {
		long now = Bukkit.getCurrentTick();
		this.phase = Phase.APPROACHING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.recoveryDeadline = Long.MIN_VALUE;
		this.nextRepathAt = now;
		this.counterPending = false;
		this.raiseShield();
	}

	@Override
	public void tick() {
		LivingEntity current = this.target;
		if (!PaperThreats.isLiveFor(this.zombie, current)) {
			return;
		}
		PaperShieldSettings config = this.settings.get().zombieShieldTactics();
		long now = Bukkit.getCurrentTick();
		this.faceTarget(current);
		this.captureBlock(current, config, now);
		switch (this.phase) {
			case STRIKING -> this.tickStrike(current, config, now);
			case RECOVERING -> this.tickRecovery(current, config, now);
			case INACTIVE, APPROACHING, GUARDING -> this.tickDefense(current, config, now);
		}
	}

	@Override
	public void stop() {
		this.lowerShield();
		this.zombie.getPathfinder().stopPathfinding();
		this.zombie.setArmsRaised(false);
		this.zombie.setAggressive(this.zombie.getTarget() != null);
		this.memory.discard(this.zombie);
		this.phase = Phase.INACTIVE;
		this.target = null;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.recoveryDeadline = Long.MIN_VALUE;
		this.counterPending = false;
	}

	@Override
	public GoalKey<Zombie> getKey() {
		return this.key;
	}

	@Override
	public EnumSet<GoalType> getTypes() {
		return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
	}

	private void tickDefense(
		final LivingEntity current,
		final PaperShieldSettings config,
		final long now
	) {
		this.raiseShield();
		if (!this.canReach(current)) {
			this.phase = Phase.APPROACHING;
			this.guardDeadline = Long.MIN_VALUE;
			this.moveTo(current.getLocation(), config, now);
			return;
		}
		this.zombie.getPathfinder().stopPathfinding();
		if (this.phase != Phase.GUARDING) {
			this.phase = Phase.GUARDING;
			this.guardDeadline = now + this.randomGuardDuration(config);
			this.metrics.shieldGuardStarted();
		}
		if (ShieldCombatPlanner.shouldOpenStrike(
			this.counterPending,
			now,
			this.counterStrikeAt,
			this.guardDeadline,
			now >= this.nextAttackAt
		)) {
			this.beginStrike(config, now);
		}
	}

	private void beginStrike(final PaperShieldSettings config, final long now) {
		this.lowerShield();
		this.phase = Phase.STRIKING;
		this.strikeAt = now + 1L;
		this.strikeDeadline = now + config.strikeWindowTicks();
		this.zombie.setAggressive(true);
		this.zombie.setArmsRaised(true);
		this.metrics.shieldStrikeWindowOpened();
	}

	private void tickStrike(
		final LivingEntity current,
		final PaperShieldSettings config,
		final long now
	) {
		this.lowerShield();
		if (now >= this.strikeAt && this.canReach(current)) {
			this.zombie.getPathfinder().stopPathfinding();
			this.zombie.swingMainHand();
			this.zombie.attack(current);
			this.metrics.shieldAttack();
			if (this.counterPending) {
				this.metrics.shieldCounterattack();
			}
			this.nextAttackAt = now + PaperWeaponProfile.cooldownTicks(
				this.zombie.getEquipment().getItemInMainHand().getType()
			);
			this.beginRecovery(config, now);
			return;
		}
		if (now >= this.strikeDeadline) {
			this.resumeDefense(now);
			return;
		}
		this.moveTo(current.getLocation(), config, now);
	}

	private void beginRecovery(final PaperShieldSettings config, final long now) {
		this.phase = Phase.RECOVERING;
		this.recoveryDeadline = now + this.randomCounterDelay(config);
		this.counterPending = false;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.zombie.setAggressive(false);
		this.zombie.setArmsRaised(false);
	}

	private void tickRecovery(
		final LivingEntity current,
		final PaperShieldSettings config,
		final long now
	) {
		this.lowerShield();
		this.faceTarget(current);
		if (now >= this.recoveryDeadline) {
			this.resumeDefense(now);
		}
	}

	private void resumeDefense(final long now) {
		this.phase = Phase.APPROACHING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterPending = false;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.recoveryDeadline = Long.MIN_VALUE;
		this.nextRepathAt = now;
		this.zombie.setArmsRaised(false);
		this.raiseShield();
	}

	private void captureBlock(
		final LivingEntity current,
		final PaperShieldSettings config,
		final long now
	) {
		PaperShieldMemory.BlockSignal signal = this.memory.consume(
			this.zombie, current, now, config.blockSignalMemoryTicks()
		);
		if (signal == null || this.counterPending || this.phase == Phase.STRIKING || this.phase == Phase.RECOVERING) {
			return;
		}
		this.counterPending = true;
		this.counterStrikeAt = signal.observedAt() + this.randomCounterDelay(config);
		this.metrics.shieldCounterScheduled();
	}

	private boolean moveTo(final Location destination, final PaperShieldSettings config, final long now) {
		if (now < this.nextRepathAt) {
			return this.zombie.getPathfinder().hasPath();
		}
		Pathfinder pathfinder = this.zombie.getPathfinder();
		Pathfinder.PathResult path = pathfinder.findPath(destination);
		boolean moving = path != null && pathfinder.moveTo(path, config.movementSpeed());
		this.nextRepathAt = now + config.repathTicks();
		if (!moving) {
			this.metrics.shieldPathFailed();
		}
		return moving;
	}

	private boolean isEligible(final LivingEntity selected, final PaperShieldSettings config) {
		PaperSettings root = this.settings.get();
		PaperSquadDirective directive = this.squads.directiveFor(this.zombie);
		return root.enabled()
			&& config.enabled()
			&& this.zombie.isValid()
			&& !this.zombie.isDead()
			&& !this.zombie.isInsideVehicle()
			&& this.intelligence.get(this.zombie) >= config.minimumIntelligence()
			&& hasShieldInOffHand(this.zombie)
			&& PaperThreats.isLiveFor(this.zombie, selected)
			&& (directive == null || directive.state() == MixedSquadState.ENGAGING);
	}

	private LivingEntity currentTarget() {
		LivingEntity shared = this.squads.sharedTargetFor(this.zombie);
		return PaperThreats.isLiveFor(this.zombie, shared) ? shared : this.zombie.getTarget();
	}

	private boolean canReach(final LivingEntity current) {
		double reach = MELEE_REACH + current.getWidth() * 0.25;
		return this.zombie.getLocation().distanceSquared(current.getLocation()) <= reach * reach
			&& this.zombie.hasLineOfSight(current);
	}

	/**
	 * Mob#lookAt 只驱动 LookControl，头部能立即追踪目标，但原版盾牌的入射角判定读取实体主体 yaw。
	 * 因此这里同时以每 tick 最多 35 度平滑转身，避免“头看着敌人、盾牌判定仍朝旧方向”。
	 */
	private void faceTarget(final LivingEntity current) {
		double deltaX = current.getX() - this.zombie.getX();
		double deltaZ = current.getZ() - this.zombie.getZ();
		if (deltaX * deltaX + deltaZ * deltaZ > 1.0E-8) {
			float desiredYaw = (float)Math.toDegrees(Math.atan2(-deltaX, deltaZ));
			float yawDelta = wrapDegrees(desiredYaw - this.zombie.getYaw());
			float nextYaw = this.zombie.getYaw() + Math.clamp(yawDelta, -35.0F, 35.0F);
			this.zombie.setRotation(nextYaw, this.zombie.getPitch());
		}
		this.zombie.lookAt(current, 55.0F, 55.0F);
	}

	private static float wrapDegrees(final float degrees) {
		float wrapped = degrees % 360.0F;
		if (wrapped >= 180.0F) {
			wrapped -= 360.0F;
		} else if (wrapped < -180.0F) {
			wrapped += 360.0F;
		}
		return wrapped;
	}

	private void raiseShield() {
		this.zombie.setAggressive(false);
		this.zombie.setArmsRaised(false);
		if (!this.zombie.hasActiveItem() || this.zombie.getActiveItemHand() != EquipmentSlot.OFF_HAND) {
			this.zombie.clearActiveItem();
			this.zombie.startUsingItem(EquipmentSlot.OFF_HAND);
		}
	}

	private void lowerShield() {
		if (this.zombie.hasActiveItem() && this.zombie.getActiveItemHand() == EquipmentSlot.OFF_HAND) {
			this.zombie.clearActiveItem();
		}
	}

	private int randomGuardDuration(final PaperShieldSettings config) {
		int range = config.maximumGuardTicks() - config.minimumGuardTicks() + 1;
		return ShieldCombatPlanner.guardDurationTicks(
			config.minimumGuardTicks(),
			config.maximumGuardTicks(),
			ThreadLocalRandom.current().nextInt(range)
		);
	}

	private int randomCounterDelay(final PaperShieldSettings config) {
		int range = config.maximumCounterDelayTicks() - config.minimumCounterDelayTicks() + 1;
		return ShieldCombatPlanner.counterDelayTicks(
			config.minimumCounterDelayTicks(),
			config.maximumCounterDelayTicks(),
			ThreadLocalRandom.current().nextInt(range)
		);
	}

	public static boolean hasShieldInOffHand(final Zombie zombie) {
		return zombie.getEquipment().getItemInOffHand().getType() == Material.SHIELD;
	}

	private enum Phase {
		INACTIVE,
		APPROACHING,
		GUARDING,
		STRIKING,
		RECOVERING
	}
}

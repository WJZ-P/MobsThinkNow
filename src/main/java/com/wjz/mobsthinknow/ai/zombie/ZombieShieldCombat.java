package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 单只盾卫的“举盾接近—观察—单次出手—重新举盾”状态机。
 *
 * <p>盾牌始终优先于进攻：进入六格交战带后先举盾，只有贴身且武器冷却完成时才会
 * 打开一次短攻击窗口。窗口来自两种博弈事件：目标刚刚攻击过，或目标在随机观察期内
 * 一直没有出手。一次挥击发生后立即回到守势；目标临时退开时也保持举盾追近，而不是
 * 放下盾牌盲目贴脸。</p>
 */
final class ZombieShieldCombat {
	private static final double SHIELD_RAISE_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double SHIELD_LOWER_DISTANCE_SQUARED = 7.5 * 7.5;
	private static final int MINIMUM_GUARD_TICKS = 12;
	private static final int MAXIMUM_GUARD_TICKS = 28;
	private static final int STRIKE_WINDOW_TICKS = 10;
	private static final long ATTACK_SIGNAL_MAX_AGE_TICKS = 20L;

	private final Zombie zombie;
	private Phase phase = Phase.INACTIVE;
	private int targetId = Integer.MIN_VALUE;
	private long guardDeadline = Long.MIN_VALUE;
	private long strikeDeadline = Long.MIN_VALUE;
	private long nextStrikeAt;
	private boolean counterPending;

	ZombieShieldCombat(final Zombie zombie) {
		this.zombie = zombie;
	}

	void tick(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final boolean hasLineOfSight
	) {
		long now = this.zombie.level().getGameTime();
		if (!this.isEligible(target, config, hasLineOfSight)) {
			this.deactivate();
			return;
		}

		if (this.targetId != target.getId()) {
			this.deactivate();
			this.targetId = target.getId();
		}

		double distanceSquared = this.zombie.distanceToSqr(target);
		if (this.phase == Phase.INACTIVE) {
			if (distanceSquared > SHIELD_RAISE_DISTANCE_SQUARED) {
				return;
			}
			this.beginApproach();
		} else if (distanceSquared > SHIELD_LOWER_DISTANCE_SQUARED) {
			this.deactivate();
			return;
		}

		this.captureIncomingAttack(target, now);
		if (this.phase == Phase.STRIKING) {
			if (now >= this.strikeDeadline) {
				// 目标在十 tick 窗口内躲开时收招重举盾，避免持续裸奔追击。
				this.resumeDefense(target, now);
			}
			return;
		}

		this.raiseShield();
		if (!this.zombie.isWithinMeleeAttackRange(target)) {
			this.phase = Phase.APPROACHING;
			this.guardDeadline = Long.MIN_VALUE;
			return;
		}

		// 已经进入出手距离：停住当前直线路径，举盾观察而不是继续挤进目标碰撞箱。
		this.zombie.getNavigation().stop();
		if (this.phase != Phase.GUARDING) {
			this.phase = Phase.GUARDING;
			this.guardDeadline = now + this.randomGuardDuration();
		}

		boolean weaponReady = now >= this.nextStrikeAt;
		if (shouldOpenStrike(this.counterPending, now, this.guardDeadline, weaponReady)) {
			this.beginStrike(now);
		}
	}

	void onAttackPerformed(final LivingEntity target) {
		long now = this.zombie.level().getGameTime();
		// 独立记录盾卫的武器冷却；即使关闭通用武器战术，守势阶段暂停原版 Goal 也不会冻结 CD。
		this.nextStrikeAt = now + ZombieWeaponCombat.attackCooldownTicks(this.zombie.getMainHandItem());
		if (this.phase != Phase.STRIKING || target.getId() != this.targetId) {
			return;
		}

		if (!ZombieArmory.hasShield(this.zombie) || ZombieArmory.isShieldDisabled(this.zombie)) {
			this.deactivate();
			return;
		}
		this.resumeDefense(target, now);
	}

	boolean hasIntent() {
		return this.phase != Phase.INACTIVE;
	}

	boolean holdsPosition() {
		return this.phase == Phase.GUARDING;
	}

	boolean blocksAttack() {
		return this.phase == Phase.APPROACHING || this.phase == Phase.GUARDING;
	}

	boolean isStrikeWindow() {
		return this.phase == Phase.STRIKING;
	}

	void stop() {
		this.deactivate();
	}

	private boolean isEligible(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final boolean hasLineOfSight
	) {
		return config.enabled
			&& config.zombieAiEnabled
			&& config.armedSquads
			&& target.isAlive()
			&& hasLineOfSight
			&& ZombieArmory.hasShield(this.zombie)
			&& !ZombieArmory.isShieldDisabled(this.zombie);
	}

	private void captureIncomingAttack(final LivingEntity target, final long now) {
		ZombieShieldMemory.AttackSignal signal = ZombieShieldMemory.consume(this.zombie);
		if (signal == null
			|| signal.attacker().getId() != target.getId()
			|| !isFreshAttackSignal(now, signal.gameTime())) {
			return;
		}
		this.counterPending = true;
	}

	private void beginApproach() {
		this.phase = Phase.APPROACHING;
		this.guardDeadline = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.counterPending = false;
		this.raiseShield();
	}

	private void beginStrike(final long now) {
		this.lowerShield();
		this.phase = Phase.STRIKING;
		this.guardDeadline = Long.MIN_VALUE;
		this.strikeDeadline = now + STRIKE_WINDOW_TICKS;
		this.counterPending = false;
	}

	private void resumeDefense(final LivingEntity target, final long now) {
		this.counterPending = false;
		this.strikeDeadline = Long.MIN_VALUE;
		this.raiseShield();
		if (this.zombie.isWithinMeleeAttackRange(target)) {
			this.phase = Phase.GUARDING;
			this.guardDeadline = now + this.randomGuardDuration();
			this.zombie.getNavigation().stop();
		} else {
			this.phase = Phase.APPROACHING;
			this.guardDeadline = Long.MIN_VALUE;
		}
	}

	private void deactivate() {
		this.lowerShield();
		this.phase = Phase.INACTIVE;
		this.targetId = Integer.MIN_VALUE;
		this.guardDeadline = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.counterPending = false;
		ZombieShieldMemory.discard(this.zombie);
	}

	private void raiseShield() {
		this.zombie.setAggressive(false);
		if (!this.zombie.isUsingItem()) {
			this.zombie.startUsingItem(InteractionHand.OFF_HAND);
		}
	}

	private void lowerShield() {
		if (this.zombie.isUsingItem() && this.zombie.getUsedItemHand() == InteractionHand.OFF_HAND) {
			this.zombie.stopUsingItem();
		}
	}

	private int randomGuardDuration() {
		int range = MAXIMUM_GUARD_TICKS - MINIMUM_GUARD_TICKS + 1;
		return guardDurationTicks(
			MINIMUM_GUARD_TICKS,
			MAXIMUM_GUARD_TICKS,
			this.zombie.getRandom().nextInt(range)
		);
	}

	static boolean shouldOpenStrike(
		final boolean counterPending,
		final long now,
		final long guardDeadline,
		final boolean attackReady
	) {
		return attackReady && (counterPending || now >= guardDeadline);
	}

	static boolean isFreshAttackSignal(final long now, final long signalTime) {
		long age = now - signalTime;
		return age >= 0L && age <= ATTACK_SIGNAL_MAX_AGE_TICKS;
	}

	static int guardDurationTicks(final int minimum, final int maximum, final int zeroBasedRoll) {
		if (zeroBasedRoll < 0 || zeroBasedRoll > maximum - minimum) {
			throw new IllegalArgumentException("Shield guard duration roll is outside the configured range");
		}
		return minimum + zeroBasedRoll;
	}

	private enum Phase {
		INACTIVE,
		APPROACHING,
		GUARDING,
		STRIKING
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 单只盾卫的“举盾接近—观察—单次出手—重新举盾”状态机。
 *
 * <p>盾牌始终优先于进攻：进入六格交战带后先举盾，只有贴身且武器冷却完成时才会
 * 打开一次短攻击窗口。成功格挡不会在同一 tick 瞬间反击，而是继续举盾随机等待 2～4 tick，
 * 随后明确放下盾牌再出手。挥击后同样保留 2～4 tick 的无盾恢复间隙，再回到守势；目标
 * 临时退开时仍保持举盾追近，而不是放下盾牌盲目贴脸。</p>
 */
final class ZombieShieldCombat {
	private static final double SHIELD_RAISE_DISTANCE_SQUARED = 6.0 * 6.0;
	private static final double SHIELD_LOWER_DISTANCE_SQUARED = 7.5 * 7.5;
	private static final int MINIMUM_GUARD_TICKS = 12;
	private static final int MAXIMUM_GUARD_TICKS = 28;
	private static final int MINIMUM_COUNTER_DELAY_TICKS = 2;
	private static final int MAXIMUM_COUNTER_DELAY_TICKS = 4;
	private static final int STRIKE_WINDOW_TICKS = 10;
	private static final long ATTACK_SIGNAL_MAX_AGE_TICKS = 20L;

	private final Zombie zombie;
	private Phase phase = Phase.INACTIVE;
	private int targetId = Integer.MIN_VALUE;
	private long guardDeadline = Long.MIN_VALUE;
	private long counterStrikeAt = Long.MIN_VALUE;
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

		this.captureSuccessfulBlock(target, now);
		if (this.phase == Phase.STRIKING || this.phase == Phase.RECOVERING) {
			// 攻击窗口和攻击后的恢复间隙都强制放盾，避免同一 tick 内“挥剑后立刻重举”被客户端合并掉。
			this.lowerShield();
			if (now >= this.strikeDeadline) {
				// 攻击落空时由十 tick 窗口兜底；已经出手时则等待短恢复间隙结束。
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
		if (shouldOpenStrike(
			this.counterPending,
			now,
			this.counterStrikeAt,
			this.guardDeadline,
			weaponReady
		)) {
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
		this.beginRecovery(now);
	}

	boolean hasIntent() {
		return this.phase != Phase.INACTIVE;
	}

	boolean holdsPosition() {
		return this.phase == Phase.GUARDING;
	}

	boolean blocksAttack() {
		return this.phase == Phase.APPROACHING
			|| this.phase == Phase.GUARDING
			|| this.phase == Phase.RECOVERING;
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

	private void captureSuccessfulBlock(final LivingEntity target, final long now) {
		ZombieShieldMemory.BlockSignal signal = ZombieShieldMemory.consume(this.zombie);
		if (signal == null
			|| signal.attacker().getId() != target.getId()
			|| !isFreshAttackSignal(now, signal.gameTime())) {
			return;
		}
		if (this.counterPending) {
			return;
		}
		this.counterPending = true;
		// 从真实格挡发生的 tick 起算，而不是从 AI 下一次消费信号时起算，确保视觉延迟严格为 2～4 tick。
		this.counterStrikeAt = signal.gameTime() + this.randomCounterDelay();
	}

	private void beginApproach() {
		this.phase = Phase.APPROACHING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.counterPending = false;
		this.raiseShield();
	}

	private void beginStrike(final long now) {
		this.lowerShield();
		this.phase = Phase.STRIKING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeDeadline = now + STRIKE_WINDOW_TICKS;
		this.counterPending = false;
	}

	private void beginRecovery(final long now) {
		this.lowerShield();
		this.phase = Phase.RECOVERING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		// 复用同一 2～4 tick 分布，让客户端至少看到数帧明确的“已放盾、正在收招”状态。
		this.strikeDeadline = now + this.randomCounterDelay();
		this.counterPending = false;
	}

	private void resumeDefense(final LivingEntity target, final long now) {
		this.counterPending = false;
		this.counterStrikeAt = Long.MIN_VALUE;
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
		this.counterStrikeAt = Long.MIN_VALUE;
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

	private int randomCounterDelay() {
		int range = MAXIMUM_COUNTER_DELAY_TICKS - MINIMUM_COUNTER_DELAY_TICKS + 1;
		return counterDelayTicks(this.zombie.getRandom().nextInt(range));
	}

	static boolean shouldOpenStrike(
		final boolean counterPending,
		final long now,
		final long counterStrikeAt,
		final long guardDeadline,
		final boolean attackReady
	) {
		if (!attackReady) {
			return false;
		}
		return counterPending ? now >= counterStrikeAt : now >= guardDeadline;
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

	static int counterDelayTicks(final int zeroBasedRoll) {
		if (zeroBasedRoll < 0 || zeroBasedRoll > MAXIMUM_COUNTER_DELAY_TICKS - MINIMUM_COUNTER_DELAY_TICKS) {
			throw new IllegalArgumentException("Shield counter delay roll is outside the configured range");
		}
		return MINIMUM_COUNTER_DELAY_TICKS + zeroBasedRoll;
	}

	private enum Phase {
		INACTIVE,
		APPROACHING,
		GUARDING,
		STRIKING,
		RECOVERING
	}
}

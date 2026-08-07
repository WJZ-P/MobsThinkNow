package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.SquadShieldOrder;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 单只盾卫的“举盾接近—观察—单次出手—重新举盾”状态机。
 *
 * <p>盾牌始终优先于进攻：进入六格交战带后先举盾，只有贴身且武器冷却完成时才会
 * 打开一次短攻击窗口。成功格挡不会在同一 tick 瞬间反击，而是继续举盾随机等待 2～4 tick，
 * 随后明确放下盾牌再出手。高智力个体可把这次反击替换为带独立命中帧的副手盾击；
 * 挥击后同样保留 2～4 tick 的无盾恢复间隙，再回到守势；目标
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
	private static final int SHIELD_BASH_DURATION_TICKS = 14;
	private static final int SHIELD_BASH_HIT_OFFSET_TICKS = 5;
	private static final double SHIELD_BASH_REACH_SQUARED = 3.0 * 3.0;
	private static final long ATTACK_SIGNAL_MAX_AGE_TICKS = 20L;

	private final Zombie zombie;
	private Phase phase = Phase.INACTIVE;
	private int targetId = Integer.MIN_VALUE;
	private long guardDeadline = Long.MIN_VALUE;
	private long counterStrikeAt = Long.MIN_VALUE;
	private long strikeDeadline = Long.MIN_VALUE;
	private long bashHitAt = Long.MIN_VALUE;
	private long nextStrikeAt;
	private boolean counterPending;
	private boolean bashPending;
	private boolean bashHitApplied;

	ZombieShieldCombat(final Zombie zombie) {
		this.zombie = zombie;
	}

	/** 保留单兵测试和旧调用入口；没有小队职责时行为与原状态机完全一致。 */
	void tick(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final boolean hasLineOfSight
	) {
		this.tick(target, config, hasLineOfSight, SquadShieldOrder.NONE, true);
	}

	void tick(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final boolean hasLineOfSight,
		final SquadShieldOrder squadOrder,
		final boolean reachedShieldWallSlot
	) {
		// 新生成的盾卫立即带图案；旧存档中的普通原版盾也在首次进入盾牌状态机时平滑升级。
		ZombieShieldDesign.decorateIfPlain(this.zombie);
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
		if (squadOrder == SquadShieldOrder.GUARD) {
			this.tickSquadGuard(target, reachedShieldWallSlot);
			return;
		}
		if (squadOrder == SquadShieldOrder.STRIKE) {
			this.tickSquadStrike(target, now, distanceSquared);
			return;
		}
		if (this.phase == Phase.INACTIVE) {
			if (distanceSquared > SHIELD_RAISE_DISTANCE_SQUARED) {
				return;
			}
			this.beginApproach();
		} else if (distanceSquared > SHIELD_LOWER_DISTANCE_SQUARED) {
			this.deactivate();
			return;
		}

		this.captureSuccessfulBlock(target, config, now);
		if (this.phase == Phase.BASHING) {
			this.tickBash(target, config, now);
			return;
		}
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
			if (this.counterPending && this.bashPending) {
				this.beginBash(now);
			} else {
				this.beginStrike(now);
			}
		}
	}

	/**
	 * 盾墙闭合时始终举盾；在抵达自己的横向槽位前仍允许导航，避免旧版一进入近战距离
	 * 就停步，导致多名盾卫再次堆回同一个点。
	 */
	private void tickSquadGuard(final LivingEntity target, final boolean reachedShieldWallSlot) {
		this.cancelOffensiveState();
		ZombieShieldMemory.discard(this.zombie);
		this.raiseShield();
		if (reachedShieldWallSlot && this.zombie.isWithinMeleeAttackRange(target)) {
			this.phase = Phase.GUARDING;
			this.zombie.getNavigation().stop();
			return;
		}
		this.phase = Phase.APPROACHING;
	}

	/** 唯一轮到的出击者明确放盾；若武器仍在冷却，则先守住缝隙而不是空挥。 */
	private void tickSquadStrike(
		final LivingEntity target,
		final long now,
		final double distanceSquared
	) {
		if (distanceSquared > SHIELD_LOWER_DISTANCE_SQUARED) {
			this.deactivate();
			return;
		}
		if (this.phase == Phase.RECOVERING && now < this.strikeDeadline) {
			this.lowerShield();
			return;
		}
		if (!this.zombie.isWithinMeleeAttackRange(target)) {
			this.cancelOffensiveState();
			this.lowerShield();
			this.phase = Phase.APPROACHING;
			return;
		}
		if (now < this.nextStrikeAt) {
			this.cancelOffensiveState();
			this.raiseShield();
			this.phase = Phase.GUARDING;
			this.zombie.getNavigation().stop();
			return;
		}
		if (this.phase != Phase.STRIKING || now >= this.strikeDeadline) {
			this.beginStrike(now);
		}
		this.lowerShield();
	}

	private void cancelOffensiveState() {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SHIELD_BASH);
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.bashHitAt = Long.MIN_VALUE;
		this.counterPending = false;
		this.bashPending = false;
		this.bashHitApplied = false;
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
		return this.phase == Phase.GUARDING || this.phase == Phase.BASHING;
	}

	boolean blocksAttack() {
		return this.phase == Phase.APPROACHING
			|| this.phase == Phase.GUARDING
			|| this.phase == Phase.BASHING
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

	private void captureSuccessfulBlock(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now
	) {
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
		this.bashPending = shouldScheduleBash(
			config.shieldBashes,
			ZombieIntelligence.get(this.zombie),
			config.shieldBashMinimumIntelligence,
			this.zombie.getRandom().nextDouble(),
			config.shieldBashChance
		);
		// 从真实格挡发生的 tick 起算，而不是从 AI 下一次消费信号时起算，确保视觉延迟严格为 2～4 tick。
		this.counterStrikeAt = signal.gameTime() + this.randomCounterDelay();
	}

	private void beginApproach() {
		this.phase = Phase.APPROACHING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.counterPending = false;
		this.bashPending = false;
		this.bashHitApplied = false;
		this.raiseShield();
	}

	private void beginStrike(final long now) {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SHIELD_BASH);
		this.lowerShield();
		this.phase = Phase.STRIKING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeDeadline = now + STRIKE_WINDOW_TICKS;
		this.counterPending = false;
		this.bashPending = false;
	}

	private void beginBash(final long now) {
		this.lowerShield();
		this.phase = Phase.BASHING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.bashHitAt = now + SHIELD_BASH_HIT_OFFSET_TICKS;
		this.strikeDeadline = now + SHIELD_BASH_DURATION_TICKS;
		this.nextStrikeAt = now + ZombieWeaponCombat.attackCooldownTicks(this.zombie.getMainHandItem());
		this.counterPending = false;
		this.bashPending = false;
		this.bashHitApplied = false;
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		ZombieBodyLanguage.play(this.zombie, ZombieBodyAction.SHIELD_BASH);
		SmartZombieMetrics.shieldBash();
	}

	private void tickBash(
		final LivingEntity target,
		final MobsThinkNowConfig config,
		final long now
	) {
		this.lowerShield();
		this.zombie.getNavigation().stop();
		this.zombie.setAggressive(false);
		this.zombie.getLookControl().setLookAt(target, 40.0F, 40.0F);
		if (!this.bashHitApplied && now >= this.bashHitAt) {
			this.bashHitApplied = true;
			this.performBashHit(target, config);
		}
		if (now >= this.strikeDeadline) {
			ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SHIELD_BASH);
			this.resumeDefense(target, now);
		}
	}

	private void performBashHit(final LivingEntity target, final MobsThinkNowConfig config) {
		if (!(this.zombie.level() instanceof ServerLevel level)
			|| !target.isAlive()
			|| this.zombie.distanceToSqr(target) > SHIELD_BASH_REACH_SQUARED
			|| !this.zombie.getSensing().hasLineOfSight(target)) {
			return;
		}

		this.zombie.swing(InteractionHand.OFF_HAND);
		target.hurtServer(
			level,
			this.zombie.damageSources().mobAttack(this.zombie),
			(float)config.shieldBashDamage
		);
		target.knockback(
			config.shieldBashKnockback,
			this.zombie.getX() - target.getX(),
			this.zombie.getZ() - target.getZ()
		);
		SmartZombieMetrics.shieldBashHit();
		level.playSound(
			null,
			this.zombie.getX(),
			this.zombie.getY(),
			this.zombie.getZ(),
			SoundEvents.SHIELD_BLOCK.value(),
			SoundSource.HOSTILE,
			0.9F,
			0.78F + this.zombie.getRandom().nextFloat() * 0.12F
		);
		level.sendParticles(
			ParticleTypes.SWEEP_ATTACK,
			target.getX(),
			target.getY(0.55),
			target.getZ(),
			1,
			0.1,
			0.1,
			0.1,
			0.0
		);
	}

	private void beginRecovery(final long now) {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SHIELD_BASH);
		this.lowerShield();
		this.phase = Phase.RECOVERING;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		// 复用同一 2～4 tick 分布，让客户端至少看到数帧明确的“已放盾、正在收招”状态。
		this.strikeDeadline = now + this.randomCounterDelay();
		this.counterPending = false;
		this.bashPending = false;
		this.bashHitApplied = false;
	}

	private void resumeDefense(final LivingEntity target, final long now) {
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SHIELD_BASH);
		this.counterPending = false;
		this.bashPending = false;
		this.bashHitApplied = false;
		this.bashHitAt = Long.MIN_VALUE;
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
		ZombieBodyLanguage.stop(this.zombie, ZombieBodyAction.SHIELD_BASH);
		this.lowerShield();
		this.phase = Phase.INACTIVE;
		this.targetId = Integer.MIN_VALUE;
		this.guardDeadline = Long.MIN_VALUE;
		this.counterStrikeAt = Long.MIN_VALUE;
		this.strikeDeadline = Long.MIN_VALUE;
		this.bashHitAt = Long.MIN_VALUE;
		this.counterPending = false;
		this.bashPending = false;
		this.bashHitApplied = false;
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

	static boolean shouldScheduleBash(
		final boolean enabled,
		final int intelligence,
		final int minimumIntelligence,
		final double randomRoll,
		final double chance
	) {
		return enabled
			&& intelligence >= minimumIntelligence
			&& randomRoll >= 0.0
			&& randomRoll < chance;
	}

	private enum Phase {
		INACTIVE,
		APPROACHING,
		GUARDING,
		BASHING,
		STRIKING,
		RECOVERING
	}
}

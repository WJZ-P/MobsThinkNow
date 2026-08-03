package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.activity.TacticalActivity;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class SmartZombieAttackGoal extends ZombieAttackGoal {
	private final Zombie zombie;
	private final ZombieTacticalController controller;
	private final ZombieWeaponCombat weaponCombat;
	private final TacticalActivityLease.Handle activityLease =
		TacticalActivityLease.handle(TacticalActivity.MELEE);

	public SmartZombieAttackGoal(final Zombie zombie, final double speedModifier, final boolean trackTarget) {
		super(zombie, speedModifier, trackTarget);
		this.zombie = zombie;
		this.controller = new ZombieTacticalController(zombie);
		this.weaponCombat = new ZombieWeaponCombat(zombie);
	}

	/**
	 * GoalSelector 在尚未运行本 Goal 时调用。先完成一次有限感知，再保留原版
	 * {@link ZombieAttackGoal#canUse()} 对路径、目标和冷却等条件的判断。
	 */
	@Override
	public boolean canUse() {
		if (!ConfigManager.get().enabled || !ConfigManager.get().zombieAiEnabled) {
			return super.canUse();
		}
		if (ZombieAirAssault.suppressGroundCombat(this.zombie, ConfigManager.get())) {
			return false;
		}

		LivingEntity target = this.zombie.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		this.controller.observe(target);
		return this.controller.hasTrackableTarget()
			&& super.canUse()
			&& this.activityLease.canAcquire(this.zombie, this.zombie.level().getGameTime());
	}

	/**
	 * Goal 已经运行时由 GoalSelector 周期调用。即使原版路径暂时结束，只要仍有小队命令或最后目击
	 * 位置，就允许 Goal 继续执行；否则会议/包抄会被原版的短暂寻路失败提前打断。
	 */
	@Override
	public boolean canContinueToUse() {
		if (!ConfigManager.get().enabled || !ConfigManager.get().zombieAiEnabled) {
			return super.canContinueToUse();
		}
		if (ZombieAirAssault.suppressGroundCombat(this.zombie, ConfigManager.get())) {
			return false;
		}

		LivingEntity target = this.zombie.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		this.controller.observe(target);
		MobsThinkNowConfig config = ConfigManager.get();
		return this.controller.hasTrackableTarget()
			&& this.activityLease.owns(this.zombie, this.zombie.level().getGameTime())
			&& (super.canContinueToUse()
				|| this.controller.hasTacticalIntent()
				|| this.weaponCombat.hasTacticalIntent(config));
	}

	@Override
	public void start() {
		this.activityLease.acquire(this.zombie, this.zombie.level().getGameTime());
		super.start();
	}

	/** 每个 AI tick 先执行战术命令；只有需要正面追击或挥击时才调用原版 tick。 */
	@Override
	public void tick() {
		if (!this.activityLease.renew(this.zombie, this.zombie.level().getGameTime())) {
			return;
		}
		LivingEntity target = this.zombie.getTarget();
		if (target == null) {
			return;
		}

		this.controller.observe(target);
		MobsThinkNowConfig config = ConfigManager.get();
		this.controller.tick(target);
		if (this.controller.shouldRunVanillaCombat(target)) {
			// 盾卫接近与单次反击由盾牌状态机控制节奏，跳过会让斧手另行准备跳劈的常规武器层。
			if (this.controller.hasShieldCombatIntent() || this.weaponCombat.tick(target, config)) {
				super.tick();
			}
		}
	}

	/** Goal 真正退出时停止原版导航，并清理本地命令快照。 */
	@Override
	public void stop() {
		super.stop();
		this.weaponCombat.stop();
		this.controller.stop();
		this.activityLease.release(this.zombie);
	}

	/** 盾牌正面包抄尚未完成时禁止过早挥击，避免侧翼又被拉回玩家正前方。 */
	@Override
	protected boolean canPerformAttack(final LivingEntity target) {
		if (this.controller.shouldHoldAttack(target)) {
			return false;
		}

		MobsThinkNowConfig config = ConfigManager.get();
		boolean handledWeapon = this.weaponCombat.handlesCurrentWeapon(config);
		if (this.controller.isShieldStrikeWindow()) {
			return (!handledWeapon || this.weaponCombat.isAttackCooldownReady(config))
				&& this.zombie.isWithinMeleeAttackRange(target)
				&& this.zombie.getSensing().hasLineOfSight(target);
		}
		if (!handledWeapon) {
			return super.canPerformAttack(target);
		}
		return this.weaponCombat.canPerformAttack(target)
			&& this.zombie.isWithinMeleeAttackRange(target)
			&& this.zombie.getSensing().hasLineOfSight(target);
	}

	/**
	 * 武装小队的斧手命中格挡目标后禁用盾牌。26.1.2 中怪物普通挥击不会走
	 * activeItem 的原版破盾判定，所以在这次挥击真正发生后显式补一次。
	 */
	@Override
	protected void checkAndPerformAttack(final LivingEntity target) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (this.weaponCombat.handlesCurrentWeapon(config)) {
			if (!this.canPerformAttack(target)) {
				return;
			}

			boolean targetWasBlocking = target.isBlocking();
			boolean shieldStrike = this.controller.isShieldStrikeWindow();
			boolean critical = !shieldStrike && this.weaponCombat.isCriticalLeapWindow();
			this.resetAttackCooldown();
			this.zombie.swing(InteractionHand.MAIN_HAND);
			this.weaponCombat.performAttack(target, critical);
			this.weaponCombat.onAttackPerformed(target);
			this.controller.onAttackPerformed(target);
			if (targetWasBlocking) {
				ZombieArmory.tryBreakShield(this.zombie, target, config);
			}
			return;
		}

		boolean attackPerformed = this.canPerformAttack(target);
		boolean blockedHitLanding = attackPerformed && target.isBlocking();
		super.checkAndPerformAttack(target);
		if (attackPerformed) {
			this.controller.onAttackPerformed(target);
		}
		if (blockedHitLanding) {
			ZombieArmory.tryBreakShield(this.zombie, target, config);
		}
	}
}

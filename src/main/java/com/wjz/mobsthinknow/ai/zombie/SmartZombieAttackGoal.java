package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class SmartZombieAttackGoal extends ZombieAttackGoal {
	private final Zombie zombie;
	private final ZombieTacticalController controller;

	public SmartZombieAttackGoal(final Zombie zombie, final double speedModifier, final boolean trackTarget) {
		super(zombie, speedModifier, trackTarget);
		this.zombie = zombie;
		this.controller = new ZombieTacticalController(zombie);
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

		LivingEntity target = this.zombie.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		this.controller.observe(target);
		return this.controller.hasTrackableTarget() && super.canUse();
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

		LivingEntity target = this.zombie.getTarget();
		if (target == null || !target.isAlive()) {
			return false;
		}

		this.controller.observe(target);
		return this.controller.hasTrackableTarget() && (super.canContinueToUse() || this.controller.hasTacticalIntent());
	}

	/** 每个 AI tick 先执行战术命令；只有需要正面追击或挥击时才调用原版 tick。 */
	@Override
	public void tick() {
		LivingEntity target = this.zombie.getTarget();
		if (target == null) {
			return;
		}

		this.controller.observe(target);
		this.controller.tick(target);
		if (this.controller.shouldRunVanillaCombat(target)) {
			super.tick();
		}
	}

	/** Goal 真正退出时停止原版导航，并清理本地命令快照。 */
	@Override
	public void stop() {
		super.stop();
		this.controller.stop();
	}

	/** 盾牌正面包抄尚未完成时禁止过早挥击，避免侧翼又被拉回玩家正前方。 */
	@Override
	protected boolean canPerformAttack(final LivingEntity target) {
		return !this.controller.shouldHoldFrontalAttack(target) && super.canPerformAttack(target);
	}
}

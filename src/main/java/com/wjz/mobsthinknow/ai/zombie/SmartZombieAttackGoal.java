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

	@Override
	public void stop() {
		super.stop();
		this.controller.stop();
	}

	@Override
	protected boolean canPerformAttack(final LivingEntity target) {
		return !this.controller.shouldHoldFrontalAttack(target) && super.canPerformAttack(target);
	}
}

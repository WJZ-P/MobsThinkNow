package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * 原版地面长矛 Goal 的薄包装：普通持矛僵尸语义不变，空袭兵则必须等弹药耗尽并落地。
 */
public final class SmartZombieSpearUseGoal extends SpearUseGoal<Zombie> {
	private final Zombie zombie;

	public SmartZombieSpearUseGoal(
		final Zombie zombie,
		final double speedModifierWhenCharging,
		final double speedModifierWhenRepositioning,
		final float approachDistance,
		final float targetInRangeRadius
	) {
		super(zombie, speedModifierWhenCharging, speedModifierWhenRepositioning, approachDistance, targetInRangeRadius);
		this.zombie = zombie;
	}

	@Override
	public boolean canUse() {
		return !ZombieAirAssault.suppressGroundCombat(this.zombie, ConfigManager.get())
			&& super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return !ZombieAirAssault.suppressGroundCombat(this.zombie, ConfigManager.get())
			&& super.canContinueToUse();
	}
}

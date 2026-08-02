package com.wjz.mobsthinknow.ai.enderman;

import com.wjz.mobsthinknow.config.ConfigManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.SpearUseGoal;
import net.minecraft.world.entity.monster.EnderMan;

/** 给虚空枪骑复用原版动能长矛状态机，并在每轮冲锋前做一次有限侧后方换位。 */
public final class EndermanVoidLancerGoal extends SpearUseGoal<EnderMan> {
	private final EnderMan enderman;

	public EndermanVoidLancerGoal(final EnderMan enderman) {
		super(enderman, 1.30, 1.12, 12.0F, 2.75F);
		this.enderman = enderman;
	}

	@Override
	public boolean canUse() {
		return enabled()
			&& EndermanProfessionProfile.get(this.enderman) == EndermanProfession.VOID_LANCER
			&& !EndermanCreeperDeliveryGoal.isCarryingCreeper(this.enderman)
			&& super.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return enabled()
			&& EndermanProfessionProfile.get(this.enderman) == EndermanProfession.VOID_LANCER
			&& !EndermanCreeperDeliveryGoal.isCarryingCreeper(this.enderman)
			&& super.canContinueToUse();
	}

	@Override
	public void start() {
		LivingEntity target = this.enderman.getTarget();
		if (target != null
			&& this.enderman.distanceToSqr(target) >= 6.0 * 6.0
			&& this.enderman.distanceToSqr(target) <= 24.0 * 24.0) {
			EndermanCombatTeleport.tryFlank(this.enderman, target, 8.5, 6);
		}
		super.start();
		SmartEndermanMetrics.spearCharge();
	}

	private static boolean enabled() {
		var config = ConfigManager.get();
		return config.enabled && config.endermanAiEnabled;
	}
}

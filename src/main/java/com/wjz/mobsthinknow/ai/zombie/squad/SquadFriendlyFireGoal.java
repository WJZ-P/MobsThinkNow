package com.wjz.mobsthinknow.ai.zombie.squad;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;

/** 苦力怕与蜘蛛使用的混编友军误伤过滤器；队外伤害仍完整委托原版反击逻辑。 */
public final class SquadFriendlyFireGoal extends HurtByTargetGoal {
	private final PathfinderMob mob;

	public SquadFriendlyFireGoal(final PathfinderMob mob) {
		super(mob);
		this.mob = mob;
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (config.enabled
			&& config.squadIgnoreFriendlyFire
			&& this.mob.getLastHurtByMob() instanceof Mob attacker
			&& ZombieSquadCoordinator.areSquadmates(this.mob, attacker)) {
			this.mob.setLastHurtByMob(null);
			return false;
		}
		return super.canUse();
	}
}

package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

/** 混编小队中的骷髅会消费队友误伤记录，不因一支穿过前排的箭立刻与僵尸内战。 */
public final class SquadSkeletonHurtByTargetGoal extends HurtByTargetGoal {
	private final AbstractSkeleton skeleton;

	public SquadSkeletonHurtByTargetGoal(final AbstractSkeleton skeleton) {
		super(skeleton);
		this.skeleton = skeleton;
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (config.enabled
			&& config.skeletonAiEnabled
			&& config.squadIgnoreFriendlyFire
			&& this.skeleton.getLastHurtByMob() instanceof Mob attacker
			&& ZombieSquadCoordinator.areSquadmates(this.skeleton, attacker)) {
			this.skeleton.setLastHurtByMob(null);
			return false;
		}
		return super.canUse();
	}
}

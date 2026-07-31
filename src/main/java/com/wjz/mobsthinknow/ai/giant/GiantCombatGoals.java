package com.wjz.mobsthinknow.ai.giant;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Giant;

/**
 * 给原版没有战斗 Goal 的 Giant 提供可热关闭的基础 Goal。
 *
 * <p>不能只在构造时检查配置，否则 {@code /mtn reload} 关闭巨人 AI 后，已经存在的巨人仍会继续
 * 索敌和攻击。每个包装器都在启动和续行入口检查实时配置，让存量实体在下一拍停手。</p>
 */
public final class GiantCombatGoals {
	private GiantCombatGoals() {
	}

	public static boolean enabled() {
		MobsThinkNowConfig config = ConfigManager.get();
		return config.enabled && config.giantZombieAiEnabled;
	}

	public static final class Melee extends MeleeAttackGoal {
		public Melee(final Giant giant, final double speedModifier, final boolean followTargetEvenIfNotSeen) {
			super(giant, speedModifier, followTargetEvenIfNotSeen);
		}

		@Override
		public boolean canUse() {
			return enabled() && !ConfigManager.get().giantZombieMeleeActions && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return enabled() && !ConfigManager.get().giantZombieMeleeActions && super.canContinueToUse();
		}
	}

	public static final class Stroll extends WaterAvoidingRandomStrollGoal {
		public Stroll(final Giant giant, final double speedModifier) {
			super(giant, speedModifier);
		}

		@Override
		public boolean canUse() {
			return enabled() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return enabled() && super.canContinueToUse();
		}
	}

	public static final class Nearest<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
		public Nearest(final Giant giant, final Class<T> targetType, final boolean mustSee) {
			super(giant, targetType, mustSee);
		}

		@Override
		public boolean canUse() {
			return enabled() && super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return enabled() && super.canContinueToUse();
		}
	}

	public static final class HurtBy extends HurtByTargetGoal {
		private final Giant giant;

		public HurtBy(final Giant giant) {
			super(giant);
			this.giant = giant;
		}

		@Override
		public boolean canUse() {
			if (!enabled()) {
				return false;
			}
			MobsThinkNowConfig config = ConfigManager.get();
			if (config.squadIgnoreFriendlyFire
				&& this.giant.getLastHurtByMob() instanceof Mob attacker
				&& ZombieSquadCoordinator.areSquadmates(this.giant, attacker)) {
				this.giant.setLastHurtByMob(null);
				return false;
			}
			return super.canUse();
		}

		@Override
		public boolean canContinueToUse() {
			return enabled() && super.canContinueToUse();
		}
	}
}

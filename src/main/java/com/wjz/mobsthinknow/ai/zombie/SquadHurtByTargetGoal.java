package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;

/**
 * 小队感知版的仇恨反击 Goal：同小队队友的误伤不转移仇恨，继续合力攻击原目标。
 *
 * <p>只拦"攻击者是同队僵尸"这一种情况；玩家、铁傀儡或队外僵尸的攻击仍走原版
 * 反击与警报逻辑。不触发 canUse 也就不会向周围僵尸广播错误的仇恨目标。</p>
 */
public final class SquadHurtByTargetGoal extends HurtByTargetGoal {
	private final Zombie zombie;

	public SquadHurtByTargetGoal(final Zombie zombie) {
		super(zombie);
		// 保留原版语义：僵尸的警报不会把僵尸猪灵卷进战斗。
		this.setAlertOthers(ZombifiedPiglin.class);
		this.zombie = zombie;
	}

	@Override
	public boolean canUse() {
		MobsThinkNowConfig config = ConfigManager.get();
		if (config.enabled
			&& config.zombieAiEnabled
			&& config.squadIgnoreFriendlyFire
			&& this.zombie.getLastHurtByMob() instanceof Zombie attacker
			&& ZombieSquadCoordinator.areSquadmates(this.zombie, attacker)) {
			// 立即消费掉这次误伤事件：lastHurtByMob 会保留 100 tick，若只返回 false，
			// 小队一旦解散（目标死亡、心跳超时），旧账就会翻出来引发僵尸内战 + 警报连锁。
			this.zombie.setLastHurtByMob(null);
			return false;
		}
		return super.canUse();
	}
}

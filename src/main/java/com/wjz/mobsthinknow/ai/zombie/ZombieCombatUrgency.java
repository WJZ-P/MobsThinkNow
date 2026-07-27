package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** 供生存型 Goal 统一判断“刚被生物攻击，立即把 MOVE/LOOK 交还战斗系统”。 */
final class ZombieCombatUrgency {
	static final int RECENT_ATTACK_TICKS = 40;

	private ZombieCombatUrgency() {
	}

	static boolean wasRecentlyAttacked(final Zombie zombie) {
		LivingEntity attacker = zombie.getLastHurtByMob();
		return attacker != null
			&& attacker.isAlive()
			&& zombie.tickCount - zombie.getLastHurtByMobTimestamp() <= RECENT_ATTACK_TICKS;
	}
}

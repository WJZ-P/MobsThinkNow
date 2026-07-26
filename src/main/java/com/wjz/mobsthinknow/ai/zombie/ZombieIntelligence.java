package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.world.entity.monster.zombie.Zombie;

/** 统一封装对 Mixin 字段的访问，避免业务代码到处出现强制类型转换。 */
public final class ZombieIntelligence {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private ZombieIntelligence() {
	}

	public static int get(final Zombie zombie) {
		return ((ZombieIntelligenceAccess)zombie).mobsthinknow$getIntelligence();
	}

	public static void set(final Zombie zombie, final int intelligence) {
		((ZombieIntelligenceAccess)zombie).mobsthinknow$setIntelligence(intelligence);
	}

	public static int clamp(final int intelligence) {
		return Math.max(MINIMUM, Math.min(MAXIMUM, intelligence));
	}
}

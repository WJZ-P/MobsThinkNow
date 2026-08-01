package com.wjz.mobsthinknow.ai.utility;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 主世界人形亡灵家族的唯一分类入口。
 *
 * <p>这里刻意按实体类型白名单判断，而不是只用 {@code instanceof Zombie} 或
 * {@code instanceof AbstractSkeleton}。这样尸壳、溺尸等真正的战术变种可以共享小队，
 * 同时不会把僵尸猪灵、凋灵骷髅、僵尸鹦鹉螺和骆驼尸壳意外拖入人形小队逻辑。</p>
 */
public final class OverworldUndeadFamilies {
	private OverworldUndeadFamilies() {
	}

	/** 普通僵尸及其主世界人形变种。 */
	public static boolean isZombieFamily(final Entity entity) {
		return isZombieFamily(entity.getType());
	}

	/** 普通僵尸及其主世界人形变种。 */
	public static boolean isZombieFamily(final EntityType<?> type) {
		return type == EntityType.ZOMBIE
			|| type == EntityType.HUSK
			|| type == EntityType.DROWNED
			|| type == EntityType.ZOMBIE_VILLAGER;
	}

	/**
	 * 可以安全复用普通僵尸地面近战状态机的成员。
	 *
	 * <p>溺尸保留自己的两栖导航、入水/上岸判断和三叉戟 Goal，只共享心跳、选举、
	 * 集结与职位；其余三类沿用普通僵尸的地面攻击结构。</p>
	 */
	public static boolean usesGroundZombieTactics(final EntityType<?> type) {
		return type == EntityType.ZOMBIE
			|| type == EntityType.HUSK
			|| type == EntityType.ZOMBIE_VILLAGER;
	}

	/** 普通骷髅及其主世界远程变种。 */
	public static boolean isSkeletonFamily(final Entity entity) {
		return isSkeletonFamily(entity.getType());
	}

	/** 普通骷髅及其主世界远程变种。 */
	public static boolean isSkeletonFamily(final EntityType<?> type) {
		return type == EntityType.SKELETON
			|| type == EntityType.STRAY
			|| type == EntityType.BOGGED
			|| type == EntityType.PARCHED;
	}

	public static boolean isSupportedUndead(final Entity entity) {
		return isZombieFamily(entity) || isSkeletonFamily(entity);
	}
}

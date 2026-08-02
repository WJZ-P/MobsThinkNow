package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.ai.zombie.squad.WeaponClass;
import org.jspecify.annotations.Nullable;

/**
 * 普通僵尸的持久视觉职业。
 *
 * <p>职业不是每帧根据临时换手重新推导：工程技能拿出桶、进食换手或空袭兵耗尽火箭时，外观都应
 * 保持稳定。只有出生定装、旧存档补全或真正永久换装时才重新分类。</p>
 */
public enum ZombieProfession {
	/** 非普通僵尸家族变种继续使用自己的原版纹理。 */
	VANILLA(0, null),
	RECRUIT(1, "recruit"),
	SWORDSMAN(2, "swordsman"),
	AXEMAN(3, "axeman"),
	SWORD_GUARD(4, "sword_guard"),
	AXE_GUARD(5, "axe_guard"),
	ENGINEER(6, "engineer"),
	WATER_SUPPORT(7, "water_support"),
	LAVA_HARASSER(8, "lava_harasser"),
	AIR_ASSAULT(9, "air_assault");

	private static final ZombieProfession[] VALUES = values();
	private final byte id;
	private final @Nullable String textureName;

	ZombieProfession(final int id, final @Nullable String textureName) {
		this.id = (byte)id;
		this.textureName = textureName;
	}

	public byte id() {
		return this.id;
	}

	public @Nullable String textureName() {
		return this.textureName;
	}

	public static ZombieProfession fromId(final int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : VANILLA;
	}

	/** 纯分类决策，便于不启动世界的单元测试覆盖全部优先级。 */
	static ZombieProfession choose(
		final boolean ordinaryZombie,
		final boolean airAssault,
		final UtilityClass utility,
		final boolean engineer,
		final boolean shield,
		final WeaponClass weapon
	) {
		if (!ordinaryZombie) {
			return VANILLA;
		}
		if (airAssault) {
			return AIR_ASSAULT;
		}
		if (utility == UtilityClass.WATER) {
			return WATER_SUPPORT;
		}
		if (utility == UtilityClass.LAVA) {
			return LAVA_HARASSER;
		}
		if (engineer) {
			return ENGINEER;
		}
		if (shield && weapon == WeaponClass.AXE) {
			return AXE_GUARD;
		}
		if (shield && weapon == WeaponClass.SWORD) {
			return SWORD_GUARD;
		}
		if (weapon == WeaponClass.AXE) {
			return AXEMAN;
		}
		if (weapon == WeaponClass.SWORD) {
			return SWORDSMAN;
		}
		return RECRUIT;
	}
}

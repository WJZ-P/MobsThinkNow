package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.shared.ai.MeleeWeaponPlanner;
import org.bukkit.Material;

/** Bukkit 材料到共享近战节奏输入的薄适配层。 */
public final class PaperWeaponProfile {
	private PaperWeaponProfile() {
	}

	public static Kind kindOf(final Material material) {
		if (material == null) {
			return Kind.NONE;
		}
		String name = material.name();
		if (name.endsWith("_SWORD")) {
			return Kind.SWORD;
		}
		if (name.endsWith("_AXE") && !name.endsWith("_PICKAXE")) {
			return Kind.AXE;
		}
		return Kind.NONE;
	}

	/** 原版物品的玩家式基础攻击速度；未知材料保持怪物 20 tick 节奏。 */
	public static int cooldownTicks(final Material material) {
		return switch (kindOf(material)) {
			case SWORD -> MeleeWeaponPlanner.attackCooldownTicks(1.6, true);
			case AXE -> MeleeWeaponPlanner.attackCooldownTicks(axeSpeed(material), true);
			case NONE -> MeleeWeaponPlanner.DEFAULT_ATTACK_COOLDOWN_TICKS;
		};
	}

	private static double axeSpeed(final Material material) {
		return switch (material) {
			case WOODEN_AXE, STONE_AXE, COPPER_AXE -> 0.8;
			case IRON_AXE -> 0.9;
			case GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> 1.0;
			default -> 1.0;
		};
	}

	public enum Kind {
		NONE,
		SWORD,
		AXE
	}
}

package com.wjz.mobsthinknow.paper.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class PaperWeaponProfileTest {
	@Test
	void recognizesOnlySwordAndAxeFamilies() {
		assertEquals(PaperWeaponProfile.Kind.SWORD, PaperWeaponProfile.kindOf(Material.IRON_SWORD));
		assertEquals(PaperWeaponProfile.Kind.AXE, PaperWeaponProfile.kindOf(Material.COPPER_AXE));
		assertEquals(PaperWeaponProfile.Kind.NONE, PaperWeaponProfile.kindOf(Material.IRON_PICKAXE));
		assertEquals(PaperWeaponProfile.Kind.NONE, PaperWeaponProfile.kindOf(Material.TRIDENT));
		assertEquals(PaperWeaponProfile.Kind.NONE, PaperWeaponProfile.kindOf(null));
	}

	@Test
	void mirrorsFabricWeaponCooldownTable() {
		assertEquals(13, PaperWeaponProfile.cooldownTicks(Material.NETHERITE_SWORD));
		assertEquals(25, PaperWeaponProfile.cooldownTicks(Material.WOODEN_AXE));
		assertEquals(25, PaperWeaponProfile.cooldownTicks(Material.COPPER_AXE));
		assertEquals(23, PaperWeaponProfile.cooldownTicks(Material.IRON_AXE));
		assertEquals(20, PaperWeaponProfile.cooldownTicks(Material.DIAMOND_AXE));
		assertEquals(20, PaperWeaponProfile.cooldownTicks(Material.ROTTEN_FLESH));
	}
}

package com.wjz.mobsthinknow.ai.enderman;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;

/** 黑紫底、品红裂隙核心与青色边框组成的末影盾牌。 */
public final class EndermanShieldDesign {
	private EndermanShieldDesign() {
	}

	public static ItemStack create(final RegistryAccess registryAccess) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		var patterns = registryAccess.lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers layers = new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(BannerPatterns.GRADIENT_UP), DyeColor.PURPLE)
			.add(patterns.getOrThrow(BannerPatterns.RHOMBUS_MIDDLE), DyeColor.MAGENTA)
			.add(patterns.getOrThrow(BannerPatterns.STRIPE_MIDDLE), DyeColor.BLACK)
			.add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.CYAN)
			.build();
		shield.set(DataComponents.BASE_COLOR, DyeColor.BLACK);
		shield.set(DataComponents.BANNER_PATTERNS, layers);
		shield.set(DataComponents.ITEM_NAME, Component.translatable("item.mobsthinknow.enderman_void_guard_shield"));
		return shield;
	}
}

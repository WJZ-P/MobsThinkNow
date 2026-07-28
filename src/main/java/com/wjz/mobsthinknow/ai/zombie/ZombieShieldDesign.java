package com.wjz.mobsthinknow.ai.zombie;

import com.wjz.mobsthinknow.MobsThinkNow;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/** 为本 Mod 的原版盾牌附加黑底绿头、黑色鼻口与紫色眼睛的像素僵尸头像。 */
public final class ZombieShieldDesign {
	public static final ResourceKey<BannerPattern> ZOMBIE_HEAD = patternKey("zombie_head");
	public static final ResourceKey<BannerPattern> ZOMBIE_FACE = patternKey("zombie_face");
	public static final ResourceKey<BannerPattern> ZOMBIE_EYES = patternKey("zombie_eyes");

	private ZombieShieldDesign() {
	}

	/** 创建出生与展示指令使用的标准僵尸盾牌。 */
	public static ItemStack create(final RegistryAccess registryAccess) {
		ItemStack shield = new ItemStack(Items.SHIELD);
		decorateIfPlain(shield, registryAccess);
		return shield;
	}

	/**
	 * 给旧存档或其他生成入口留下的无图案原版盾牌补图案。
	 * 已有底色或图案的盾牌保持原样，模组盾也不会被强行写入原版旗帜组件。
	 */
	public static void decorateIfPlain(final Zombie zombie) {
		decorateIfPlain(zombie.getOffhandItem(), zombie.registryAccess());
	}

	static void decorateIfPlain(final ItemStack shield, final RegistryAccess registryAccess) {
		if (!shield.is(Items.SHIELD)
			|| shield.has(DataComponents.BASE_COLOR)
			|| !shield.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty()) {
			return;
		}

		var patterns = registryAccess.lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers layers = new BannerPatternLayers.Builder()
			.add(patterns.getOrThrow(ZOMBIE_HEAD), DyeColor.GREEN)
			.add(patterns.getOrThrow(ZOMBIE_FACE), DyeColor.BLACK)
			.add(patterns.getOrThrow(ZOMBIE_EYES), DyeColor.PURPLE)
			.build();
		shield.set(DataComponents.BASE_COLOR, DyeColor.BLACK);
		shield.set(DataComponents.BANNER_PATTERNS, layers);
		shield.set(DataComponents.ITEM_NAME, Component.translatable("item.mobsthinknow.zombie_guard_shield"));
	}

	static boolean hasZombieHead(final ItemStack shield) {
		BannerPatternLayers layers = shield.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
		return shield.get(DataComponents.BASE_COLOR) == DyeColor.BLACK
			&& layers.layers().stream().anyMatch(layer -> layer.pattern().is(ZOMBIE_HEAD))
			&& layers.layers().stream().anyMatch(layer -> layer.pattern().is(ZOMBIE_FACE))
			&& layers.layers().stream().anyMatch(layer -> layer.pattern().is(ZOMBIE_EYES));
	}

	private static ResourceKey<BannerPattern> patternKey(final String path) {
		return ResourceKey.create(
			Registries.BANNER_PATTERN,
			Identifier.fromNamespaceAndPath(MobsThinkNow.MOD_ID, path)
		);
	}
}

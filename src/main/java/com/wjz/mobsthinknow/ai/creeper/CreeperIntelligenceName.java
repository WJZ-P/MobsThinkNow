package com.wjz.mobsthinknow.ai.creeper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.Creeper;
import org.jspecify.annotations.Nullable;

/** 在普通苦力怕名称末尾追加结构化智力数字，同时保留玩家命名牌或测试兵种名称。 */
public final class CreeperIntelligenceName {
	private CreeperIntelligenceName() {
	}

	public static void apply(final Creeper creeper, final int intelligence) {
		Component current = creeper.getCustomName();
		DecoratedName existing = decoratedName(current);
		Component base = existing != null
			? existing.base()
			: current != null ? current : creeper.getType().getDescription();
		Component decorated = decorate(base, intelligence);
		if (!decorated.equals(current)) {
			creeper.setCustomName(decorated);
		}
		creeper.setCustomNameVisible(true);
	}

	public static void updateExisting(final Creeper creeper, final int intelligence) {
		DecoratedName existing = decoratedName(creeper.getCustomName());
		if (existing == null) {
			apply(creeper, intelligence);
			return;
		}
		creeper.setCustomName(decorate(existing.base(), intelligence));
		creeper.setCustomNameVisible(true);
	}

	public static void removeSyntheticMarker(final Creeper creeper) {
		DecoratedName existing = decoratedName(creeper.getCustomName());
		if (existing == null) {
			return;
		}
		if (isOwnTypeName(creeper, existing.base())) {
			creeper.setCustomName(null);
			creeper.setCustomNameVisible(false);
		} else {
			creeper.setCustomName(existing.base());
		}
	}

	private static Component decorate(final Component base, final int intelligence) {
		MutableComponent marker = Component.literal(" [" + CreeperIntelligence.clamp(intelligence) + "]")
			.withStyle(ChatFormatting.DARK_GREEN);
		return Component.empty().append(base).append(marker);
	}

	private static @Nullable DecoratedName decoratedName(final @Nullable Component name) {
		if (name == null || name.getSiblings().size() != 2) {
			return null;
		}
		Component marker = name.getSiblings().getLast();
		int intelligence = parseMarker(marker.getString());
		return intelligence == 0 ? null : new DecoratedName(name.getSiblings().getFirst(), intelligence);
	}

	private static int parseMarker(final String marker) {
		if (!marker.startsWith(" [") || !marker.endsWith("]")) {
			return 0;
		}
		try {
			int intelligence = Integer.parseInt(marker.substring(2, marker.length() - 1));
			return intelligence >= CreeperIntelligence.MINIMUM && intelligence <= CreeperIntelligence.MAXIMUM
				? intelligence
				: 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isOwnTypeName(final Creeper creeper, final Component base) {
		if (!(base.getContents() instanceof TranslatableContents baseTranslation)
			|| !(creeper.getType().getDescription().getContents() instanceof TranslatableContents typeTranslation)) {
			return false;
		}
		return baseTranslation.getKey().equals(typeTranslation.getKey());
	}

	private record DecoratedName(Component base, int intelligence) {
	}
}

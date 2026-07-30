package com.wjz.mobsthinknow.ai.giant;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.Giant;
import org.jspecify.annotations.Nullable;

/** 给原版巨人实体追加结构化智力数字，并保留玩家自定义名称。 */
public final class GiantIntelligenceName {
	private GiantIntelligenceName() {
	}

	public static void apply(final Giant giant, final int intelligence) {
		Component current = giant.getCustomName();
		DecoratedName existing = decoratedName(current);
		Component base = existing != null
			? existing.base()
			: current != null ? current : giant.getType().getDescription();
		Component decorated = decorate(base, intelligence);
		if (!decorated.equals(current)) {
			giant.setCustomName(decorated);
		}
		giant.setCustomNameVisible(true);
	}

	public static void updateExisting(final Giant giant, final int intelligence) {
		DecoratedName existing = decoratedName(giant.getCustomName());
		if (existing == null) {
			apply(giant, intelligence);
			return;
		}
		giant.setCustomName(decorate(existing.base(), intelligence));
		giant.setCustomNameVisible(true);
	}

	public static void removeSyntheticMarker(final Giant giant) {
		DecoratedName existing = decoratedName(giant.getCustomName());
		if (existing == null) {
			return;
		}
		if (isOwnTypeName(giant, existing.base())) {
			giant.setCustomName(null);
			giant.setCustomNameVisible(false);
		} else {
			giant.setCustomName(existing.base());
		}
	}

	private static Component decorate(final Component base, final int intelligence) {
		MutableComponent marker = Component.literal(" [" + GiantIntelligence.clamp(intelligence) + "]")
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
			return intelligence >= GiantIntelligence.MINIMUM && intelligence <= GiantIntelligence.MAXIMUM
				? intelligence
				: 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isOwnTypeName(final Giant giant, final Component base) {
		if (!(base.getContents() instanceof TranslatableContents baseTranslation)
			|| !(giant.getType().getDescription().getContents() instanceof TranslatableContents typeTranslation)) {
			return false;
		}
		return baseTranslation.getKey().equals(typeTranslation.getKey());
	}

	private record DecoratedName(Component base, int intelligence) {
	}
}

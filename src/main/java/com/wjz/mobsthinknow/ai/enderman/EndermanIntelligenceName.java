package com.wjz.mobsthinknow.ai.enderman;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.EnderMan;
import org.jspecify.annotations.Nullable;

/** 在普通末影人名称末尾追加结构化智力数字，同时保留玩家命名牌和展示名称。 */
public final class EndermanIntelligenceName {
	private EndermanIntelligenceName() {
	}

	public static void apply(final EnderMan enderman, final int intelligence) {
		Component current = enderman.getCustomName();
		DecoratedName existing = decoratedName(current);
		Component base = existing != null
			? existing.base()
			: current != null ? current : enderman.getType().getDescription();
		Component decorated = decorate(base, intelligence);
		if (!decorated.equals(current)) {
			enderman.setCustomName(decorated);
		}
		enderman.setCustomNameVisible(true);
	}

	public static void updateExisting(final EnderMan enderman, final int intelligence) {
		DecoratedName existing = decoratedName(enderman.getCustomName());
		if (existing == null) {
			apply(enderman, intelligence);
			return;
		}
		enderman.setCustomName(decorate(existing.base(), intelligence));
		enderman.setCustomNameVisible(true);
	}

	public static void removeSyntheticMarker(final EnderMan enderman) {
		DecoratedName existing = decoratedName(enderman.getCustomName());
		if (existing == null) {
			return;
		}
		if (isOwnTypeName(enderman, existing.base())) {
			enderman.setCustomName(null);
			enderman.setCustomNameVisible(false);
		} else {
			enderman.setCustomName(existing.base());
		}
	}

	private static Component decorate(final Component base, final int intelligence) {
		MutableComponent marker = Component.literal(" [" + EndermanIntelligence.clamp(intelligence) + "]")
			.withStyle(ChatFormatting.DARK_PURPLE);
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
			return intelligence >= EndermanIntelligence.MINIMUM && intelligence <= EndermanIntelligence.MAXIMUM
				? intelligence
				: 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isOwnTypeName(final EnderMan enderman, final Component base) {
		if (!(base.getContents() instanceof TranslatableContents baseTranslation)
			|| !(enderman.getType().getDescription().getContents() instanceof TranslatableContents typeTranslation)) {
			return false;
		}
		return baseTranslation.getKey().equals(typeTranslation.getKey());
	}

	private record DecoratedName(Component base, int intelligence) {
	}
}

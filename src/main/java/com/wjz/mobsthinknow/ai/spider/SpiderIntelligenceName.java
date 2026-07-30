package com.wjz.mobsthinknow.ai.spider;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.spider.Spider;
import org.jspecify.annotations.Nullable;

/** 在普通蜘蛛名称末尾追加结构化智力数字，同时保留玩家命名牌与测试兵种名称。 */
public final class SpiderIntelligenceName {
	private SpiderIntelligenceName() {
	}

	public static void apply(final Spider spider, final int intelligence) {
		Component current = spider.getCustomName();
		DecoratedName existing = decoratedName(current);
		Component base = existing != null
			? existing.base()
			: current != null ? current : spider.getType().getDescription();
		Component decorated = decorate(base, intelligence);
		if (!decorated.equals(current)) {
			spider.setCustomName(decorated);
		}
		spider.setCustomNameVisible(true);
	}

	public static void updateExisting(final Spider spider, final int intelligence) {
		DecoratedName existing = decoratedName(spider.getCustomName());
		if (existing == null) {
			apply(spider, intelligence);
			return;
		}
		spider.setCustomName(decorate(existing.base(), intelligence));
		spider.setCustomNameVisible(true);
	}

	public static void removeSyntheticMarker(final Spider spider) {
		DecoratedName existing = decoratedName(spider.getCustomName());
		if (existing == null) {
			return;
		}
		if (isOwnTypeName(spider, existing.base())) {
			spider.setCustomName(null);
			spider.setCustomNameVisible(false);
		} else {
			spider.setCustomName(existing.base());
		}
	}

	private static Component decorate(final Component base, final int intelligence) {
		MutableComponent marker = Component.literal(" [" + SpiderIntelligence.clamp(intelligence) + "]")
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
			return intelligence >= SpiderIntelligence.MINIMUM && intelligence <= SpiderIntelligence.MAXIMUM
				? intelligence
				: 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isOwnTypeName(final Spider spider, final Component base) {
		if (!(base.getContents() instanceof TranslatableContents baseTranslation)
			|| !(spider.getType().getDescription().getContents() instanceof TranslatableContents typeTranslation)) {
			return false;
		}
		return baseTranslation.getKey().equals(typeTranslation.getKey());
	}

	private record DecoratedName(Component base, int intelligence) {
	}
}

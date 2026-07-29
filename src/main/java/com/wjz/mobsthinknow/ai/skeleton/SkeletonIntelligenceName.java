package com.wjz.mobsthinknow.ai.skeleton;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.jspecify.annotations.Nullable;

/** 在普通骷髅名称末尾追加结构化的智力数字，并保留玩家命名牌名称。 */
public final class SkeletonIntelligenceName {
	private static final String ROLE_KEY_PREFIX = "mobsthinknow.role.";

	private SkeletonIntelligenceName() {
	}

	public static void apply(final AbstractSkeleton skeleton, final int intelligence) {
		Component current = skeleton.getCustomName();
		DecoratedName existing = decoratedName(current);
		Component base = existing != null
			? existing.base()
			: current != null ? current : skeleton.getType().getDescription();
		Component decorated = decorate(base, intelligence);
		if (!decorated.equals(current)) {
			skeleton.setCustomName(decorated);
		}
		skeleton.setCustomNameVisible(true);
	}

	public static void updateExisting(final AbstractSkeleton skeleton, final int intelligence) {
		DecoratedName existing = decoratedName(skeleton.getCustomName());
		if (existing == null) {
			if (!hasRoleTag(skeleton.getCustomName())) {
				apply(skeleton, intelligence);
			}
			return;
		}
		skeleton.setCustomName(decorate(existing.base(), intelligence));
		skeleton.setCustomNameVisible(true);
	}

	public static void removeSyntheticMarker(final AbstractSkeleton skeleton) {
		DecoratedName existing = decoratedName(skeleton.getCustomName());
		if (existing == null) {
			return;
		}
		if (isOwnTypeName(skeleton, existing.base())) {
			skeleton.setCustomName(null);
			skeleton.setCustomNameVisible(false);
		} else {
			skeleton.setCustomName(existing.base());
		}
	}

	private static Component decorate(final Component base, final int intelligence) {
		MutableComponent marker = Component.literal(" [" + SkeletonIntelligence.clamp(intelligence) + "]")
			.withStyle(ChatFormatting.DARK_GRAY);
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
			return intelligence >= SkeletonIntelligence.MINIMUM && intelligence <= SkeletonIntelligence.MAXIMUM
				? intelligence
				: 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isOwnTypeName(final AbstractSkeleton skeleton, final Component base) {
		if (!(base.getContents() instanceof TranslatableContents baseTranslation)
			|| !(skeleton.getType().getDescription().getContents() instanceof TranslatableContents typeTranslation)) {
			return false;
		}
		return baseTranslation.getKey().equals(typeTranslation.getKey());
	}

	private static boolean hasRoleTag(final @Nullable Component name) {
		if (name == null || name.getSiblings().isEmpty()) {
			return false;
		}
		Component last = name.getSiblings().getLast();
		return last.getContents() instanceof TranslatableContents translation
			&& translation.getKey().startsWith(ROLE_KEY_PREFIX);
	}

	private record DecoratedName(Component base, int intelligence) {
	}
}

package com.wjz.mobsthinknow.ai.zombie;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.jspecify.annotations.Nullable;

/**
 * 给普通名字追加一个轻量的智力标记，例如“僵尸 [7]”。
 *
 * <p>标记故意是结构化 Component 的最后一个 sibling，而不是拼接后的纯字符串：小队职业标签
 * 可以继续追加在其后，死亡或转化时也能只剥掉本 Mod 的数字而保留玩家用命名牌设置的原名。</p>
 */
public final class ZombieIntelligenceName {
	private static final String ROLE_KEY_PREFIX = "mobsthinknow.role.";

	private ZombieIntelligenceName() {
	}

	/** 出生或读档后确保名字末尾恰好存在一个与当前智力相符的数字。 */
	public static void apply(final Zombie zombie, final int intelligence) {
		Component current = zombie.getCustomName();
		DecoratedName existing = decoratedName(current);
		Component base = existing != null
			? existing.base()
			: current != null ? current : zombie.getType().getDescription();
		Component decorated = decorate(base, ZombieIntelligence.clamp(intelligence));
		if (!decorated.equals(current)) {
			zombie.setCustomName(decorated);
		}
		zombie.setCustomNameVisible(true);
	}

	/** 测试、命令等途径修改智力时，只更新已经存在的数字，不把正在显示的职业标签再次包进去。 */
	public static void updateExisting(final Zombie zombie, final int intelligence) {
		DecoratedName existing = decoratedName(zombie.getCustomName());
		if (existing == null) {
			// GameTest/命令直接创建的实体可能绕过 finalizeSpawn；没有职业后缀时顺便补建数字。
			if (!hasRoleTag(zombie.getCustomName())) {
				apply(zombie, intelligence);
			}
			return;
		}
		zombie.setCustomName(decorate(existing.base(), ZombieIntelligence.clamp(intelligence)));
		zombie.setCustomNameVisible(true);
	}

	/**
	 * 死亡日志和僵尸转化前移除数字：默认类型名恢复为 {@code null}，玩家自定义名则原样保留。
	 */
	public static void removeSyntheticMarker(final Zombie zombie) {
		DecoratedName existing = decoratedName(zombie.getCustomName());
		if (existing == null) {
			return;
		}
		if (isOwnTypeName(zombie, existing.base())) {
			zombie.setCustomName(null);
			zombie.setCustomNameVisible(false);
		} else {
			zombie.setCustomName(existing.base());
		}
	}

	static Component decorate(final Component base, final int intelligence) {
		MutableComponent marker = Component.literal(" [" + ZombieIntelligence.clamp(intelligence) + "]")
			.withStyle(ChatFormatting.DARK_GRAY);
		return Component.empty().append(base).append(marker);
	}

	static @Nullable DecoratedName decoratedName(final @Nullable Component name) {
		if (name == null || name.getSiblings().size() != 2) {
			return null;
		}
		Component marker = name.getSiblings().getLast();
		int intelligence = parseMarker(marker.getString());
		if (intelligence == 0) {
			return null;
		}
		return new DecoratedName(name.getSiblings().getFirst(), intelligence);
	}

	static int parseMarker(final String marker) {
		if (!marker.startsWith(" [") || !marker.endsWith("]")) {
			return 0;
		}
		try {
			int intelligence = Integer.parseInt(marker.substring(2, marker.length() - 1));
			return intelligence >= ZombieIntelligence.MINIMUM && intelligence <= ZombieIntelligence.MAXIMUM
				? intelligence
				: 0;
		} catch (NumberFormatException ignored) {
			return 0;
		}
	}

	private static boolean isOwnTypeName(final Zombie zombie, final Component base) {
		if (!(base.getContents() instanceof TranslatableContents baseTranslation)
			|| !(zombie.getType().getDescription().getContents() instanceof TranslatableContents typeTranslation)) {
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

	record DecoratedName(Component base, int intelligence) {
	}
}

package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.IntelligenceDistribution;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 用 PDC 保存跨重启智力，并只修改由本插件拥有的名字，避免覆盖其他插件/命名牌。 */
public final class PaperIntelligenceService {
	private final NamespacedKey intelligenceKey;
	private final NamespacedKey ownedNameKey;
	private final Supplier<PaperSettings> settings;
	private final PaperMetrics metrics;

	public PaperIntelligenceService(
		final Plugin plugin,
		final Supplier<PaperSettings> settings,
		final PaperMetrics metrics
	) {
		this.intelligenceKey = new NamespacedKey(plugin, "intelligence");
		this.ownedNameKey = new NamespacedKey(plugin, "intelligence_name_owned");
		this.settings = settings;
		this.metrics = metrics;
	}

	public boolean supports(final Mob mob) {
		return mob instanceof Zombie
			|| mob instanceof AbstractSkeleton
			|| mob instanceof Creeper
			|| (mob instanceof Spider && mob.getType() == EntityType.SPIDER);
	}

	public int ensure(final Mob mob) {
		if (!this.supports(mob)) {
			return IntelligenceDistribution.MINIMUM;
		}
		PersistentDataContainer data = mob.getPersistentDataContainer();
		Integer stored = data.get(this.intelligenceKey, PersistentDataType.INTEGER);
		PaperSettings config = this.settings.get();
		if (!config.enabled()) {
			this.clearOwnedName(mob, data);
			return stored == null
				? IntelligenceDistribution.MINIMUM
				: IntelligenceDistribution.clamp(stored);
		}
		int intelligence;
		if (stored == null) {
			intelligence = IntelligenceDistribution.roll(
				PaperDifficultyAdapter.fromBukkit(mob.getWorld().getDifficulty()),
				ThreadLocalRandom.current().nextDouble()
			);
			data.set(this.intelligenceKey, PersistentDataType.INTEGER, intelligence);
			this.metrics.intelligenceAssigned();
		} else {
			intelligence = IntelligenceDistribution.clamp(stored);
			if (stored != intelligence) {
				data.set(this.intelligenceKey, PersistentDataType.INTEGER, intelligence);
			}
		}
		this.refreshOwnedName(mob, intelligence, config);
		return intelligence;
	}

	public int get(final Mob mob) {
		return this.ensure(mob);
	}

	public void set(final Mob mob, final int intelligence) {
		if (!this.supports(mob)) {
			return;
		}
		int clamped = IntelligenceDistribution.clamp(intelligence);
		mob.getPersistentDataContainer().set(this.intelligenceKey, PersistentDataType.INTEGER, clamped);
		this.refreshOwnedName(mob, clamped, this.settings.get());
	}

	private void refreshOwnedName(final Mob mob, final int intelligence, final PaperSettings config) {
		PersistentDataContainer data = mob.getPersistentDataContainer();
		boolean ownsName = data.has(this.ownedNameKey, PersistentDataType.BYTE);
		if (!config.enabled() || !config.showIntelligenceNames()) {
			this.clearOwnedName(mob, data);
			return;
		}
		if (mob.customName() != null && !ownsName) {
			return;
		}
		Component name = Component.translatable(mob.getType().translationKey())
			.append(Component.text(" [IQ " + intelligence + "]", intelligenceColor(intelligence)));
		mob.customName(name);
		mob.setCustomNameVisible(false);
		data.set(this.ownedNameKey, PersistentDataType.BYTE, (byte)1);
	}

	private void clearOwnedName(final Mob mob, final PersistentDataContainer data) {
		if (data.has(this.ownedNameKey, PersistentDataType.BYTE)) {
			mob.customName(null);
			data.remove(this.ownedNameKey);
		}
	}

	private static NamedTextColor intelligenceColor(final int intelligence) {
		if (intelligence >= 9) {
			return NamedTextColor.GOLD;
		}
		if (intelligence >= 6) {
			return NamedTextColor.YELLOW;
		}
		return NamedTextColor.GRAY;
	}

}

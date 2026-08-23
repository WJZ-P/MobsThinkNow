package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.shared.ai.IntelligenceDistribution;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
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
	private final Map<Mob, Integer> runtimeCache = new IdentityHashMap<>();
	private long runtimeCacheHits;
	private long persistentReads;

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
		return isSupportedType(mob.getType());
	}

	/** Explicit type boundary avoids pulling ZombifiedPiglin into overworld squads through Bukkit's Zombie API. */
	public static boolean isSupportedType(final EntityType type) {
		return type == EntityType.ZOMBIE
			|| type == EntityType.HUSK
			|| type == EntityType.DROWNED
			|| type == EntityType.ZOMBIE_VILLAGER
			|| type == EntityType.SKELETON
			|| type == EntityType.STRAY
			|| type == EntityType.BOGGED
			|| type == EntityType.PARCHED
			|| type == EntityType.WITHER_SKELETON
			|| type == EntityType.CREEPER
			|| type == EntityType.SPIDER;
	}

	public int ensure(final Mob mob) {
		if (!this.supports(mob)) {
			return IntelligenceDistribution.MINIMUM;
		}
		PersistentDataContainer data = mob.getPersistentDataContainer();
		this.persistentReads++;
		Integer stored = data.get(this.intelligenceKey, PersistentDataType.INTEGER);
		PaperSettings config = this.settings.get();
		if (!config.enabled()) {
			int fallback = stored == null
				? IntelligenceDistribution.MINIMUM
				: IntelligenceDistribution.clamp(stored);
			this.clearOwnedName(mob, data, fallback);
			return this.cache(mob, fallback);
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
		return this.cache(mob, intelligence);
	}

	public int get(final Mob mob) {
		if (!this.supports(mob)) {
			return IntelligenceDistribution.MINIMUM;
		}
		Integer cached = this.runtimeCache.get(mob);
		if (cached != null) {
			this.runtimeCacheHits++;
			return cached;
		}
		this.persistentReads++;
		Integer stored = mob.getPersistentDataContainer().get(this.intelligenceKey, PersistentDataType.INTEGER);
		if (stored == null) {
			return this.ensure(mob);
		}
		int clamped = IntelligenceDistribution.clamp(stored);
		if (stored != clamped) {
			return this.ensure(mob);
		}
		return this.cache(mob, clamped);
	}

	public void set(final Mob mob, final int intelligence) {
		if (!this.supports(mob)) {
			return;
		}
		int clamped = IntelligenceDistribution.clamp(intelligence);
		mob.getPersistentDataContainer().set(this.intelligenceKey, PersistentDataType.INTEGER, clamped);
		this.refreshOwnedName(mob, clamped, this.settings.get());
		this.cache(mob, clamped);
	}

	/** 实体离开世界时释放强身份键；PDC 仍是下次装载的持久事实来源。 */
	public void forget(final Mob mob) {
		this.runtimeCache.remove(mob);
	}

	/** 配置重载和插件停用边界会先清空，再通过 ensure 重新校准当前已装载实体。 */
	public void clearRuntimeCache() {
		this.runtimeCache.clear();
	}

	public int runtimeCacheSize() {
		return this.runtimeCache.size();
	}

	public long runtimeCacheHits() {
		return this.runtimeCacheHits;
	}

	public long persistentReads() {
		return this.persistentReads;
	}

	private int cache(final Mob mob, final int intelligence) {
		this.runtimeCache.put(mob, intelligence);
		return intelligence;
	}

	private void refreshOwnedName(final Mob mob, final int intelligence, final PaperSettings config) {
		PersistentDataContainer data = mob.getPersistentDataContainer();
		NameOwnership ownership = this.nameOwnership(mob, data, intelligence);
		if (!config.enabled() || !config.showIntelligenceNames()) {
			if (ownership == NameOwnership.OWNED) {
				mob.customName(null);
				mob.setCustomNameVisible(false);
			}
			data.remove(this.ownedNameKey);
			return;
		}
		if (ownership == NameOwnership.RELINQUISHED
			|| mob.customName() != null && ownership != NameOwnership.OWNED) {
			return;
		}
		mob.customName(syntheticName(mob.getType(), intelligence));
		mob.setCustomNameVisible(false);
		data.set(this.ownedNameKey, PersistentDataType.INTEGER, intelligence);
	}

	private void clearOwnedName(final Mob mob, final PersistentDataContainer data, final int fallbackIntelligence) {
		if (this.nameOwnership(mob, data, fallbackIntelligence) == NameOwnership.OWNED) {
			mob.customName(null);
			mob.setCustomNameVisible(false);
		}
		data.remove(this.ownedNameKey);
	}

	private NameOwnership nameOwnership(
		final Mob mob,
		final PersistentDataContainer data,
		final int fallbackIntelligence
	) {
		Integer generatedIntelligence = data.get(this.ownedNameKey, PersistentDataType.INTEGER);
		if (generatedIntelligence != null) {
			if (generatedIntelligence < IntelligenceDistribution.MINIMUM) {
				return NameOwnership.RELINQUISHED;
			}
			if (matchesSyntheticName(mob.customName(), mob.getType(), generatedIntelligence)) {
				return NameOwnership.OWNED;
			}
			// Zero is a persistent opt-out: an external rename/clear remains authoritative on future IQ reads.
			data.set(this.ownedNameKey, PersistentDataType.INTEGER, 0);
			return NameOwnership.RELINQUISHED;
		}
		if (!data.has(this.ownedNameKey, PersistentDataType.BYTE)) {
			return NameOwnership.NONE;
		}
		// v0.1.0-alpha.1 stored only a byte marker. Accept any generated IQ variant once, then migrate on refresh.
		for (int candidate = IntelligenceDistribution.MINIMUM; candidate <= IntelligenceDistribution.MAXIMUM; candidate++) {
			if (matchesSyntheticName(mob.customName(), mob.getType(), candidate)) {
				return NameOwnership.OWNED;
			}
		}
		if (matchesSyntheticName(mob.customName(), mob.getType(), fallbackIntelligence)) {
			return NameOwnership.OWNED;
		}
		data.set(this.ownedNameKey, PersistentDataType.INTEGER, 0);
		return NameOwnership.RELINQUISHED;
	}

	static Component syntheticName(final EntityType type, final int intelligence) {
		return syntheticName(type.translationKey(), intelligence);
	}

	static Component syntheticName(final String translationKey, final int intelligence) {
		int clamped = IntelligenceDistribution.clamp(intelligence);
		return Component.translatable(translationKey)
			.append(Component.text(" [IQ " + clamped + "]", intelligenceColor(clamped)));
	}

	static boolean matchesSyntheticName(final Component current, final EntityType type, final int intelligence) {
		return Objects.equals(current, syntheticName(type, intelligence));
	}

	static boolean matchesSyntheticName(final Component current, final String translationKey, final int intelligence) {
		return Objects.equals(current, syntheticName(translationKey, intelligence));
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

	private enum NameOwnership {
		NONE,
		OWNED,
		RELINQUISHED
	}

}

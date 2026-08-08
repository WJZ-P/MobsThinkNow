package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import com.wjz.mobsthinknow.paper.PaperSkeletonLoadoutSettings;
import com.wjz.mobsthinknow.shared.ai.CrossbowLoadoutPlanner;
import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
import com.wjz.mobsthinknow.shared.ai.IntelligenceDistribution;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.Difficulty;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** 只在可识别的自然出生边界执行一次，并以 PDC 防止重载或其他生命周期回调重复洗装备。 */
public final class PaperSkeletonLoadoutService {
	private static final float VANILLA_EQUIPMENT_DROP_CHANCE = 0.085F;
	private static final Set<CreatureSpawnEvent.SpawnReason> ELIGIBLE_REASONS = EnumSet.of(
		CreatureSpawnEvent.SpawnReason.NATURAL,
		CreatureSpawnEvent.SpawnReason.JOCKEY,
		CreatureSpawnEvent.SpawnReason.TRAP
	);

	private final NamespacedKey initializedKey;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperMetrics metrics;

	public PaperSkeletonLoadoutService(
		final Plugin plugin,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperMetrics metrics
	) {
		this.initializedKey = new NamespacedKey(plugin, "skeleton_natural_loadout_initialized");
		this.settings = settings;
		this.intelligence = intelligence;
		this.metrics = metrics;
	}

	public void initialize(
		final AbstractSkeleton skeleton,
		final CreatureSpawnEvent.SpawnReason reason
	) {
		if (skeleton.getType() != EntityType.SKELETON || !isEligibleReason(reason)) {
			return;
		}
		PersistentDataContainer data = skeleton.getPersistentDataContainer();
		if (data.has(this.initializedKey, PersistentDataType.BYTE)) {
			return;
		}
		data.set(this.initializedKey, PersistentDataType.BYTE, (byte)1);
		this.metrics.naturalSkeletonLoadoutInitialized();

		PaperSettings root = this.settings.get();
		PaperSkeletonLoadoutSettings config = root.skeletonCrossbowTactics().naturalLoadout();
		if (!root.enabled()
			|| !root.skeletonCrossbowTactics().enabled()
			|| !config.enabled()
			|| skeleton.getEquipment().getItemInMainHand().getType() != Material.BOW) {
			return;
		}

		int iq = this.intelligence.ensure(skeleton);
		DifficultyTier difficulty = PaperDifficultyAdapter.fromBukkit(skeleton.getWorld().getDifficulty());
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (!CrossbowLoadoutPlanner.succeeds(
			CrossbowLoadoutPlanner.effectiveCrossbowChance(config.crossbowChance(), difficulty, iq),
			random.nextDouble()
		)) {
			return;
		}

		skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
		skeleton.getEquipment().setItemInMainHandDropChance(VANILLA_EQUIPMENT_DROP_CHANCE);
		this.metrics.naturalCrossbowEquipped();
		if (!root.skeletonCrossbowTactics().firework().enabled()
			|| !CrossbowLoadoutPlanner.succeeds(
				CrossbowLoadoutPlanner.effectiveFireworkChance(
					config.fireworkCrossbowChance(),
					difficulty,
					iq
				),
				random.nextDouble()
			)) {
			return;
		}

		int rockets = CrossbowLoadoutPlanner.rocketCount(difficulty, iq, random.nextDouble());
		skeleton.getEquipment().setItemInOffHand(explosiveRockets(rockets));
		skeleton.getEquipment().setItemInOffHandDropChance(VANILLA_EQUIPMENT_DROP_CHANCE);
		this.metrics.naturalFireworkCrossbowEquipped();
	}

	public static boolean isEligibleReason(final CreatureSpawnEvent.SpawnReason reason) {
		return ELIGIBLE_REASONS.contains(reason);
	}

	/** 隔离冒烟仅在配置与难度让所有可能 IQ 都必定成功时启用自然事件探针。 */
	public boolean guaranteesCrossbow(final Difficulty bukkitDifficulty) {
		PaperSettings root = this.settings.get();
		PaperSkeletonLoadoutSettings config = root.skeletonCrossbowTactics().naturalLoadout();
		DifficultyTier difficulty = PaperDifficultyAdapter.fromBukkit(bukkitDifficulty);
		int minimumIq = IntelligenceDistribution.rangeFor(difficulty).minimum();
		return root.enabled()
			&& root.skeletonCrossbowTactics().enabled()
			&& config.enabled()
			&& CrossbowLoadoutPlanner.effectiveCrossbowChance(
				config.crossbowChance(),
				difficulty,
				minimumIq
			) >= 1.0;
	}

	private static ItemStack explosiveRockets(final int count) {
		ItemStack rockets = new ItemStack(Material.FIREWORK_ROCKET, count);
		if (rockets.getItemMeta() instanceof FireworkMeta meta) {
			meta.addEffect(FireworkEffect.builder()
				.with(FireworkEffect.Type.BURST)
				.withColor(Color.fromRGB(0x80C71F), Color.fromRGB(0x474F52))
				.withFade(Color.WHITE)
				.trail(true)
				.flicker(true)
				.build());
			meta.setPower(1);
			rockets.setItemMeta(meta);
		}
		return rockets;
	}
}

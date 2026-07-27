package com.wjz.mobsthinknow.client;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 使用 Cloth Config 构造的客户端配置页。 */
@Environment(EnvType.CLIENT)
public final class MobsThinkNowConfigScreen {
	private MobsThinkNowConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		MobsThinkNowConfig config = ConfigManager.get();
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("mobsthinknow.config.title"));
		ConfigCategory squadCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.squad")
		);
		ConfigEntryBuilder entries = builder.entryBuilder();

		// Mod Menu 位于客户端；这段提示避免玩家误以为它可以直接修改远程服务器规则。
		squadCategory.addEntry(entries.startTextDescription(
			Component.translatable("mobsthinknow.config.server_authority_note")
		).build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.maximum_coordinated_zombies"),
			config.maximumCoordinatedZombies,
			MobsThinkNowConfig.MINIMUM_MAXIMUM_COORDINATED_ZOMBIES,
			MobsThinkNowConfig.MAXIMUM_MAXIMUM_COORDINATED_ZOMBIES
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_MAXIMUM_COORDINATED_ZOMBIES)
			.setTooltip(Component.translatable("mobsthinknow.config.maximum_coordinated_zombies.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.maximumCoordinatedZombies = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_visual_effects"),
			config.squadVisualEffects
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_visual_effects.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.squadVisualEffects = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_role_name_tags"),
			config.squadRoleNameTags
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_role_name_tags.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.squadRoleNameTags = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.individual_traits"),
			config.individualTraits
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.individual_traits.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.individualTraits = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.retreat_tactics"),
			config.retreatTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_tactics.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.retreatTactics = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_heavy_hit_threshold"),
			(int)Math.round(config.retreatHeavyHitThreshold * 100.0),
			5,
			100
		)
			.setDefaultValue(30)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_heavy_hit_threshold.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.retreatHeavyHitThreshold = value / 100.0))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_maximum_seconds"),
			(int)Math.round(config.retreatMaximumTicks / 20.0),
			MobsThinkNowConfig.MINIMUM_RETREAT_MAXIMUM_TICKS / 20,
			MobsThinkNowConfig.MAXIMUM_RETREAT_MAXIMUM_TICKS / 20
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_RETREAT_MAXIMUM_TICKS / 20)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_maximum_seconds.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.retreatMaximumTicks = value * 20))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_safe_distance"),
			(int)Math.round(config.retreatSafeDistance),
			(int)MobsThinkNowConfig.MINIMUM_RETREAT_SAFE_DISTANCE,
			(int)MobsThinkNowConfig.MAXIMUM_RETREAT_SAFE_DISTANCE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_RETREAT_SAFE_DISTANCE)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_safe_distance.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.retreatSafeDistance = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_speed_percent"),
			(int)Math.round(config.retreatSpeedModifier * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_RETREAT_SPEED_MODIFIER * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_RETREAT_SPEED_MODIFIER * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_RETREAT_SPEED_MODIFIER * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_speed_percent.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.retreatSpeedModifier = value / 100.0))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.food_scavenging"),
			config.foodScavenging
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.food_scavenging.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.foodScavenging = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.food_minimum_intelligence"),
			config.foodMinimumIntelligence,
			MobsThinkNowConfig.MINIMUM_FOOD_MINIMUM_INTELLIGENCE,
			MobsThinkNowConfig.MAXIMUM_FOOD_MINIMUM_INTELLIGENCE
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_FOOD_MINIMUM_INTELLIGENCE)
			.setTooltip(Component.translatable("mobsthinknow.config.food_minimum_intelligence.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.foodMinimumIntelligence = value))
			.build());

		ConfigCategory terrainCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.terrain")
		);
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.terrain_tactics"),
			config.terrainTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.terrain_tactics.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.terrainTactics = value))
			.build());
		terrainCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.terrain_minimum_intelligence"),
			config.terrainMinimumIntelligence,
			MobsThinkNowConfig.MINIMUM_TERRAIN_MINIMUM_INTELLIGENCE,
			MobsThinkNowConfig.MAXIMUM_TERRAIN_MINIMUM_INTELLIGENCE
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_TERRAIN_MINIMUM_INTELLIGENCE)
			.setTooltip(Component.translatable("mobsthinknow.config.terrain_minimum_intelligence.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.terrainMinimumIntelligence = value))
			.build());
		terrainCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.terrain_block_inventory_limit"),
			config.terrainBlockInventoryLimit,
			MobsThinkNowConfig.MINIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT,
			MobsThinkNowConfig.MAXIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_TERRAIN_BLOCK_INVENTORY_LIMIT)
			.setTooltip(Component.translatable("mobsthinknow.config.terrain_block_inventory_limit.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.terrainBlockInventoryLimit = value))
			.build());
		ConfigCategory armedCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.armed")
		);
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.armed_squads"),
			config.armedSquads
		)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("mobsthinknow.config.armed_squads.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.armedSquads = value))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.weapon_combat_tactics"),
			config.weaponCombatTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.weapon_combat_tactics.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.weaponCombatTactics = value))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spear_air_assault"),
			config.spearAirAssault
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spear_air_assault.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.spearAirAssault = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.spear_rocket_efficiency"),
			(int)Math.round(config.spearRocketEfficiency * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_SPEAR_ROCKET_EFFICIENCY * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_SPEAR_ROCKET_EFFICIENCY * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SPEAR_ROCKET_EFFICIENCY * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.spear_rocket_efficiency.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.spearRocketEfficiency = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.special_equipment"),
			config.specialEquipment
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.special_equipment.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.specialEquipment = value))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.fluid_tactics"),
			config.fluidTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.fluid_tactics.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.fluidTactics = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.water_bucket_chance"),
			(int)Math.round(config.waterBucketChance * 100.0),
			0,
			100
		)
			.setDefaultValue(4)
			.setTooltip(Component.translatable("mobsthinknow.config.water_bucket_chance.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.waterBucketChance = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.lava_bucket_chance"),
			(int)Math.round(config.lavaBucketChance * 100.0),
			0,
			100
		)
			.setDefaultValue(2)
			.setTooltip(Component.translatable("mobsthinknow.config.lava_bucket_chance.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.lavaBucketChance = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.special_equipment_drop_chance"),
			(int)Math.round(config.specialEquipmentDropChance * 1000.0),
			0,
			1000
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SPECIAL_EQUIPMENT_DROP_CHANCE * 1000.0))
			.setTooltip(Component.translatable("mobsthinknow.config.special_equipment_drop_chance.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.specialEquipmentDropChance = value / 1000.0))
			.build());

		return builder.build();
	}
}

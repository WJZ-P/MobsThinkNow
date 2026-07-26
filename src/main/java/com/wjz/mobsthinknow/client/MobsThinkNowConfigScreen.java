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
			Component.translatable("mobsthinknow.config.bait_tactics"),
			config.baitTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.bait_tactics.tooltip"))
			.setSaveConsumer(value -> ConfigManager.update(updated -> updated.baitTactics = value))
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

		return builder.build();
	}
}

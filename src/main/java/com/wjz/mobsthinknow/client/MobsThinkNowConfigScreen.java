package com.wjz.mobsthinknow.client;

import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** 使用 Cloth Config 构造的客户端配置页。 */
@Environment(EnvType.CLIENT)
public final class MobsThinkNowConfigScreen {
	private static final SystemToast.SystemToastId SAVE_FAILURE_TOAST = new SystemToast.SystemToastId();

	private MobsThinkNowConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		MobsThinkNowConfig edited = ConfigManager.editableCopy();
		MobsThinkNowConfig config = edited;
		ConfigBuilder builder = ConfigBuilder.create()
			.setParentScreen(parent)
			.setTitle(Component.translatable("mobsthinknow.config.title"));
		ConfigEntryBuilder entries = builder.entryBuilder();
		ConfigCategory generalCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.general")
		);
		generalCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.enabled"),
			config.enabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.enabled = value))
			.build());
		generalCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.zombie_ai_enabled"),
			config.zombieAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.zombie_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.zombieAiEnabled = value))
			.build());
		generalCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.pack_surrounding"),
			config.packSurrounding
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.pack_surrounding.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.packSurrounding = value))
			.build());
		generalCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.shield_flanking"),
			config.shieldFlanking
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.shield_flanking.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.shieldFlanking = value))
			.build());
		generalCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_ignore_friendly_fire"),
			config.squadIgnoreFriendlyFire
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_ignore_friendly_fire.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadIgnoreFriendlyFire = value))
			.build());
		generalCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.debug_logging"),
			config.debugLogging
		)
			.setDefaultValue(false)
			.setTooltip(Component.translatable("mobsthinknow.config.debug_logging.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.debugLogging = value))
			.build());
		ConfigCategory squadCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.squad")
		);
		ConfigCategory skeletonCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.skeleton")
		);
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_ai_enabled"),
			config.skeletonAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonAiEnabled = value))
			.build());
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_crossbows"),
			config.skeletonCrossbows
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_crossbows.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonCrossbows = value))
			.build());
		skeletonCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.skeleton_crossbow_chance"),
			(int)Math.round(config.skeletonCrossbowChance * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SKELETON_CROSSBOW_CHANCE * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_crossbow_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonCrossbowChance = value / 100.0))
			.build());
		skeletonCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.skeleton_firework_crossbow_chance"),
			(int)Math.round(config.skeletonFireworkCrossbowChance * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SKELETON_FIREWORK_CROSSBOW_CHANCE * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_firework_crossbow_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited,
				updated -> updated.skeletonFireworkCrossbowChance = value / 100.0
			))
			.build());
		skeletonCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.skeleton_preferred_range"),
			(int)Math.round(config.skeletonPreferredRange),
			(int)MobsThinkNowConfig.MINIMUM_SKELETON_PREFERRED_RANGE,
			(int)MobsThinkNowConfig.MAXIMUM_SKELETON_PREFERRED_RANGE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_SKELETON_PREFERRED_RANGE)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_preferred_range.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonPreferredRange = value))
			.build());
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_emergency_disengage"),
			config.skeletonEmergencyDisengage
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_emergency_disengage.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonEmergencyDisengage = value))
			.build());
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_cover_peeking"),
			config.skeletonCoverPeeking
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_cover_peeking.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonCoverPeeking = value))
			.build());
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_firing_lane_reposition"),
			config.skeletonFiringLaneReposition
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_firing_lane_reposition.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonFiringLaneReposition = value))
			.build());
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_projectile_dodging"),
			config.skeletonProjectileDodging
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_projectile_dodging.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonProjectileDodging = value))
			.build());
		skeletonCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.skeleton_predictive_aim"),
			config.skeletonPredictiveAim
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_predictive_aim.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.skeletonPredictiveAim = value))
			.build());
		skeletonCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.skeleton_aim_prediction_strength"),
			(int)Math.round(config.skeletonAimPredictionStrength * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SKELETON_AIM_PREDICTION_STRENGTH * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.skeleton_aim_prediction_strength.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited,
				updated -> updated.skeletonAimPredictionStrength = value / 100.0
			))
			.build());

		ConfigCategory creeperCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.creeper")
		);
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_ai_enabled"),
			config.creeperAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperAiEnabled = value))
			.build());
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_flanking"),
			config.creeperFlanking
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_flanking.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperFlanking = value))
			.build());
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_moving_fuse"),
			config.creeperMovingFuse
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_moving_fuse.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperMovingFuse = value))
			.build());
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_fuse_feints"),
			config.creeperFuseFeints
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_fuse_feints.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperFuseFeints = value))
			.build());
		creeperCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.creeper_fuse_feint_cooldown"),
			config.creeperFuseFeintCooldownTicks,
			MobsThinkNowConfig.MINIMUM_CREEPER_FUSE_FEINT_COOLDOWN_TICKS,
			MobsThinkNowConfig.MAXIMUM_CREEPER_FUSE_FEINT_COOLDOWN_TICKS
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_CREEPER_FUSE_FEINT_COOLDOWN_TICKS)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_fuse_feint_cooldown.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperFuseFeintCooldownTicks = value))
			.build());
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_squad_evacuation"),
			config.creeperSquadEvacuation
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_squad_evacuation.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperSquadEvacuation = value))
			.build());
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_blast_reservations"),
			config.creeperBlastReservations
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_blast_reservations.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperBlastReservations = value))
			.build());
		creeperCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.creeper_wall_breaching"),
			config.creeperWallBreaching
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_wall_breaching.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperWallBreaching = value))
			.build());
		creeperCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.creeper_maximum_fuse_start_distance"),
			(int)Math.round(config.creeperMaximumFuseStartDistance),
			(int)MobsThinkNowConfig.MINIMUM_CREEPER_MAXIMUM_FUSE_START_DISTANCE,
			(int)MobsThinkNowConfig.MAXIMUM_CREEPER_MAXIMUM_FUSE_START_DISTANCE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_CREEPER_MAXIMUM_FUSE_START_DISTANCE)
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_maximum_fuse_start_distance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperMaximumFuseStartDistance = value))
			.build());
		creeperCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.creeper_fuse_movement_speed"),
			(int)Math.round(config.creeperFuseMovementSpeed * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_CREEPER_FUSE_MOVEMENT_SPEED * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_CREEPER_FUSE_MOVEMENT_SPEED * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_CREEPER_FUSE_MOVEMENT_SPEED * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.creeper_fuse_movement_speed.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.creeperFuseMovementSpeed = value / 100.0))
			.build());

		ConfigCategory spiderCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.spider")
		);
		spiderCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spider_ai_enabled"),
			config.spiderAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderAiEnabled = value))
			.build());
		spiderCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spider_predictive_pounce"),
			config.spiderPredictivePounce
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_predictive_pounce.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderPredictivePounce = value))
			.build());
		spiderCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spider_hit_and_run"),
			config.spiderHitAndRun
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_hit_and_run.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderHitAndRun = value))
			.build());
		spiderCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spider_web_traps"),
			config.spiderWebTraps
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_web_traps.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderWebTraps = value))
			.build());
		spiderCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.spider_web_trap_cooldown"),
			config.spiderWebTrapCooldownTicks,
			MobsThinkNowConfig.MINIMUM_SPIDER_WEB_TRAP_COOLDOWN_TICKS,
			MobsThinkNowConfig.MAXIMUM_SPIDER_WEB_TRAP_COOLDOWN_TICKS
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_SPIDER_WEB_TRAP_COOLDOWN_TICKS)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_web_trap_cooldown.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderWebTrapCooldownTicks = value))
			.build());
		spiderCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.spider_web_trap_lifetime"),
			config.spiderWebTrapLifetimeTicks,
			MobsThinkNowConfig.MINIMUM_SPIDER_WEB_TRAP_LIFETIME_TICKS,
			MobsThinkNowConfig.MAXIMUM_SPIDER_WEB_TRAP_LIFETIME_TICKS
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_SPIDER_WEB_TRAP_LIFETIME_TICKS)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_web_trap_lifetime.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderWebTrapLifetimeTicks = value))
			.build());
		spiderCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spider_creeper_coordination"),
			config.spiderCreeperCoordination
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_creeper_coordination.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderCreeperCoordination = value))
			.build());
		spiderCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spider_transport_route_assessment"),
			config.spiderTransportRouteAssessment
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_transport_route_assessment.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderTransportRouteAssessment = value))
			.build());
		spiderCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.spider_creeper_search_radius"),
			(int)Math.round(config.spiderCreeperSearchRadius),
			(int)MobsThinkNowConfig.MINIMUM_SPIDER_CREEPER_SEARCH_RADIUS,
			(int)MobsThinkNowConfig.MAXIMUM_SPIDER_CREEPER_SEARCH_RADIUS
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_SPIDER_CREEPER_SEARCH_RADIUS)
			.setTooltip(Component.translatable("mobsthinknow.config.spider_creeper_search_radius.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderCreeperSearchRadius = value))
			.build());
		spiderCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.spider_creeper_carrier_speed"),
			(int)Math.round(config.spiderCreeperCarrierSpeed * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_SPIDER_CREEPER_CARRIER_SPEED * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_SPIDER_CREEPER_CARRIER_SPEED * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SPIDER_CREEPER_CARRIER_SPEED * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.spider_creeper_carrier_speed.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spiderCreeperCarrierSpeed = value / 100.0))
			.build());

		ConfigCategory endermanCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.enderman")
		);
		endermanCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.enderman_ai_enabled"),
			config.endermanAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.enderman_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.endermanAiEnabled = value))
			.build());
		endermanCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.enderman_creeper_delivery"),
			config.endermanCreeperDelivery
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.enderman_creeper_delivery.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.endermanCreeperDelivery = value))
			.build());
		endermanCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.enderman_creeper_search_radius"),
			(int)Math.round(config.endermanCreeperSearchRadius),
			(int)MobsThinkNowConfig.MINIMUM_ENDERMAN_CREEPER_SEARCH_RADIUS,
			(int)MobsThinkNowConfig.MAXIMUM_ENDERMAN_CREEPER_SEARCH_RADIUS
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_ENDERMAN_CREEPER_SEARCH_RADIUS)
			.setTooltip(Component.translatable("mobsthinknow.config.enderman_creeper_search_radius.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.endermanCreeperSearchRadius = value))
			.build());
		endermanCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.enderman_creeper_delivery_cooldown_seconds"),
			(int)Math.round(config.endermanCreeperDeliveryCooldownTicks / 20.0),
			MobsThinkNowConfig.MINIMUM_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS / 20,
			MobsThinkNowConfig.MAXIMUM_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS / 20
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS / 20)
			.setTooltip(Component.translatable("mobsthinknow.config.enderman_creeper_delivery_cooldown_seconds.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited,
				updated -> updated.endermanCreeperDeliveryCooldownTicks = value * 20
			))
			.build());
		endermanCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.enderman_creeper_drop_distance"),
			(int)Math.round(config.endermanCreeperDropDistance),
			(int)MobsThinkNowConfig.MINIMUM_ENDERMAN_CREEPER_DROP_DISTANCE,
			(int)MobsThinkNowConfig.MAXIMUM_ENDERMAN_CREEPER_DROP_DISTANCE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_ENDERMAN_CREEPER_DROP_DISTANCE)
			.setTooltip(Component.translatable("mobsthinknow.config.enderman_creeper_drop_distance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.endermanCreeperDropDistance = value))
			.build());
		endermanCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.enderman_creeper_front_delivery_chance"),
			(int)Math.round(config.endermanCreeperFrontDeliveryChance * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE * 100.0)
		)
			.setDefaultValue((int)Math.round(
				MobsThinkNowConfig.DEFAULT_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE * 100.0
			))
			.setTooltip(Component.translatable("mobsthinknow.config.enderman_creeper_front_delivery_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited,
				updated -> updated.endermanCreeperFrontDeliveryChance = value / 100.0
			))
			.build());

		ConfigCategory giantCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.giant")
		);
		giantCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.giant_zombie_ai_enabled"),
			config.giantZombieAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombieAiEnabled = value))
			.build());
		giantCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.giant_zombie_spawn_chance"),
			(int)Math.round(config.giantZombieSpawnChance * 1000.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_GIANT_ZOMBIE_SPAWN_CHANCE * 1000.0))
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_spawn_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombieSpawnChance = value / 1000.0))
			.build());
		giantCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.giant_zombie_maximum_health"),
			(int)Math.round(config.giantZombieMaximumHealth),
			(int)MobsThinkNowConfig.MINIMUM_GIANT_ZOMBIE_MAXIMUM_HEALTH,
			(int)MobsThinkNowConfig.MAXIMUM_GIANT_ZOMBIE_MAXIMUM_HEALTH
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_GIANT_ZOMBIE_MAXIMUM_HEALTH)
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_maximum_health.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombieMaximumHealth = value))
			.build());
		giantCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.giant_zombie_attack_damage"),
			(int)Math.round(config.giantZombieAttackDamage),
			(int)MobsThinkNowConfig.MINIMUM_GIANT_ZOMBIE_ATTACK_DAMAGE,
			(int)MobsThinkNowConfig.MAXIMUM_GIANT_ZOMBIE_ATTACK_DAMAGE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_GIANT_ZOMBIE_ATTACK_DAMAGE)
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_attack_damage.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombieAttackDamage = value))
			.build());
		giantCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.giant_zombie_movement_speed"),
			(int)Math.round(config.giantZombieMovementSpeed * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_GIANT_ZOMBIE_MOVEMENT_SPEED * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_GIANT_ZOMBIE_MOVEMENT_SPEED * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_GIANT_ZOMBIE_MOVEMENT_SPEED * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_movement_speed.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombieMovementSpeed = value / 100.0))
			.build());
		giantCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.giant_zombie_payload_throwing"),
			config.giantZombiePayloadThrowing
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_payload_throwing.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombiePayloadThrowing = value))
			.build());
		giantCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.giant_zombie_melee_actions"),
			config.giantZombieMeleeActions
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.giant_zombie_melee_actions.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.giantZombieMeleeActions = value))
			.build());

		ConfigCategory netherCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.nether")
		);
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.nether_ai_enabled"),
			config.netherAiEnabled
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.nether_ai_enabled.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.netherAiEnabled = value))
			.build());
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.nether_profession_skins"),
			config.netherProfessionSkins
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.nether_profession_skins.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.netherProfessionSkins = value))
			.build());
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.piglin_formation_tactics"),
			config.piglinFormationTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.piglin_formation_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.piglinFormationTactics = value))
			.build());
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.blaze_combat_tactics"),
			config.blazeCombatTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.blaze_combat_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.blazeCombatTactics = value))
			.build());
		netherCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.blaze_preferred_range"),
			(int)Math.round(config.blazePreferredRange),
			(int)MobsThinkNowConfig.MINIMUM_BLAZE_PREFERRED_RANGE,
			(int)MobsThinkNowConfig.MAXIMUM_BLAZE_PREFERRED_RANGE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_BLAZE_PREFERRED_RANGE)
			.setTooltip(Component.translatable("mobsthinknow.config.blaze_preferred_range.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.blazePreferredRange = value))
			.build());
		netherCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.nether_prediction_strength"),
			(int)Math.round(config.netherPredictionStrength * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_NETHER_PREDICTION_STRENGTH * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.nether_prediction_strength.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.netherPredictionStrength = value / 100.0))
			.build());
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.ghast_artillery_tactics"),
			config.ghastArtilleryTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.ghast_artillery_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.ghastArtilleryTactics = value))
			.build());
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.hoglin_charge_tactics"),
			config.hoglinChargeTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.hoglin_charge_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.hoglinChargeTactics = value))
			.build());
		netherCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.hoglin_charge_speed"),
			(int)Math.round(config.hoglinChargeSpeed * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_HOGLIN_CHARGE_SPEED * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_HOGLIN_CHARGE_SPEED * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_HOGLIN_CHARGE_SPEED * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.hoglin_charge_speed.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.hoglinChargeSpeed = value / 100.0))
			.build());
		netherCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.magma_cube_predictive_pounce"),
			config.magmaCubePredictivePounce
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.magma_cube_predictive_pounce.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.magmaCubePredictivePounce = value))
			.build());
		netherCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.magma_cube_pounce_speed"),
			(int)Math.round(config.magmaCubePounceSpeed * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_MAGMA_CUBE_POUNCE_SPEED * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_MAGMA_CUBE_POUNCE_SPEED * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_MAGMA_CUBE_POUNCE_SPEED * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.magma_cube_pounce_speed.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.magmaCubePounceSpeed = value / 100.0))
			.build());

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
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.maximumCoordinatedZombies = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.dynamic_squad_replanning"),
			config.dynamicSquadReplanning
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.dynamic_squad_replanning.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.dynamicSquadReplanning = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.observable_target_tactics"),
			config.observableTargetTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.observable_target_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.observableTargetTactics = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_shared_danger_memory"),
			config.squadSharedDangerMemory
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_shared_danger_memory.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadSharedDangerMemory = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_firing_lane_reservations"),
			config.squadFiringLaneReservations
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_firing_lane_reservations.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadFiringLaneReservations = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_web_ambush_followup"),
			config.squadWebAmbushFollowup
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_web_ambush_followup.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadWebAmbushFollowup = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_creeper_web_containment"),
			config.squadCreeperWebContainment
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_creeper_web_containment.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadCreeperWebContainment = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_spider_pounce_staggering"),
			config.squadSpiderPounceStaggering
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_spider_pounce_staggering.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadSpiderPounceStaggering = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.squad_spider_pounce_interval"),
			config.squadSpiderPounceIntervalTicks,
			MobsThinkNowConfig.MINIMUM_SQUAD_SPIDER_POUNCE_INTERVAL_TICKS,
			MobsThinkNowConfig.MAXIMUM_SQUAD_SPIDER_POUNCE_INTERVAL_TICKS
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_SQUAD_SPIDER_POUNCE_INTERVAL_TICKS)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_spider_pounce_interval.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadSpiderPounceIntervalTicks = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_shield_wall_rotation"),
			config.squadShieldWallRotation
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_shield_wall_rotation.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadShieldWallRotation = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_threat_distribution"),
			config.squadThreatDistribution
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_threat_distribution.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadThreatDistribution = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_casualty_extraction"),
			config.squadCasualtyExtraction
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_casualty_extraction.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadCasualtyExtraction = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_spider_casualty_transport"),
			config.squadSpiderCasualtyTransport
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_spider_casualty_transport.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadSpiderCasualtyTransport = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.squad_casualty_health_threshold"),
			(int)Math.round(config.squadCasualtyHealthThreshold * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_SQUAD_CASUALTY_HEALTH_THRESHOLD * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_SQUAD_CASUALTY_HEALTH_THRESHOLD * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SQUAD_CASUALTY_HEALTH_THRESHOLD * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.squad_casualty_health_threshold.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadCasualtyHealthThreshold = value / 100.0))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.squad_casualty_response_ticks"),
			config.squadCasualtyResponseTicks,
			MobsThinkNowConfig.MINIMUM_SQUAD_CASUALTY_RESPONSE_TICKS,
			MobsThinkNowConfig.MAXIMUM_SQUAD_CASUALTY_RESPONSE_TICKS
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_SQUAD_CASUALTY_RESPONSE_TICKS)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_casualty_response_ticks.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadCasualtyResponseTicks = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.briefing_ticks"),
			config.briefingTicks,
			60,
			100
		)
			.setDefaultValue(64)
			.setTooltip(Component.translatable("mobsthinknow.config.briefing_ticks.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.briefingTicks = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.regroup_ticks"),
			config.regroupTicks,
			40,
			80
		)
			.setDefaultValue(48)
			.setTooltip(Component.translatable("mobsthinknow.config.regroup_ticks.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.regroupTicks = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_visual_effects"),
			config.squadVisualEffects
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_visual_effects.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadVisualEffects = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.zombie_profession_skins"),
			config.zombieProfessionSkins
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.zombie_profession_skins.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.zombieProfessionSkins = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.zombie_body_language"),
			config.zombieBodyLanguage
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.zombie_body_language.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.zombieBodyLanguage = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.zombie_animation_blend_ticks"),
			config.zombieAnimationBlendTicks,
			0,
			8
		)
			.setDefaultValue(4)
			.setTooltip(Component.translatable("mobsthinknow.config.zombie_animation_blend_ticks.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.zombieAnimationBlendTicks = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.squad_role_name_tags"),
			config.squadRoleNameTags
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.squad_role_name_tags.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.squadRoleNameTags = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.individual_traits"),
			config.individualTraits
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.individual_traits.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.individualTraits = value))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.retreat_tactics"),
			config.retreatTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.retreatTactics = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_heavy_hit_threshold"),
			(int)Math.round(config.retreatHeavyHitThreshold * 100.0),
			5,
			100
		)
			.setDefaultValue(30)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_heavy_hit_threshold.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.retreatHeavyHitThreshold = value / 100.0))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_maximum_seconds"),
			(int)Math.round(config.retreatMaximumTicks / 20.0),
			MobsThinkNowConfig.MINIMUM_RETREAT_MAXIMUM_TICKS / 20,
			MobsThinkNowConfig.MAXIMUM_RETREAT_MAXIMUM_TICKS / 20
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_RETREAT_MAXIMUM_TICKS / 20)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_maximum_seconds.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.retreatMaximumTicks = value * 20))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_safe_distance"),
			(int)Math.round(config.retreatSafeDistance),
			(int)MobsThinkNowConfig.MINIMUM_RETREAT_SAFE_DISTANCE,
			(int)MobsThinkNowConfig.MAXIMUM_RETREAT_SAFE_DISTANCE
		)
			.setDefaultValue((int)MobsThinkNowConfig.DEFAULT_RETREAT_SAFE_DISTANCE)
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_safe_distance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.retreatSafeDistance = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.retreat_speed_percent"),
			(int)Math.round(config.retreatSpeedModifier * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_RETREAT_SPEED_MODIFIER * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_RETREAT_SPEED_MODIFIER * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_RETREAT_SPEED_MODIFIER * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.retreat_speed_percent.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.retreatSpeedModifier = value / 100.0))
			.build());
		squadCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.food_scavenging"),
			config.foodScavenging
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.food_scavenging.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.foodScavenging = value))
			.build());
		squadCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.food_minimum_intelligence"),
			config.foodMinimumIntelligence,
			MobsThinkNowConfig.MINIMUM_FOOD_MINIMUM_INTELLIGENCE,
			MobsThinkNowConfig.MAXIMUM_FOOD_MINIMUM_INTELLIGENCE
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_FOOD_MINIMUM_INTELLIGENCE)
			.setTooltip(Component.translatable("mobsthinknow.config.food_minimum_intelligence.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.foodMinimumIntelligence = value))
			.build());

		ConfigCategory terrainCategory = builder.getOrCreateCategory(
			Component.translatable("mobsthinknow.config.category.terrain")
		);
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.sunlight_survival"),
			config.sunlightSurvival
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.sunlight_survival.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.sunlightSurvival = value))
			.build());
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.smart_traversal"),
			config.smartTraversal
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.smart_traversal.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.smartTraversal = value))
			.build());
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.terrain_tactics"),
			config.terrainTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.terrain_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.terrainTactics = value))
			.build());
		terrainCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.terrain_minimum_intelligence"),
			config.terrainMinimumIntelligence,
			MobsThinkNowConfig.MINIMUM_TERRAIN_MINIMUM_INTELLIGENCE,
			MobsThinkNowConfig.MAXIMUM_TERRAIN_MINIMUM_INTELLIGENCE
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_TERRAIN_MINIMUM_INTELLIGENCE)
			.setTooltip(Component.translatable("mobsthinknow.config.terrain_minimum_intelligence.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.terrainMinimumIntelligence = value))
			.build());
		terrainCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.terrain_block_inventory_limit"),
			config.terrainBlockInventoryLimit,
			MobsThinkNowConfig.MINIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT,
			MobsThinkNowConfig.MAXIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_TERRAIN_BLOCK_INVENTORY_LIMIT)
			.setTooltip(Component.translatable("mobsthinknow.config.terrain_block_inventory_limit.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.terrainBlockInventoryLimit = value))
			.build());
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.engineer_skills"),
			config.engineerSkills
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.engineer_skills.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.engineerSkills = value))
			.build());
		terrainCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.engineer_spawn_chance"),
			(int)Math.round(config.engineerSpawnChance * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_ENGINEER_SPAWN_CHANCE * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.engineer_spawn_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.engineerSpawnChance = value / 100.0))
			.build());
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.engineer_tnt_skill"),
			config.engineerTntSkill
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.engineer_tnt_skill.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.engineerTntSkill = value))
			.build());
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.engineer_fluid_skills"),
			config.engineerFluidSkills
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.engineer_fluid_skills.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.engineerFluidSkills = value))
			.build());
		terrainCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.engineer_ignition_skill"),
			config.engineerIgnitionSkill
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.engineer_ignition_skill.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.engineerIgnitionSkill = value))
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
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.armedSquads = value))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.weapon_combat_tactics"),
			config.weaponCombatTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.weapon_combat_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.weaponCombatTactics = value))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.sword_feints"),
			config.swordFeints
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.sword_feints.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.swordFeints = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.sword_feint_minimum_intelligence"),
			config.swordFeintMinimumIntelligence,
			1,
			10
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_SWORD_FEINT_MINIMUM_INTELLIGENCE)
			.setTooltip(Component.translatable("mobsthinknow.config.sword_feint_minimum_intelligence.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.swordFeintMinimumIntelligence = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.sword_feint_chance"),
			(int)Math.round(config.swordFeintChance * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SWORD_FEINT_CHANCE * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.sword_feint_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.swordFeintChance = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.shield_bashes"),
			config.shieldBashes
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.shield_bashes.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.shieldBashes = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.shield_bash_minimum_intelligence"),
			config.shieldBashMinimumIntelligence,
			1,
			10
		)
			.setDefaultValue(MobsThinkNowConfig.DEFAULT_SHIELD_BASH_MINIMUM_INTELLIGENCE)
			.setTooltip(Component.translatable("mobsthinknow.config.shield_bash_minimum_intelligence.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.shieldBashMinimumIntelligence = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.shield_bash_chance"),
			(int)Math.round(config.shieldBashChance * 100.0),
			0,
			100
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SHIELD_BASH_CHANCE * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.shield_bash_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.shieldBashChance = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.shield_bash_damage"),
			(int)Math.round(config.shieldBashDamage * 10.0),
			0,
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_SHIELD_BASH_DAMAGE * 10.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SHIELD_BASH_DAMAGE * 10.0))
			.setTooltip(Component.translatable("mobsthinknow.config.shield_bash_damage.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.shieldBashDamage = value / 10.0))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.shield_bash_knockback"),
			(int)Math.round(config.shieldBashKnockback * 100.0),
			0,
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_SHIELD_BASH_KNOCKBACK * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SHIELD_BASH_KNOCKBACK * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.shield_bash_knockback.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.shieldBashKnockback = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.spear_air_assault"),
			config.spearAirAssault
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.spear_air_assault.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spearAirAssault = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.spear_rocket_efficiency"),
			(int)Math.round(config.spearRocketEfficiency * 100.0),
			(int)Math.round(MobsThinkNowConfig.MINIMUM_SPEAR_ROCKET_EFFICIENCY * 100.0),
			(int)Math.round(MobsThinkNowConfig.MAXIMUM_SPEAR_ROCKET_EFFICIENCY * 100.0)
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SPEAR_ROCKET_EFFICIENCY * 100.0))
			.setTooltip(Component.translatable("mobsthinknow.config.spear_rocket_efficiency.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.spearRocketEfficiency = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.special_equipment"),
			config.specialEquipment
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.special_equipment.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.specialEquipment = value))
			.build());
		armedCategory.addEntry(entries.startBooleanToggle(
			Component.translatable("mobsthinknow.config.fluid_tactics"),
			config.fluidTactics
		)
			.setDefaultValue(true)
			.setTooltip(Component.translatable("mobsthinknow.config.fluid_tactics.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.fluidTactics = value))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.water_bucket_chance"),
			(int)Math.round(config.waterBucketChance * 100.0),
			0,
			100
		)
			.setDefaultValue(4)
			.setTooltip(Component.translatable("mobsthinknow.config.water_bucket_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.waterBucketChance = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.lava_bucket_chance"),
			(int)Math.round(config.lavaBucketChance * 100.0),
			0,
			100
		)
			.setDefaultValue(2)
			.setTooltip(Component.translatable("mobsthinknow.config.lava_bucket_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.lavaBucketChance = value / 100.0))
			.build());
		armedCategory.addEntry(entries.startIntSlider(
			Component.translatable("mobsthinknow.config.special_equipment_drop_chance"),
			(int)Math.round(config.specialEquipmentDropChance * 1000.0),
			0,
			1000
		)
			.setDefaultValue((int)Math.round(MobsThinkNowConfig.DEFAULT_SPECIAL_EQUIPMENT_DROP_CHANCE * 1000.0))
			.setTooltip(Component.translatable("mobsthinknow.config.special_equipment_drop_chance.tooltip"))
			.setSaveConsumer(value -> updateDraft(edited, updated -> updated.specialEquipmentDropChance = value / 1000.0))
			.build());

		builder.setSavingRunnable(() -> saveDraft(edited));
		return builder.build();
	}

	private static void saveDraft(final MobsThinkNowConfig draft) {
		if (ConfigManager.replace(draft)) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		SystemToast.add(
			client.getToastManager(),
			SAVE_FAILURE_TOAST,
			Component.translatable("mobsthinknow.config.title"),
			Component.translatable("mobsthinknow.config.save_failed")
		);
	}

	private static void updateDraft(
		final MobsThinkNowConfig draft,
		final Consumer<MobsThinkNowConfig> updater
	) {
		updater.accept(draft);
	}
}

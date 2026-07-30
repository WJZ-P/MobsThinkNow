package com.wjz.mobsthinknow.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperMetrics;
import com.wjz.mobsthinknow.ai.spider.SmartSpiderMetrics;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

public final class MtnCommands {
	private MtnCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("mtn")
				.executes(MtnCommands::status)
				.then(Commands.literal("status").executes(MtnCommands::status))
				.then(
					Commands.literal("spawnall")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAll)
						.then(Commands.literal("skeletons").executes(MtnCommands::spawnAllSkeletons))
						.then(Commands.literal("creepers").executes(MtnCommands::spawnAllCreepers))
						.then(Commands.literal("spiders").executes(MtnCommands::spawnAllSpiders))
				)
				.then(
					Commands.literal("spawnskeletons")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllSkeletons)
				)
				.then(
					Commands.literal("spawncreepers")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllCreepers)
				)
				.then(
					Commands.literal("spawnspiders")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllSpiders)
				)
				.then(spawnSpecificCommand())
				.then(
					Commands.literal("reload")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::reload)
				)
		));
	}

	/** 每个兵种都注册成真实 literal，因此客户端按 Tab 就能直接看到完整可选列表。 */
	private static LiteralArgumentBuilder<CommandSourceStack> spawnSpecificCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("spawn")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
			.executes(MtnCommands::listSpawnTypes);
		for (ZombieShowcaseSpawner.ShowcaseArchetype archetype
			: ZombieShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecific(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, ZombieShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecific(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		for (SkeletonShowcaseSpawner.ShowcaseArchetype archetype
			: SkeletonShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecificSkeleton(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, SkeletonShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecificSkeleton(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		for (CreeperShowcaseSpawner.ShowcaseArchetype archetype
			: CreeperShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecificCreeper(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, CreeperShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecificCreeper(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		for (SpiderShowcaseSpawner.ShowcaseArchetype archetype
			: SpiderShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecificSpider(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, SpiderShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecificSpider(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		return command;
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		MobsThinkNowConfig config = ConfigManager.get();
		SmartZombieMetrics.Snapshot metrics = SmartZombieMetrics.snapshot();
		SmartSkeletonMetrics.Snapshot skeletonMetrics = SmartSkeletonMetrics.snapshot();
		SmartCreeperMetrics.Snapshot creeperMetrics = SmartCreeperMetrics.snapshot();
		SmartSpiderMetrics.Snapshot spiderMetrics = SmartSpiderMetrics.snapshot();
		String message = "Mobs Think Now | enabled=%s, zombieAI=%s, installed=%d, decisions=%d, flanks=%d, searches=%d, failedPaths=%d, squads=%d, elections=%d, reelections=%d, candidateChecks=%d, retreats=%d, terrainMined=%d, terrainPlaced=%d, perchedHits=%d, water=%d, lava=%d, fluidRecovered=%d, fluidLost=%d, engineerTnt=%d, engineerWater=%d, engineerLava=%d, engineerIgnitions=%d, skeletonAI=%s, skeletonGoals=%d, skeletonEmergencyGoals=%d, skeletonEscapes=%d, skeletonCoverPlans=%d, skeletonCoverShots=%d, skeletonKites=%d, skeletonDodges=%d, skeletonShots=%d, skeletonPredictedShots=%d, skeletonCrossbowShots=%d, skeletonFireworkShots=%d, creeperAI=%s, creeperGoals=%d, creeperFlanks=%d, creeperIntercepts=%d, creeperMovingFuses=%d, creeperBreaches=%d, creeperAborts=%d"
			.formatted(
				config.enabled,
				config.zombieAiEnabled,
				metrics.installedGoals(),
				metrics.decisions(),
				metrics.flankDecisions(),
				metrics.searchDecisions(),
				metrics.failedPaths(),
				ZombieSquadCoordinator.activeSquadCount(),
				metrics.leaderElections(),
				metrics.leaderReelections(),
				metrics.squadCandidateChecks(),
				metrics.retreats(),
				metrics.terrainBlocksHarvested(),
				metrics.terrainBlocksPlaced(),
				metrics.perchedAttacks(),
				metrics.waterDeployments(),
				metrics.lavaDeployments(),
				metrics.fluidRecoveries(),
				metrics.fluidSourcesLost(),
				metrics.engineerTntCharges(),
				metrics.engineerWaterDeployments(),
				metrics.engineerLavaDeployments(),
				metrics.engineerIgnitions(),
				config.skeletonAiEnabled,
				skeletonMetrics.installedGoals(),
				skeletonMetrics.installedEmergencyGoals(),
				skeletonMetrics.emergencyDisengages(),
				skeletonMetrics.coverPlans(),
				skeletonMetrics.coverShots(),
				skeletonMetrics.kites(),
				skeletonMetrics.projectileDodges(),
				skeletonMetrics.shots(),
				skeletonMetrics.predictiveShots(),
				skeletonMetrics.crossbowShots(),
				skeletonMetrics.fireworkCrossbowShots(),
				config.creeperAiEnabled,
				creeperMetrics.installedGoals(),
				creeperMetrics.flanks(),
				creeperMetrics.intercepts(),
				creeperMetrics.movingFuses(),
				creeperMetrics.breachFuses(),
				creeperMetrics.abortedFuses()
			);
		message += ", spiderAI=%s, spiderGoals=%d, spiderFlanks=%d, spiderPounces=%d, spiderRepositions=%d, spiderCarrierSearches=%d, spiderCandidateChecks=%d, spiderCreepersMounted=%d, spiderDeliveryFuses=%d"
			.formatted(
				config.spiderAiEnabled,
				spiderMetrics.installedGoals(),
				spiderMetrics.flanks(),
				spiderMetrics.pounces(),
				spiderMetrics.repositions(),
				spiderMetrics.carrierSearches(),
				spiderMetrics.carrierCandidateChecks(),
				spiderMetrics.creepersMounted(),
				spiderMetrics.deliveryFuses()
			);
		String statusMessage = message;
		context.getSource().sendSuccess(() -> Component.literal(statusMessage), false);
		return Command.SINGLE_SUCCESS;
	}

	private static int reload(final CommandContext<CommandSourceStack> context) {
		boolean loaded = ConfigManager.load();
		if (loaded) {
			context.getSource().sendSuccess(() -> Component.literal("Mobs Think Now configuration reloaded."), true);
			return Command.SINGLE_SUCCESS;
		}

		context.getSource().sendFailure(Component.literal("Mobs Think Now configuration could not be reloaded. Check the server log."));
		return 0;
	}

	private static int spawnAll(final CommandContext<CommandSourceStack> context) {
		ZombieShowcaseSpawner.SpawnResult result = ZombieShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all.success",
					"Spawned %s tactical zombie archetypes in a 3x3 formation; behavior still follows the current configuration.",
					count
				),
				true
			);
			return count;
		}

		ErrorMessage error = switch (result.failure()) {
			case PEACEFUL -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.no_space",
				"No nearby ground has enough safe space for the complete 3x3 zombie formation."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.create_failed",
				"Zombie entity creation failed; no showcase formation was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.add_failed",
				"Adding the formation to the world failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful spawn result reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int listSpawnTypes(final CommandContext<CommandSourceStack> context) {
		String zombieTypes = String.join(
			", ",
			Arrays.stream(ZombieShowcaseSpawner.ShowcaseArchetype.values())
				.map(ZombieShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String skeletonTypes = String.join(
			", ",
			Arrays.stream(SkeletonShowcaseSpawner.ShowcaseArchetype.values())
				.map(SkeletonShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String creeperTypes = String.join(
			", ",
			Arrays.stream(CreeperShowcaseSpawner.ShowcaseArchetype.values())
				.map(CreeperShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String spiderTypes = String.join(
			", ",
			Arrays.stream(SpiderShowcaseSpawner.ShowcaseArchetype.values())
				.map(SpiderShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String types = zombieTypes + ", " + skeletonTypes + ", " + creeperTypes + ", " + spiderTypes;
		context.getSource().sendSuccess(
			() -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.types",
				"Available tactical monster types (usage: /mtn spawn <type> [count], count 1-%s): %s",
				ZombieShowcaseSpawner.MAX_BATCH_SIZE,
				types
			),
			false
		);
		return Command.SINGLE_SUCCESS;
	}

	private static int spawnSpecific(
		final CommandContext<CommandSourceStack> context,
		final ZombieShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		ZombieShowcaseSpawner.SpawnResult result = ZombieShowcaseSpawner.spawnBatch(
			context.getSource(),
			archetype,
			requestedCount
		);
		if (result.success()) {
			int spawnedCount = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn.success",
					"Spawned %s × %s (%s).",
					spawnedCount,
					archetype.displayName(),
					archetype.commandId()
				),
				true
			);
			return spawnedCount;
		}

		Component error = switch (result.failure()) {
			case PEACEFUL -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.no_space",
				"No safe nearby ground was found for all %s requested zombies; nothing was spawned.",
				requestedCount
			);
			case CREATE_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.create_failed",
				"Preparing the requested batch of %s zombies failed; nothing was spawned.",
				requestedCount
			);
			case ADD_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the requested batch of %s zombies failed; the entire batch was rolled back.",
				requestedCount
			);
			case NONE -> throw new IllegalStateException("Successful specific spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private static int spawnAllSkeletons(final CommandContext<CommandSourceStack> context) {
		SkeletonShowcaseSpawner.SpawnResult result = SkeletonShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_skeletons.success",
					"Spawned %s tactical skeleton archetypes.",
					count
				),
				true
			);
			return count;
		}

		ErrorMessage error = switch (result.failure()) {
			case PEACEFUL -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_skeletons.no_space",
				"No nearby ground has enough safe space for all tactical skeleton archetypes."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_skeletons.create_failed",
				"Skeleton entity creation failed; no showcase formation was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_skeletons.add_failed",
				"Adding the skeleton formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful skeleton spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnSpecificSkeleton(
		final CommandContext<CommandSourceStack> context,
		final SkeletonShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		SkeletonShowcaseSpawner.SpawnResult result = SkeletonShowcaseSpawner.spawnBatch(
			context.getSource(),
			archetype,
			requestedCount
		);
		if (result.success()) {
			int spawnedCount = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn.success",
					"Spawned %s × %s (%s).",
					spawnedCount,
					archetype.displayName(),
					archetype.commandId()
				),
				true
			);
			return spawnedCount;
		}

		Component error = switch (result.failure()) {
			case PEACEFUL -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.no_space",
				"No safe nearby ground was found for all %s requested mobs; nothing was spawned.",
				requestedCount
			);
			case CREATE_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.create_failed",
				"Preparing the requested batch of %s mobs failed; nothing was spawned.",
				requestedCount
			);
			case ADD_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the requested batch of %s mobs failed; the entire batch was rolled back.",
				requestedCount
			);
			case NONE -> throw new IllegalStateException("Successful skeleton spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private static int spawnAllCreepers(final CommandContext<CommandSourceStack> context) {
		CreeperShowcaseSpawner.SpawnResult result = CreeperShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_creepers.success",
					"Spawned %s tactical creeper archetypes.",
					count
				),
				true
			);
			return count;
		}

		ErrorMessage error = switch (result.failure()) {
			case PEACEFUL -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_creepers.no_space",
				"No nearby ground has enough safe space for all tactical creeper archetypes."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_creepers.create_failed",
				"Creeper entity creation failed; no showcase formation was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_creepers.add_failed",
				"Adding the creeper formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful creeper spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnSpecificCreeper(
		final CommandContext<CommandSourceStack> context,
		final CreeperShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		CreeperShowcaseSpawner.SpawnResult result = CreeperShowcaseSpawner.spawnBatch(
			context.getSource(),
			archetype,
			requestedCount
		);
		if (result.success()) {
			int spawnedCount = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn.success",
					"Spawned %s × %s (%s).",
					spawnedCount,
					archetype.displayName(),
					archetype.commandId()
				),
				true
			);
			return spawnedCount;
		}

		Component error = switch (result.failure()) {
			case PEACEFUL -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.no_space",
				"No safe nearby ground was found for all %s requested mobs; nothing was spawned.",
				requestedCount
			);
			case CREATE_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.create_failed",
				"Preparing the requested batch of %s mobs failed; nothing was spawned.",
				requestedCount
			);
			case ADD_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the requested batch of %s mobs failed; the entire batch was rolled back.",
				requestedCount
			);
			case NONE -> throw new IllegalStateException("Successful creeper spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private static int spawnAllSpiders(final CommandContext<CommandSourceStack> context) {
		SpiderShowcaseSpawner.SpawnResult result = SpiderShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_spiders.success",
					"Spawned %s tactical spider archetypes.",
					count
				),
				true
			);
			return count;
		}

		ErrorMessage error = switch (result.failure()) {
			case PEACEFUL -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_spiders.no_space",
				"No nearby ground has enough safe space for all tactical spider archetypes."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_spiders.create_failed",
				"Spider entity creation failed; no showcase formation was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_spiders.add_failed",
				"Adding the spider formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful spider spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnSpecificSpider(
		final CommandContext<CommandSourceStack> context,
		final SpiderShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		SpiderShowcaseSpawner.SpawnResult result = SpiderShowcaseSpawner.spawnBatch(
			context.getSource(),
			archetype,
			requestedCount
		);
		if (result.success()) {
			int spawnedCount = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn.success",
					"Spawned %s × %s (%s).",
					spawnedCount,
					archetype.displayName(),
					archetype.commandId()
				),
				true
			);
			return spawnedCount;
		}

		Component error = switch (result.failure()) {
			case PEACEFUL -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.no_space",
				"No safe nearby ground was found for all %s requested mobs; nothing was spawned.",
				requestedCount
			);
			case CREATE_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.create_failed",
				"Preparing the requested batch of %s mobs failed; nothing was spawned.",
				requestedCount
			);
			case ADD_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the requested batch of %s mobs failed; the entire batch was rolled back.",
				requestedCount
			);
			case NONE -> throw new IllegalStateException("Successful spider spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private record ErrorMessage(String key, String fallback) {
	}
}

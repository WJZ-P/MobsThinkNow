package com.wjz.mobsthinknow.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
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
		return command;
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		MobsThinkNowConfig config = ConfigManager.get();
		SmartZombieMetrics.Snapshot metrics = SmartZombieMetrics.snapshot();
		SmartSkeletonMetrics.Snapshot skeletonMetrics = SmartSkeletonMetrics.snapshot();
		String message = "Mobs Think Now | enabled=%s, zombieAI=%s, installed=%d, decisions=%d, flanks=%d, searches=%d, failedPaths=%d, squads=%d, elections=%d, reelections=%d, candidateChecks=%d, retreats=%d, terrainMined=%d, terrainPlaced=%d, perchedHits=%d, water=%d, lava=%d, fluidRecovered=%d, fluidLost=%d, engineerTnt=%d, engineerWater=%d, engineerLava=%d, engineerIgnitions=%d, skeletonAI=%s, skeletonGoals=%d, skeletonEmergencyGoals=%d, skeletonEmergencyDisengages=%d, skeletonRetreats=%d, skeletonDodges=%d, skeletonShots=%d, skeletonPredictedShots=%d"
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
				skeletonMetrics.retreats(),
				skeletonMetrics.projectileDodges(),
				skeletonMetrics.shots(),
				skeletonMetrics.predictiveShots()
			);
		context.getSource().sendSuccess(() -> Component.literal(message), false);
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
		String types = String.join(
			", ",
			Arrays.stream(ZombieShowcaseSpawner.ShowcaseArchetype.values())
				.map(ZombieShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		context.getSource().sendSuccess(
			() -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.types",
				"Available tactical zombie types (usage: /mtn spawn <type> [count], count 1-%s): %s",
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

	private record ErrorMessage(String key, String fallback) {
	}
}

package com.wjz.mobsthinknow.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
					.executes(context -> spawnOne(context, archetype))
			);
		}
		return command;
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		MobsThinkNowConfig config = ConfigManager.get();
		SmartZombieMetrics.Snapshot metrics = SmartZombieMetrics.snapshot();
		String message = "Mobs Think Now | enabled=%s, zombieAI=%s, installed=%d, decisions=%d, flanks=%d, searches=%d, failedPaths=%d, squads=%d, elections=%d, reelections=%d, candidateChecks=%d, retreats=%d, terrainMined=%d, terrainPlaced=%d, perchedHits=%d, water=%d, lava=%d, fluidRecovered=%d, fluidLost=%d, engineerTnt=%d, engineerWater=%d, engineerLava=%d, engineerIgnitions=%d"
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
				metrics.engineerIgnitions()
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
				"Available tactical zombie types: %s",
				types
			),
			false
		);
		return Command.SINGLE_SUCCESS;
	}

	private static int spawnOne(
		final CommandContext<CommandSourceStack> context,
		final ZombieShowcaseSpawner.ShowcaseArchetype archetype
	) {
		ZombieShowcaseSpawner.SpawnResult result = ZombieShowcaseSpawner.spawnOne(context.getSource(), archetype);
		if (result.success()) {
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn.success",
					"Spawned %s (%s).",
					archetype.displayName(),
					archetype.commandId()
				),
				true
			);
			return Command.SINGLE_SUCCESS;
		}

		ErrorMessage error = switch (result.failure()) {
			case PEACEFUL -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.peaceful",
				"Peaceful difficulty removes hostile mobs; switch difficulty before using this command."
			);
			case NO_SPACE -> new ErrorMessage(
				"mobsthinknow.command.spawn.no_space",
				"No safe nearby ground was found for this tactical zombie."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn.create_failed",
				"Creating the selected zombie entity failed."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the selected zombie to the world failed."
			);
			case NONE -> throw new IllegalStateException("Successful single spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private record ErrorMessage(String key, String fallback) {
	}
}

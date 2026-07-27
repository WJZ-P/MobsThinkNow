package com.wjz.mobsthinknow.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.wjz.mobsthinknow.ai.zombie.SmartZombieMetrics;
import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
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
					Commands.literal("reload")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::reload)
				)
		));
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		MobsThinkNowConfig config = ConfigManager.get();
		SmartZombieMetrics.Snapshot metrics = SmartZombieMetrics.snapshot();
		String message = "Mobs Think Now | enabled=%s, zombieAI=%s, installed=%d, decisions=%d, flanks=%d, searches=%d, failedPaths=%d, squads=%d, elections=%d, reelections=%d, candidateChecks=%d, retreats=%d, terrainMined=%d, terrainPlaced=%d, perchedHits=%d, water=%d, lava=%d, fluidRecovered=%d, fluidLost=%d"
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
				metrics.fluidSourcesLost()
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
}

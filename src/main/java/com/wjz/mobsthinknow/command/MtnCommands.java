package com.wjz.mobsthinknow.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.wjz.mobsthinknow.ai.activity.TacticalActivityLease;
import com.wjz.mobsthinknow.ai.skeleton.SmartSkeletonMetrics;
import com.wjz.mobsthinknow.ai.creeper.SmartCreeperMetrics;
import com.wjz.mobsthinknow.ai.enderman.SmartEndermanMetrics;
import com.wjz.mobsthinknow.ai.giant.SmartGiantMetrics;
import com.wjz.mobsthinknow.ai.nether.SmartNetherMetrics;
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
						.then(Commands.literal("zombies").executes(MtnCommands::spawnAllZombies))
						.then(Commands.literal("skeletons").executes(MtnCommands::spawnAllSkeletons))
						.then(Commands.literal("creepers").executes(MtnCommands::spawnAllCreepers))
						.then(Commands.literal("spiders").executes(MtnCommands::spawnAllSpiders))
						.then(Commands.literal("endermen").executes(MtnCommands::spawnAllEndermen))
						.then(Commands.literal("giants").executes(MtnCommands::spawnAllGiants))
						.then(Commands.literal("nether").executes(MtnCommands::spawnAllNether))
						.then(Commands.literal("assault").executes(context -> spawnOverworldAssault(context, 1)))
				)
				.then(
					Commands.literal("spawnzombies")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllZombies)
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
				.then(
					Commands.literal("spawnendermen")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllEndermen)
				)
				.then(
					Commands.literal("spawngiants")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllGiants)
				)
				.then(
					Commands.literal("spawnoverworldassault")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(context -> spawnOverworldAssault(context, 1))
						.then(
							Commands.argument("groups", IntegerArgumentType.integer(1, OverworldAssaultShowcaseSpawner.MAX_GROUPS))
								.executes(context -> spawnOverworldAssault(
									context,
									IntegerArgumentType.getInteger(context, "groups")
								))
						)
				)
				.then(
					Commands.literal("spawnnether")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
						.executes(MtnCommands::spawnAllNether)
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
		// 先注册全局、分类与基础实体入口；所有战术/变种预设仍保留独立 Tab literal。
		command.then(Commands.literal("all").executes(MtnCommands::spawnAll));
		command.then(Commands.literal("zombies").executes(MtnCommands::spawnAllZombies));
		command.then(Commands.literal("skeletons").executes(MtnCommands::spawnAllSkeletons));
		command.then(Commands.literal("creepers").executes(MtnCommands::spawnAllCreepers));
		command.then(Commands.literal("spiders").executes(MtnCommands::spawnAllSpiders));
		command.then(Commands.literal("endermen").executes(MtnCommands::spawnAllEndermen));
		command.then(Commands.literal("giants").executes(MtnCommands::spawnAllGiants));
		command.then(Commands.literal("nether").executes(MtnCommands::spawnAllNether));
		command.then(
			Commands.literal("overworld_assault")
				.executes(context -> spawnOverworldAssault(context, 1))
				.then(
					Commands.argument("groups", IntegerArgumentType.integer(1, OverworldAssaultShowcaseSpawner.MAX_GROUPS))
						.executes(context -> spawnOverworldAssault(
							context,
							IntegerArgumentType.getInteger(context, "groups")
						))
				)
		);
		command.then(
			Commands.literal("zombie")
				.executes(context -> spawnSpecific(
					context,
					ZombieShowcaseSpawner.ShowcaseArchetype.UNARMED,
					1
				))
				.then(
					Commands.argument("count", IntegerArgumentType.integer(1, ZombieShowcaseSpawner.MAX_BATCH_SIZE))
						.executes(context -> spawnSpecific(
							context,
							ZombieShowcaseSpawner.ShowcaseArchetype.UNARMED,
							IntegerArgumentType.getInteger(context, "count")
						))
				)
		);
		command.then(
			Commands.literal("skeleton")
				.executes(context -> spawnSpecificSkeleton(
					context,
					SkeletonShowcaseSpawner.ShowcaseArchetype.BOW,
					1
				))
				.then(
					Commands.argument("count", IntegerArgumentType.integer(1, SkeletonShowcaseSpawner.MAX_BATCH_SIZE))
						.executes(context -> spawnSpecificSkeleton(
							context,
							SkeletonShowcaseSpawner.ShowcaseArchetype.BOW,
							IntegerArgumentType.getInteger(context, "count")
						))
				)
		);
		command.then(
			Commands.literal("creeper")
				.executes(context -> spawnSpecificCreeper(
					context,
					CreeperShowcaseSpawner.ShowcaseArchetype.HUNTER,
					1
				))
				.then(
					Commands.argument("count", IntegerArgumentType.integer(1, CreeperShowcaseSpawner.MAX_BATCH_SIZE))
						.executes(context -> spawnSpecificCreeper(
							context,
							CreeperShowcaseSpawner.ShowcaseArchetype.HUNTER,
							IntegerArgumentType.getInteger(context, "count")
						))
				)
		);
		command.then(
			Commands.literal("spider")
				.executes(context -> spawnSpecificSpider(
					context,
					SpiderShowcaseSpawner.ShowcaseArchetype.HUNTER,
					1
				))
				.then(
					Commands.argument("count", IntegerArgumentType.integer(1, SpiderShowcaseSpawner.MAX_BATCH_SIZE))
						.executes(context -> spawnSpecificSpider(
							context,
							SpiderShowcaseSpawner.ShowcaseArchetype.HUNTER,
							IntegerArgumentType.getInteger(context, "count")
						))
				)
		);
		command.then(
			Commands.literal("enderman")
				.executes(context -> spawnSpecificEnderman(
					context,
					EndermanShowcaseSpawner.ShowcaseArchetype.HUNTER,
					1
				))
				.then(
					Commands.argument("count", IntegerArgumentType.integer(1, EndermanShowcaseSpawner.MAX_BATCH_SIZE))
						.executes(context -> spawnSpecificEnderman(
							context,
							EndermanShowcaseSpawner.ShowcaseArchetype.HUNTER,
							IntegerArgumentType.getInteger(context, "count")
						))
				)
		);
		command.then(
			Commands.literal("giant")
				.executes(context -> spawnSpecificGiant(
					context,
					GiantShowcaseSpawner.ShowcaseArchetype.GIANT_SIEGE,
					1
				))
				.then(
					Commands.argument("count", IntegerArgumentType.integer(1, GiantShowcaseSpawner.MAX_BATCH_SIZE))
						.executes(context -> spawnSpecificGiant(
							context,
							GiantShowcaseSpawner.ShowcaseArchetype.GIANT_SIEGE,
							IntegerArgumentType.getInteger(context, "count")
						))
				)
		);
		command.then(netherAlias("piglin", NetherShowcaseSpawner.ShowcaseArchetype.PIGLIN_CROSSBOW));
		command.then(netherAlias("hoglin", NetherShowcaseSpawner.ShowcaseArchetype.HOGLIN_CHARGER));
		command.then(netherAlias("zoglin", NetherShowcaseSpawner.ShowcaseArchetype.ZOGLIN_CHARGER));
		command.then(netherAlias("blaze", NetherShowcaseSpawner.ShowcaseArchetype.BLAZE_SKIRMISHER));
		command.then(netherAlias("ghast", NetherShowcaseSpawner.ShowcaseArchetype.GHAST_ARTILLERY));
		command.then(netherAlias("magma_cube", NetherShowcaseSpawner.ShowcaseArchetype.MAGMA_CUBE_HUNTER));
		command.then(netherAlias("zombified_piglin", NetherShowcaseSpawner.ShowcaseArchetype.ZOMBIFIED_PIGLIN_BERSERKER));
		command.then(netherAlias("wither_skeleton", NetherShowcaseSpawner.ShowcaseArchetype.WITHER_SKELETON_REAPER));
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
		for (EndermanShowcaseSpawner.ShowcaseArchetype archetype
			: EndermanShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecificEnderman(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, EndermanShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecificEnderman(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		for (GiantShowcaseSpawner.ShowcaseArchetype archetype
			: GiantShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecificGiant(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, GiantShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecificGiant(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		for (NetherShowcaseSpawner.ShowcaseArchetype archetype
			: NetherShowcaseSpawner.ShowcaseArchetype.values()) {
			command.then(
				Commands.literal(archetype.commandId())
					.executes(context -> spawnSpecificNether(context, archetype, 1))
					.then(
						Commands.argument(
							"count",
							IntegerArgumentType.integer(1, NetherShowcaseSpawner.MAX_BATCH_SIZE)
						).executes(context -> spawnSpecificNether(
							context,
							archetype,
							IntegerArgumentType.getInteger(context, "count")
						))
					)
			);
		}
		return command;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> netherAlias(
		final String literal,
		final NetherShowcaseSpawner.ShowcaseArchetype archetype
	) {
		return Commands.literal(literal)
			.executes(context -> spawnSpecificNether(context, archetype, 1))
			.then(
				Commands.argument("count", IntegerArgumentType.integer(1, NetherShowcaseSpawner.MAX_BATCH_SIZE))
					.executes(context -> spawnSpecificNether(
						context,
						archetype,
						IntegerArgumentType.getInteger(context, "count")
					))
			);
	}

	private static int status(final CommandContext<CommandSourceStack> context) {
		MobsThinkNowConfig config = ConfigManager.get();
		SmartZombieMetrics.Snapshot metrics = SmartZombieMetrics.snapshot();
		SmartSkeletonMetrics.Snapshot skeletonMetrics = SmartSkeletonMetrics.snapshot();
		SmartCreeperMetrics.Snapshot creeperMetrics = SmartCreeperMetrics.snapshot();
		SmartSpiderMetrics.Snapshot spiderMetrics = SmartSpiderMetrics.snapshot();
		SmartEndermanMetrics.Snapshot endermanMetrics = SmartEndermanMetrics.snapshot();
		SmartGiantMetrics.Snapshot giantMetrics = SmartGiantMetrics.snapshot();
		SmartNetherMetrics.Snapshot netherMetrics = SmartNetherMetrics.snapshot();
		String message = "Mobs Think Now | enabled=%s, zombieAI=%s, installed=%d, decisions=%d, flanks=%d, searches=%d, failedPaths=%d, squads=%d, activeActivities=%d, elections=%d, reelections=%d, candidateChecks=%d, assaultPlans=%d, crossfirePlans=%d, mountedBreachPlans=%d, combinedArmsPlans=%d, retreats=%d, terrainMined=%d, terrainPlaced=%d, perchedHits=%d, water=%d, lava=%d, fluidRecovered=%d, fluidLost=%d, engineerTnt=%d, engineerWater=%d, engineerLava=%d, engineerIgnitions=%d, swordFeints=%d, axeWindups=%d, shieldBashes=%d, shieldBashHits=%d, leaderSocialGestures=%d, memberSocialGestures=%d, briefingRouteChecks=%d, briefingRouteObjections=%d, briefingReplans=%d, combatRouteFailures=%d, combatRouteChecks=%d, combatReplans=%d, combatReplanSuppressed=%d, targetTacticChanges=%d, skeletonAI=%s, skeletonGoals=%d, skeletonEmergencyGoals=%d, skeletonEscapes=%d, skeletonCoverPlans=%d, skeletonCoverShots=%d, skeletonKites=%d, skeletonDodges=%d, skeletonShots=%d, skeletonPredictedShots=%d, skeletonCrossbowShots=%d, skeletonFireworkShots=%d, friendlyShotsHeld=%d, explosiveShotsHeld=%d, firingLaneReplans=%d, creeperAI=%s, creeperGoals=%d, creeperFlanks=%d, creeperIntercepts=%d, creeperMovingFuses=%d, creeperBreaches=%d, creeperAborts=%d, creeperSquadEvacuations=%d"
			.formatted(
				config.enabled,
				config.zombieAiEnabled,
				metrics.installedGoals(),
				metrics.decisions(),
				metrics.flankDecisions(),
				metrics.searchDecisions(),
				metrics.failedPaths(),
				ZombieSquadCoordinator.activeSquadCount(),
				TacticalActivityLease.activeLeaseCount(context.getSource().getLevel().getGameTime()),
				metrics.leaderElections(),
				metrics.leaderReelections(),
				metrics.squadCandidateChecks(),
				metrics.assaultPlans(),
				metrics.crossfirePlans(),
				metrics.mountedBreachPlans(),
				metrics.combinedArmsPlans(),
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
				metrics.swordFeints(),
				metrics.axeWindups(),
				metrics.shieldBashes(),
				metrics.shieldBashHits(),
				metrics.leaderSocialGestures(),
				metrics.memberSocialGestures(),
				metrics.briefingRouteChecks(),
				metrics.briefingRouteObjections(),
				metrics.briefingReplans(),
				metrics.combatRouteFailures(),
				metrics.combatRouteChecks(),
				metrics.combatReplans(),
				metrics.combatReplanSuppressed(),
				metrics.targetTacticChanges(),
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
				skeletonMetrics.friendlyShotsHeld(),
				skeletonMetrics.explosiveShotsHeld(),
				skeletonMetrics.firingLaneReplans(),
				config.creeperAiEnabled,
				creeperMetrics.installedGoals(),
				creeperMetrics.flanks(),
				creeperMetrics.intercepts(),
				creeperMetrics.movingFuses(),
				creeperMetrics.breachFuses(),
				creeperMetrics.abortedFuses(),
				creeperMetrics.squadEvacuations()
			);
		message += ", sharedDangers=%d, dangersAvoided=%d, activeDangerCells=%d, secondaryThreats=%d, threatReassignments=%d, activeSecondaryTargets=%d, firingLanesReserved=%d, alliesClearedLanes=%d, activeFiringLanes=%d, blastReservations=%d, blastConflicts=%d, blastReleases=%d, activeBlastReservations=%d"
			.formatted(
				metrics.sharedDangersReported(),
				metrics.sharedDangersAvoided(),
				ZombieSquadCoordinator.activeDangerMemoryCount(),
				metrics.secondaryThreatsObserved(),
				metrics.threatAssignmentsChanged(),
				ZombieSquadCoordinator.activeSecondaryTargetAssignments(),
				skeletonMetrics.firingLanesReserved(),
				skeletonMetrics.alliesClearedFiringLanes(),
				ZombieSquadCoordinator.activeFiringLaneCount(),
				creeperMetrics.blastReservationsAcquired(),
				creeperMetrics.blastReservationConflicts(),
				creeperMetrics.blastReservationsReleased(),
				ZombieSquadCoordinator.activeBlastReservationCount()
			);
		message += ", spiderAI=%s, spiderGoals=%d, spiderFlanks=%d, spiderPounces=%d, spiderRepositions=%d, spiderWebWindups=%d, spiderWebsPlaced=%d, spiderWebsExpired=%d, activeSpiderWebs=%d, spiderCarrierSearches=%d, spiderCandidateChecks=%d, spiderCreepersMounted=%d, spiderDeliveryFuses=%d, spiderBreachStaging=%d, spiderMobileFireSupport=%d, spiderRouteChecks=%d, spiderRouteRejections=%d, spiderSafeDismounts=%d"
			.formatted(
				config.spiderAiEnabled,
				spiderMetrics.installedGoals(),
				spiderMetrics.flanks(),
				spiderMetrics.pounces(),
				spiderMetrics.repositions(),
				spiderMetrics.webTrapWindups(),
				spiderMetrics.webTrapsPlaced(),
				spiderMetrics.webTrapsExpired(),
				com.wjz.mobsthinknow.ai.spider.SpiderWebTrapRegistry.activeCount(),
				spiderMetrics.carrierSearches(),
				spiderMetrics.carrierCandidateChecks(),
				spiderMetrics.creepersMounted(),
				spiderMetrics.deliveryFuses(),
				spiderMetrics.coordinatedBreachStaging(),
				spiderMetrics.mobileFireSupportMoves(),
				spiderMetrics.transportRouteChecks(),
				spiderMetrics.transportRouteRejections(),
				spiderMetrics.transportSafeDismounts()
			);
		message += ", endermanAI=%s, endermanGoals=%d, endermanCarrierSearches=%d, endermanCandidateChecks=%d, endermanPayloadsPickedUp=%d, endermanDeliveryTeleports=%d, endermanPayloadsIgnited=%d, endermanCombatTeleports=%d, endermanShieldBlocks=%d, endermanShieldCounterHits=%d, endermanSpearCharges=%d, endermanProfessionHits=%d"
			.formatted(
				config.endermanAiEnabled,
				endermanMetrics.installedGoals(),
				endermanMetrics.carrierSearches(),
				endermanMetrics.candidateChecks(),
				endermanMetrics.payloadsPickedUp(),
				endermanMetrics.deliveryTeleports(),
				endermanMetrics.payloadsIgnited(),
				endermanMetrics.combatTeleports(),
				endermanMetrics.shieldBlocks(),
				endermanMetrics.shieldCounterHits(),
				endermanMetrics.spearCharges(),
				endermanMetrics.professionHits()
			);
		message += ", giantAI=%s, giantGoals=%d, giantConversions=%d, giantRiders=%d, giantPayloadsPickedUp=%d, giantCreepersThrown=%d, giantZombiesThrown=%d, giantMeleeActions=%d, giantMeleeImpacts=%d, giantMeleeVictims=%d, giantGrabs=%d, giantGrabThrows=%d, giantMeleeInterrupts=%d, giantSafeReleaseRelocations=%d"
			.formatted(
				config.giantZombieAiEnabled,
				giantMetrics.installedGoals(),
				giantMetrics.zombiesConverted(),
				giantMetrics.ridersMounted(),
				giantMetrics.payloadsPickedUp(),
				giantMetrics.creepersThrown(),
				giantMetrics.zombiesThrown(),
				giantMetrics.meleeActionsStarted(),
				giantMetrics.meleeImpacts(),
				giantMetrics.meleeVictimsHit(),
				giantMetrics.targetsGrabbed(),
				giantMetrics.grabThrowsCompleted(),
				giantMetrics.meleeInterrupts(),
				giantMetrics.grappleReleaseRelocations()
			);
		message += ", netherAI=%s, netherControllers=%d, piglinFormationMoves=%d, blazeVolleys=%d, blazeFireballs=%d, ghastShots=%d, ghastRelocations=%d, hoglinCharges=%d, hoglinImpacts=%d, magmaPounces=%d, netherUndeadFeints=%d, netherUndeadLunges=%d, netherUndeadStrikes=%d, netherUndeadPredictedShots=%d"
			.formatted(
				config.netherAiEnabled,
				netherMetrics.installedControllers(),
				netherMetrics.piglinFormationMoves(),
				netherMetrics.blazeVolleys(),
				netherMetrics.blazeFireballs(),
				netherMetrics.ghastShots(),
				netherMetrics.ghastRelocations(),
				netherMetrics.hoglinCharges(),
				netherMetrics.hoglinImpacts(),
				netherMetrics.magmaPounces(),
				netherMetrics.netherUndeadFeints(),
				netherMetrics.netherUndeadLunges(),
				netherMetrics.netherUndeadStrikes(),
				netherMetrics.netherUndeadPredictedShots()
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

	private static int spawnOverworldAssault(
		final CommandContext<CommandSourceStack> context,
		final int groups
	) {
		OverworldAssaultShowcaseSpawner.SpawnResult result = OverworldAssaultShowcaseSpawner.spawn(
			context.getSource(),
			groups
		);
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_overworld_assault.success",
					"Spawned %s combined-arms assault group(s), %s mobs total; targeted command executor: %s.",
					result.groups(),
					count,
					result.targetedExecutor()
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
				"mobsthinknow.command.spawn_overworld_assault.no_space",
				"No nearby ground has enough safe space for the complete combined-arms formation."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_overworld_assault.create_failed",
				"Preparing the combined-arms formation failed; no assault mobs were added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_overworld_assault.add_failed",
				"Adding the combined-arms formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful assault spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnAll(final CommandContext<CommandSourceStack> context) {
		AllShowcaseSpawner.SpawnResult result = AllShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawnedRoots().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all.success",
					"Spawned %s intelligent-AI archetypes (%s entities): %s zombies, %s skeletons, %s creepers, %s spiders, %s endermen, %s giants, and %s Nether mobs.",
					count,
					result.totalEntities(),
					AllShowcaseSpawner.ZOMBIE_ARCHETYPES,
					AllShowcaseSpawner.SKELETON_ARCHETYPES,
					AllShowcaseSpawner.CREEPER_ARCHETYPES,
					AllShowcaseSpawner.SPIDER_ARCHETYPES,
					AllShowcaseSpawner.ENDERMAN_ARCHETYPES,
					AllShowcaseSpawner.GIANT_ARCHETYPES,
					AllShowcaseSpawner.NETHER_ARCHETYPES
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
				"No nearby ground has enough safe space for the complete mixed intelligent-monster formation."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all.create_failed",
				"Preparing the mixed intelligent-monster formation failed; no showcase entities were added."
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

	private static int spawnAllZombies(final CommandContext<CommandSourceStack> context) {
		ZombieShowcaseSpawner.SpawnResult result = ZombieShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_zombies.success",
					"Spawned %s zombie-family entries in a compact mixed formation.",
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
				"mobsthinknow.command.spawn_all_zombies.no_space",
				"No nearby ground has enough safe space for the complete zombie-family formation."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_zombies.create_failed",
				"Zombie entity creation failed; no showcase formation was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_zombies.add_failed",
				"Adding the zombie formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful zombie spawn reached the failure branch.");
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
		String endermanTypes = String.join(
			", ",
			Arrays.stream(EndermanShowcaseSpawner.ShowcaseArchetype.values())
				.map(EndermanShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String giantTypes = String.join(
			", ",
			Arrays.stream(GiantShowcaseSpawner.ShowcaseArchetype.values())
				.map(GiantShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String netherTypes = String.join(
			", ",
			Arrays.stream(NetherShowcaseSpawner.ShowcaseArchetype.values())
				.map(NetherShowcaseSpawner.ShowcaseArchetype::commandId)
				.toList()
		);
		String types = "all, overworld_assault, zombie, skeleton, creeper, spider, enderman, giant, piglin, hoglin, zoglin, blaze, ghast, magma_cube, zombified_piglin, wither_skeleton, zombies, skeletons, creepers, spiders, endermen, giants, nether, "
			+ zombieTypes + ", " + skeletonTypes + ", " + creeperTypes + ", " + spiderTypes + ", " + endermanTypes
			+ ", " + giantTypes + ", " + netherTypes;
		context.getSource().sendSuccess(
			() -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.types",
				"Available spawn entries (base/variant count 1-%s; all/plural entries spawn a complete group): %s",
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

	private static int spawnAllEndermen(final CommandContext<CommandSourceStack> context) {
		EndermanShowcaseSpawner.SpawnResult result = EndermanShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_endermen.success",
					"Spawned %s tactical enderman archetypes.",
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
				"mobsthinknow.command.spawn_all_endermen.no_space",
				"No nearby ground has enough safe space for all tactical enderman archetypes."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_endermen.create_failed",
				"Enderman or creeper payload creation failed; no showcase formation was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_endermen.add_failed",
				"Adding the enderman formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful enderman spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnSpecificEnderman(
		final CommandContext<CommandSourceStack> context,
		final EndermanShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		EndermanShowcaseSpawner.SpawnResult result = EndermanShowcaseSpawner.spawnBatch(
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
			case NONE -> throw new IllegalStateException("Successful enderman spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private static int spawnAllGiants(final CommandContext<CommandSourceStack> context) {
		GiantShowcaseSpawner.SpawnResult result = GiantShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_giants.success",
					"Spawned %s giant siege platform archetypes (%s total entities).",
					count,
					count * 4
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
				"mobsthinknow.command.spawn_all_giants.no_space",
				"No nearby ground has the full twelve-block clearance required by a giant siege platform."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_giants.create_failed",
				"Preparing the giant, head rider, and two hand payloads failed; nothing was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_giants.add_failed",
				"Adding the giant siege platform failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful giant spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnSpecificGiant(
		final CommandContext<CommandSourceStack> context,
		final GiantShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		GiantShowcaseSpawner.SpawnResult result = GiantShowcaseSpawner.spawnBatch(
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
				"No safe nearby ground was found for all %s requested giant platforms; nothing was spawned.",
				requestedCount
			);
			case CREATE_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.create_failed",
				"Preparing the requested batch of %s giant platforms failed; nothing was spawned.",
				requestedCount
			);
			case ADD_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the requested batch of %s giant platforms failed; the entire batch was rolled back.",
				requestedCount
			);
			case NONE -> throw new IllegalStateException("Successful giant spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private static int spawnAllNether(final CommandContext<CommandSourceStack> context) {
		NetherShowcaseSpawner.SpawnResult result = NetherShowcaseSpawner.spawnAll(context.getSource());
		if (result.success()) {
			int count = result.spawned().size();
			context.getSource().sendSuccess(
				() -> Component.translatableWithFallback(
					"mobsthinknow.command.spawn_all_nether.success",
					"Spawned %s intelligent Nether combat archetypes.",
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
				"mobsthinknow.command.spawn_all_nether.no_space",
				"No nearby formation has enough ground and air clearance for every Nether archetype."
			);
			case CREATE_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_nether.create_failed",
				"Preparing the Nether combat formation failed; nothing was added."
			);
			case ADD_FAILED -> new ErrorMessage(
				"mobsthinknow.command.spawn_all_nether.add_failed",
				"Adding the Nether combat formation failed; this spawn attempt was rolled back."
			);
			case NONE -> throw new IllegalStateException("Successful Nether spawn reached the failure branch.");
		};
		context.getSource().sendFailure(Component.translatableWithFallback(error.key(), error.fallback()));
		return 0;
	}

	private static int spawnSpecificNether(
		final CommandContext<CommandSourceStack> context,
		final NetherShowcaseSpawner.ShowcaseArchetype archetype,
		final int requestedCount
	) {
		NetherShowcaseSpawner.SpawnResult result = NetherShowcaseSpawner.spawnBatch(
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
				"No safe nearby space was found for all %s requested Nether mobs; nothing was spawned.",
				requestedCount
			);
			case CREATE_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.create_failed",
				"Preparing the requested batch of %s Nether mobs failed; nothing was spawned.",
				requestedCount
			);
			case ADD_FAILED -> Component.translatableWithFallback(
				"mobsthinknow.command.spawn.add_failed",
				"Adding the requested batch of %s Nether mobs failed; the entire batch was rolled back.",
				requestedCount
			);
			case NONE -> throw new IllegalStateException("Successful Nether spawn reached the failure branch.");
		};
		context.getSource().sendFailure(error);
		return 0;
	}

	private record ErrorMessage(String key, String fallback) {
	}
}

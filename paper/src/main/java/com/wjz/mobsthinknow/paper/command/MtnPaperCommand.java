package com.wjz.mobsthinknow.paper.command;

import com.wjz.mobsthinknow.paper.MobsThinkNowPaperPlugin;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperMobLifecycle;
import com.wjz.mobsthinknow.paper.ai.PaperDamageMemory;
import com.wjz.mobsthinknow.paper.ai.PaperBlastReservationBoard;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperPounceCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.paper.squad.PaperSquadMetrics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** 首批插件诊断与测试命令；所有改写命令均受 mobsthinknow.admin 权限保护。 */
public final class MtnPaperCommand implements TabExecutor {
	private static final String ADMIN_PERMISSION = "mobsthinknow.admin";
	private static final Map<String, EntityType> SPAWN_TYPES = Map.ofEntries(
		Map.entry("zombie", EntityType.ZOMBIE),
		Map.entry("husk", EntityType.HUSK),
		Map.entry("drowned", EntityType.DROWNED),
		Map.entry("zombie_villager", EntityType.ZOMBIE_VILLAGER),
		Map.entry("skeleton", EntityType.SKELETON),
		Map.entry("stray", EntityType.STRAY),
		Map.entry("bogged", EntityType.BOGGED),
		Map.entry("wither_skeleton", EntityType.WITHER_SKELETON),
		Map.entry("creeper", EntityType.CREEPER),
		Map.entry("spider", EntityType.SPIDER)
	);

	private final MobsThinkNowPaperPlugin plugin;
	private final PaperIntelligenceService intelligence;
	private final PaperMobLifecycle lifecycle;
	private final PaperDamageMemory damageMemory;
	private final PaperBlastReservationBoard blastReservations;
	private final PaperPounceCoordinator pounceCoordinator;
	private final PaperSquadCoordinator squadCoordinator;
	private final PaperMetrics metrics;
	private final PaperTestSpawner testSpawner;
	private final PaperRuntimeSelfTest runtimeSelfTest;

	public MtnPaperCommand(
		final MobsThinkNowPaperPlugin plugin,
		final PaperIntelligenceService intelligence,
		final PaperMobLifecycle lifecycle,
		final PaperDamageMemory damageMemory,
		final PaperBlastReservationBoard blastReservations,
		final PaperPounceCoordinator pounceCoordinator,
		final PaperSquadCoordinator squadCoordinator,
		final PaperRuntimeSelfTest runtimeSelfTest,
		final PaperMetrics metrics
	) {
		this.plugin = plugin;
		this.intelligence = intelligence;
		this.lifecycle = lifecycle;
		this.damageMemory = damageMemory;
		this.blastReservations = blastReservations;
		this.pounceCoordinator = pounceCoordinator;
		this.squadCoordinator = squadCoordinator;
		this.metrics = metrics;
		this.testSpawner = new PaperTestSpawner(intelligence);
		this.runtimeSelfTest = runtimeSelfTest;
	}

	@Override
	public boolean onCommand(
		final CommandSender sender,
		final Command command,
		final String label,
		final String[] args
	) {
		String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
		return switch (action) {
			case "status" -> this.status(sender);
			case "reload" -> this.reload(sender);
			case "inspect" -> this.inspect(sender);
			case "setiq" -> this.setIntelligence(sender, args);
			case "spawn" -> this.spawn(sender, args);
			case "spawnall" -> this.spawnAll(sender);
			case "assault" -> this.spawnAssault(sender, args, 1);
			case "selftest" -> this.selfTest(sender);
			default -> this.usage(sender);
		};
	}

	@Override
	public List<String> onTabComplete(
		final CommandSender sender,
		final Command command,
		final String alias,
		final String[] args
	) {
		if (args.length == 1) {
			String prefix = args[0].toLowerCase(Locale.ROOT);
			return List.of("status", "inspect", "reload", "setiq", "spawn", "spawnall", "assault", "selftest").stream()
				.filter(value -> value.startsWith(prefix))
				.toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("setiq")) {
			List<String> values = new ArrayList<>(10);
			for (int value = 1; value <= 10; value++) {
				values.add(Integer.toString(value));
			}
			return values.stream().filter(value -> value.startsWith(args[1])).toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
			String prefix = args[1].toLowerCase(Locale.ROOT);
			return java.util.stream.Stream.concat(SPAWN_TYPES.keySet().stream(), java.util.stream.Stream.of("assault"))
				.sorted()
				.filter(value -> value.startsWith(prefix))
				.toList();
		}
		if (args.length == 3 && args[0].equalsIgnoreCase("spawn")) {
			return List.of("1", "4", "8", "16", "20").stream()
				.filter(value -> value.startsWith(args[2]))
				.toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("assault")) {
			return List.of("1", "2", "4", "8").stream()
				.filter(value -> value.startsWith(args[1]))
				.toList();
		}
		return List.of();
	}

	private boolean status(final CommandSender sender) {
		PaperMetrics.Snapshot snapshot = this.metrics.snapshot();
		PaperSquadMetrics.Snapshot squads = this.squadCoordinator.metrics().snapshot();
		sender.sendMessage(Component.text(
			"Mobs Think Now Paper | enabled=" + this.plugin.settings().enabled()
				+ ", loadedSupportedMobs=" + this.lifecycle.loadedSupportedMobCount()
				+ ", intelligenceAssignments=" + snapshot.intelligenceAssignments()
				+ ", retreatGoalsInstalled=" + snapshot.retreatGoalsInstalled()
				+ ", retreatGoalsRemoved=" + snapshot.retreatGoalsRemoved()
				+ ", retreats=" + snapshot.retreatStarts()
				+ ", retreatPathFailures=" + snapshot.retreatPathFailures()
				+ ", skeletonGoalsInstalled=" + snapshot.skeletonDisengageGoalsInstalled()
				+ ", skeletonGoalsRemoved=" + snapshot.skeletonDisengageGoalsRemoved()
				+ ", skeletonDisengages=" + snapshot.skeletonDisengageStarts()
				+ ", skeletonPathFailures=" + snapshot.skeletonDisengagePathFailures()
				+ ", creeperGoalsInstalled=" + snapshot.creeperGoalsInstalled()
				+ ", creeperGoalsRemoved=" + snapshot.creeperGoalsRemoved()
				+ ", creeperFlanks=" + snapshot.creeperFlanks()
				+ ", creeperIntercepts=" + snapshot.creeperIntercepts()
				+ ", creeperQueueWaits=" + snapshot.creeperQueueWaits()
				+ ", creeperFuseStarts=" + snapshot.creeperFuseStarts()
				+ ", creeperMovingPaths=" + snapshot.creeperMovingFusePaths()
				+ ", creeperFuseAborts=" + snapshot.creeperFuseAborts()
				+ ", blastReservations=" + snapshot.blastReservationsAcquired()
				+ ", blastConflicts=" + snapshot.blastReservationConflicts()
				+ ", blastSaturations=" + snapshot.blastReservationSaturations()
				+ ", activeBlastReservations=" + this.blastReservations.activeCount()
				+ ", spiderGoalsInstalled=" + snapshot.spiderGoalsInstalled()
				+ ", spiderGoalsRemoved=" + snapshot.spiderGoalsRemoved()
				+ ", spiderFlanks=" + snapshot.spiderFlanks()
				+ ", spiderHitAndRuns=" + snapshot.spiderHitAndRuns()
				+ ", spiderPounces=" + snapshot.spiderPounces()
				+ ", spiderPounceWaits=" + snapshot.spiderPounceWaits()
				+ ", unsafeLandings=" + snapshot.spiderUnsafeLandingsRejected()
				+ ", pounceConflicts=" + snapshot.spiderPounceReservationConflicts()
				+ ", activePounceReservations=" + this.pounceCoordinator.activeCount()
				+ ", activeSquads=" + this.squadCoordinator.activeSquadCount()
				+ ", squadMembers=" + this.squadCoordinator.assignedMemberCount()
				+ ", squadsFormed=" + squads.squadsFormed()
				+ ", squadRecruits=" + squads.membersRecruited()
				+ ", leaderReplacements=" + squads.leaderReplacements()
				+ ", phaseTransitions=" + squads.phaseTransitions()
				+ ", sharedTargets=" + squads.sharedTargets()
				+ ", friendlyFirePrevented=" + squads.friendlyDamagePrevented()
				+ ", candidateChecks=" + squads.boundedCandidateChecks()
				+ ", orderPathFailures=" + squads.orderPathFailures()
				+ ", pendingDamageMemories=" + this.damageMemory.pendingCount(),
			NamedTextColor.AQUA
		));
		return true;
	}

	private boolean reload(final CommandSender sender) {
		if (!this.requireAdmin(sender)) {
			return true;
		}
		this.plugin.reloadPluginSettings();
		sender.sendMessage(Component.text("Mobs Think Now Paper configuration reloaded.", NamedTextColor.GREEN));
		return true;
	}

	private boolean inspect(final CommandSender sender) {
		Mob mob = this.nearestSupportedMob(sender);
		if (mob == null) {
			sender.sendMessage(Component.text("No supported mob found within 12 blocks.", NamedTextColor.YELLOW));
			return true;
		}
		PaperSquadDirective directive = this.squadCoordinator.directiveFor(mob);
		sender.sendMessage(Component.text(
			mob.getType().key().asString()
				+ " | uuid=" + mob.getUniqueId()
				+ ", intelligence=" + this.intelligence.get(mob)
				+ ", target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getUniqueId())
				+ (directive == null ? ", squad=none" : ", squad=" + directive.squadId()
					+ ", term=" + directive.term()
					+ ", leader=" + directive.leaderId()
					+ ", state=" + directive.state()
					+ ", plan=" + directive.plan()
					+ ", role=" + directive.role()),
			NamedTextColor.AQUA
		));
		return true;
	}

	private boolean setIntelligence(final CommandSender sender, final String[] args) {
		if (!this.requireAdmin(sender)) {
			return true;
		}
		if (args.length < 2) {
			return this.usage(sender);
		}
		int value;
		try {
			value = Integer.parseInt(args[1]);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Component.text("Intelligence must be an integer from 1 to 10.", NamedTextColor.RED));
			return true;
		}
		if (value < 1 || value > 10) {
			sender.sendMessage(Component.text("Intelligence must be from 1 to 10.", NamedTextColor.RED));
			return true;
		}
		Mob mob = this.nearestSupportedMob(sender);
		if (mob == null) {
			sender.sendMessage(Component.text("No supported mob found within 12 blocks.", NamedTextColor.YELLOW));
			return true;
		}
		this.intelligence.set(mob, value);
		sender.sendMessage(Component.text("Updated nearby mob intelligence to " + value + ".", NamedTextColor.GREEN));
		return true;
	}

	private boolean spawn(final CommandSender sender, final String[] args) {
		if (!this.requireAdmin(sender)) {
			return true;
		}
		if (!(sender instanceof Player player) || args.length < 2) {
			return this.usage(sender);
		}
		String typeName = args[1].toLowerCase(Locale.ROOT);
		if (typeName.equals("assault")) {
			return this.spawnAssault(sender, args, 2);
		}
		EntityType type = SPAWN_TYPES.get(typeName);
		if (type == null) {
			sender.sendMessage(Component.text("Unknown Paper AI type: " + typeName, NamedTextColor.RED));
			return true;
		}
		Integer count = parseCount(
			sender,
			args,
			2,
			1,
			PaperTestSpawner.MAXIMUM_SINGLE_TYPE_COUNT,
			"count"
		);
		if (count == null) {
			return true;
		}
		return this.reportSpawn(sender, this.testSpawner.spawnType(player, type, count));
	}

	private boolean selfTest(final CommandSender sender) {
		if (!this.requireAdmin(sender)) {
			return true;
		}
		this.runtimeSelfTest.start(sender);
		return true;
	}

	private boolean spawnAll(final CommandSender sender) {
		if (!this.requireAdmin(sender)) {
			return true;
		}
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("This command needs an in-world player source.", NamedTextColor.RED));
			return true;
		}
		return this.reportSpawn(sender, this.testSpawner.spawnAll(player));
	}

	private boolean spawnAssault(final CommandSender sender, final String[] args, final int countIndex) {
		if (!this.requireAdmin(sender)) {
			return true;
		}
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Component.text("This command needs an in-world player source.", NamedTextColor.RED));
			return true;
		}
		Integer groups = parseCount(
			sender,
			args,
			countIndex,
			1,
			PaperTestSpawner.MAXIMUM_ASSAULT_GROUPS,
			"assault groups"
		);
		if (groups == null) {
			return true;
		}
		return this.reportSpawn(sender, this.testSpawner.spawnAssault(player, groups));
	}

	private boolean reportSpawn(final CommandSender sender, final PaperTestSpawner.Result result) {
		if (result.rolledBack()) {
			sender.sendMessage(Component.text(
				"Spawn batch rolled back: requested=" + result.requested() + ", reason=" + result.detail(),
				NamedTextColor.RED
			));
		} else {
			sender.sendMessage(Component.text(
				"Spawned " + result.spawned() + " intelligent mob(s).",
				NamedTextColor.GREEN
			));
		}
		return true;
	}

	private static Integer parseCount(
		final CommandSender sender,
		final String[] args,
		final int index,
		final int minimum,
		final int maximum,
		final String label
	) {
		if (args.length <= index) {
			return minimum;
		}
		int count;
		try {
			count = Integer.parseInt(args[index]);
		} catch (NumberFormatException exception) {
			sender.sendMessage(Component.text(label + " must be an integer.", NamedTextColor.RED));
			return null;
		}
		if (count < minimum || count > maximum) {
			sender.sendMessage(Component.text(
				label + " must be from " + minimum + " to " + maximum + ".",
				NamedTextColor.RED
			));
			return null;
		}
		return count;
	}

	private Mob nearestSupportedMob(final CommandSender sender) {
		if (!(sender instanceof Player player)) {
			return null;
		}
		return player.getNearbyEntities(12.0, 8.0, 12.0).stream()
			.filter(Entity::isValid)
			.filter(entity -> entity instanceof Mob mob && this.intelligence.supports(mob))
			.map(entity -> (Mob)entity)
			.min(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
			.orElse(null);
	}

	private boolean requireAdmin(final CommandSender sender) {
		if (sender.hasPermission(ADMIN_PERMISSION)) {
			return true;
		}
		sender.sendMessage(Component.text("Missing permission: " + ADMIN_PERMISSION, NamedTextColor.RED));
		return false;
	}

	private boolean usage(final CommandSender sender) {
		sender.sendMessage(Component.text(
			"Usage: /mtnpaper <status|inspect|reload|setiq 1-10|spawn <type|assault> [count]|spawnall|assault [groups]|selftest>",
			NamedTextColor.YELLOW
		));
		return true;
	}
}

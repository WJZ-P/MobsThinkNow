package com.wjz.mobsthinknow.paper.command;

import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlan;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.IronGolem;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** 在真实 Paper tick 中验证四兵种 Goal 安装、同队、共享目标和 COMBINED_ARMS，然后无条件清理。 */
public final class PaperRuntimeSelfTest {
	private static final List<EntityType> CORE_TYPES = List.of(
		EntityType.ZOMBIE,
		EntityType.SKELETON,
		EntityType.CREEPER,
		EntityType.SPIDER
	);
	private static final int VALIDATION_DELAY_TICKS = 25;

	private final Plugin plugin;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final List<Entity> activeEntities = new ArrayList<>();
	private final List<Chunk> temporarilyForcedChunks = new ArrayList<>();
	private BukkitTask validationTask;

	public PaperRuntimeSelfTest(
		final Plugin plugin,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads
	) {
		this.plugin = plugin;
		this.intelligence = intelligence;
		this.squads = squads;
	}

	public boolean start(final CommandSender sender) {
		if (this.validationTask != null) {
			sender.sendMessage(Component.text("MTN Paper self-test is already running.", NamedTextColor.YELLOW));
			return false;
		}
		if (!this.squads.enabled() || Bukkit.getWorlds().isEmpty()) {
			this.report(sender, false, "coordination disabled or no loaded world");
			return false;
		}

		World world = Bukkit.getWorlds().getFirst();
		Location anchor = safeSurface(world, world.getSpawnLocation().getBlockX() + 24,
			world.getSpawnLocation().getBlockZ() + 24);
		try {
			Location targetLocation = safeSurface(world, anchor.getBlockX(), anchor.getBlockZ() + 14);
			this.forceChunk(anchor);
			this.forceChunk(targetLocation);
			IronGolem target = (IronGolem)world.spawnEntity(targetLocation, EntityType.IRON_GOLEM);
			target.setInvulnerable(true);
			target.setAI(false);
			target.setPlayerCreated(true);
			target.setPersistent(false);
			this.activeEntities.add(target);

			List<Mob> mobs = new ArrayList<>(CORE_TYPES.size());
			for (int index = 0; index < CORE_TYPES.size(); index++) {
				double x = (index - 1.5) * 1.8;
				Entity entity = world.spawnEntity(anchor.clone().add(x, 0.0, 0.0), CORE_TYPES.get(index));
				if (!(entity instanceof Mob mob)) {
					throw new IllegalStateException("self-test entity is not a Mob: " + entity.getType());
				}
				mob.setInvulnerable(true);
				mob.setPersistent(false);
				mob.setRemoveWhenFarAway(false);
				this.intelligence.set(mob, 10);
				this.squads.observeTarget(mob, target);
				mob.setTarget(target);
				mobs.add(mob);
				this.activeEntities.add(mob);
			}
			this.validationTask = Bukkit.getScheduler().runTaskLater(
				this.plugin,
				() -> this.validate(sender, target, mobs),
				VALIDATION_DELAY_TICKS
			);
			sender.sendMessage(Component.text(
				"MTN Paper self-test scheduled for " + VALIDATION_DELAY_TICKS + " ticks.",
				NamedTextColor.AQUA
			));
			return true;
		} catch (RuntimeException exception) {
			this.cleanup();
			this.report(sender, false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
			return false;
		}
	}

	public void close() {
		if (this.validationTask != null) {
			this.validationTask.cancel();
			this.validationTask = null;
		}
		this.cleanup();
	}

	private void validate(final CommandSender sender, final LivingEntity target, final List<Mob> mobs) {
		this.validationTask = null;
		try {
			List<PaperSquadDirective> directives = mobs.stream()
				.map(this.squads::directiveFor)
				.toList();
			if (directives.stream().anyMatch(java.util.Objects::isNull)) {
				this.report(
					sender,
					false,
					"one or more core mobs received no squad directive; tracked="
						+ this.squads.trackedMemberCount()
						+ ", activeSquads=" + this.squads.activeSquadCount()
						+ ", targets=" + targetSnapshot(mobs)
				);
				return;
			}
			long squadId = directives.getFirst().squadId();
			boolean oneSquad = directives.stream().allMatch(directive -> directive.squadId() == squadId);
			boolean combinedArms = directives.stream()
				.allMatch(directive -> directive.plan() == MixedSquadPlan.COMBINED_ARMS);
			boolean sharedTarget = mobs.stream().allMatch(mob -> this.squads.sharedTargetFor(mob) == target);
			boolean allTracked = this.squads.assignedMemberCount() >= CORE_TYPES.size();
			if (!oneSquad || !combinedArms || !sharedTarget || !allTracked) {
				this.report(
					sender,
					false,
					"oneSquad=" + oneSquad
						+ ", combinedArms=" + combinedArms
						+ ", sharedTarget=" + sharedTarget
						+ ", allTracked=" + allTracked
				);
				return;
			}
			this.report(
				sender,
				true,
				"squad=" + squadId
					+ ", leader=" + directives.getFirst().leaderId()
					+ ", state=" + directives.getFirst().state()
					+ ", plan=" + directives.getFirst().plan()
			);
		} catch (RuntimeException exception) {
			this.report(sender, false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
		} finally {
			this.cleanup();
		}
	}

	private void cleanup() {
		for (Entity entity : this.activeEntities) {
			if (entity.isValid()) {
				entity.remove();
			}
		}
		this.activeEntities.clear();
		for (Chunk chunk : this.temporarilyForcedChunks) {
			if (chunk.isLoaded()) {
				chunk.setForceLoaded(false);
			}
		}
		this.temporarilyForcedChunks.clear();
	}

	private void forceChunk(final Location location) {
		Chunk chunk = location.getChunk();
		if (!chunk.isForceLoaded()) {
			chunk.setForceLoaded(true);
			this.temporarilyForcedChunks.add(chunk);
		}
	}

	private static String targetSnapshot(final List<Mob> mobs) {
		return mobs.stream()
			.map(mob -> mob.getType().key().asString()
				+ "[valid=" + mob.isValid()
				+ ",dead=" + mob.isDead()
				+ ",target=" + (mob.getTarget() == null ? "none" : mob.getTarget().getType().key().asString())
				+ "]")
			.collect(java.util.stream.Collectors.joining(","));
	}

	private void report(final CommandSender sender, final boolean success, final String detail) {
		String message = "[MTN SELFTEST " + (success ? "PASS" : "FAIL") + "] " + detail;
		sender.sendMessage(Component.text(message, success ? NamedTextColor.GREEN : NamedTextColor.RED));
		if (success) {
			this.plugin.getLogger().info(message);
		} else {
			this.plugin.getLogger().severe(message);
		}
	}

	private static Location safeSurface(final World world, final int x, final int z) {
		int y = world.getHighestBlockYAt(x, z) + 1;
		return new Location(world, x + 0.5, y, z + 0.5);
	}
}

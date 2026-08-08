package com.wjz.mobsthinknow.paper;

import com.wjz.mobsthinknow.paper.ai.PaperDamageMemory;
import com.wjz.mobsthinknow.paper.ai.PaperBlastReservationBoard;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperSkeletonProfile;
import com.wjz.mobsthinknow.paper.ai.PaperPounceCoordinator;
import com.wjz.mobsthinknow.paper.command.MtnPaperCommand;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper 26.1.2 插件入口；客户端无需安装 Mod。 */
public final class MobsThinkNowPaperPlugin extends JavaPlugin {
	private final PaperMetrics metrics = new PaperMetrics();
	private final PaperDamageMemory damageMemory = new PaperDamageMemory();
	private PaperSettings settings;
	private PaperIntelligenceService intelligence;
	private PaperSkeletonProfile skeletonProfile;
	private PaperBlastReservationBoard blastReservations;
	private PaperPounceCoordinator pounceCoordinator;
	private PaperMobLifecycle mobLifecycle;

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		this.settings = this.readSettings();
		this.intelligence = new PaperIntelligenceService(this, this::settings, this.metrics);
		this.skeletonProfile = new PaperSkeletonProfile(this);
		this.blastReservations = new PaperBlastReservationBoard(this::settings, this.metrics);
		this.pounceCoordinator = new PaperPounceCoordinator(this::settings, this.metrics);
		this.mobLifecycle = new PaperMobLifecycle(
			this,
			this::settings,
			this.intelligence,
			this.skeletonProfile,
			this.blastReservations,
			this.pounceCoordinator,
			this.damageMemory,
			this.metrics
		);
		this.getServer().getPluginManager().registerEvents(this.mobLifecycle, this);
		MtnPaperCommand commandHandler = new MtnPaperCommand(
			this,
			this.intelligence,
			this.mobLifecycle,
			this.damageMemory,
			this.blastReservations,
			this.pounceCoordinator,
			this.metrics
		);
		PluginCommand command = Objects.requireNonNull(
			this.getCommand("mtnpaper"),
			"plugin.yml is missing the mtnpaper command"
		);
		command.setExecutor(commandHandler);
		command.setTabCompleter(commandHandler);
		this.mobLifecycle.installLoadedEntities();
		this.getLogger().info(
			"Mobs Think Now Paper enabled: loadedSupportedMobs=" + this.mobLifecycle.loadedSupportedMobCount()
		);
	}

	@Override
	public void onDisable() {
		if (this.mobLifecycle != null) {
			this.mobLifecycle.removeGoalsFromLoadedEntities();
		}
		this.damageMemory.clear();
		if (this.blastReservations != null) {
			this.blastReservations.clear();
		}
		if (this.pounceCoordinator != null) {
			this.pounceCoordinator.clear();
		}
	}

	public PaperSettings settings() {
		return this.settings;
	}

	public void reloadPluginSettings() {
		this.reloadConfig();
		this.settings = this.readSettings();
		this.damageMemory.clear();
		this.blastReservations.clear();
		this.pounceCoordinator.clear();
		this.mobLifecycle.refreshLoadedEntities();
	}

	private PaperSettings readSettings() {
		return PaperSettings.validated(
			this.getConfig().getBoolean("enabled", true),
			this.getConfig().getBoolean("identity.show-intelligence-names", true),
			this.getConfig().getBoolean("zombie.retreat.enabled", true),
			this.getConfig().getInt("zombie.retreat.minimum-intelligence", 1),
			this.getConfig().getDouble("zombie.retreat.health-threshold", 0.20),
			this.getConfig().getDouble("zombie.retreat.heavy-hit-threshold", 0.30),
			this.getConfig().getInt("zombie.retreat.maximum-ticks", 100),
			this.getConfig().getDouble("zombie.retreat.safe-distance", 5.0),
			this.getConfig().getDouble("zombie.retreat.speed", 1.50),
			this.getConfig().getInt("zombie.retreat.damage-memory-ticks", 20),
			this.getConfig().getBoolean("skeleton.spacing.enabled", true),
			this.getConfig().getInt("skeleton.spacing.minimum-intelligence", 1),
			this.getConfig().getDouble("skeleton.spacing.preferred-range", 10.0),
			this.getConfig().getInt("skeleton.spacing.maximum-disengage-ticks", 80),
			this.getConfig().getInt("skeleton.spacing.timeout-cooldown-ticks", 20),
			this.getConfig().getBoolean("creeper.tactics.enabled", true),
			this.getConfig().getInt("creeper.tactics.minimum-intelligence", 1),
			this.getConfig().getBoolean("creeper.tactics.flanking", true),
			this.getConfig().getDouble("creeper.tactics.maximum-fuse-start-distance", 4.0),
			this.getConfig().getBoolean("creeper.tactics.moving-fuse", true),
			this.getConfig().getDouble("creeper.tactics.maximum-fuse-movement-speed", 1.25),
			this.getConfig().getDouble("creeper.blast-reservation.conflict-radius", 6.0),
			this.getConfig().getInt("creeper.blast-reservation.separation-ticks", 24),
			this.getConfig().getInt("creeper.blast-reservation.lease-ticks", 40),
			this.getConfig().getInt("creeper.blast-reservation.maximum-checks", 32),
			this.getConfig().getBoolean("spider.tactics.enabled", true),
			this.getConfig().getInt("spider.tactics.minimum-intelligence", 1),
			this.getConfig().getBoolean("spider.tactics.predictive-pounce", true),
			this.getConfig().getBoolean("spider.tactics.hit-and-run", true),
			this.getConfig().getInt("spider.tactics.pounce-stagger-ticks", 10),
			this.getConfig().getInt("spider.tactics.pounce-lease-ticks", 20),
			this.getConfig().getInt("spider.tactics.maximum-air-ticks", 40)
		);
	}
}

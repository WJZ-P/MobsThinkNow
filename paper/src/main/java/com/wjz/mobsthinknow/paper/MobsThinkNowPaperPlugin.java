package com.wjz.mobsthinknow.paper;

import com.wjz.mobsthinknow.paper.ai.PaperDamageMemory;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
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
	private PaperMobLifecycle mobLifecycle;

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		this.settings = this.readSettings();
		this.intelligence = new PaperIntelligenceService(this, this::settings, this.metrics);
		this.mobLifecycle = new PaperMobLifecycle(
			this,
			this::settings,
			this.intelligence,
			this.damageMemory,
			this.metrics
		);
		this.getServer().getPluginManager().registerEvents(this.mobLifecycle, this);
		MtnPaperCommand commandHandler = new MtnPaperCommand(
			this,
			this.intelligence,
			this.mobLifecycle,
			this.damageMemory,
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
	}

	public PaperSettings settings() {
		return this.settings;
	}

	public void reloadPluginSettings() {
		this.reloadConfig();
		this.settings = this.readSettings();
		this.damageMemory.clear();
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
			this.getConfig().getInt("zombie.retreat.damage-memory-ticks", 20)
		);
	}
}

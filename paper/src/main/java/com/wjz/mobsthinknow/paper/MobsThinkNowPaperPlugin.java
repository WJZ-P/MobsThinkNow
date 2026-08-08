package com.wjz.mobsthinknow.paper;

import com.wjz.mobsthinknow.paper.ai.PaperDamageMemory;
import com.wjz.mobsthinknow.paper.ai.PaperBlastReservationBoard;
import com.wjz.mobsthinknow.paper.ai.PaperCreeperFeintMemory;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperFireworkBoltService;
import com.wjz.mobsthinknow.paper.ai.PaperSkeletonProfile;
import com.wjz.mobsthinknow.paper.ai.PaperSkeletonLoadoutService;
import com.wjz.mobsthinknow.paper.ai.PaperPounceCoordinator;
import com.wjz.mobsthinknow.paper.ai.PaperShieldMemory;
import com.wjz.mobsthinknow.paper.command.MtnPaperCommand;
import com.wjz.mobsthinknow.paper.command.PaperRuntimeSelfTest;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadSettings;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper 26.1.2 插件入口；客户端无需安装 Mod。 */
public final class MobsThinkNowPaperPlugin extends JavaPlugin {
	private final PaperMetrics metrics = new PaperMetrics();
	private final PaperDamageMemory damageMemory = new PaperDamageMemory();
	private final PaperShieldMemory shieldMemory = new PaperShieldMemory();
	private final PaperCreeperFeintMemory creeperFeintMemory = new PaperCreeperFeintMemory();
	private PaperSettings settings;
	private PaperIntelligenceService intelligence;
	private PaperSkeletonProfile skeletonProfile;
	private PaperSkeletonLoadoutService skeletonLoadouts;
	private PaperFireworkBoltService fireworkBolts;
	private PaperBlastReservationBoard blastReservations;
	private PaperPounceCoordinator pounceCoordinator;
	private PaperSquadSettings squadSettings;
	private PaperSquadCoordinator squadCoordinator;
	private PaperRuntimeSelfTest runtimeSelfTest;
	private PaperMobLifecycle mobLifecycle;

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		this.settings = this.readSettings();
		this.squadSettings = this.readSquadSettings();
		this.intelligence = new PaperIntelligenceService(this, this::settings, this.metrics);
		this.skeletonProfile = new PaperSkeletonProfile(this);
		this.skeletonLoadouts = new PaperSkeletonLoadoutService(
			this,
			this::settings,
			this.intelligence,
			this.metrics
		);
		this.fireworkBolts = new PaperFireworkBoltService(this, this::settings, this.metrics);
		this.blastReservations = new PaperBlastReservationBoard(this::settings, this.metrics);
		this.pounceCoordinator = new PaperPounceCoordinator(this::settings, this.metrics);
		this.squadCoordinator = new PaperSquadCoordinator(
			this,
			() -> this.settings.enabled(),
			this::squadSettings,
			this.intelligence
		);
		this.runtimeSelfTest = new PaperRuntimeSelfTest(
			this,
			this.intelligence,
			this.squadCoordinator,
			this.fireworkBolts,
			this.skeletonLoadouts,
			this.metrics
		);
		this.mobLifecycle = new PaperMobLifecycle(
			this,
			this::settings,
			this.intelligence,
			this.skeletonProfile,
			this.skeletonLoadouts,
			this.creeperFeintMemory,
			this.blastReservations,
			this.pounceCoordinator,
			this.squadCoordinator,
			this.damageMemory,
			this.shieldMemory,
			this.fireworkBolts,
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
			this.fireworkBolts,
			this.squadCoordinator,
			this.runtimeSelfTest,
			this.metrics
		);
		PluginCommand command = Objects.requireNonNull(
			this.getCommand("mtnpaper"),
			"plugin.yml is missing the mtnpaper command"
		);
		command.setExecutor(commandHandler);
		command.setTabCompleter(commandHandler);
		this.mobLifecycle.installLoadedEntities();
		this.fireworkBolts.start();
		this.squadCoordinator.start();
		this.getLogger().info(
			"Mobs Think Now Paper enabled: loadedSupportedMobs=" + this.mobLifecycle.loadedSupportedMobCount()
		);
	}

	@Override
	public void onDisable() {
		if (this.runtimeSelfTest != null) {
			this.runtimeSelfTest.close();
		}
		if (this.mobLifecycle != null) {
			this.mobLifecycle.removeGoalsFromLoadedEntities();
		}
		if (this.fireworkBolts != null) {
			this.fireworkBolts.stop();
		}
		if (this.squadCoordinator != null) {
			this.squadCoordinator.stop();
		}
		this.damageMemory.clear();
		this.shieldMemory.clear();
		this.creeperFeintMemory.clear();
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

	public PaperSquadSettings squadSettings() {
		return this.squadSettings;
	}

	public void reloadPluginSettings() {
		this.runtimeSelfTest.close();
		this.reloadConfig();
		this.settings = this.readSettings();
		this.squadSettings = this.readSquadSettings();
		this.damageMemory.clear();
		this.shieldMemory.clear();
		this.creeperFeintMemory.clear();
		this.blastReservations.clear();
		this.pounceCoordinator.clear();
		this.fireworkBolts.stop();
		this.fireworkBolts.start();
		this.squadCoordinator.reconfigure();
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
			PaperWeaponSettings.validated(
				this.getConfig().getBoolean("zombie.weapon-tactics.enabled", true),
				this.getConfig().getInt("zombie.weapon-tactics.minimum-intelligence", 3),
				this.getConfig().getDouble("zombie.weapon-tactics.spacing-radius", 2.8),
				this.getConfig().getDouble("zombie.weapon-tactics.movement-speed", 1.15),
				this.getConfig().getInt("zombie.weapon-tactics.repath-ticks", 6),
				this.getConfig().getInt("zombie.weapon-tactics.axe.minimum-intelligence", 6),
				this.getConfig().getInt("zombie.weapon-tactics.axe.windup-ticks", 8),
				this.getConfig().getInt("zombie.weapon-tactics.axe.preparation-timeout-ticks", 30),
				this.getConfig().getDouble("zombie.weapon-tactics.axe.horizontal-speed", 0.34),
				this.getConfig().getDouble("zombie.weapon-tactics.axe.critical-damage-multiplier", 1.50)
			),
			PaperShieldSettings.validated(
				this.getConfig().getBoolean("zombie.shield-tactics.enabled", true),
				this.getConfig().getInt("zombie.shield-tactics.minimum-intelligence", 4),
				this.getConfig().getDouble("zombie.shield-tactics.raise-distance", 6.0),
				this.getConfig().getDouble("zombie.shield-tactics.lower-distance", 7.5),
				this.getConfig().getDouble("zombie.shield-tactics.movement-speed", 1.10),
				this.getConfig().getInt("zombie.shield-tactics.repath-ticks", 6),
				this.getConfig().getInt("zombie.shield-tactics.guard.minimum-ticks", 12),
				this.getConfig().getInt("zombie.shield-tactics.guard.maximum-ticks", 28),
				this.getConfig().getInt("zombie.shield-tactics.counter.minimum-delay-ticks", 2),
				this.getConfig().getInt("zombie.shield-tactics.counter.maximum-delay-ticks", 4),
				this.getConfig().getInt("zombie.shield-tactics.strike-window-ticks", 10),
				this.getConfig().getInt("zombie.shield-tactics.block-signal-memory-ticks", 20),
				this.getConfig().getInt("zombie.shield-tactics.block.minimum-use-ticks", 5),
				this.getConfig().getDouble("zombie.shield-tactics.block.minimum-facing-dot", 0.0),
				this.getConfig().getDouble("zombie.shield-tactics.axe-disable-seconds", 3.0)
			),
			PaperCrossbowSettings.validated(
				this.getConfig().getBoolean("skeleton.crossbow.enabled", true),
				this.getConfig().getInt("skeleton.crossbow.minimum-intelligence", 3),
				this.getConfig().getInt("skeleton.crossbow.charge-ticks", 25),
				this.getConfig().getInt("skeleton.crossbow.aim.minimum-ticks", 4),
				this.getConfig().getInt("skeleton.crossbow.aim.maximum-ticks", 10),
				this.getConfig().getDouble("skeleton.crossbow.projectile-speed", 3.15),
				this.getConfig().getDouble("skeleton.crossbow.projectile-spread", 2.0),
				this.getConfig().getDouble("skeleton.crossbow.maximum-lead-ticks", 20.0),
				this.getConfig().getDouble("skeleton.crossbow.gravity-per-tick-squared", 0.05),
				PaperFireworkSettings.validated(
					this.getConfig().getBoolean("skeleton.crossbow.firework.enabled", true),
					this.getConfig().getInt("skeleton.crossbow.firework.minimum-intelligence", 7),
					this.getConfig().getDouble("skeleton.crossbow.firework.minimum-range", 6.0),
					this.getConfig().getDouble("skeleton.crossbow.firework.maximum-range", 30.0),
					this.getConfig().getDouble("skeleton.crossbow.firework.ally-danger-radius", 3.5),
					this.getConfig().getInt("skeleton.crossbow.firework.maximum-ally-checks", 20),
					this.getConfig().getDouble("skeleton.crossbow.firework.projectile-speed", 1.6),
					this.getConfig().getInt("skeleton.crossbow.firework.projectile-lifetime-ticks", 40),
					this.getConfig().getInt("skeleton.crossbow.firework.maximum-active-projectiles", 48),
					this.getConfig().getBoolean("skeleton.crossbow.firework.consume-ammunition", true)
				),
				PaperSkeletonLoadoutSettings.validated(
					this.getConfig().getBoolean("skeleton.crossbow.natural-loadout.enabled", true),
					this.getConfig().getDouble("skeleton.crossbow.natural-loadout.crossbow-chance", 0.18),
					this.getConfig().getDouble("skeleton.crossbow.natural-loadout.firework-crossbow-chance", 0.25)
				)
			),
			this.getConfig().getBoolean("skeleton.spacing.enabled", true),
			this.getConfig().getInt("skeleton.spacing.minimum-intelligence", 1),
			this.getConfig().getDouble("skeleton.spacing.preferred-range", 10.0),
			this.getConfig().getInt("skeleton.spacing.maximum-disengage-ticks", 80),
			this.getConfig().getInt("skeleton.spacing.timeout-cooldown-ticks", 20),
			this.getConfig().getBoolean("skeleton.coordinated-fire.enabled", true),
			this.getConfig().getInt("skeleton.coordinated-fire.minimum-intelligence", 4),
			this.getConfig().getDouble("skeleton.coordinated-fire.maximum-range", 24.0),
			this.getConfig().getInt("skeleton.coordinated-fire.charge-ticks", 16),
			this.getConfig().getInt("skeleton.coordinated-fire.minimum-shot-interval-ticks", 28),
			this.getConfig().getDouble("skeleton.coordinated-fire.friendly-lane-radius", 0.75),
			this.getConfig().getInt("skeleton.coordinated-fire.maximum-lane-checks", 20),
			this.getConfig().getDouble("skeleton.coordinated-fire.reposition-distance", 3.0),
			this.getConfig().getBoolean("creeper.tactics.enabled", true),
			this.getConfig().getInt("creeper.tactics.minimum-intelligence", 1),
			this.getConfig().getBoolean("creeper.tactics.flanking", true),
			this.getConfig().getDouble("creeper.tactics.maximum-fuse-start-distance", 4.0),
			this.getConfig().getBoolean("creeper.tactics.moving-fuse", true),
			this.getConfig().getDouble("creeper.tactics.maximum-fuse-movement-speed", 1.25),
			PaperCreeperFeintSettings.validated(
				this.getConfig().getBoolean("creeper.tactics.feint.enabled", true),
				this.getConfig().getInt("creeper.tactics.feint.cooldown-ticks", 240),
				this.getConfig().getDouble("creeper.tactics.feint.reposition-speed", 1.16)
			),
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
			this.getConfig().getInt("spider.tactics.maximum-air-ticks", 40),
			this.getConfig().getBoolean("spider.tactics.mounted-breach", true),
			this.getConfig().getDouble("spider.tactics.maximum-carrier-speed", 1.35),
			this.getConfig().getDouble("spider.tactics.payload-release-progress", 0.35),
			this.getConfig().getInt("spider.tactics.assembly-timeout-ticks", 100),
			this.getConfig().getInt("spider.tactics.remount-cooldown-ticks", 100)
		);
	}

	private PaperSquadSettings readSquadSettings() {
		return PaperSquadSettings.validated(
			this.getConfig().getBoolean("coordination.enabled", true),
			this.getConfig().getBoolean("coordination.share-targets", true),
			this.getConfig().getBoolean("coordination.prevent-friendly-fire", true),
			this.getConfig().getDouble("coordination.formation-radius", 16.0),
			this.getConfig().getInt("coordination.minimum-members", 2),
			this.getConfig().getInt("coordination.maximum-members", 20),
			this.getConfig().getInt("coordination.raw-scan-limit", 64),
			this.getConfig().getInt("coordination.heartbeat-ticks", 5),
			this.getConfig().getInt("coordination.forming-timeout-ticks", 40),
			this.getConfig().getInt("coordination.briefing-ticks", 30),
			this.getConfig().getInt("coordination.deployment-timeout-ticks", 40),
			this.getConfig().getInt("coordination.reorganizing-ticks", 20),
			this.getConfig().getDouble("coordination.emergency-distance", 8.0),
			this.getConfig().getDouble("coordination.maximum-separation", 48.0),
			this.getConfig().getInt("coordination.target-memory-ticks", 100)
		);
	}
}

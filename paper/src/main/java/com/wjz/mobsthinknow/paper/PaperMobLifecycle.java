package com.wjz.mobsthinknow.paper;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.wjz.mobsthinknow.paper.ai.PaperDamageMemory;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperZombieRetreatGoal;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.plugin.Plugin;

/** 实体装载、伤害事件与 Paper MobGoals 注册的唯一边界。 */
public final class PaperMobLifecycle implements Listener {
	private static final int RETREAT_GOAL_PRIORITY = 1;

	private final GoalKey<Zombie> retreatGoalKey;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperDamageMemory damageMemory;
	private final PaperMetrics metrics;

	public PaperMobLifecycle(
		final Plugin plugin,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperDamageMemory damageMemory,
		final PaperMetrics metrics
	) {
		this.retreatGoalKey = GoalKey.of(Zombie.class, new NamespacedKey(plugin, "zombie_reactive_retreat"));
		this.settings = settings;
		this.intelligence = intelligence;
		this.damageMemory = damageMemory;
		this.metrics = metrics;
	}

	@EventHandler
	public void onEntityAdded(final EntityAddToWorldEvent event) {
		this.install(event.getEntity());
	}

	@EventHandler
	public void onEntityRemoved(final EntityRemoveFromWorldEvent event) {
		if (event.getEntity() instanceof Zombie zombie) {
			this.damageMemory.discard(zombie);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onZombieDamaged(final EntityDamageByEntityEvent event) {
		if (!(event.getEntity() instanceof Zombie zombie) || !this.settings.get().enabled()) {
			return;
		}
		LivingEntity attacker = causingLivingEntity(event.getDamager());
		if (attacker != null) {
			this.damageMemory.record(zombie, attacker, event.getFinalDamage(), Bukkit.getCurrentTick());
		}
	}

	public void installLoadedEntities() {
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				this.install(entity);
			}
		}
	}

	public void refreshLoadedEntities() {
		this.installLoadedEntities();
	}

	public void removeGoalsFromLoadedEntities() {
		for (World world : Bukkit.getWorlds()) {
			for (Zombie zombie : world.getEntitiesByClass(Zombie.class)) {
				if (Bukkit.getMobGoals().hasGoal(zombie, this.retreatGoalKey)) {
					Bukkit.getMobGoals().removeGoal(zombie, this.retreatGoalKey);
					this.metrics.retreatGoalRemoved();
				}
			}
		}
	}

	public int loadedSupportedMobCount() {
		int count = 0;
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				if (entity instanceof Mob mob && this.intelligence.supports(mob)) {
					count++;
				}
			}
		}
		return count;
	}

	private void install(final Entity entity) {
		if (!(entity instanceof Mob mob) || !this.intelligence.supports(mob)) {
			return;
		}
		this.intelligence.ensure(mob);
		if (!(mob instanceof Zombie zombie)) {
			return;
		}
		PaperSettings config = this.settings.get();
		boolean shouldHaveGoal = config.enabled() && config.zombieRetreatEnabled();
		boolean hasGoal = Bukkit.getMobGoals().hasGoal(zombie, this.retreatGoalKey);
		if (shouldHaveGoal && !hasGoal) {
			Bukkit.getMobGoals().addGoal(
				zombie,
				RETREAT_GOAL_PRIORITY,
				new PaperZombieRetreatGoal(
					zombie,
					this.retreatGoalKey,
					this.settings,
					this.intelligence,
					this.damageMemory,
					this.metrics
				)
			);
			this.metrics.retreatGoalInstalled();
		} else if (!shouldHaveGoal && hasGoal) {
			Bukkit.getMobGoals().removeGoal(zombie, this.retreatGoalKey);
			this.damageMemory.discard(zombie);
			this.metrics.retreatGoalRemoved();
		}
	}

	private static LivingEntity causingLivingEntity(final Entity damager) {
		if (damager instanceof LivingEntity living) {
			return living;
		}
		if (damager instanceof Projectile projectile) {
			ProjectileSource shooter = projectile.getShooter();
			return shooter instanceof LivingEntity living ? living : null;
		}
		return null;
	}
}

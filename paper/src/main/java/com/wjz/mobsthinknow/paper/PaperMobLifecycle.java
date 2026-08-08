package com.wjz.mobsthinknow.paper;

import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.wjz.mobsthinknow.paper.ai.PaperDamageMemory;
import com.wjz.mobsthinknow.paper.ai.PaperBlastReservationBoard;
import com.wjz.mobsthinknow.paper.ai.PaperCreeperApproachGoal;
import com.wjz.mobsthinknow.paper.ai.PaperCreeperFuseGoal;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperSkeletonDisengageGoal;
import com.wjz.mobsthinknow.paper.ai.PaperSkeletonProfile;
import com.wjz.mobsthinknow.paper.ai.PaperPounceCoordinator;
import com.wjz.mobsthinknow.paper.ai.PaperSpiderCombatGoal;
import com.wjz.mobsthinknow.paper.ai.PaperSpiderPounceGoal;
import com.wjz.mobsthinknow.paper.ai.PaperZombieRetreatGoal;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Spider;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.plugin.Plugin;

/** 实体装载、伤害事件与 Paper MobGoals 注册的唯一边界。 */
public final class PaperMobLifecycle implements Listener {
	private static final int RETREAT_GOAL_PRIORITY = 1;
	private static final int CREEPER_FUSE_GOAL_PRIORITY = 1;
	private static final int CREEPER_APPROACH_GOAL_PRIORITY = 3;
	private static final int SPIDER_POUNCE_GOAL_PRIORITY = 2;
	private static final int SPIDER_COMBAT_GOAL_PRIORITY = 4;

	private final GoalKey<Zombie> retreatGoalKey;
	private final GoalKey<AbstractSkeleton> skeletonDisengageGoalKey;
	private final GoalKey<Creeper> creeperFuseGoalKey;
	private final GoalKey<Creeper> creeperApproachGoalKey;
	private final GoalKey<Spider> spiderPounceGoalKey;
	private final GoalKey<Spider> spiderCombatGoalKey;
	private final Supplier<PaperSettings> settings;
	private final PaperIntelligenceService intelligence;
	private final PaperSkeletonProfile skeletonProfile;
	private final PaperBlastReservationBoard blastReservations;
	private final PaperPounceCoordinator pounceCoordinator;
	private final PaperDamageMemory damageMemory;
	private final PaperMetrics metrics;
	private final Map<UUID, OriginalSpiderGoals> originalSpiderGoals = new HashMap<>();

	public PaperMobLifecycle(
		final Plugin plugin,
		final Supplier<PaperSettings> settings,
		final PaperIntelligenceService intelligence,
		final PaperSkeletonProfile skeletonProfile,
		final PaperBlastReservationBoard blastReservations,
		final PaperPounceCoordinator pounceCoordinator,
		final PaperDamageMemory damageMemory,
		final PaperMetrics metrics
	) {
		this.retreatGoalKey = GoalKey.of(Zombie.class, new NamespacedKey(plugin, "zombie_reactive_retreat"));
		this.skeletonDisengageGoalKey = GoalKey.of(
			AbstractSkeleton.class,
			new NamespacedKey(plugin, "skeleton_emergency_disengage")
		);
		this.creeperFuseGoalKey = GoalKey.of(Creeper.class, new NamespacedKey(plugin, "creeper_tactical_fuse"));
		this.creeperApproachGoalKey = GoalKey.of(Creeper.class, new NamespacedKey(plugin, "creeper_tactical_approach"));
		this.spiderPounceGoalKey = GoalKey.of(Spider.class, new NamespacedKey(plugin, "spider_predictive_pounce"));
		this.spiderCombatGoalKey = GoalKey.of(Spider.class, new NamespacedKey(plugin, "spider_tactical_combat"));
		this.settings = settings;
		this.intelligence = intelligence;
		this.skeletonProfile = skeletonProfile;
		this.blastReservations = blastReservations;
		this.pounceCoordinator = pounceCoordinator;
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
		if (event.getEntity() instanceof Creeper creeper) {
			this.blastReservations.release(creeper);
		}
		if (event.getEntity() instanceof Spider spider) {
			// 区块卸载也会触发移除事件。先把精确保存的原版 Goal 放回实体，
			// 这样同一实体再次装载、插件热卸载或其他插件接管时都不会留下半改造状态。
			if (spider.getType() == EntityType.SPIDER) {
				this.removeCustomSpiderGoals(spider);
				this.restoreOriginalSpiderGoals(spider);
			}
			this.pounceCoordinator.release(spider, false);
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
			for (Entity entity : world.getEntities()) {
				if (entity instanceof Zombie zombie
					&& Bukkit.getMobGoals().hasGoal(zombie, this.retreatGoalKey)) {
					Bukkit.getMobGoals().removeGoal(zombie, this.retreatGoalKey);
					this.metrics.retreatGoalRemoved();
				}
				if (entity instanceof AbstractSkeleton skeleton
					&& Bukkit.getMobGoals().hasGoal(skeleton, this.skeletonDisengageGoalKey)) {
					Bukkit.getMobGoals().removeGoal(skeleton, this.skeletonDisengageGoalKey);
					this.metrics.skeletonDisengageGoalRemoved();
				}
				if (entity instanceof Creeper creeper) {
					if (Bukkit.getMobGoals().hasGoal(creeper, this.creeperFuseGoalKey)) {
						Bukkit.getMobGoals().removeGoal(creeper, this.creeperFuseGoalKey);
						this.metrics.creeperGoalRemoved();
					}
					if (Bukkit.getMobGoals().hasGoal(creeper, this.creeperApproachGoalKey)) {
						Bukkit.getMobGoals().removeGoal(creeper, this.creeperApproachGoalKey);
						this.metrics.creeperGoalRemoved();
					}
					this.blastReservations.release(creeper);
				}
				if (entity instanceof Spider spider && spider.getType() == EntityType.SPIDER) {
					this.removeCustomSpiderGoals(spider);
					this.restoreOriginalSpiderGoals(spider);
					this.pounceCoordinator.release(spider, false);
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
		if (mob instanceof Zombie zombie) {
			this.synchronizeZombieGoal(zombie);
		}
		if (mob instanceof AbstractSkeleton skeleton) {
			this.synchronizeSkeletonGoal(skeleton);
		}
		if (mob instanceof Creeper creeper) {
			this.synchronizeCreeperGoals(creeper);
		}
		if (mob instanceof Spider spider && spider.getType() == EntityType.SPIDER) {
			this.synchronizeSpiderGoals(spider);
		}
	}

	private void synchronizeZombieGoal(final Zombie zombie) {
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

	private void synchronizeSkeletonGoal(final AbstractSkeleton skeleton) {
		PaperSettings config = this.settings.get();
		boolean shouldHaveGoal = config.enabled() && config.skeletonSpacingEnabled();
		boolean hasGoal = Bukkit.getMobGoals().hasGoal(skeleton, this.skeletonDisengageGoalKey);
		if (shouldHaveGoal && !hasGoal) {
			Bukkit.getMobGoals().addGoal(
				skeleton,
				RETREAT_GOAL_PRIORITY,
				new PaperSkeletonDisengageGoal(
					skeleton,
					this.skeletonDisengageGoalKey,
					this.settings,
					this.intelligence,
					this.skeletonProfile,
					this.metrics
				)
			);
			this.metrics.skeletonDisengageGoalInstalled();
		} else if (!shouldHaveGoal && hasGoal) {
			Bukkit.getMobGoals().removeGoal(skeleton, this.skeletonDisengageGoalKey);
			this.metrics.skeletonDisengageGoalRemoved();
		}
	}

	private void synchronizeCreeperGoals(final Creeper creeper) {
		PaperSettings config = this.settings.get();
		boolean shouldHaveGoals = config.enabled() && config.creeperTacticsEnabled();
		boolean hasFuse = Bukkit.getMobGoals().hasGoal(creeper, this.creeperFuseGoalKey);
		boolean hasApproach = Bukkit.getMobGoals().hasGoal(creeper, this.creeperApproachGoalKey);
		if (shouldHaveGoals && !hasFuse) {
			Bukkit.getMobGoals().addGoal(
				creeper,
				CREEPER_FUSE_GOAL_PRIORITY,
				new PaperCreeperFuseGoal(
					creeper,
					this.creeperFuseGoalKey,
					this.settings,
					this.intelligence,
					this.blastReservations,
					this.metrics
				)
			);
			this.metrics.creeperGoalInstalled();
		} else if (!shouldHaveGoals && hasFuse) {
			Bukkit.getMobGoals().removeGoal(creeper, this.creeperFuseGoalKey);
			this.metrics.creeperGoalRemoved();
		}
		if (shouldHaveGoals && !hasApproach) {
			Bukkit.getMobGoals().addGoal(
				creeper,
				CREEPER_APPROACH_GOAL_PRIORITY,
				new PaperCreeperApproachGoal(
					creeper,
					this.creeperApproachGoalKey,
					this.settings,
					this.intelligence,
					this.blastReservations,
					this.metrics
				)
			);
			this.metrics.creeperGoalInstalled();
		} else if (!shouldHaveGoals && hasApproach) {
			Bukkit.getMobGoals().removeGoal(creeper, this.creeperApproachGoalKey);
			this.metrics.creeperGoalRemoved();
		}
		if (!shouldHaveGoals) {
			this.blastReservations.release(creeper);
		}
	}

	private void synchronizeSpiderGoals(final Spider spider) {
		PaperSettings config = this.settings.get();
		boolean shouldHaveGoals = config.enabled() && config.spiderTacticsEnabled();
		if (shouldHaveGoals) {
			this.captureAndRemoveOriginalSpiderGoals(spider);
			if (!Bukkit.getMobGoals().hasGoal(spider, this.spiderPounceGoalKey)) {
				Bukkit.getMobGoals().addGoal(
					spider,
					SPIDER_POUNCE_GOAL_PRIORITY,
					new PaperSpiderPounceGoal(
						spider,
						this.spiderPounceGoalKey,
						this.settings,
						this.intelligence,
						this.pounceCoordinator,
						this.metrics
					)
				);
				this.metrics.spiderGoalInstalled();
			}
			if (!Bukkit.getMobGoals().hasGoal(spider, this.spiderCombatGoalKey)) {
				Bukkit.getMobGoals().addGoal(
					spider,
					SPIDER_COMBAT_GOAL_PRIORITY,
					new PaperSpiderCombatGoal(
						spider,
						this.spiderCombatGoalKey,
						this.settings,
						this.intelligence,
						this.metrics
					)
				);
				this.metrics.spiderGoalInstalled();
			}
			return;
		}
		this.removeCustomSpiderGoals(spider);
		this.restoreOriginalSpiderGoals(spider);
		this.pounceCoordinator.release(spider, false);
	}

	private void captureAndRemoveOriginalSpiderGoals(final Spider spider) {
		if (this.originalSpiderGoals.containsKey(spider.getUniqueId())) {
			return;
		}
		GoalKey<Spider> leapKey = spiderKey(VanillaGoal.LEAP_AT);
		GoalKey<Spider> attackKey = spiderKey(VanillaGoal.SPIDER_ATTACK);
		List<Goal<Spider>> leapGoals = new ArrayList<>(Bukkit.getMobGoals().getGoals(spider, leapKey));
		List<Goal<Spider>> attackGoals = new ArrayList<>(Bukkit.getMobGoals().getGoals(spider, attackKey));
		for (Goal<Spider> goal : leapGoals) {
			Bukkit.getMobGoals().removeGoal(spider, goal);
		}
		for (Goal<Spider> goal : attackGoals) {
			Bukkit.getMobGoals().removeGoal(spider, goal);
		}
		this.originalSpiderGoals.put(spider.getUniqueId(), new OriginalSpiderGoals(leapGoals, attackGoals));
	}

	private void removeCustomSpiderGoals(final Spider spider) {
		if (Bukkit.getMobGoals().hasGoal(spider, this.spiderPounceGoalKey)) {
			Bukkit.getMobGoals().removeGoal(spider, this.spiderPounceGoalKey);
			this.metrics.spiderGoalRemoved();
		}
		if (Bukkit.getMobGoals().hasGoal(spider, this.spiderCombatGoalKey)) {
			Bukkit.getMobGoals().removeGoal(spider, this.spiderCombatGoalKey);
			this.metrics.spiderGoalRemoved();
		}
	}

	private void restoreOriginalSpiderGoals(final Spider spider) {
		OriginalSpiderGoals originals = this.originalSpiderGoals.remove(spider.getUniqueId());
		if (originals == null) {
			return;
		}
		GoalKey<Spider> leapKey = spiderKey(VanillaGoal.LEAP_AT);
		GoalKey<Spider> attackKey = spiderKey(VanillaGoal.SPIDER_ATTACK);
		if (!Bukkit.getMobGoals().hasGoal(spider, leapKey)) {
			for (Goal<Spider> goal : originals.leapGoals()) {
				Bukkit.getMobGoals().addGoal(spider, 3, goal);
			}
		}
		if (!Bukkit.getMobGoals().hasGoal(spider, attackKey)) {
			for (Goal<Spider> goal : originals.attackGoals()) {
				Bukkit.getMobGoals().addGoal(spider, 4, goal);
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static GoalKey<Spider> spiderKey(final GoalKey<?> key) {
		return (GoalKey)key;
	}

	private record OriginalSpiderGoals(List<Goal<Spider>> leapGoals, List<Goal<Spider>> attackGoals) {
		private OriginalSpiderGoals {
			leapGoals = List.copyOf(leapGoals);
			attackGoals = List.copyOf(attackGoals);
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

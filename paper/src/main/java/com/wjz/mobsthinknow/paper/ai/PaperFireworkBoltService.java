package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperFireworkSettings;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * 全服单任务、硬容量上限的烟花弩弹体管理器。每枚弹体每 tick 最多进行一次前向射线，绝不为每一发
 * 创建独立调度任务；卸载、超时、插件关闭都会显式移除实体。
 */
public final class PaperFireworkBoltService {
	private static final double MINIMUM_DIRECTION_LENGTH_SQUARED = 1.0E-8;
	private static final double ENTITY_RAY_SIZE = 0.35;
	private static final double RAY_MARGIN = 0.40;

	private final Plugin plugin;
	private final Supplier<PaperSettings> settings;
	private final PaperMetrics metrics;
	private final Map<UUID, ActiveBolt> active = new LinkedHashMap<>();
	private BukkitTask task;

	public PaperFireworkBoltService(
		final Plugin plugin,
		final Supplier<PaperSettings> settings,
		final PaperMetrics metrics
	) {
		this.plugin = Objects.requireNonNull(plugin, "plugin");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.metrics = Objects.requireNonNull(metrics, "metrics");
	}

	public void start() {
		if (this.task == null) {
			this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
		}
	}

	public void stop() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
		for (ActiveBolt bolt : this.active.values()) {
			if (bolt.firework().isValid()) {
				bolt.firework().remove();
			}
		}
		this.active.clear();
	}

	/** 返回 false 时调用者应无副作用地降级为普通箭。 */
	public boolean launch(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final Vector requestedDirection
	) {
		PaperFireworkSettings config = this.settings.get().skeletonCrossbowTactics().firework();
		if (!config.enabled()
			|| !shooter.isValid()
			|| !target.isValid()
			|| shooter.getWorld() != target.getWorld()
			|| requestedDirection.lengthSquared() < MINIMUM_DIRECTION_LENGTH_SQUARED
			|| this.active.size() >= config.maximumActiveProjectiles()) {
			this.metrics.fireworkCapacityRejected();
			return false;
		}
		Vector direction = requestedDirection.clone().normalize();
		Vector velocity = direction.clone().multiply(config.projectileSpeed());
		Location origin = shooter.getEyeLocation().clone().add(direction.clone().multiply(0.45));
		Firework firework = shooter.getWorld().spawn(origin, Firework.class, spawned -> {
			FireworkMeta meta = spawned.getFireworkMeta();
			meta.clearEffects();
			meta.addEffect(FireworkEffect.builder()
				.with(FireworkEffect.Type.BURST)
				.withColor(Color.fromRGB(0x80C71F), Color.fromRGB(0x474F52))
				.withFade(Color.WHITE)
				.trail(true)
				.flicker(true)
				.build());
			meta.setPower(1);
			spawned.setFireworkMeta(meta);
			spawned.setShooter(shooter);
			spawned.setShotAtAngle(true);
			spawned.setGravity(false);
			spawned.setPersistent(false);
		});
		firework.setTicksToDetonate(config.projectileLifetimeTicks() + 20);
		firework.setVelocity(velocity);
		long now = Bukkit.getCurrentTick();
		this.active.put(
			firework.getUniqueId(),
			new ActiveBolt(firework, shooter.getUniqueId(), target.getUniqueId(), direction, now + config.projectileLifetimeTicks())
		);
		this.metrics.fireworkLaunched();
		return true;
	}

	public int activeCount() {
		return this.active.size();
	}

	/** 事务式测试/世界清理使用：只移除与指定射手或目标有关的受管弹体。 */
	public void discardRelatedTo(final Set<UUID> entityIds) {
		if (entityIds.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, ActiveBolt>> iterator = this.active.entrySet().iterator();
		while (iterator.hasNext()) {
			ActiveBolt bolt = iterator.next().getValue();
			if (entityIds.contains(bolt.shooterId()) || entityIds.contains(bolt.targetId())) {
				if (bolt.firework().isValid()) {
					bolt.firework().remove();
				}
				iterator.remove();
			}
		}
	}

	private void tick() {
		long now = Bukkit.getCurrentTick();
		PaperFireworkSettings config = this.settings.get().skeletonCrossbowTactics().firework();
		Iterator<Map.Entry<UUID, ActiveBolt>> iterator = this.active.entrySet().iterator();
		while (iterator.hasNext()) {
			ActiveBolt bolt = iterator.next().getValue();
			Firework firework = bolt.firework();
			if (!firework.isValid() || firework.isDead() || firework.isDetonated()) {
				iterator.remove();
				continue;
			}
			if (now >= bolt.expiresAt()) {
				this.detonate(firework, firework.getLocation());
				this.metrics.fireworkTimedOut();
				iterator.remove();
				continue;
			}

			World world = firework.getWorld();
			double travel = config.projectileSpeed() + RAY_MARGIN;
			RayTraceResult hit = world.rayTrace(
				firework.getLocation(),
				bolt.direction(),
				travel,
				FluidCollisionMode.NEVER,
				true,
				ENTITY_RAY_SIZE,
				entity -> isIntendedTarget(entity, bolt.targetId())
			);
			if (hit != null) {
				Vector impact = hit.getHitPosition();
				this.detonate(firework, new Location(world, impact.getX(), impact.getY(), impact.getZ()));
				iterator.remove();
				continue;
			}
			// 原版烟花会自行加速；每 tick 重置速度，让配置值成为真实硬上限并保持弹道可预测。
			firework.setVelocity(bolt.direction().clone().multiply(config.projectileSpeed()));
		}
	}

	private void detonate(final Firework firework, final Location impact) {
		firework.teleport(impact);
		firework.setVelocity(new Vector());
		firework.detonate();
		this.metrics.fireworkDetonated();
	}

	private static boolean isIntendedTarget(final Entity entity, final UUID targetId) {
		return entity instanceof LivingEntity && entity.getUniqueId().equals(targetId);
	}

	private record ActiveBolt(
		Firework firework,
		UUID shooterId,
		UUID targetId,
		Vector direction,
		long expiresAt
	) {
		private ActiveBolt {
			Objects.requireNonNull(firework, "firework");
			Objects.requireNonNull(shooterId, "shooterId");
			Objects.requireNonNull(targetId, "targetId");
			direction = Objects.requireNonNull(direction, "direction").clone();
		}
	}
}

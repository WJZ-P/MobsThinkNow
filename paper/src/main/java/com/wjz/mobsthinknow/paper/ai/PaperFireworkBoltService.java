package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.paper.PaperFireworkSettings;
import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.PaperSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.function.Predicate;
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
	private final Map<UUID, ActiveBolt> active = new HashMap<>();
	private final ActiveBoltChain activeOrder = new ActiveBoltChain();
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
		PaperSettings root = this.settings.get();
		if (this.task == null && root.enabled() && root.skeletonCrossbowTactics().firework().enabled()) {
			this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
		}
	}

	public void stop() {
		if (this.task != null) {
			this.task.cancel();
			this.task = null;
		}
		for (ActiveBolt bolt = this.activeOrder.first(); bolt != null; bolt = bolt.next) {
			if (bolt.firework().isValid()) {
				bolt.firework().remove();
			}
		}
		this.active.clear();
		this.activeOrder.clear();
	}

	/** 返回 false 时调用者应无副作用地降级为普通箭。 */
	public boolean launch(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final Vector requestedDirection
	) {
		PaperSettings root = this.settings.get();
		PaperFireworkSettings config = root.skeletonCrossbowTactics().firework();
		if (!root.enabled()
			|| !root.skeletonCrossbowTactics().enabled()
			|| !config.enabled()
			|| this.task == null
			|| !shooter.isValid()
			|| shooter.isDead()
			|| !target.isValid()
			|| target.isDead()
			|| shooter.getWorld() != target.getWorld()
			|| !isUsableDirection(requestedDirection)
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
		UUID fireworkId = firework.getUniqueId();
		ActiveBolt bolt = new ActiveBolt(
			fireworkId,
			firework,
			shooter.getUniqueId(),
			target.getUniqueId(),
			direction,
			saturatingAdd(now, config.projectileLifetimeTicks())
		);
		this.active.put(fireworkId, bolt);
		this.activeOrder.add(bolt);
		this.metrics.fireworkLaunched();
		return true;
	}

	public int activeCount() {
		return this.active.size();
	}

	public boolean isRunning() {
		return this.task != null;
	}

	/** 事务式测试/世界清理使用：只移除与指定射手或目标有关的受管弹体。 */
	public void discardRelatedTo(final Set<UUID> entityIds) {
		if (entityIds.isEmpty()) {
			return;
		}
		ActiveBolt bolt = this.activeOrder.first();
		while (bolt != null) {
			ActiveBolt next = bolt.next;
			if (entityIds.contains(bolt.shooterId()) || entityIds.contains(bolt.targetId())) {
				if (bolt.firework().isValid()) {
					bolt.firework().remove();
				}
				this.remove(bolt);
			}
			bolt = next;
		}
	}

	private void tick() {
		long now = Bukkit.getCurrentTick();
		PaperFireworkSettings config = this.settings.get().skeletonCrossbowTactics().firework();
		ActiveBolt bolt = this.activeOrder.first();
		while (bolt != null) {
			ActiveBolt next = bolt.next;
			Firework firework = bolt.firework();
			if (!firework.isValid() || firework.isDead() || firework.isDetonated()) {
				this.remove(bolt);
				bolt = next;
				continue;
			}
			if (now >= bolt.expiresAt()) {
				this.detonate(firework, firework.getLocation());
				this.metrics.fireworkTimedOut();
				this.remove(bolt);
				bolt = next;
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
				bolt
			);
			if (hit != null) {
				Vector impact = hit.getHitPosition();
				this.detonate(firework, new Location(world, impact.getX(), impact.getY(), impact.getZ()));
				this.remove(bolt);
				bolt = next;
				continue;
			}
			// 原版烟花会自行加速；每 tick 重置速度，让配置值成为真实硬上限并保持弹道可预测。
			firework.setVelocity(bolt.direction().clone().multiply(config.projectileSpeed()));
			bolt = next;
		}
	}

	private void remove(final ActiveBolt bolt) {
		if (this.active.remove(bolt.id, bolt)) {
			this.activeOrder.remove(bolt);
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

	static boolean isUsableDirection(final Vector direction) {
		return direction != null
			&& Double.isFinite(direction.getX())
			&& Double.isFinite(direction.getY())
			&& Double.isFinite(direction.getZ())
			&& direction.lengthSquared() >= MINIMUM_DIRECTION_LENGTH_SQUARED;
	}

	private static long saturatingAdd(final long left, final long right) {
		return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
	}

	static final class ActiveBolt implements Predicate<Entity> {
		private final UUID id;
		private final Firework firework;
		private final UUID shooterId;
		private final UUID targetId;
		private final Vector direction;
		private final long expiresAt;
		private ActiveBolt previous;
		private ActiveBolt next;

		ActiveBolt(
			final UUID id,
			final Firework firework,
			final UUID shooterId,
			final UUID targetId,
			final Vector direction,
			final long expiresAt
		) {
			this.id = Objects.requireNonNull(id, "id");
			this.firework = Objects.requireNonNull(firework, "firework");
			this.shooterId = Objects.requireNonNull(shooterId, "shooterId");
			this.targetId = Objects.requireNonNull(targetId, "targetId");
			this.direction = Objects.requireNonNull(direction, "direction").clone();
			this.expiresAt = expiresAt;
		}

		private Firework firework() {
			return this.firework;
		}

		private UUID shooterId() {
			return this.shooterId;
		}

		private UUID targetId() {
			return this.targetId;
		}

		private Vector direction() {
			return this.direction;
		}

		private long expiresAt() {
			return this.expiresAt;
		}

		@Override
		public boolean test(final Entity entity) {
			return isIntendedTarget(entity, this.targetId);
		}
	}

	static final class ActiveBoltChain {
		private ActiveBolt first;
		private ActiveBolt last;
		private int size;

		void add(final ActiveBolt bolt) {
			bolt.previous = this.last;
			bolt.next = null;
			if (this.last == null) {
				this.first = bolt;
			} else {
				this.last.next = bolt;
			}
			this.last = bolt;
			this.size++;
		}

		void remove(final ActiveBolt bolt) {
			if (bolt.previous == null) {
				this.first = bolt.next;
			} else {
				bolt.previous.next = bolt.next;
			}
			if (bolt.next == null) {
				this.last = bolt.previous;
			} else {
				bolt.next.previous = bolt.previous;
			}
			bolt.previous = null;
			bolt.next = null;
			this.size--;
		}

		void clear() {
			this.first = null;
			this.last = null;
			this.size = 0;
		}

		ActiveBolt first() {
			return this.first;
		}

		int size() {
			return this.size;
		}
	}
}

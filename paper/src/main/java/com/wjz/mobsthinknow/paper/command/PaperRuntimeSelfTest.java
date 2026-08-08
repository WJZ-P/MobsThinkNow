package com.wjz.mobsthinknow.paper.command;

import com.wjz.mobsthinknow.paper.PaperMetrics;
import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperFireworkBoltService;
import com.wjz.mobsthinknow.paper.ai.PaperSkeletonLoadoutService;
import com.wjz.mobsthinknow.paper.squad.PaperSquadCoordinator;
import com.wjz.mobsthinknow.paper.squad.PaperSquadDirective;
import com.wjz.mobsthinknow.shared.squad.MixedSquadPlan;
import com.wjz.mobsthinknow.shared.squad.MixedSquadState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Arrow;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** 在真实 Paper tick 中验证四兵种 Goal 安装、同队、共享目标和 COMBINED_ARMS，然后无条件清理。 */
public final class PaperRuntimeSelfTest {
	private static final List<EntityType> CORE_TYPES = List.of(
		EntityType.ZOMBIE,
		EntityType.SKELETON,
		EntityType.SKELETON,
		EntityType.CREEPER,
		EntityType.SPIDER
	);
	private static final int STRUCTURE_VALIDATION_DELAY_TICKS = 25;
	private static final int COMBAT_VALIDATION_DELAY_TICKS = 120;
	private static final int COMBAT_PROBE_SEARCH_RADIUS = 32;
	private static final int[] COMBAT_PROBE_TARGET_OFFSETS = {2, 3};
	private static final int[] FIREWORK_PROBE_TARGET_OFFSETS = {12, 10, 8};
	private static final int[][] CARDINAL_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private final Plugin plugin;
	private final PaperIntelligenceService intelligence;
	private final PaperSquadCoordinator squads;
	private final PaperFireworkBoltService fireworkBolts;
	private final PaperSkeletonLoadoutService skeletonLoadouts;
	private final PaperMetrics metrics;
	private final List<Entity> activeEntities = new ArrayList<>();
	private final List<Chunk> temporarilyForcedChunks = new ArrayList<>();
	private BukkitTask validationTask;
	private BukkitTask shieldProbeAttackTask;
	private BukkitTask shieldDisableProbeTask;
	private Zombie shieldProbeGuard;
	private IronGolem shieldProbeAttacker;
	private AbstractSkeleton fireworkProbeShooter;
	private Skeleton naturalLoadoutProbe;
	private boolean naturalLoadoutProbeExpected;
	private long shieldProbeAttackAttempts;
	private long nextShieldProbeAttackAt;
	private long shieldDisableProbeAttempts;

	public PaperRuntimeSelfTest(
		final Plugin plugin,
		final PaperIntelligenceService intelligence,
		final PaperSquadCoordinator squads,
		final PaperFireworkBoltService fireworkBolts,
		final PaperSkeletonLoadoutService skeletonLoadouts,
		final PaperMetrics metrics
	) {
		this.plugin = plugin;
		this.intelligence = intelligence;
		this.squads = squads;
		this.fireworkBolts = fireworkBolts;
		this.skeletonLoadouts = skeletonLoadouts;
		this.metrics = metrics;
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
		PaperMetrics.Snapshot baseline = this.metrics.snapshot();
		try {
			Location targetLocation = safeSurface(world, anchor.getBlockX(), anchor.getBlockZ() + 6);
			this.forceChunk(anchor);
			this.forceChunk(targetLocation);
			IronGolem target = (IronGolem)world.spawnEntity(targetLocation, EntityType.IRON_GOLEM);
			target.setInvulnerable(true);
			target.setAI(false);
			target.setPlayerCreated(true);
			target.setPersistent(false);
			this.activeEntities.add(target);

			List<Mob> mobs = new ArrayList<>(CORE_TYPES.size());
			int skeletonIndex = 0;
			for (int index = 0; index < CORE_TYPES.size(); index++) {
				int xOffset = (int)Math.round((index - (CORE_TYPES.size() - 1) * 0.5) * 1.8);
				Location mobLocation = safeSurface(world, anchor.getBlockX() + xOffset, anchor.getBlockZ());
				this.forceChunk(mobLocation);
				Entity entity = world.spawnEntity(mobLocation, CORE_TYPES.get(index));
				if (!(entity instanceof Mob mob)) {
					throw new IllegalStateException("self-test entity is not a Mob: " + entity.getType());
				}
				mob.setInvulnerable(true);
				mob.setPersistent(false);
				mob.setRemoveWhenFarAway(false);
				if (mob instanceof Creeper creeper) {
					creeper.setExplosionRadius(0);
					creeper.setMaxFuseTicks(200);
				}
				if (mob instanceof Zombie zombie) {
					zombie.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
				}
				if (mob instanceof AbstractSkeleton skeleton && skeletonIndex++ == 1) {
					skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
				}
				this.intelligence.set(mob, 10);
				this.squads.observeTarget(mob, target);
				mob.setTarget(target);
				mobs.add(mob);
				this.activeEntities.add(mob);
			}
			this.spawnAxeProbe(world, anchor);
			this.spawnShieldProbe(world, anchor);
			this.spawnShieldDisableProbe(world, anchor);
			this.spawnFireworkProbe(world, anchor);
			this.naturalLoadoutProbeExpected = this.skeletonLoadouts.guaranteesCrossbow(world.getDifficulty());
			if (this.naturalLoadoutProbeExpected) {
				this.spawnNaturalLoadoutProbe(world, anchor);
			}
			this.validationTask = Bukkit.getScheduler().runTaskLater(
				this.plugin,
				() -> this.validateStructure(sender, target, mobs, baseline),
				STRUCTURE_VALIDATION_DELAY_TICKS
			);
			sender.sendMessage(Component.text(
				"MTN Paper self-test scheduled: structure=" + STRUCTURE_VALIDATION_DELAY_TICKS
					+ " ticks, combat=+" + COMBAT_VALIDATION_DELAY_TICKS + " ticks.",
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

	private void validateStructure(
		final CommandSender sender,
		final LivingEntity target,
		final List<Mob> mobs,
		final PaperMetrics.Snapshot baseline
	) {
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
				this.cleanup();
				return;
			}
			long squadId = directives.getFirst().squadId();
			boolean oneSquad = directives.stream().allMatch(directive -> directive.squadId() == squadId);
			boolean combinedArms = directives.stream()
				.allMatch(directive -> directive.plan() == MixedSquadPlan.COMBINED_ARMS);
			boolean sharedTarget = mobs.stream().allMatch(mob -> this.squads.sharedTargetFor(mob) == target);
			boolean allTracked = this.squads.assignedMemberCount() >= CORE_TYPES.size();
			Spider spider = mobs.stream()
				.filter(Spider.class::isInstance)
				.map(Spider.class::cast)
				.findFirst()
				.orElseThrow();
			Creeper creeper = mobs.stream()
				.filter(Creeper.class::isInstance)
				.map(Creeper.class::cast)
				.findFirst()
				.orElseThrow();
			boolean mountedPair = this.squads.assignedTransportPartnerFor(spider) == creeper;
			if (!oneSquad || !combinedArms || !sharedTarget || !allTracked || !mountedPair) {
				this.report(
					sender,
					false,
					"oneSquad=" + oneSquad
						+ ", combinedArms=" + combinedArms
						+ ", sharedTarget=" + sharedTarget
						+ ", allTracked=" + allTracked
						+ ", mountedPair=" + mountedPair
				);
				this.cleanup();
				return;
			}
			sender.sendMessage(Component.text(
				"[MTN SELFTEST STRUCTURE PASS] squad=" + squadId
					+ ", state=" + directives.getFirst().state()
					+ ", plan=" + directives.getFirst().plan()
					+ ", mountedPair=true",
				NamedTextColor.AQUA
			));
			this.validationTask = Bukkit.getScheduler().runTaskLater(
				this.plugin,
				() -> this.validateCombat(sender, mobs, baseline),
				COMBAT_VALIDATION_DELAY_TICKS
			);
		} catch (RuntimeException exception) {
			this.report(sender, false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
			this.cleanup();
		}
	}

	private void validateCombat(
		final CommandSender sender,
		final List<Mob> mobs,
		final PaperMetrics.Snapshot baseline
	) {
		this.validationTask = null;
		try {
			List<PaperSquadDirective> directives = mobs.stream()
				.filter(Entity::isValid)
				.map(this.squads::directiveFor)
				.toList();
			boolean engaging = !directives.isEmpty()
				&& directives.stream().allMatch(directive -> directive != null
					&& directive.state() == MixedSquadState.ENGAGING);
			PaperMetrics.Snapshot current = this.metrics.snapshot();
			long coordinatedShots = current.coordinatedShots() - baseline.coordinatedShots();
			long crossbowCharges = current.crossbowCharges() - baseline.crossbowCharges();
			long crossbowPoseTicks = current.crossbowChargePoseTicks() - baseline.crossbowChargePoseTicks();
			long crossbowShots = current.crossbowShots() - baseline.crossbowShots();
			long fireworkLaunches = current.fireworkLaunches() - baseline.fireworkLaunches();
			long fireworkDetonations = current.fireworkDetonations() - baseline.fireworkDetonations();
			boolean fireworkAmmoConsumed = this.fireworkProbeShooter != null
				&& this.fireworkProbeShooter.isValid()
				&& this.fireworkProbeShooter.getEquipment().getItemInOffHand().getAmount() < 4;
			int activeFireworkBolts = this.fireworkBolts.activeCount();
			long naturalLoadoutInitializations = current.naturalSkeletonLoadoutInitializations()
				- baseline.naturalSkeletonLoadoutInitializations();
			long naturalCrossbows = current.naturalCrossbowsEquipped() - baseline.naturalCrossbowsEquipped();
			boolean naturalProbeEquipped = this.naturalLoadoutProbe != null
				&& this.naturalLoadoutProbe.isValid()
				&& this.naturalLoadoutProbe.getEquipment().getItemInMainHand().getType() == Material.CROSSBOW;
			long weaponAttacks = current.weaponAttacks() - baseline.weaponAttacks();
			long axeLeaps = current.axeLeaps() - baseline.axeLeaps();
			long axeCriticals = current.axeCriticalAttacks() - baseline.axeCriticalAttacks();
			long axeRejectAirborne = current.axeLaunchAirborneRejects() - baseline.axeLaunchAirborneRejects();
			long axeRejectWater = current.axeLaunchWaterRejects() - baseline.axeLaunchWaterRejects();
			long axeRejectSight = current.axeLaunchSightRejects() - baseline.axeLaunchSightRejects();
			long axeRejectBand = current.axeLaunchBandRejects() - baseline.axeLaunchBandRejects();
			long axeRejectCollision = current.axeLaunchCollisionRejects() - baseline.axeLaunchCollisionRejects();
			long mounted = current.mountedBreachMounts() - baseline.mountedBreachMounts();
			long released = current.mountedBreachPayloadReleases() - baseline.mountedBreachPayloadReleases();
			long shieldBlocks = current.shieldBlocks() - baseline.shieldBlocks();
			long shieldCounterattacks = current.shieldCounterattacks() - baseline.shieldCounterattacks();
			long shieldGuards = current.shieldGuards() - baseline.shieldGuards();
			long shieldStrikeWindows = current.shieldStrikeWindows() - baseline.shieldStrikeWindows();
			long shieldAttacks = current.shieldAttacks() - baseline.shieldAttacks();
			long shieldDisables = current.shieldDisables() - baseline.shieldDisables();
			if (!engaging
				|| coordinatedShots <= 0L
				|| crossbowCharges <= 0L
				|| crossbowPoseTicks <= 0L
				|| crossbowShots <= 0L
				|| fireworkLaunches <= 0L
				|| fireworkDetonations <= 0L
				|| !fireworkAmmoConsumed
				|| activeFireworkBolts != 0
				|| this.naturalLoadoutProbeExpected
					&& (naturalLoadoutInitializations != 1L || naturalCrossbows != 1L || !naturalProbeEquipped)
				|| weaponAttacks <= 0L
				|| axeLeaps <= 0L
				|| mounted <= 0L
				|| shieldBlocks <= 0L
				|| shieldCounterattacks <= 0L
				|| shieldDisables <= 0L) {
				this.report(
					sender,
					false,
					"engaging=" + engaging
						+ ", coordinatedShots=" + coordinatedShots
						+ ", crossbowCharges=" + crossbowCharges
						+ ", crossbowPoseTicks=" + crossbowPoseTicks
						+ ", crossbowShots=" + crossbowShots
						+ ", fireworkLaunches=" + fireworkLaunches
						+ ", fireworkDetonations=" + fireworkDetonations
						+ ", fireworkAmmoConsumed=" + fireworkAmmoConsumed
						+ ", activeFireworkBolts=" + activeFireworkBolts
						+ ", naturalProbeExpected=" + this.naturalLoadoutProbeExpected
						+ ", naturalLoadoutInitializations=" + naturalLoadoutInitializations
						+ ", naturalCrossbows=" + naturalCrossbows
						+ ", naturalProbeEquipped=" + naturalProbeEquipped
						+ ", weaponAttacks=" + weaponAttacks
						+ ", axeLeaps=" + axeLeaps
						+ ", axeCriticals=" + axeCriticals
						+ ", axeRejects=[airborne:" + axeRejectAirborne
						+ ",water:" + axeRejectWater
						+ ",sight:" + axeRejectSight
						+ ",band:" + axeRejectBand
						+ ",collision:" + axeRejectCollision + "]"
						+ ", creepersMounted=" + mounted
						+ ", payloadReleases=" + released
						+ ", shieldBlocks=" + shieldBlocks
						+ ", shieldCounterattacks=" + shieldCounterattacks
						+ ", shieldGuards=" + shieldGuards
						+ ", shieldStrikeWindows=" + shieldStrikeWindows
						+ ", shieldAttacks=" + shieldAttacks
						+ ", shieldDisables=" + shieldDisables
						+ ", shieldDisableAttempts=" + this.shieldDisableProbeAttempts
						+ ", shieldProbe=" + this.shieldProbeSnapshot()
						+ ", carrierPathFailures="
						+ (current.mountedBreachPathFailures() - baseline.mountedBreachPathFailures())
						+ ", firingLanePathFailures="
						+ (current.firingLanePathFailures() - baseline.firingLanePathFailures())
				);
				return;
			}
			this.report(
				sender,
				true,
				"state=ENGAGING, plan=COMBINED_ARMS, coordinatedShots=" + coordinatedShots
					+ ", crossbowCharges=" + crossbowCharges
					+ ", crossbowPoseTicks=" + crossbowPoseTicks
					+ ", crossbowShots=" + crossbowShots
					+ ", fireworkLaunches=" + fireworkLaunches
					+ ", fireworkDetonations=" + fireworkDetonations
					+ ", fireworkAmmoConsumed=" + fireworkAmmoConsumed
					+ ", activeFireworkBolts=" + activeFireworkBolts
					+ ", naturalProbeExpected=" + this.naturalLoadoutProbeExpected
					+ ", naturalLoadoutInitializations=" + naturalLoadoutInitializations
					+ ", naturalCrossbows=" + naturalCrossbows
					+ ", weaponAttacks=" + weaponAttacks
					+ ", axeLeaps=" + axeLeaps
					+ ", axeCriticals=" + axeCriticals
					+ ", creepersMounted=" + mounted
					+ ", payloadReleases=" + released
					+ ", shieldBlocks=" + shieldBlocks
					+ ", shieldCounterattacks=" + shieldCounterattacks
					+ ", shieldGuards=" + shieldGuards
					+ ", shieldStrikeWindows=" + shieldStrikeWindows
					+ ", shieldAttacks=" + shieldAttacks
					+ ", shieldDisables=" + shieldDisables
			);
		} catch (RuntimeException exception) {
			this.report(sender, false, exception.getClass().getSimpleName() + ": " + exception.getMessage());
		} finally {
			this.cleanup();
		}
	}

	private void cleanup() {
		if (this.shieldProbeAttackTask != null) {
			this.shieldProbeAttackTask.cancel();
			this.shieldProbeAttackTask = null;
		}
		if (this.shieldDisableProbeTask != null) {
			this.shieldDisableProbeTask.cancel();
			this.shieldDisableProbeTask = null;
		}
		this.shieldProbeGuard = null;
		this.shieldProbeAttacker = null;
		this.fireworkProbeShooter = null;
		this.naturalLoadoutProbe = null;
		this.naturalLoadoutProbeExpected = false;
		this.shieldProbeAttackAttempts = 0L;
		this.nextShieldProbeAttackAt = Long.MIN_VALUE;
		this.shieldDisableProbeAttempts = 0L;
		Set<java.util.UUID> cleanupIds = this.activeEntities.stream()
			.map(Entity::getUniqueId)
			.collect(Collectors.toUnmodifiableSet());
		this.fireworkBolts.discardRelatedTo(cleanupIds);
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

	/**
	 * 把斧手物理动作放到独立的平坦样本中验证。混编战场会被射手、载具和阵位共同占用，若把跳劈断言
	 * 绑定在那一处，测试结果会错误地依赖实体碰撞顺序，而不是斧手 Goal 本身。
	 */
	private void spawnAxeProbe(final World world, final Location squadAnchor) {
		CombatProbePlacement placement = findCombatProbePlacement(
			world,
			squadAnchor.getBlockX() + 40,
			squadAnchor.getBlockZ()
		);
		if (placement == null) {
			throw new IllegalStateException("no flat collision-free axe probe lane found");
		}
		this.forceChunk(placement.zombie());
		this.forceChunk(placement.target());

		IronGolem target = (IronGolem)world.spawnEntity(placement.target(), EntityType.IRON_GOLEM);
		// 不设无敌：原版目标选择器会清除不可攻击目标。默认 100 点生命足以覆盖 145 tick 的探针窗口。
		target.setAI(false);
		target.setPlayerCreated(true);
		target.setPersistent(false);
		this.activeEntities.add(target);

		Zombie axeman = (Zombie)world.spawnEntity(placement.zombie(), EntityType.ZOMBIE);
		axeman.setInvulnerable(true);
		axeman.setShouldBurnInDay(false);
		axeman.setPersistent(false);
		axeman.setRemoveWhenFarAway(false);
		axeman.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
		this.intelligence.set(axeman, 10);
		axeman.setTarget(target);
		this.activeEntities.add(axeman);
	}

	/**
	 * 由真实铁傀儡向真实举盾僵尸发射箭矢。探针既验证 Paper 正面格挡适配，也验证事件邮箱在
	 * 2-4 tick 后驱动 Goal 主动放盾反击；高生命值仅用于让失败快照保留完整诊断窗口。
	 */
	private void spawnShieldProbe(final World world, final Location squadAnchor) {
		CombatProbePlacement placement = findCombatProbePlacement(
			world,
			squadAnchor.getBlockX() + 80,
			squadAnchor.getBlockZ()
		);
		if (placement == null) {
			throw new IllegalStateException("no flat collision-free shield probe lane found");
		}
		this.forceChunk(placement.zombie());
		this.forceChunk(placement.target());

		IronGolem attacker = (IronGolem)world.spawnEntity(placement.target(), EntityType.IRON_GOLEM);
		attacker.setAI(false);
		attacker.setPlayerCreated(true);
		attacker.setPersistent(false);
		setMaximumHealth(attacker, 500.0);
		this.activeEntities.add(attacker);

		Zombie shieldGuard = (Zombie)world.spawnEntity(placement.zombie(), EntityType.ZOMBIE);
		shieldGuard.setShouldBurnInDay(false);
		shieldGuard.setPersistent(false);
		shieldGuard.setRemoveWhenFarAway(false);
		shieldGuard.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
		shieldGuard.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
		setMaximumHealth(shieldGuard, 200.0);
		this.intelligence.set(shieldGuard, 10);
		shieldGuard.setTarget(attacker);
		this.shieldProbeGuard = shieldGuard;
		this.shieldProbeAttacker = attacker;
		this.shieldProbeAttackAttempts = 0L;
		this.nextShieldProbeAttackAt = Bukkit.getCurrentTick();
		this.activeEntities.add(shieldGuard);

		this.shieldProbeAttackTask = Bukkit.getScheduler().runTaskTimer(
			this.plugin,
			() -> {
				long now = Bukkit.getCurrentTick();
				if (attacker.isValid()
					&& shieldGuard.isValid()
					&& now >= this.nextShieldProbeAttackAt
					&& shieldGuard.hasActiveItem()
					&& shieldGuard.getActiveItemUsedTime() >= 10) {
					this.shieldProbeAttackAttempts++;
					this.nextShieldProbeAttackAt = now + 20L;
					attacker.lookAt(shieldGuard);
					org.bukkit.util.Vector direction = shieldGuard.getEyeLocation().toVector()
						.subtract(attacker.getEyeLocation().toVector())
						.normalize();
					Arrow arrow = world.spawnArrow(attacker.getEyeLocation(), direction, 1.8F, 0.0F);
					arrow.setShooter(attacker);
					arrow.setDamage(4.0);
					this.activeEntities.add(arrow);
				}
			},
			1L,
			1L
		);
	}

	private String shieldProbeSnapshot() {
		Zombie guard = this.shieldProbeGuard;
		IronGolem attacker = this.shieldProbeAttacker;
		if (guard == null || attacker == null) {
			return "missing";
		}
		org.bukkit.util.Vector facing = guard.getEyeLocation().getDirection().setY(0.0);
		org.bukkit.util.Vector towardAttacker = attacker.getLocation().toVector()
			.subtract(guard.getLocation().toVector())
			.setY(0.0);
		double facingDot = facing.lengthSquared() > 1.0E-8 && towardAttacker.lengthSquared() > 1.0E-8
			? facing.normalize().dot(towardAttacker.normalize())
			: Double.NaN;
		return "attempts:" + this.shieldProbeAttackAttempts
			+ ",guardValid:" + guard.isValid()
			+ ",health:" + guard.getHealth()
			+ ",active:" + guard.hasActiveItem()
			+ ",usedTicks:" + guard.getActiveItemUsedTime()
			+ ",yaw:" + guard.getYaw()
			+ ",facingDot:" + facingDot
			+ ",distance:" + Math.sqrt(guard.getLocation().distanceSquared(attacker.getLocation()));
	}

	/** 独立斧手探针要求正面成熟举盾被打断，伤害照常结算，且不会误记为成功格挡反击。 */
	private void spawnShieldDisableProbe(final World world, final Location squadAnchor) {
		CombatProbePlacement placement = findCombatProbePlacement(
			world,
			squadAnchor.getBlockX() + 120,
			squadAnchor.getBlockZ()
		);
		if (placement == null) {
			throw new IllegalStateException("no flat collision-free shield-disable probe lane found");
		}
		this.forceChunk(placement.zombie());
		this.forceChunk(placement.target());

		Mob breaker = (Mob)world.spawnEntity(placement.target(), EntityType.VINDICATOR);
		breaker.setAI(false);
		breaker.setPersistent(false);
		breaker.setRemoveWhenFarAway(false);
		breaker.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
		setMaximumHealth(breaker, 500.0);
		this.activeEntities.add(breaker);

		Zombie shieldGuard = (Zombie)world.spawnEntity(placement.zombie(), EntityType.ZOMBIE);
		shieldGuard.setShouldBurnInDay(false);
		shieldGuard.setPersistent(false);
		shieldGuard.setRemoveWhenFarAway(false);
		shieldGuard.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
		shieldGuard.getEquipment().setItemInOffHand(new ItemStack(Material.SHIELD));
		setMaximumHealth(shieldGuard, 200.0);
		this.intelligence.set(shieldGuard, 10);
		shieldGuard.setTarget(breaker);
		this.activeEntities.add(shieldGuard);
		this.shieldDisableProbeAttempts = 0L;

		this.shieldDisableProbeTask = Bukkit.getScheduler().runTaskTimer(
			this.plugin,
			() -> {
				if (this.shieldDisableProbeAttempts == 0L
					&& breaker.isValid()
					&& shieldGuard.isValid()
					&& shieldGuard.hasActiveItem()
					&& shieldGuard.getActiveItemUsedTime() >= 10) {
					this.shieldDisableProbeAttempts++;
					breaker.lookAt(shieldGuard);
					breaker.attack(shieldGuard);
				}
			},
			1L,
			1L
		);
	}

	/** 独立远距样本验证无小队弩手、真实副手弹药、受限烟花弹体和碰撞引爆完整链路。 */
	private void spawnFireworkProbe(final World world, final Location squadAnchor) {
		CombatProbePlacement placement = findCombatProbePlacement(
			world,
			squadAnchor.getBlockX() + 160,
			squadAnchor.getBlockZ(),
			FIREWORK_PROBE_TARGET_OFFSETS
		);
		if (placement == null) {
			throw new IllegalStateException("no flat collision-free firework probe lane found");
		}
		this.forceChunk(placement.zombie());
		this.forceChunk(placement.target());

		IronGolem target = (IronGolem)world.spawnEntity(placement.target(), EntityType.IRON_GOLEM);
		target.setAI(false);
		target.setPlayerCreated(true);
		target.setPersistent(false);
		setMaximumHealth(target, 500.0);
		this.activeEntities.add(target);

		AbstractSkeleton skeleton = (AbstractSkeleton)world.spawnEntity(placement.zombie(), EntityType.SKELETON);
		skeleton.setShouldBurnInDay(false);
		skeleton.setInvulnerable(true);
		skeleton.setPersistent(false);
		skeleton.setRemoveWhenFarAway(false);
		skeleton.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
		skeleton.getEquipment().setItemInOffHand(new ItemStack(Material.FIREWORK_ROCKET, 4));
		this.intelligence.set(skeleton, 10);
		skeleton.setTarget(target);
		this.fireworkProbeShooter = skeleton;
		this.activeEntities.add(skeleton);
	}

	/** 由 Paper 自己发出 NATURAL 出生事件；第二次显式初始化必须被 PDC 标记幂等拒绝。 */
	private void spawnNaturalLoadoutProbe(final World world, final Location squadAnchor) {
		Location location = safeSurface(
			world,
			squadAnchor.getBlockX() + 200,
			squadAnchor.getBlockZ()
		);
		this.forceChunk(location);
		Skeleton skeleton = world.spawn(
			location,
			Skeleton.class,
			CreatureSpawnEvent.SpawnReason.NATURAL,
			true,
			spawned -> {
				spawned.setShouldBurnInDay(false);
				spawned.setPersistent(false);
				spawned.setRemoveWhenFarAway(false);
			}
		);
		this.skeletonLoadouts.initialize(skeleton, CreatureSpawnEvent.SpawnReason.NATURAL);
		this.naturalLoadoutProbe = skeleton;
		this.activeEntities.add(skeleton);
	}

	private static CombatProbePlacement findCombatProbePlacement(final World world, final int originX, final int originZ) {
		return findCombatProbePlacement(world, originX, originZ, COMBAT_PROBE_TARGET_OFFSETS);
	}

	private static CombatProbePlacement findCombatProbePlacement(
		final World world,
		final int originX,
		final int originZ,
		final int[] targetOffsets
	) {
		for (int radius = 0; radius <= COMBAT_PROBE_SEARCH_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
						continue;
					}
					int zombieX = originX + dx;
					int zombieZ = originZ + dz;
					int feetY = world.getHighestBlockYAt(zombieX, zombieZ) + 1;
					for (int[] direction : CARDINAL_DIRECTIONS) {
					for (int targetOffset : targetOffsets) {
							int targetX = zombieX + direction[0] * targetOffset;
							int targetZ = zombieZ + direction[1] * targetOffset;
							if (world.getHighestBlockYAt(targetX, targetZ) + 1 != feetY
								|| !isClearStandingColumn(world, zombieX, feetY, zombieZ)
								|| !isClearStandingColumn(world, targetX, feetY, targetZ)) {
								continue;
							}
							Location zombie = new Location(world, zombieX + 0.5, feetY, zombieZ + 0.5);
							Location target = new Location(world, targetX + 0.5, feetY, targetZ + 0.5);
							if (!world.getWorldBorder().isInside(zombie)
								|| !world.getWorldBorder().isInside(target)
								|| !world.getNearbyEntities(zombie, 1.0, 1.5, 1.0).isEmpty()
								|| !world.getNearbyEntities(target, 1.0, 2.0, 1.0).isEmpty()) {
								continue;
							}
							return new CombatProbePlacement(zombie, target);
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean isClearStandingColumn(final World world, final int x, final int feetY, final int z) {
		Block floor = world.getBlockAt(x, feetY - 1, z);
		if (!floor.getType().isSolid()) {
			return false;
		}
		for (int offset = 0; offset <= 3; offset++) {
			Block block = world.getBlockAt(x, feetY + offset, z);
			if (!block.isPassable() || block.isLiquid()) {
				return false;
			}
		}
		return true;
	}

	private static void setMaximumHealth(final LivingEntity entity, final double health) {
		AttributeInstance maximumHealth = entity.getAttribute(Attribute.MAX_HEALTH);
		if (maximumHealth == null) {
			throw new IllegalStateException(entity.getType() + " has no maximum-health attribute");
		}
		maximumHealth.setBaseValue(health);
		entity.setHealth(health);
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

	private record CombatProbePlacement(Location zombie, Location target) {
	}
}

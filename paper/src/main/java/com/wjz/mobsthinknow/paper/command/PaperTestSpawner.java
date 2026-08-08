package com.wjz.mobsthinknow.paper.command;

import com.wjz.mobsthinknow.paper.ai.PaperIntelligenceService;
import com.wjz.mobsthinknow.paper.ai.PaperThreats;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** 管理员测试生成器：先规划全部安全位置，生成失败时事务式移除本批实体。 */
public final class PaperTestSpawner {
	public static final int MAXIMUM_SINGLE_TYPE_COUNT = 100;
	public static final int MAXIMUM_ASSAULT_GROUPS = 8;
	private static final int GRID_COLUMNS = 5;
	private static final double GRID_SPACING = 2.4;
	private static final int[] HEIGHT_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
	private static final Set<Material> HAZARDS = EnumSet.of(
		Material.LAVA,
		Material.FIRE,
		Material.SOUL_FIRE,
		Material.CAMPFIRE,
		Material.SOUL_CAMPFIRE,
		Material.CACTUS,
		Material.MAGMA_BLOCK,
		Material.POWDER_SNOW,
		Material.SWEET_BERRY_BUSH
	);
	private static final List<EntityType> ALL_SUPPORTED_TYPES = List.of(
		EntityType.ZOMBIE,
		EntityType.HUSK,
		EntityType.DROWNED,
		EntityType.ZOMBIE_VILLAGER,
		EntityType.SKELETON,
		EntityType.STRAY,
		EntityType.BOGGED,
		EntityType.WITHER_SKELETON,
		EntityType.CREEPER,
		EntityType.SPIDER
	);
	private static final List<EntityType> ASSAULT_GROUP = List.of(
		EntityType.ZOMBIE,
		EntityType.SKELETON,
		EntityType.CREEPER,
		EntityType.SPIDER
	);
	private static final Map<String, Preset> PRESETS = Map.of(
		"zombie_swordsman", new Preset(EntityType.ZOMBIE, Material.IRON_SWORD, null, 7),
		"zombie_axeman", new Preset(EntityType.ZOMBIE, Material.IRON_AXE, null, 10),
		"zombie_shieldguard", new Preset(EntityType.ZOMBIE, Material.IRON_SWORD, Material.SHIELD, 10),
		"skeleton_crossbow", new Preset(EntityType.SKELETON, Material.CROSSBOW, null, 10)
	);

	private final PaperIntelligenceService intelligence;

	public PaperTestSpawner(final PaperIntelligenceService intelligence) {
		this.intelligence = intelligence;
	}

	public Result spawnType(final Player player, final EntityType type, final int count) {
		return this.spawn(player, repeat(new SpawnSpec(type, null), count), false);
	}

	public Result spawnPreset(final Player player, final String name, final int count) {
		Preset preset = PRESETS.get(name);
		if (preset == null) {
			return new Result(0, count, true, "unknown preset: " + name);
		}
		return this.spawn(player, repeat(new SpawnSpec(preset.type(), preset), count), false);
	}

	public Result spawnAll(final Player player) {
		List<SpawnSpec> specs = new ArrayList<>(ALL_SUPPORTED_TYPES.size() + PRESETS.size());
		for (EntityType type : ALL_SUPPORTED_TYPES) {
			specs.add(new SpawnSpec(type, null));
		}
		PRESETS.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.map(preset -> new SpawnSpec(preset.type(), preset))
			.forEach(specs::add);
		return this.spawn(player, specs, false);
	}

	public Result spawnAssault(final Player player, final int groups) {
		List<SpawnSpec> types = new ArrayList<>(groups * ASSAULT_GROUP.size());
		for (int group = 0; group < groups; group++) {
			for (EntityType type : ASSAULT_GROUP) {
				types.add(new SpawnSpec(type, null));
			}
		}
		return this.spawn(player, types, true);
	}

	public static boolean isSupportedType(final EntityType type) {
		return ALL_SUPPORTED_TYPES.contains(type);
	}

	public static List<EntityType> supportedTypes() {
		return ALL_SUPPORTED_TYPES;
	}

	public static Set<String> presetNames() {
		return PRESETS.keySet();
	}

	private Result spawn(final Player player, final List<SpawnSpec> types, final boolean maximumIntelligence) {
		if (types.isEmpty()) {
			return new Result(0, 0, false, "empty batch");
		}
		List<Location> placements = this.planPlacements(player, types.size());
		if (placements.size() != types.size()) {
			return new Result(0, types.size(), true, "not enough collision-free ground near the command source");
		}

		List<Entity> spawned = new ArrayList<>(types.size());
		try {
			for (int index = 0; index < types.size(); index++) {
				SpawnSpec spec = types.get(index);
				Entity entity = player.getWorld().spawnEntity(placements.get(index), spec.type());
				spawned.add(entity);
				if (!(entity instanceof Mob mob) || !this.intelligence.supports(mob)) {
					throw new IllegalStateException("spawned entity is not supported: " + entity.getType());
				}
				if (spec.preset() != null) {
					this.configurePreset(mob, spec.preset());
				} else if (maximumIntelligence) {
					this.intelligence.set(mob, 10);
				} else {
					this.intelligence.ensure(mob);
				}
				if (PaperThreats.isLiveFor(mob, player)) {
					mob.setTarget(player);
				}
			}
			return new Result(spawned.size(), types.size(), false, "ok");
		} catch (RuntimeException exception) {
			for (Entity entity : spawned) {
				if (entity.isValid()) {
					entity.remove();
				}
			}
			return new Result(0, types.size(), true, exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
	}

	private void configurePreset(final Mob mob, final Preset preset) {
		mob.getEquipment().setItemInMainHand(new ItemStack(preset.weapon()));
		mob.getEquipment().setItemInMainHandDropChance(0.085F);
		if (preset.offhand() != null) {
			mob.getEquipment().setItemInOffHand(new ItemStack(preset.offhand()));
			mob.getEquipment().setItemInOffHandDropChance(0.085F);
		}
		this.intelligence.set(mob, preset.intelligence());
	}

	private List<Location> planPlacements(final Player player, final int count) {
		World world = player.getWorld();
		Vector forward = player.getLocation().getDirection().setY(0.0);
		if (forward.lengthSquared() < 1.0E-6) {
			forward = new Vector(0.0, 0.0, 1.0);
		} else {
			forward.normalize();
		}
		Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
		Location origin = player.getLocation().clone().add(forward.clone().multiply(6.0));
		List<Location> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			int row = index / GRID_COLUMNS;
			int column = index % GRID_COLUMNS;
			double centeredColumn = column - (Math.min(count, GRID_COLUMNS) - 1) * 0.5;
			Location preferred = origin.clone()
				.add(forward.clone().multiply(row * GRID_SPACING))
				.add(right.clone().multiply(centeredColumn * GRID_SPACING));
			Location safe = findSafe(world, preferred, result);
			if (safe == null) {
				return List.of();
			}
			result.add(safe);
		}
		return result;
	}

	private static Location findSafe(
		final World world,
		final Location preferred,
		final List<Location> reserved
	) {
		int baseY = preferred.getBlockY();
		for (int offset : HEIGHT_OFFSETS) {
			int feetY = baseY + offset;
			Location candidate = new Location(
				world,
				preferred.getBlockX() + 0.5,
				feetY,
				preferred.getBlockZ() + 0.5,
				preferred.getYaw(),
				0.0F
			);
			if (!world.getWorldBorder().isInside(candidate) || tooCloseToReserved(candidate, reserved)) {
				continue;
			}
			Block feet = world.getBlockAt(candidate);
			Block head = world.getBlockAt(feet.getX(), feetY + 1, feet.getZ());
			Block floor = world.getBlockAt(feet.getX(), feetY - 1, feet.getZ());
			if (isOpen(feet)
				&& isOpen(head)
				&& floor.getType().isSolid()
				&& !HAZARDS.contains(floor.getType())
				&& world.getNearbyEntities(candidate, 0.9, 1.3, 0.9).isEmpty()) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean tooCloseToReserved(final Location candidate, final List<Location> reserved) {
		for (Location previous : reserved) {
			if (candidate.distanceSquared(previous) < 2.0 * 2.0) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOpen(final Block block) {
		return block.isPassable() && !block.isLiquid() && !HAZARDS.contains(block.getType());
	}

	private static <T> List<T> repeat(final T value, final int count) {
		List<T> result = new ArrayList<>(count);
		for (int index = 0; index < count; index++) {
			result.add(value);
		}
		return result;
	}

	private record SpawnSpec(EntityType type, Preset preset) {
	}

	private record Preset(EntityType type, Material weapon, Material offhand, int intelligence) {
	}

	public record Result(int spawned, int requested, boolean rolledBack, String detail) {
	}
}

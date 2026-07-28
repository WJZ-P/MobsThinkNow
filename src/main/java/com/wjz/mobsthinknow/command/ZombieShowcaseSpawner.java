package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.zombie.ZombieAirAssault;
import com.wjz.mobsthinknow.ai.zombie.ZombieBuilderInventoryAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieEngineerProfile;
import com.wjz.mobsthinknow.ai.zombie.ZombieFlightAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierAccess;
import com.wjz.mobsthinknow.ai.zombie.ZombieFluidCarrierState;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieShieldDesign;
import com.wjz.mobsthinknow.ai.zombie.ZombieSpecialEquipment;
import com.wjz.mobsthinknow.ai.zombie.squad.UtilityClass;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 为管理命令生成一组可直接观察的战术僵尸样本。
 *
 * <p>这里生成的是当前 Mod 的“装备/职责兵种”，而不是尸壳、溺尸或僵尸村民等原版实体变种；
 * 后三者目前没有注入本 Mod 的普通僵尸 AI。觅食和受击撤退属于所有合格僵尸的临场状态，
 * 因此也不会为了凑数重复生成一个静态样本。</p>
 */
public final class ZombieShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 100;
	private static final double GRID_SPACING = 3.0;
	private static final double FORMATION_FRONT_DISTANCE = 5.0;
	private static final double SINGLE_SPAWN_DISTANCE = 4.0;
	private static final int[] VERTICAL_SEARCH = {0, 1, -1, 2, -2, 3, -3, 4, -4};
	private static final int[][] LOCAL_OFFSETS = {
		{0, 0},
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};
	private static final List<EquipmentSlot> HUMANOID_EQUIPMENT = List.of(
		EquipmentSlot.MAINHAND,
		EquipmentSlot.OFFHAND,
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	);

	private ZombieShowcaseSpawner() {
	}

	/**
	 * 在命令源面前排出 3×3 阵型。所有落点先统一预检，空间不足时一只也不生成，
	 * 避免玩家只拿到残缺阵容；真正加入世界时若出现异常，同样回滚已经加入的实体。
	 */
	public static SpawnResult spawnAll(final CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}

		List<ShowcaseArchetype> archetypes = List.of(ShowcaseArchetype.values());
		List<BlockPos> positions = findFormation(level, source.getPosition(), source.getRotation().y, archetypes.size());
		if (positions.size() != archetypes.size()) {
			return SpawnResult.failed(Failure.NO_SPACE);
		}

		return prepareAndSpawn(level, source.getPosition(), archetypes, positions);
	}

	/**
	 * 在命令源正前方生成一个指定兵种。落点仍经过地基、碰撞、流体、世界边界与区块加载检查，
	 * 因此单兵指令不会把僵尸塞进墙内或为了寻找地面强制加载新区块。
	 */
	public static SpawnResult spawnOne(final CommandSourceStack source, final ShowcaseArchetype archetype) {
		return spawnBatch(source, archetype, 1);
	}

	/**
	 * 批量生成同一兵种。数量为一时保持原来的单兵落点；数量更大时按近似正方形阵型排开。
	 * 所有落点与实体都会先准备完毕，任一步失败便整批取消，避免只生成请求数量的一部分。
	 */
	public static SpawnResult spawnBatch(
		final CommandSourceStack source,
		final ShowcaseArchetype archetype,
		final int count
	) {
		if (count < 1 || count > MAX_BATCH_SIZE) {
			throw new IllegalArgumentException("Batch count must be between 1 and " + MAX_BATCH_SIZE + ".");
		}

		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}

		List<BlockPos> positions;
		if (count == 1) {
			double radians = Math.toRadians(source.getRotation().y);
			Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
			Vec3 preferred = source.getPosition().add(forward.scale(SINGLE_SPAWN_DISTANCE));
			@Nullable BlockPos feet = findSafeFeet(level, preferred, source.getPosition().y, List.of());
			positions = feet == null ? List.of() : List.of(feet);
		} else {
			positions = findFormation(level, source.getPosition(), source.getRotation().y, count);
		}
		if (positions.size() != count) {
			return SpawnResult.failed(Failure.NO_SPACE);
		}

		return prepareAndSpawn(
			level,
			source.getPosition(),
			Collections.nCopies(count, archetype),
			positions
		);
	}

	private static SpawnResult prepareAndSpawn(
		final ServerLevel level,
		final Vec3 faceToward,
		final List<ShowcaseArchetype> archetypes,
		final List<BlockPos> positions
	) {
		List<PreparedZombie> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			ShowcaseArchetype archetype = archetypes.get(index);
			Zombie zombie = createZombie(level, positions.get(index), faceToward, archetype);
			if (zombie == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(new PreparedZombie(archetype, zombie));
		}

		List<SpawnedZombie> spawned = new ArrayList<>(prepared.size());
		for (PreparedZombie entry : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(entry.zombie())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
			spawned.add(new SpawnedZombie(entry.archetype(), entry.zombie()));
		}
		return SpawnResult.succeeded(spawned);
	}

	private static List<BlockPos> findFormation(
		final ServerLevel level,
		final Vec3 origin,
		final float yaw,
		final int count
	) {
		double radians = Math.toRadians(yaw);
		Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
		Vec3 lateral = new Vec3(Math.cos(radians), 0.0, Math.sin(radians));
		int columns = (int)Math.ceil(Math.sqrt(count));
		List<BlockPos> positions = new ArrayList<>(count);
		List<AABB> reservedBoxes = new ArrayList<>(count);

		for (int index = 0; index < count; index++) {
			int row = index / columns;
			int rowStart = row * columns;
			int rowSize = Math.min(columns, count - rowStart);
			int column = index - rowStart;
			double lateralOffset = (column - (rowSize - 1) * 0.5) * GRID_SPACING;
			Vec3 preferred = origin
				.add(forward.scale(FORMATION_FRONT_DISTANCE + row * GRID_SPACING))
				.add(lateral.scale(lateralOffset));
			@Nullable BlockPos safe = findSafeFeet(level, preferred, origin.y, reservedBoxes);
			if (safe == null) {
				return List.of();
			}
			positions.add(safe);
			reservedBoxes.add(spawnBox(safe));
		}
		return List.copyOf(positions);
	}

	private static @Nullable BlockPos findSafeFeet(
		final ServerLevel level,
		final Vec3 preferred,
		final double originY,
		final List<AABB> reservedBoxes
	) {
		BlockPos horizontal = BlockPos.containing(preferred.x, originY, preferred.z);
		for (int[] offset : LOCAL_OFFSETS) {
			for (int dy : VERTICAL_SEARCH) {
				BlockPos candidate = horizontal.offset(offset[0], dy, offset[1]);
				if (isSafeFeet(level, candidate, reservedBoxes)) {
					return candidate.immutable();
				}
			}
		}

		// 飞行中的玩家或洞内命令源附近没有地面时，最后尝试同一阵型列的世界表面。
		if (!isChunkLoaded(level, horizontal)) {
			return null;
		}
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal);
		for (int[] offset : LOCAL_OFFSETS) {
			BlockPos candidate = surface.offset(offset[0], 0, offset[1]);
			if (isSafeFeet(level, candidate, reservedBoxes)) {
				return candidate.immutable();
			}
		}
		return null;
	}

	private static boolean isSafeFeet(
		final ServerLevel level,
		final BlockPos feet,
		final List<AABB> reservedBoxes
	) {
		if (!Level.isInSpawnableBounds(feet) || !isChunkLoaded(level, feet)) {
			return false;
		}
		BlockPos support = feet.below();
		if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
			|| !level.getBlockState(feet).getFluidState().isEmpty()
			|| !level.getBlockState(feet.above()).getFluidState().isEmpty()) {
			return false;
		}

		AABB box = spawnBox(feet);
		if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(box)) {
			return false;
		}
		return reservedBoxes.stream().noneMatch(box::intersects);
	}

	private static AABB spawnBox(final BlockPos feet) {
		return EntityType.ZOMBIE.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
	}

	private static boolean isChunkLoaded(final ServerLevel level, final BlockPos pos) {
		return level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ())
		);
	}

	private static @Nullable Zombie createZombie(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		if (zombie == null) {
			return null;
		}

		double x = feet.getX() + 0.5;
		double z = feet.getZ() + 0.5;
		float yRot = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		zombie.snapTo(x, feet.getY(), z, Mth.wrapDegrees(yRot), 0.0F);
		zombie.setYBodyRot(zombie.getYRot());
		zombie.setYHeadRot(zombie.getYRot());
		zombie.finalizeSpawn(
			level,
			level.getCurrentDifficultyAt(feet),
			EntitySpawnReason.COMMAND,
			new Zombie.ZombieGroupData(false, false)
		);
		configureLoadout(level, zombie, archetype);
		return zombie;
	}

	private static void configureLoadout(
		final ServerLevel level,
		final Zombie zombie,
		final ShowcaseArchetype archetype
	) {
		// finalizeSpawn 会正确安装随机个体属性与 Mod Goal；随后只清理随机出生装备，换成确定样本。
		zombie.stopUsingItem();
		((ZombieFlightAccess)zombie).mobsthinknow$stopFallFlying();
		for (EquipmentSlot slot : HUMANOID_EQUIPMENT) {
			zombie.setItemSlot(slot, ItemStack.EMPTY);
		}
		((ZombieFluidCarrierAccess)zombie).mobsthinknow$setFluidCarrierState(ZombieFluidCarrierState.NONE);
		((ZombieBuilderInventoryAccess)zombie).mobsthinknow$setBuildingBlocks(ItemStack.EMPTY);
		// finalizeSpawn 可能自然掷中工程兵；展示命令必须把职业重置为确定结果。
		ZombieEngineerProfile.setEngineer(zombie, false);
		zombie.setBaby(false);
		zombie.setPersistenceRequired();
		zombie.setHealth(zombie.getMaxHealth());

		MobsThinkNowConfig config = ConfigManager.get();
		switch (archetype) {
			case UNARMED -> {
			}
			case SWORDSMAN -> equip(zombie, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			case AXEMAN -> equip(zombie, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			case SWORD_SHIELD -> {
				equip(zombie, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
				equip(zombie, EquipmentSlot.OFFHAND, ZombieShieldDesign.create(zombie.registryAccess()));
			}
			case AXE_SHIELD -> {
				equip(zombie, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
				equip(zombie, EquipmentSlot.OFFHAND, ZombieShieldDesign.create(zombie.registryAccess()));
			}
			case BUILDER -> {
				int blocks = Math.max(1, config.terrainBlockInventoryLimit);
				((ZombieBuilderInventoryAccess)zombie).mobsthinknow$setBuildingBlocks(new ItemStack(Items.DIRT, blocks));
				ZombieEngineerProfile.setEngineer(zombie, true);
			}
			case WATER_SUPPORT -> {
				ZombieSpecialEquipment.markRecovered(
					zombie,
					UtilityClass.WATER,
					new ItemStack(Items.WATER_BUCKET),
					level.getGameTime()
				);
				zombie.setDropChance(EquipmentSlot.MAINHAND, (float)config.specialEquipmentDropChance);
				ZombieEngineerProfile.setEngineer(zombie, true);
			}
			case LAVA_HARASSER -> {
				ZombieSpecialEquipment.markRecovered(
					zombie,
					UtilityClass.LAVA,
					new ItemStack(Items.LAVA_BUCKET),
					level.getGameTime()
				);
				zombie.setDropChance(EquipmentSlot.MAINHAND, (float)config.specialEquipmentDropChance);
				ZombieEngineerProfile.setEngineer(zombie, true);
			}
			case AIR_ASSAULT -> {
				equip(zombie, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SPEAR));
				equip(zombie, EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
				int rockets = ZombieAirAssault.rocketCount(level.getDifficulty(), zombie.getRandom().nextDouble());
				equip(zombie, EquipmentSlot.OFFHAND, new ItemStack(Items.FIREWORK_ROCKET, rockets));
			}
		}

		zombie.setCustomName(archetype.displayName());
		ZombieIntelligence.set(zombie, archetype.intelligence());
		zombie.setCustomNameVisible(true);
	}

	private static void equip(final Zombie zombie, final EquipmentSlot slot, final ItemStack stack) {
		zombie.setItemSlot(slot, stack);
		zombie.setDropChance(slot, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
	}

	private static void discardPrepared(final List<PreparedZombie> prepared) {
		for (PreparedZombie entry : prepared) {
			entry.zombie().discard();
		}
	}

	/** 以 enum 顺序映射到三行阵型，确保命令每次给出相同的观察布局。 */
	public enum ShowcaseArchetype {
		UNARMED("mobsthinknow.showcase.unarmed", "Unarmed Zombie", ChatFormatting.GRAY, 3),
		SWORDSMAN("mobsthinknow.showcase.swordsman", "Zombie Swordsman", ChatFormatting.AQUA, 6),
		AXEMAN("mobsthinknow.showcase.axeman", "Zombie Axeman", ChatFormatting.RED, 7),
		SWORD_SHIELD("mobsthinknow.showcase.sword_shield", "Sword-and-Shield Zombie", ChatFormatting.BLUE, 7),
		AXE_SHIELD("mobsthinknow.showcase.axe_shield", "Axe-and-Shield Zombie", ChatFormatting.DARK_RED, 8),
		BUILDER("mobsthinknow.showcase.builder", "Zombie Engineer", ChatFormatting.GREEN, 10),
		WATER_SUPPORT("mobsthinknow.showcase.water_support", "Water Support Zombie", ChatFormatting.DARK_AQUA, 8),
		LAVA_HARASSER("mobsthinknow.showcase.lava_harasser", "Lava Harasser Zombie", ChatFormatting.GOLD, 9),
		AIR_ASSAULT("mobsthinknow.showcase.air_assault", "Spear Air-Assault Zombie", ChatFormatting.LIGHT_PURPLE, 10);

		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final int intelligence;

		ShowcaseArchetype(
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence
		) {
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.intelligence = intelligence;
		}

		public Component displayName() {
			return Component.translatableWithFallback(this.translationKey, this.fallback).withStyle(this.color);
		}

		/** Brigadier 子命令使用稳定、纯 ASCII 的小写枚举名，方便输入、补全和命令方块调用。 */
		public String commandId() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public int intelligence() {
			return this.intelligence;
		}
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnedZombie(ShowcaseArchetype archetype, Zombie zombie) {
	}

	public record SpawnResult(List<SpawnedZombie> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedZombie> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	private record PreparedZombie(ShowcaseArchetype archetype, Zombie zombie) {
	}
}

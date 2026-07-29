package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.skeleton.SkeletonCrossbowLoadout;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 为管理指令生成可稳定复现的普通骷髅远程兵种。 */
public final class SkeletonShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 100;
	private static final int SHOWCASE_FIREWORKS = 6;
	private static final List<EquipmentSlot> HUMANOID_EQUIPMENT = List.of(
		EquipmentSlot.MAINHAND,
		EquipmentSlot.OFFHAND,
		EquipmentSlot.HEAD,
		EquipmentSlot.CHEST,
		EquipmentSlot.LEGS,
		EquipmentSlot.FEET
	);

	private SkeletonShowcaseSpawner() {
	}

	/** 在命令源前方一次生成弓手、弩手和爆炸烟花弩手。 */
	public static SpawnResult spawnAll(final CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}

		List<ShowcaseArchetype> archetypes = List.of(ShowcaseArchetype.values());
		List<BlockPos> positions = ShowcaseSpawnPlacement.findFormation(
			level,
			source.getPosition(),
			source.getRotation().y,
			archetypes.size(),
			EntityType.SKELETON
		);
		if (positions.size() != archetypes.size()) {
			return SpawnResult.failed(Failure.NO_SPACE);
		}
		return prepareAndSpawn(level, source.getPosition(), archetypes, positions);
	}

	/** 按近似方阵批量生成同一种骷髅；落点与实体全部准备好后才作为事务加入世界。 */
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
		List<BlockPos> positions = ShowcaseSpawnPlacement.findBatch(
			level,
			source.getPosition(),
			source.getRotation().y,
			count,
			EntityType.SKELETON
		);
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
		List<PreparedSkeleton> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			ShowcaseArchetype archetype = archetypes.get(index);
			Skeleton skeleton = createSkeleton(level, positions.get(index), faceToward, archetype);
			if (skeleton == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(new PreparedSkeleton(archetype, skeleton));
		}

		List<SpawnedSkeleton> spawned = new ArrayList<>(prepared.size());
		for (PreparedSkeleton entry : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(entry.skeleton())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
			spawned.add(new SpawnedSkeleton(entry.archetype(), entry.skeleton()));
		}
		return SpawnResult.succeeded(spawned);
	}

	private static Skeleton createSkeleton(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		Skeleton skeleton = EntityType.SKELETON.create(level, EntitySpawnReason.COMMAND);
		if (skeleton == null) {
			return null;
		}

		double x = feet.getX() + 0.5;
		double z = feet.getZ() + 0.5;
		float yaw = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		skeleton.snapTo(x, feet.getY(), z, Mth.wrapDegrees(yaw), 0.0F);
		skeleton.setYBodyRot(skeleton.getYRot());
		skeleton.setYHeadRot(skeleton.getYRot());
		skeleton.finalizeSpawn(
			level,
			level.getCurrentDifficultyAt(feet),
			EntitySpawnReason.COMMAND,
			null
		);
		configureLoadout(skeleton, archetype);
		return skeleton;
	}

	private static void configureLoadout(final Skeleton skeleton, final ShowcaseArchetype archetype) {
		skeleton.stopUsingItem();
		for (EquipmentSlot slot : HUMANOID_EQUIPMENT) {
			skeleton.setItemSlot(slot, ItemStack.EMPTY);
		}
		skeleton.setPersistenceRequired();
		skeleton.setHealth(skeleton.getMaxHealth());

		switch (archetype) {
			case BOW -> equip(skeleton, EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			case CROSSBOW -> equip(skeleton, EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
			case FIREWORK_CROSSBOW -> {
				equip(skeleton, EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
				equip(
					skeleton,
					EquipmentSlot.OFFHAND,
					SkeletonCrossbowLoadout.explosiveRockets(SHOWCASE_FIREWORKS)
				);
			}
		}

		skeleton.setCustomName(archetype.displayName());
		SkeletonIntelligence.set(skeleton, archetype.intelligence());
		skeleton.setCustomNameVisible(true);
		skeleton.reassessWeaponGoal();
	}

	private static void equip(final Skeleton skeleton, final EquipmentSlot slot, final ItemStack stack) {
		skeleton.setItemSlot(slot, stack);
		skeleton.setDropChance(slot, DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE);
	}

	private static void discardPrepared(final List<PreparedSkeleton> prepared) {
		for (PreparedSkeleton entry : prepared) {
			entry.skeleton().discard();
		}
	}

	public enum ShowcaseArchetype {
		BOW("skeleton_bow", "mobsthinknow.showcase.skeleton_bow", "Skeleton Bowman", ChatFormatting.GRAY, 5),
		CROSSBOW("skeleton_crossbow", "mobsthinknow.showcase.skeleton_crossbow", "Skeleton Crossbowman", ChatFormatting.AQUA, 8),
		FIREWORK_CROSSBOW(
			"skeleton_firework_crossbow",
			"mobsthinknow.showcase.skeleton_firework_crossbow",
			"Firework Crossbow Skeleton",
			ChatFormatting.LIGHT_PURPLE,
			10
		);

		private final String commandId;
		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final int intelligence;

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence
		) {
			this.commandId = commandId;
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.intelligence = intelligence;
		}

		public String commandId() {
			return this.commandId;
		}

		public Component displayName() {
			return Component.translatableWithFallback(this.translationKey, this.fallback).withStyle(this.color);
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

	public record SpawnedSkeleton(ShowcaseArchetype archetype, Skeleton skeleton) {
	}

	public record SpawnResult(List<SpawnedSkeleton> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedSkeleton> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	private record PreparedSkeleton(ShowcaseArchetype archetype, Skeleton skeleton) {
	}
}

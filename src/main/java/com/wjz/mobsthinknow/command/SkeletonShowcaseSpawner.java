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
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** 为管理指令生成可稳定复现的骷髅家族远程兵种与主世界变种。 */
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

	/** 在命令源前方生成三种普通骷髅战术装备，以及流浪者、沼骸和干尸。 */
	public static SpawnResult spawnAll(final CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}

		List<ShowcaseArchetype> archetypes = List.of(ShowcaseArchetype.values());
		List<BlockPos> positions = ShowcaseSpawnPlacement.findMixedFormation(
			level,
			source.getPosition(),
			source.getRotation().y,
			archetypes.stream().<EntityType<?>>map(ShowcaseArchetype::entityType).toList()
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
			archetype.entityType()
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
			AbstractSkeleton skeleton = createSkeleton(level, positions.get(index), faceToward, archetype);
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

	static AbstractSkeleton createSkeleton(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		AbstractSkeleton skeleton = archetype.entityType().create(level, EntitySpawnReason.COMMAND);
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

	private static void configureLoadout(final AbstractSkeleton skeleton, final ShowcaseArchetype archetype) {
		skeleton.setPersistenceRequired();
		skeleton.setHealth(skeleton.getMaxHealth());
		if (archetype.isVanillaVariant()) {
			// 保留流浪者减速箭、沼骸毒箭和干尸虚弱箭，只固定测试所需的智力与名称。
			skeleton.setCustomName(archetype.displayName());
			SkeletonIntelligence.set(skeleton, archetype.intelligence());
			skeleton.setCustomNameVisible(true);
			return;
		}
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

	private static void equip(final AbstractSkeleton skeleton, final EquipmentSlot slot, final ItemStack stack) {
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
		),
		STRAY("stray", "mobsthinknow.showcase.stray", "Stray", ChatFormatting.AQUA, 7, EntityType.STRAY),
		BOGGED("bogged", "mobsthinknow.showcase.bogged", "Bogged", ChatFormatting.DARK_GREEN, 8, EntityType.BOGGED),
		PARCHED("parched", "mobsthinknow.showcase.parched", "Parched", ChatFormatting.GOLD, 9, EntityType.PARCHED);

		private final String commandId;
		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final int intelligence;
		private final EntityType<? extends AbstractSkeleton> entityType;

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence
		) {
			this(commandId, translationKey, fallback, color, intelligence, EntityType.SKELETON);
		}

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence,
			final EntityType<? extends AbstractSkeleton> entityType
		) {
			this.commandId = commandId;
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.intelligence = intelligence;
			this.entityType = entityType;
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

		public EntityType<? extends AbstractSkeleton> entityType() {
			return this.entityType;
		}

		public boolean isVanillaVariant() {
			return this.entityType != EntityType.SKELETON;
		}
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnedSkeleton(ShowcaseArchetype archetype, AbstractSkeleton skeleton) {
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

	private record PreparedSkeleton(ShowcaseArchetype archetype, AbstractSkeleton skeleton) {
	}
}

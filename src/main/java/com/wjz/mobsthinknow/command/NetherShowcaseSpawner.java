package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.nether.NetherProfession;
import com.wjz.mobsthinknow.ai.nether.NetherProfessionProfile;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 为下界智能 AI 提供可批量复现的二十种职业测试预设。 */
public final class NetherShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 100;

	private NetherShowcaseSpawner() {
	}

	public static SpawnResult spawnAll(final CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}
		List<ShowcaseArchetype> archetypes = List.of(ShowcaseArchetype.values());
		List<EntityType<?>> types = new ArrayList<>(archetypes.size());
		for (ShowcaseArchetype archetype : archetypes) {
			types.add(archetype.entityType());
		}
		List<BlockPos> positions = ShowcaseSpawnPlacement.findMixedFormation(
			level,
			source.getPosition(),
			source.getRotation().y,
			types
		);
		if (positions.size() != archetypes.size()) {
			return SpawnResult.failed(Failure.NO_SPACE);
		}
		return prepareAndSpawn(level, source.getPosition(), archetypes, positions);
	}

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
		return prepareAndSpawn(level, source.getPosition(), Collections.nCopies(count, archetype), positions);
	}

	private static SpawnResult prepareAndSpawn(
		final ServerLevel level,
		final Vec3 faceToward,
		final List<ShowcaseArchetype> archetypes,
		final List<BlockPos> positions
	) {
		List<SpawnedNetherMob> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			ShowcaseArchetype archetype = archetypes.get(index);
			Mob mob = createMob(level, positions.get(index), faceToward, archetype);
			if (mob == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(new SpawnedNetherMob(archetype, mob));
		}
		for (SpawnedNetherMob entry : prepared) {
			if (!level.addFreshEntity(entry.mob())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
		}
		return SpawnResult.succeeded(prepared);
	}

	static @Nullable Mob createMob(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		Mob mob = archetype.entityType().create(level, EntitySpawnReason.COMMAND);
		if (mob == null) {
			return null;
		}
		double x = feet.getX() + 0.5;
		double y = feet.getY() + archetype.verticalOffset();
		double z = feet.getZ() + 0.5;
		float yaw = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		mob.snapTo(x, y, z, Mth.wrapDegrees(yaw), 0.0F);
		mob.setYBodyRot(mob.getYRot());
		mob.setYHeadRot(mob.getYRot());
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.COMMAND, null);

		if (mob instanceof AbstractPiglin piglin) {
			// 测试指令常在主世界使用；预设应保持猪灵身份，而不是五秒后因维度自动僵尸化。
			piglin.setImmuneToZombification(true);
		}
		if (mob instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
		if (mob.getType() == EntityType.PIGLIN) {
			ItemStack weapon = switch (archetype.profession()) {
				case PIGLIN_MARKSMAN -> new ItemStack(Items.CROSSBOW);
				case PIGLIN_COMMANDER -> new ItemStack(Items.GOLDEN_AXE);
				default -> new ItemStack(Items.GOLDEN_SWORD);
			};
			mob.setItemSlot(EquipmentSlot.MAINHAND, weapon);
		}
		if (mob instanceof MagmaCube cube) {
			int size = switch (archetype.profession()) {
				case MAGMA_AMBUSHER -> 2;
				case MAGMA_TITAN -> 4;
				default -> 3;
			};
			cube.setSize(size, true);
		}
		NetherProfessionProfile.set(mob, archetype.profession());

		mob.setPersistenceRequired();
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(archetype.displayName());
		mob.setCustomNameVisible(true);
		return mob;
	}

	private static void discardPrepared(final List<SpawnedNetherMob> prepared) {
		prepared.forEach(entry -> entry.mob().discard());
	}

	public enum ShowcaseArchetype {
		PIGLIN_CROSSBOW("piglin_crossbow", "mobsthinknow.showcase.piglin_crossbow", "Piglin Marksman", ChatFormatting.AQUA, EntityType.PIGLIN, NetherProfession.PIGLIN_MARKSMAN, 0.0),
		PIGLIN_VANGUARD("piglin_vanguard", "mobsthinknow.showcase.piglin_vanguard", "Piglin Vanguard", ChatFormatting.RED, EntityType.PIGLIN, NetherProfession.PIGLIN_VANGUARD, 0.0),
		PIGLIN_COMMANDER("piglin_commander", "mobsthinknow.showcase.piglin_commander", "Piglin Commander", ChatFormatting.GOLD, EntityType.PIGLIN, NetherProfession.PIGLIN_COMMANDER, 0.0),
		PIGLIN_BRUTE("piglin_brute", "mobsthinknow.showcase.piglin_brute", "Piglin Brute Vanguard", ChatFormatting.DARK_RED, EntityType.PIGLIN_BRUTE, NetherProfession.PIGLIN_VANGUARD, 0.0),
		PIGLIN_BRUTE_COMMANDER("piglin_brute_commander", "mobsthinknow.showcase.piglin_brute_commander", "Piglin Brute Commander", ChatFormatting.DARK_PURPLE, EntityType.PIGLIN_BRUTE, NetherProfession.PIGLIN_COMMANDER, 0.0),

		HOGLIN_CHARGER("hoglin_charger", "mobsthinknow.showcase.hoglin_charger", "Hoglin Charger", ChatFormatting.RED, EntityType.HOGLIN, NetherProfession.HOGLIN_CHARGER, 0.0),
		HOGLIN_BULWARK("hoglin_bulwark", "mobsthinknow.showcase.hoglin_bulwark", "Hoglin Bulwark", ChatFormatting.GOLD, EntityType.HOGLIN, NetherProfession.HOGLIN_BULWARK, 0.0),
		HOGLIN_RAVAGER("hoglin_ravager", "mobsthinknow.showcase.hoglin_ravager", "Hoglin Ravager", ChatFormatting.DARK_RED, EntityType.HOGLIN, NetherProfession.HOGLIN_RAVAGER, 0.0),
		ZOGLIN_CHARGER("zoglin_charger", "mobsthinknow.showcase.zoglin_charger", "Zoglin Charger", ChatFormatting.DARK_GREEN, EntityType.ZOGLIN, NetherProfession.HOGLIN_CHARGER, 0.0),
		ZOGLIN_BULWARK("zoglin_bulwark", "mobsthinknow.showcase.zoglin_bulwark", "Zoglin Bulwark", ChatFormatting.GRAY, EntityType.ZOGLIN, NetherProfession.HOGLIN_BULWARK, 0.0),
		ZOGLIN_RAVAGER("zoglin_ravager", "mobsthinknow.showcase.zoglin_ravager", "Zoglin Ravager", ChatFormatting.DARK_RED, EntityType.ZOGLIN, NetherProfession.HOGLIN_RAVAGER, 0.0),

		BLAZE_SKIRMISHER("blaze_skirmisher", "mobsthinknow.showcase.blaze_skirmisher", "Blaze Skirmisher", ChatFormatting.YELLOW, EntityType.BLAZE, NetherProfession.BLAZE_SKIRMISHER, 1.0),
		BLAZE_VOLLEYMASTER("blaze_volleymaster", "mobsthinknow.showcase.blaze_volleymaster", "Blaze Volleymaster", ChatFormatting.LIGHT_PURPLE, EntityType.BLAZE, NetherProfession.BLAZE_VOLLEYMASTER, 1.0),
		BLAZE_CINDER_GUARD("blaze_cinder_guard", "mobsthinknow.showcase.blaze_cinder_guard", "Blaze Cinder Guard", ChatFormatting.GOLD, EntityType.BLAZE, NetherProfession.BLAZE_CINDER_GUARD, 1.0),

		GHAST_ARTILLERY("ghast_artillery", "mobsthinknow.showcase.ghast_artillery", "Ghast Artillery", ChatFormatting.WHITE, EntityType.GHAST, NetherProfession.GHAST_ARTILLERY, 4.0),
		GHAST_SPOTTER("ghast_spotter", "mobsthinknow.showcase.ghast_spotter", "Ghast Spotter", ChatFormatting.AQUA, EntityType.GHAST, NetherProfession.GHAST_SPOTTER, 4.0),
		GHAST_SIEGEBREAKER("ghast_siegebreaker", "mobsthinknow.showcase.ghast_siegebreaker", "Ghast Siegebreaker", ChatFormatting.DARK_RED, EntityType.GHAST, NetherProfession.GHAST_SIEGEBREAKER, 4.0),

		MAGMA_CUBE_HUNTER("magma_cube_hunter", "mobsthinknow.showcase.magma_cube_hunter", "Magma Cube Hunter", ChatFormatting.DARK_RED, EntityType.MAGMA_CUBE, NetherProfession.MAGMA_HUNTER, 0.0),
		MAGMA_CUBE_AMBUSHER("magma_cube_ambusher", "mobsthinknow.showcase.magma_cube_ambusher", "Magma Cube Ambusher", ChatFormatting.LIGHT_PURPLE, EntityType.MAGMA_CUBE, NetherProfession.MAGMA_AMBUSHER, 0.0),
		MAGMA_CUBE_TITAN("magma_cube_titan", "mobsthinknow.showcase.magma_cube_titan", "Magma Cube Titan", ChatFormatting.GOLD, EntityType.MAGMA_CUBE, NetherProfession.MAGMA_TITAN, 0.0);

		private final String commandId;
		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final EntityType<? extends Mob> entityType;
		private final NetherProfession profession;
		private final double verticalOffset;

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final EntityType<? extends Mob> entityType,
			final NetherProfession profession,
			final double verticalOffset
		) {
			this.commandId = commandId;
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.entityType = entityType;
			this.profession = profession;
			this.verticalOffset = verticalOffset;
		}

		public String commandId() {
			return this.commandId;
		}

		public Component displayName() {
			return Component.translatableWithFallback(this.translationKey, this.fallback).withStyle(this.color);
		}

		public EntityType<? extends Mob> entityType() {
			return this.entityType;
		}

		public NetherProfession profession() {
			return this.profession;
		}

		double verticalOffset() {
			return this.verticalOffset;
		}
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnedNetherMob(ShowcaseArchetype archetype, Mob mob) {
	}

	public record SpawnResult(List<SpawnedNetherMob> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedNetherMob> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}
}

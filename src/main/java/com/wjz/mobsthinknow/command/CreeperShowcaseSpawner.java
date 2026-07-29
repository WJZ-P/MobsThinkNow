package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.creeper.CreeperPowerAccess;
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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;

/** 为管理指令生成可稳定复现的猎手、绕后手、破墙手与带电破墙手。 */
public final class CreeperShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 100;

	private CreeperShowcaseSpawner() {
	}

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
			EntityType.CREEPER
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
			EntityType.CREEPER
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
		List<PreparedCreeper> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			ShowcaseArchetype archetype = archetypes.get(index);
			Creeper creeper = createCreeper(level, positions.get(index), faceToward, archetype);
			if (creeper == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(new PreparedCreeper(archetype, creeper));
		}

		List<SpawnedCreeper> spawned = new ArrayList<>(prepared.size());
		for (PreparedCreeper entry : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(entry.creeper())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
			spawned.add(new SpawnedCreeper(entry.archetype(), entry.creeper()));
		}
		return SpawnResult.succeeded(spawned);
	}

	private static Creeper createCreeper(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		Creeper creeper = EntityType.CREEPER.create(level, EntitySpawnReason.COMMAND);
		if (creeper == null) {
			return null;
		}
		double x = feet.getX() + 0.5;
		double z = feet.getZ() + 0.5;
		float yaw = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		creeper.snapTo(x, feet.getY(), z, Mth.wrapDegrees(yaw), 0.0F);
		creeper.setYBodyRot(creeper.getYRot());
		creeper.setYHeadRot(creeper.getYRot());
		creeper.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.COMMAND, null);
		creeper.setPersistenceRequired();
		creeper.setHealth(creeper.getMaxHealth());
		creeper.setCustomName(archetype.displayName());
		CreeperIntelligence.set(creeper, archetype.intelligence());
		((CreeperPowerAccess)creeper).mobsthinknow$setCreeperPowered(archetype.powered());
		creeper.setCustomNameVisible(true);
		return creeper;
	}

	private static void discardPrepared(final List<PreparedCreeper> prepared) {
		for (PreparedCreeper entry : prepared) {
			entry.creeper().discard();
		}
	}

	public enum ShowcaseArchetype {
		HUNTER("creeper_hunter", "mobsthinknow.showcase.creeper_hunter", "Creeper Hunter", ChatFormatting.GREEN, 5, false),
		FLANKER("creeper_flanker", "mobsthinknow.showcase.creeper_flanker", "Creeper Flanker", ChatFormatting.AQUA, 8, false),
		BREACHER("creeper_breacher", "mobsthinknow.showcase.creeper_breacher", "Creeper Breacher", ChatFormatting.GOLD, 10, false),
		CHARGED_BREACHER(
			"creeper_charged_breacher",
			"mobsthinknow.showcase.creeper_charged_breacher",
			"Charged Creeper Breacher",
			ChatFormatting.LIGHT_PURPLE,
			10,
			true
		);

		private final String commandId;
		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final int intelligence;
		private final boolean powered;

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence,
			final boolean powered
		) {
			this.commandId = commandId;
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.intelligence = intelligence;
			this.powered = powered;
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

		public boolean powered() {
			return this.powered;
		}
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnedCreeper(ShowcaseArchetype archetype, Creeper creeper) {
	}

	public record SpawnResult(List<SpawnedCreeper> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedCreeper> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	private record PreparedCreeper(ShowcaseArchetype archetype, Creeper creeper) {
	}
}

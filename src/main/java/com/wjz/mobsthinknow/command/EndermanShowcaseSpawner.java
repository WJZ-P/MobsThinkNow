package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.enderman.EndermanIntelligence;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfession;
import com.wjz.mobsthinknow.ai.enderman.EndermanProfessionProfile;
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
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 为测试指令生成四种末影人职业，以及已经把真实苦力怕抱在胸前的预装使者。 */
public final class EndermanShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 100;

	private EndermanShowcaseSpawner() {
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
			EntityType.ENDERMAN
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
			EntityType.ENDERMAN
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
		List<PreparedEnderman> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			PreparedEnderman entry = createEnderman(level, positions.get(index), faceToward, archetypes.get(index));
			if (entry == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(entry);
		}

		List<SpawnedEnderman> spawned = new ArrayList<>(prepared.size());
		for (PreparedEnderman entry : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(entry.enderman())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
			spawned.add(new SpawnedEnderman(entry.archetype(), entry.enderman(), entry.payload()));
		}
		return SpawnResult.succeeded(spawned);
	}

	static @Nullable PreparedEnderman createEnderman(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		EnderMan enderman = EntityType.ENDERMAN.create(level, EntitySpawnReason.COMMAND);
		if (enderman == null) {
			return null;
		}
		double x = feet.getX() + 0.5;
		double z = feet.getZ() + 0.5;
		float yaw = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		enderman.snapTo(x, feet.getY(), z, Mth.wrapDegrees(yaw), 0.0F);
		enderman.setYBodyRot(enderman.getYRot());
		enderman.setYHeadRot(enderman.getYRot());
		enderman.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.COMMAND, null);
		enderman.setPersistenceRequired();
		enderman.setHealth(enderman.getMaxHealth());
		enderman.setCustomName(archetype.displayName());
		EndermanIntelligence.set(enderman, archetype.intelligence());
		EndermanProfessionProfile.applyShowcaseLoadout(enderman, archetype.profession());
		enderman.setCustomNameVisible(true);

		Creeper payload = null;
		if (archetype.carriesCreeper()) {
			payload = EntityType.CREEPER.create(level, EntitySpawnReason.JOCKEY);
			if (payload == null) {
				enderman.discard();
				return null;
			}
			payload.snapTo(x, feet.getY(), z, enderman.getYRot(), 0.0F);
			payload.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.JOCKEY, null);
			payload.setPersistenceRequired();
			payload.setHealth(payload.getMaxHealth());
			payload.setCustomName(Component.translatableWithFallback(
				"mobsthinknow.showcase.enderman_creeper_payload",
				"Enderman Creeper Payload"
			).withStyle(ChatFormatting.DARK_GREEN));
			CreeperIntelligence.set(payload, 10);
			payload.setCustomNameVisible(true);
			payload.setSwellDir(-1);
			if (!payload.startRiding(enderman, true, true)) {
				payload.discard();
				enderman.discard();
				return null;
			}
		}
		return new PreparedEnderman(archetype, enderman, payload);
	}

	private static void discardPrepared(final List<PreparedEnderman> prepared) {
		for (PreparedEnderman entry : prepared) {
			if (entry.payload() != null) {
				entry.payload().discard();
			}
			entry.enderman().discard();
		}
	}

	public enum ShowcaseArchetype {
		HUNTER(
			"enderman_hunter",
			"mobsthinknow.showcase.enderman_hunter",
			"Enderman Riftblade",
			ChatFormatting.DARK_PURPLE,
			7,
			EndermanProfession.RIFTBLADE,
			false
		),
		VOID_GUARD(
			"enderman_void_guard",
			"mobsthinknow.showcase.enderman_void_guard",
			"Enderman Void Guard",
			ChatFormatting.AQUA,
			9,
			EndermanProfession.VOID_GUARD,
			false
		),
		VOID_LANCER(
			"enderman_void_lancer",
			"mobsthinknow.showcase.enderman_void_lancer",
			"Enderman Void Lancer",
			ChatFormatting.GOLD,
			9,
			EndermanProfession.VOID_LANCER,
			false
		),
		CREEPER_BOMBER(
			"enderman_creeper_bomber",
			"mobsthinknow.showcase.enderman_creeper_bomber",
			"Enderman Creeper Bomber",
			ChatFormatting.LIGHT_PURPLE,
			10,
			EndermanProfession.CREEPER_HERALD,
			true
		);

		private final String commandId;
		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final int intelligence;
		private final EndermanProfession profession;
		private final boolean carriesCreeper;

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence,
			final EndermanProfession profession,
			final boolean carriesCreeper
		) {
			this.commandId = commandId;
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.intelligence = intelligence;
			this.profession = profession;
			this.carriesCreeper = carriesCreeper;
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

		public EndermanProfession profession() {
			return this.profession;
		}

		public boolean carriesCreeper() {
			return this.carriesCreeper;
		}
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnedEnderman(ShowcaseArchetype archetype, EnderMan enderman, @Nullable Creeper payload) {
	}

	public record SpawnResult(List<SpawnedEnderman> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedEnderman> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	record PreparedEnderman(ShowcaseArchetype archetype, EnderMan enderman, @Nullable Creeper payload) {
	}
}

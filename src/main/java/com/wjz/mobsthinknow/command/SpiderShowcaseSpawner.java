package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.spider.SpiderIntelligence;
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
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 为管理指令生成可稳定复现的猎手、伏击者、首领与蜘蛛苦力怕投送组。 */
public final class SpiderShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 100;

	private SpiderShowcaseSpawner() {
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
			EntityType.SPIDER
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
			EntityType.SPIDER
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
		List<PreparedSpider> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			PreparedSpider entry = createSpider(level, positions.get(index), faceToward, archetypes.get(index));
			if (entry == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(entry);
		}

		List<SpawnedSpider> spawned = new ArrayList<>(prepared.size());
		for (PreparedSpider entry : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(entry.spider())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
			spawned.add(new SpawnedSpider(entry.archetype(), entry.spider(), entry.payload()));
		}
		return SpawnResult.succeeded(spawned);
	}

	private static @Nullable PreparedSpider createSpider(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		Spider spider = EntityType.SPIDER.create(level, EntitySpawnReason.COMMAND);
		if (spider == null) {
			return null;
		}
		double x = feet.getX() + 0.5;
		double z = feet.getZ() + 0.5;
		float yaw = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		spider.snapTo(x, feet.getY(), z, Mth.wrapDegrees(yaw), 0.0F);
		spider.setYBodyRot(spider.getYRot());
		spider.setYHeadRot(spider.getYRot());
		spider.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.COMMAND, null);
		spider.setPersistenceRequired();
		spider.setHealth(spider.getMaxHealth());
		spider.setCustomName(archetype.displayName());
		SpiderIntelligence.set(spider, archetype.intelligence());
		spider.setCustomNameVisible(true);

		Creeper payload = null;
		if (archetype.carriesCreeper()) {
			payload = EntityType.CREEPER.create(level, EntitySpawnReason.JOCKEY);
			if (payload == null) {
				spider.discard();
				return null;
			}
			payload.snapTo(x, feet.getY(), z, spider.getYRot(), 0.0F);
			payload.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.JOCKEY, null);
			payload.setPersistenceRequired();
			payload.setHealth(payload.getMaxHealth());
			payload.setCustomName(Component.translatableWithFallback(
				"mobsthinknow.showcase.spider_creeper_payload",
				"Creeper Payload"
			).withStyle(ChatFormatting.DARK_GREEN));
			CreeperIntelligence.set(payload, 10);
			payload.setCustomNameVisible(true);
			if (!payload.startRiding(spider, true, true)) {
				payload.discard();
				spider.discard();
				return null;
			}
		}
		return new PreparedSpider(archetype, spider, payload);
	}

	private static void discardPrepared(final List<PreparedSpider> prepared) {
		for (PreparedSpider entry : prepared) {
			if (entry.payload() != null) {
				entry.payload().discard();
			}
			entry.spider().discard();
		}
	}

	public enum ShowcaseArchetype {
		HUNTER("spider_hunter", "mobsthinknow.showcase.spider_hunter", "Spider Hunter", ChatFormatting.GRAY, 5, false),
		AMBUSHER("spider_ambusher", "mobsthinknow.showcase.spider_ambusher", "Spider Ambusher", ChatFormatting.DARK_PURPLE, 8, false),
		ALPHA("spider_alpha", "mobsthinknow.showcase.spider_alpha", "Spider Alpha", ChatFormatting.LIGHT_PURPLE, 10, false),
		CREEPER_BOMBER(
			"spider_creeper_bomber",
			"mobsthinknow.showcase.spider_creeper_bomber",
			"Spider-Creeper Bomber",
			ChatFormatting.RED,
			10,
			true
		);

		private final String commandId;
		private final String translationKey;
		private final String fallback;
		private final ChatFormatting color;
		private final int intelligence;
		private final boolean carriesCreeper;

		ShowcaseArchetype(
			final String commandId,
			final String translationKey,
			final String fallback,
			final ChatFormatting color,
			final int intelligence,
			final boolean carriesCreeper
		) {
			this.commandId = commandId;
			this.translationKey = translationKey;
			this.fallback = fallback;
			this.color = color;
			this.intelligence = intelligence;
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

	public record SpawnedSpider(ShowcaseArchetype archetype, Spider spider, @Nullable Creeper payload) {
	}

	public record SpawnResult(List<SpawnedSpider> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedSpider> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	private record PreparedSpider(ShowcaseArchetype archetype, Spider spider, @Nullable Creeper payload) {
	}
}

package com.wjz.mobsthinknow.command;

import com.wjz.mobsthinknow.ai.creeper.CreeperIntelligence;
import com.wjz.mobsthinknow.ai.giant.GiantIntelligence;
import com.wjz.mobsthinknow.ai.giant.GiantZombieProfile;
import com.wjz.mobsthinknow.ai.skeleton.SkeletonIntelligence;
import com.wjz.mobsthinknow.ai.zombie.ZombieIntelligence;
import com.wjz.mobsthinknow.config.ConfigManager;
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
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 为测试指令生成一套可立即观察三挂点的巨人攻城平台。 */
public final class GiantShowcaseSpawner {
	public static final int MAX_BATCH_SIZE = 32;

	private GiantShowcaseSpawner() {
	}

	public static SpawnResult spawnAll(final CommandSourceStack source) {
		return spawnBatch(source, ShowcaseArchetype.GIANT_SIEGE, 1);
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
			EntityType.GIANT
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
		List<PreparedGiant> prepared = new ArrayList<>(archetypes.size());
		for (int index = 0; index < archetypes.size(); index++) {
			PreparedGiant entry = createGiant(level, positions.get(index), faceToward, archetypes.get(index));
			if (entry == null) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(entry);
		}

		List<SpawnedGiant> spawned = new ArrayList<>(prepared.size());
		for (PreparedGiant entry : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(entry.giant())) {
				discardPrepared(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
			spawned.add(new SpawnedGiant(
				entry.archetype(),
				entry.giant(),
				entry.headRider(),
				entry.creeperPayload(),
				entry.zombiePayload()
			));
		}
		return SpawnResult.succeeded(spawned);
	}

	static @Nullable PreparedGiant createGiant(
		final ServerLevel level,
		final BlockPos feet,
		final Vec3 faceToward,
		final ShowcaseArchetype archetype
	) {
		Giant giant = EntityType.GIANT.create(level, EntitySpawnReason.COMMAND);
		if (giant == null) {
			return null;
		}
		double x = feet.getX() + 0.5;
		double z = feet.getZ() + 0.5;
		float yaw = (float)(Mth.atan2(faceToward.z - z, faceToward.x - x) * Mth.RAD_TO_DEG) - 90.0F;
		giant.snapTo(x, feet.getY(), z, Mth.wrapDegrees(yaw), 0.0F);
		giant.setYBodyRot(giant.getYRot());
		giant.setYHeadRot(giant.getYRot());
		giant.finalizeSpawn(level, level.getCurrentDifficultyAt(feet), EntitySpawnReason.COMMAND, null);
		GiantZombieProfile.applyAttributes(giant, ConfigManager.get());
		giant.setHealth(giant.getMaxHealth());
		giant.setPersistenceRequired();
		giant.setCustomName(archetype.displayName());
		GiantIntelligence.set(giant, archetype.intelligence());
		giant.setCustomNameVisible(true);

		Skeleton rider = SkeletonShowcaseSpawner.createSkeleton(
			level,
			feet,
			faceToward,
			SkeletonShowcaseSpawner.ShowcaseArchetype.BOW
		);
		Creeper creeper = CreeperShowcaseSpawner.createCreeper(
			level,
			feet,
			faceToward,
			CreeperShowcaseSpawner.ShowcaseArchetype.BREACHER
		);
		Zombie zombie = ZombieShowcaseSpawner.createZombie(
			level,
			feet,
			faceToward,
			ZombieShowcaseSpawner.ShowcaseArchetype.UNARMED
		);
		if (rider == null || creeper == null || zombie == null) {
			discardTree(giant);
			if (rider != null) rider.discard();
			if (creeper != null) creeper.discard();
			if (zombie != null) zombie.discard();
			return null;
		}

		configureChild(rider, "mobsthinknow.showcase.giant_head_rider", "Giant Head Rider", ChatFormatting.AQUA);
		SkeletonIntelligence.set(rider, 10);
		configureChild(creeper, "mobsthinknow.showcase.giant_creeper_payload", "Giant Creeper Payload", ChatFormatting.GREEN);
		CreeperIntelligence.set(creeper, 10);
		creeper.setSwellDir(-1);
		configureChild(zombie, "mobsthinknow.showcase.giant_zombie_payload", "Giant Zombie Payload", ChatFormatting.DARK_GREEN);
		ZombieIntelligence.set(zombie, 9);

		// 顺序就是头顶、右手、左手；真实乘员关系负责同步、存档与客户端实体渲染。
		if (!rider.startRiding(giant, true, true)
			|| !creeper.startRiding(giant, true, true)
			|| !zombie.startRiding(giant, true, true)) {
			discardTree(giant);
			// 短路求值可能留下尚未登乘的后续子实体；逐个 discard 让失败路径同样具备事务性。
			rider.discard();
			creeper.discard();
			zombie.discard();
			return null;
		}
		return new PreparedGiant(archetype, giant, rider, creeper, zombie);
	}

	private static void configureChild(
		final net.minecraft.world.entity.Mob mob,
		final String key,
		final String fallback,
		final ChatFormatting color
	) {
		mob.setPersistenceRequired();
		mob.setHealth(mob.getMaxHealth());
		mob.setCustomName(Component.translatableWithFallback(key, fallback).withStyle(color));
		mob.setCustomNameVisible(true);
	}

	private static void discardPrepared(final List<PreparedGiant> prepared) {
		for (PreparedGiant entry : prepared) {
			discardTree(entry.giant());
		}
	}

	private static void discardTree(final net.minecraft.world.entity.Entity root) {
		for (net.minecraft.world.entity.Entity passenger : List.copyOf(root.getPassengers())) {
			discardTree(passenger);
		}
		root.discard();
	}

	public enum ShowcaseArchetype {
		GIANT_SIEGE(
			"giant_siege",
			"mobsthinknow.showcase.giant_siege",
			"Giant Siege Platform",
			ChatFormatting.DARK_GREEN,
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

	public record SpawnedGiant(
		ShowcaseArchetype archetype,
		Giant giant,
		Skeleton headRider,
		Creeper creeperPayload,
		Zombie zombiePayload
	) {
	}

	public record SpawnResult(List<SpawnedGiant> spawned, Failure failure) {
		private static SpawnResult succeeded(final List<SpawnedGiant> spawned) {
			return new SpawnResult(List.copyOf(spawned), Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	record PreparedGiant(
		ShowcaseArchetype archetype,
		Giant giant,
		Skeleton headRider,
		Creeper creeperPayload,
		Zombie zombiePayload
	) {
	}
}

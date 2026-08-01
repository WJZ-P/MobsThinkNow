package com.wjz.mobsthinknow.command;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 为无参数 {@code /mtn spawnall} 生成当前全部智能 AI 战术预设。
 * 二十九个根实体统一预检、统一准备并事务式加入世界；所有坐骑、射手和载荷随根实体加入。
 */
public final class AllShowcaseSpawner {
	public static final int ZOMBIE_ARCHETYPES = ZombieShowcaseSpawner.ShowcaseArchetype.values().length;
	public static final int SKELETON_ARCHETYPES = SkeletonShowcaseSpawner.ShowcaseArchetype.values().length;
	public static final int CREEPER_ARCHETYPES = CreeperShowcaseSpawner.ShowcaseArchetype.values().length;
	public static final int SPIDER_ARCHETYPES = SpiderShowcaseSpawner.ShowcaseArchetype.values().length;
	public static final int ENDERMAN_ARCHETYPES = EndermanShowcaseSpawner.ShowcaseArchetype.values().length;
	public static final int GIANT_ARCHETYPES = GiantShowcaseSpawner.ShowcaseArchetype.values().length;
	public static final int ARCHETYPE_COUNT = ZOMBIE_ARCHETYPES
		+ SKELETON_ARCHETYPES
		+ CREEPER_ARCHETYPES
		+ SPIDER_ARCHETYPES
		+ ENDERMAN_ARCHETYPES
		+ GIANT_ARCHETYPES;

	private AllShowcaseSpawner() {
	}

	public static SpawnResult spawnAll(final CommandSourceStack source) {
		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}

		List<SpawnSpec> specs = createSpecs();
		List<EntityType<?>> placementTypes = specs.stream().map(SpawnSpec::placementType).toList();
		List<BlockPos> positions = ShowcaseSpawnPlacement.findMixedFormation(
			level,
			source.getPosition(),
			source.getRotation().y,
			placementTypes
		);
		if (positions.size() != specs.size()) {
			return SpawnResult.failed(Failure.NO_SPACE);
		}

		List<Entity> prepared = new ArrayList<>(specs.size());
		for (int index = 0; index < specs.size(); index++) {
			Entity root = specs.get(index).factory().create(level, positions.get(index), source.getPosition());
			if (root == null) {
				discardEntityTrees(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(root);
		}

		for (Entity root : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(root)) {
				discardEntityTrees(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
		}
		int totalEntities = prepared.stream().mapToInt(AllShowcaseSpawner::entityTreeSize).sum();
		return SpawnResult.succeeded(prepared, totalEntities);
	}

	private static List<SpawnSpec> createSpecs() {
		List<SpawnSpec> specs = new ArrayList<>(ARCHETYPE_COUNT);
		for (ZombieShowcaseSpawner.ShowcaseArchetype archetype
			: ZombieShowcaseSpawner.ShowcaseArchetype.values()) {
			specs.add(new SpawnSpec(
				archetype.entityType(),
				(level, feet, faceToward) -> ZombieShowcaseSpawner.createZombie(
					level,
					feet,
					faceToward,
					archetype
				)
			));
		}
		for (SkeletonShowcaseSpawner.ShowcaseArchetype archetype
			: SkeletonShowcaseSpawner.ShowcaseArchetype.values()) {
			specs.add(new SpawnSpec(
				archetype.entityType(),
				(level, feet, faceToward) -> SkeletonShowcaseSpawner.createSkeleton(
					level,
					feet,
					faceToward,
					archetype
				)
			));
		}
		for (CreeperShowcaseSpawner.ShowcaseArchetype archetype
			: CreeperShowcaseSpawner.ShowcaseArchetype.values()) {
			specs.add(new SpawnSpec(
				EntityType.CREEPER,
				(level, feet, faceToward) -> CreeperShowcaseSpawner.createCreeper(
					level,
					feet,
					faceToward,
					archetype
				)
			));
		}
		for (SpiderShowcaseSpawner.ShowcaseArchetype archetype
			: SpiderShowcaseSpawner.ShowcaseArchetype.values()) {
			// 投送兵用铁傀儡的 1.4×2.7 盒子预留完整载荷空间，实际生成物仍是蜘蛛与苦力怕。
			EntityType<?> placementType = archetype.carriesCreeper() ? EntityType.IRON_GOLEM : EntityType.SPIDER;
			specs.add(new SpawnSpec(
				placementType,
				(level, feet, faceToward) -> {
					SpiderShowcaseSpawner.PreparedSpider prepared = SpiderShowcaseSpawner.createSpider(
						level,
						feet,
						faceToward,
						archetype
					);
					return prepared == null ? null : prepared.spider();
				}
			));
		}
		for (EndermanShowcaseSpawner.ShowcaseArchetype archetype
			: EndermanShowcaseSpawner.ShowcaseArchetype.values()) {
			specs.add(new SpawnSpec(
				EntityType.ENDERMAN,
				(level, feet, faceToward) -> {
					EndermanShowcaseSpawner.PreparedEnderman prepared = EndermanShowcaseSpawner.createEnderman(
						level,
						feet,
						faceToward,
						archetype
					);
					return prepared == null ? null : prepared.enderman();
				}
			));
		}
		for (GiantShowcaseSpawner.ShowcaseArchetype archetype
			: GiantShowcaseSpawner.ShowcaseArchetype.values()) {
			specs.add(new SpawnSpec(
				EntityType.GIANT,
				(level, feet, faceToward) -> {
					GiantShowcaseSpawner.PreparedGiant prepared = GiantShowcaseSpawner.createGiant(
						level,
						feet,
						faceToward,
						archetype
					);
					return prepared == null ? null : prepared.giant();
				}
			));
		}
		return List.copyOf(specs);
	}

	private static int entityTreeSize(final Entity root) {
		int count = 1;
		for (Entity passenger : root.getPassengers()) {
			count += entityTreeSize(passenger);
		}
		return count;
	}

	private static void discardEntityTrees(final List<Entity> roots) {
		for (Entity root : roots) {
			discardEntityTree(root);
		}
	}

	private static void discardEntityTree(final Entity root) {
		for (Entity passenger : List.copyOf(root.getPassengers())) {
			discardEntityTree(passenger);
		}
		root.discard();
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnResult(List<Entity> spawnedRoots, int totalEntities, Failure failure) {
		private static SpawnResult succeeded(final List<Entity> spawnedRoots, final int totalEntities) {
			return new SpawnResult(List.copyOf(spawnedRoots), totalEntities, Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(List.of(), 0, failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	private record SpawnSpec(EntityType<?> placementType, SpawnFactory factory) {
	}

	@FunctionalInterface
	private interface SpawnFactory {
		@Nullable Entity create(ServerLevel level, BlockPos feet, Vec3 faceToward);
	}
}

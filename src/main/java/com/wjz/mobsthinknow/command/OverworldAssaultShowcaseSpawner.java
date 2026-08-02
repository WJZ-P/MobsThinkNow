package com.wjz.mobsthinknow.command;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 一条指令生成可直接观察联合兵种编排的八成员主世界突击组。
 *
 * <p>每组固定包含盾兵、斧手、水桶辅助、弓手、弩手、苦力怕和两只蜘蛛。协调器会把一只蜘蛛
 * 分给苦力怕，另一只分给高智力弩手；由玩家执行时，全部成员立即共享命令执行者为目标。</p>
 */
public final class OverworldAssaultShowcaseSpawner {
	public static final int MAX_GROUPS = 8;
	public static final int ROOTS_PER_GROUP = 8;

	private OverworldAssaultShowcaseSpawner() {
	}

	public static SpawnResult spawn(final CommandSourceStack source, final int requestedGroups) {
		int groups = Math.max(1, Math.min(MAX_GROUPS, requestedGroups));
		ServerLevel level = source.getLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return SpawnResult.failed(Failure.PEACEFUL);
		}

		List<SpawnSpec> specs = createSpecs(groups);
		List<BlockPos> positions = ShowcaseSpawnPlacement.findMixedFormation(
			level,
			source.getPosition(),
			source.getRotation().y,
			specs.stream().map(SpawnSpec::placementType).toList()
		);
		if (positions.size() != specs.size()) {
			return SpawnResult.failed(Failure.NO_SPACE);
		}

		List<Mob> prepared = new ArrayList<>(specs.size());
		for (int index = 0; index < specs.size(); index++) {
			Mob mob = specs.get(index).factory().create(level, positions.get(index), source.getPosition());
			if (mob == null) {
				discard(prepared);
				return SpawnResult.failed(Failure.CREATE_FAILED);
			}
			prepared.add(mob);
		}

		LivingEntity target = commandTarget(source);
		if (target != null) {
			for (Mob mob : prepared) {
				mob.setTarget(target);
			}
		}
		for (Mob mob : prepared) {
			if (!level.tryAddFreshEntityWithPassengers(mob)) {
				discard(prepared);
				return SpawnResult.failed(Failure.ADD_FAILED);
			}
		}
		return SpawnResult.succeeded(groups, prepared, target != null);
	}

	private static List<SpawnSpec> createSpecs(final int groups) {
		List<SpawnSpec> specs = new ArrayList<>(groups * ROOTS_PER_GROUP);
		for (int group = 0; group < groups; group++) {
			addZombie(specs, ZombieShowcaseSpawner.ShowcaseArchetype.SWORD_SHIELD);
			addZombie(specs, ZombieShowcaseSpawner.ShowcaseArchetype.AXEMAN);
			addZombie(specs, ZombieShowcaseSpawner.ShowcaseArchetype.WATER_SUPPORT);
			addSkeleton(specs, SkeletonShowcaseSpawner.ShowcaseArchetype.BOW);
			addSkeleton(specs, SkeletonShowcaseSpawner.ShowcaseArchetype.CROSSBOW);
			addCreeper(specs, CreeperShowcaseSpawner.ShowcaseArchetype.FLANKER);
			addSpider(specs, SpiderShowcaseSpawner.ShowcaseArchetype.AMBUSHER);
			addSpider(specs, SpiderShowcaseSpawner.ShowcaseArchetype.ALPHA);
		}
		return List.copyOf(specs);
	}

	private static void addZombie(
		final List<SpawnSpec> specs,
		final ZombieShowcaseSpawner.ShowcaseArchetype archetype
	) {
		specs.add(new SpawnSpec(
			archetype.entityType(),
			(level, feet, faceToward) -> ZombieShowcaseSpawner.createZombie(level, feet, faceToward, archetype)
		));
	}

	private static void addSkeleton(
		final List<SpawnSpec> specs,
		final SkeletonShowcaseSpawner.ShowcaseArchetype archetype
	) {
		specs.add(new SpawnSpec(
			archetype.entityType(),
			(level, feet, faceToward) -> SkeletonShowcaseSpawner.createSkeleton(level, feet, faceToward, archetype)
		));
	}

	private static void addCreeper(
		final List<SpawnSpec> specs,
		final CreeperShowcaseSpawner.ShowcaseArchetype archetype
	) {
		specs.add(new SpawnSpec(
			EntityType.CREEPER,
			(level, feet, faceToward) -> CreeperShowcaseSpawner.createCreeper(level, feet, faceToward, archetype)
		));
	}

	private static void addSpider(
		final List<SpawnSpec> specs,
		final SpiderShowcaseSpawner.ShowcaseArchetype archetype
	) {
		specs.add(new SpawnSpec(
			EntityType.SPIDER,
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

	private static @Nullable LivingEntity commandTarget(final CommandSourceStack source) {
		Entity entity = source.getEntity();
		return entity instanceof Player player && !player.isCreative() && !player.isSpectator() && player.isAlive()
			? player
			: null;
	}

	private static void discard(final List<? extends Entity> entities) {
		for (Entity entity : entities) {
			entity.discard();
		}
	}

	public enum Failure {
		NONE,
		PEACEFUL,
		NO_SPACE,
		CREATE_FAILED,
		ADD_FAILED
	}

	public record SpawnResult(int groups, List<Mob> spawned, boolean targetedExecutor, Failure failure) {
		private static SpawnResult succeeded(
			final int groups,
			final List<Mob> spawned,
			final boolean targetedExecutor
		) {
			return new SpawnResult(groups, List.copyOf(spawned), targetedExecutor, Failure.NONE);
		}

		private static SpawnResult failed(final Failure failure) {
			return new SpawnResult(0, List.of(), false, failure);
		}

		public boolean success() {
			return this.failure == Failure.NONE;
		}
	}

	private record SpawnSpec(EntityType<?> placementType, SpawnFactory factory) {
	}

	@FunctionalInterface
	private interface SpawnFactory {
		@Nullable Mob create(ServerLevel level, BlockPos feet, Vec3 faceToward);
	}
}

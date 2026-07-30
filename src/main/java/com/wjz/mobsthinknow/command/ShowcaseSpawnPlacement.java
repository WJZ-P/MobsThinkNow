package com.wjz.mobsthinknow.command;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 所有战术怪物展示指令共用的安全落点和阵型规划。 */
final class ShowcaseSpawnPlacement {
	private static final double GRID_SPACING = 3.0;
	private static final double FORMATION_FRONT_DISTANCE = 5.0;
	private static final double SINGLE_SPAWN_DISTANCE = 4.0;
	private static final int[] VERTICAL_SEARCH = {0, 1, -1, 2, -2, 3, -3, 4, -4};
	private static final int[][] LOCAL_OFFSETS = {
		{0, 0},
		{1, 0}, {-1, 0}, {0, 1}, {0, -1},
		{1, 1}, {1, -1}, {-1, 1}, {-1, -1}
	};

	private ShowcaseSpawnPlacement() {
	}

	static List<BlockPos> findBatch(
		final ServerLevel level,
		final Vec3 origin,
		final float yaw,
		final int count,
		final EntityType<?> entityType
	) {
		if (count != 1) {
			return findFormation(level, origin, yaw, count, entityType);
		}
		double radians = Math.toRadians(yaw);
		Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
		Vec3 preferred = origin.add(forward.scale(SINGLE_SPAWN_DISTANCE));
		@Nullable BlockPos feet = findSafeFeet(level, preferred, origin.y, List.of(), entityType);
		return feet == null ? List.of() : List.of(feet);
	}

	static List<BlockPos> findFormation(
		final ServerLevel level,
		final Vec3 origin,
		final float yaw,
		final int count,
		final EntityType<?> entityType
	) {
		return findMixedFormation(level, origin, yaw, java.util.Collections.nCopies(count, entityType));
	}

	/**
	 * 为混合实体阵型逐格使用真实实体尺寸做地基、碰撞和预留检查。
	 * 这样蜘蛛的宽碰撞箱、僵尸/骷髅的高度与苦力怕载荷都不会退化成单一模板估算。
	 */
	static List<BlockPos> findMixedFormation(
		final ServerLevel level,
		final Vec3 origin,
		final float yaw,
		final List<EntityType<?>> entityTypes
	) {
		int count = entityTypes.size();
		double radians = Math.toRadians(yaw);
		Vec3 forward = new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
		Vec3 lateral = new Vec3(Math.cos(radians), 0.0, Math.sin(radians));
		int columns = (int)Math.ceil(Math.sqrt(count));
		List<BlockPos> positions = new ArrayList<>(count);
		List<AABB> reservedBoxes = new ArrayList<>(count);

		for (int index = 0; index < count; index++) {
			EntityType<?> entityType = entityTypes.get(index);
			int row = index / columns;
			int rowStart = row * columns;
			int rowSize = Math.min(columns, count - rowStart);
			int column = index - rowStart;
			double lateralOffset = (column - (rowSize - 1) * 0.5) * GRID_SPACING;
			Vec3 preferred = origin
				.add(forward.scale(FORMATION_FRONT_DISTANCE + row * GRID_SPACING))
				.add(lateral.scale(lateralOffset));
			@Nullable BlockPos safe = findSafeFeet(level, preferred, origin.y, reservedBoxes, entityType);
			if (safe == null) {
				return List.of();
			}
			positions.add(safe);
			reservedBoxes.add(spawnBox(entityType, safe));
		}
		return List.copyOf(positions);
	}

	private static @Nullable BlockPos findSafeFeet(
		final ServerLevel level,
		final Vec3 preferred,
		final double originY,
		final List<AABB> reservedBoxes,
		final EntityType<?> entityType
	) {
		BlockPos horizontal = BlockPos.containing(preferred.x, originY, preferred.z);
		for (int[] offset : LOCAL_OFFSETS) {
			for (int dy : VERTICAL_SEARCH) {
				BlockPos candidate = horizontal.offset(offset[0], dy, offset[1]);
				if (isSafeFeet(level, candidate, reservedBoxes, entityType)) {
					return candidate.immutable();
				}
			}
		}

		if (!isChunkLoaded(level, horizontal)) {
			return null;
		}
		BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, horizontal);
		for (int[] offset : LOCAL_OFFSETS) {
			BlockPos candidate = surface.offset(offset[0], 0, offset[1]);
			if (isSafeFeet(level, candidate, reservedBoxes, entityType)) {
				return candidate.immutable();
			}
		}
		return null;
	}

	private static boolean isSafeFeet(
		final ServerLevel level,
		final BlockPos feet,
		final List<AABB> reservedBoxes,
		final EntityType<?> entityType
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

		AABB box = spawnBox(entityType, feet);
		if (!level.getWorldBorder().isWithinBounds(box) || !level.noCollision(box)) {
			return false;
		}
		return reservedBoxes.stream().noneMatch(box::intersects);
	}

	private static AABB spawnBox(final EntityType<?> entityType, final BlockPos feet) {
		return entityType.getSpawnAABB(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
	}

	private static boolean isChunkLoaded(final ServerLevel level, final BlockPos pos) {
		return level.getChunkSource().hasChunk(
			SectionPos.blockToSectionCoord(pos.getX()),
			SectionPos.blockToSectionCoord(pos.getZ())
		);
	}
}

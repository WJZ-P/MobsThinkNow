package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner;
import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.GridPosition;
import com.wjz.mobsthinknow.shared.ai.CoverPositionPlanner.SearchLimits;
import com.wjz.mobsthinknow.shared.math.Vec3d;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fabric 方块、碰撞与射线适配层；候选排序和评分由共享战术内核统一完成。
 *
 * <p>每次搜索仍然最多检查 96 个站立格，并只把评分最好的四组“藏身格 + 相邻探头格”交给
 * 原版导航验证，因此复杂度不随附近实体或区块总量增长。</p>
 */
public final class SkeletonCoverPlanner {
	public static final int SEARCH_RADIUS = 4;
	public static final int MAXIMUM_RAW_CANDIDATES = 96;
	public static final int MAXIMUM_PLANS = 4;
	private static final SearchLimits SEARCH_LIMITS = new SearchLimits(
		SEARCH_RADIUS,
		1,
		MAXIMUM_RAW_CANDIDATES,
		MAXIMUM_PLANS,
		0.70,
		1.55
	);

	private SkeletonCoverPlanner() {
	}

	public static List<CoverPlan> findPlans(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final double configuredPreferredRange
	) {
		BlockPos origin = skeleton.blockPosition();
		var result = CoverPositionPlanner.findPlans(
			toGrid(origin),
			toShared(skeleton.position()),
			toShared(target.position()),
			configuredPreferredRange,
			skeleton.getId(),
			SEARCH_LIMITS,
			new CoverPositionPlanner.Probe() {
				@Override
				public boolean isStandable(final int x, final int y, final int z) {
					return SkeletonCoverPlanner.isStandable(skeleton, new BlockPos(x, y, z));
				}

				@Override
				public boolean isHidden(final int x, final int y, final int z) {
					return SkeletonCoverPlanner.isHiddenFromTarget(skeleton, target, new BlockPos(x, y, z));
				}

				@Override
				public boolean hasClearShot(final int x, final int y, final int z) {
					return SkeletonCoverPlanner.hasClearShotFrom(skeleton, target, new BlockPos(x, y, z));
				}
			}
		);
		return result.plans().stream()
			.map(plan -> new CoverPlan(toBlockPos(plan.hide()), toBlockPos(plan.peek())))
			.toList();
	}

	public static boolean isHiddenFromTarget(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final BlockPos hide
	) {
		return !hasClearRay(skeleton, eyeAt(skeleton, hide), target.getEyePosition());
	}

	public static boolean hasClearShotFrom(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final BlockPos peek
	) {
		return hasClearRay(skeleton, eyeAt(skeleton, peek), target.getEyePosition());
	}

	public static boolean isStandable(final AbstractSkeleton skeleton, final BlockPos feet) {
		Level level = skeleton.level();
		BlockPos support = feet.below();
		if (!level.getBlockState(support).isFaceSturdy(level, support, Direction.UP)
			|| !level.getFluidState(feet).isEmpty()
			|| !level.getFluidState(feet.above()).isEmpty()) {
			return false;
		}

		Vec3 destination = center(feet);
		AABB destinationBox = skeleton.getBoundingBox()
			.move(destination.subtract(skeleton.position()))
			.deflate(0.05);
		return level.getWorldBorder().isWithinBounds(destinationBox)
			&& level.noCollision(skeleton, destinationBox);
	}

	static boolean isUsefulRange(final double distanceSquared, final double configuredPreferredRange) {
		return CoverPositionPlanner.isUsefulRange(distanceSquared, configuredPreferredRange, SEARCH_LIMITS);
	}

	static double planScore(
		final Vec3 skeletonPosition,
		final Vec3 targetPosition,
		final BlockPos hide,
		final BlockPos peek,
		final double configuredPreferredRange
	) {
		return CoverPositionPlanner.score(
			toShared(skeletonPosition),
			toShared(targetPosition),
			toGrid(hide),
			toGrid(peek),
			configuredPreferredRange
		);
	}

	private static boolean hasClearRay(final AbstractSkeleton skeleton, final Vec3 from, final Vec3 to) {
		return skeleton.level().clip(new ClipContext(
			from,
			to,
			ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE,
			skeleton
		)).getType() == HitResult.Type.MISS;
	}

	private static Vec3 eyeAt(final AbstractSkeleton skeleton, final BlockPos feet) {
		return center(feet).add(0.0, skeleton.getEyeHeight(), 0.0);
	}

	private static Vec3 center(final BlockPos feet) {
		return Vec3.atBottomCenterOf(feet);
	}

	private static GridPosition toGrid(final BlockPos position) {
		return new GridPosition(position.getX(), position.getY(), position.getZ());
	}

	private static BlockPos toBlockPos(final GridPosition position) {
		return new BlockPos(position.x(), position.y(), position.z());
	}

	private static Vec3d toShared(final Vec3 vector) {
		return new Vec3d(vector.x, vector.y, vector.z);
	}

	public record CoverPlan(BlockPos hide, BlockPos peek) {
	}
}

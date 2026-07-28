package com.wjz.mobsthinknow.ai.skeleton;

import java.util.ArrayList;
import java.util.Comparator;
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
 * 为持弓骷髅寻找“藏身格 + 相邻探头格”的固定预算局部规划器。
 *
 * <p>它不扫描实体，也不遍历区块：一次搜索最多检查 96 个、距骷髅不超过四格的站立格，
 * 最多把四组几何候选交给原版导航验证。藏身格到目标眼睛的射线必须被实体碰撞形状阻断，
 * 相邻探头格到目标眼睛的射线则必须完全畅通，因此普通两格高墙柱就能自然成为掩体。</p>
 */
public final class SkeletonCoverPlanner {
	public static final int SEARCH_RADIUS = 4;
	public static final int MAXIMUM_RAW_CANDIDATES = 96;
	public static final int MAXIMUM_PLANS = 4;
	private static final double MINIMUM_RANGE_RATIO = 0.70;
	private static final double MAXIMUM_RANGE_RATIO = 1.55;
	private static final Direction[] HORIZONTAL_DIRECTIONS = {
		Direction.NORTH,
		Direction.SOUTH,
		Direction.WEST,
		Direction.EAST
	};
	private static final List<Offset> SEARCH_OFFSETS = createSearchOffsets();

	private SkeletonCoverPlanner() {
	}

	public static List<CoverPlan> findPlans(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final double configuredPreferredRange
	) {
		double preferredRange = validPreferredRange(configuredPreferredRange);
		BlockPos origin = skeleton.blockPosition();
		List<ScoredPlan> candidates = new ArrayList<>(MAXIMUM_PLANS * 2);
		int rawChecks = 0;
		int directionOffset = Math.floorMod(skeleton.getId(), HORIZONTAL_DIRECTIONS.length);

		for (Offset offset : SEARCH_OFFSETS) {
			if (rawChecks++ >= MAXIMUM_RAW_CANDIDATES) {
				break;
			}

			BlockPos hide = origin.offset(offset.x(), offset.y(), offset.z());
			if (!isStandable(skeleton, hide)
				|| !isUsefulRange(center(hide).distanceToSqr(target.position()), preferredRange)
				|| !isHiddenFromTarget(skeleton, target, hide)) {
				continue;
			}

			CoverPlan bestAtHide = null;
			double bestAtHideScore = Double.POSITIVE_INFINITY;
			for (int directionIndex = 0; directionIndex < HORIZONTAL_DIRECTIONS.length; directionIndex++) {
				// 同批骷髅从不同墙角方向开始评分，几何同分时不会清一色挤向同一侧。
				Direction direction = HORIZONTAL_DIRECTIONS[
					(directionIndex + directionOffset) % HORIZONTAL_DIRECTIONS.length
				];
				BlockPos peek = hide.relative(direction);
				if (!isStandable(skeleton, peek)
					|| !isUsefulRange(center(peek).distanceToSqr(target.position()), preferredRange)
					|| !hasClearShotFrom(skeleton, target, peek)) {
					continue;
				}

				double score = planScore(skeleton.position(), target.position(), hide, peek, preferredRange);
				if (score < bestAtHideScore) {
					bestAtHideScore = score;
					bestAtHide = new CoverPlan(hide.immutable(), peek.immutable());
				}
			}

			if (bestAtHide != null) {
				candidates.add(new ScoredPlan(bestAtHide, bestAtHideScore));
			}
		}

		candidates.sort(Comparator.comparingDouble(ScoredPlan::score));
		List<CoverPlan> plans = new ArrayList<>(Math.min(MAXIMUM_PLANS, candidates.size()));
		for (int index = 0; index < candidates.size() && index < MAXIMUM_PLANS; index++) {
			plans.add(candidates.get(index).plan());
		}
		return List.copyOf(plans);
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
		if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
			return false;
		}
		double preferredRange = validPreferredRange(configuredPreferredRange);
		double minimum = preferredRange * MINIMUM_RANGE_RATIO;
		double maximum = preferredRange * MAXIMUM_RANGE_RATIO;
		return distanceSquared >= minimum * minimum && distanceSquared <= maximum * maximum;
	}

	static double planScore(
		final Vec3 skeletonPosition,
		final Vec3 targetPosition,
		final BlockPos hide,
		final BlockPos peek,
		final double configuredPreferredRange
	) {
		double preferredRange = validPreferredRange(configuredPreferredRange);
		double travelCost = center(hide).distanceToSqr(skeletonPosition);
		double peekRangeError = Math.sqrt(center(peek).distanceToSqr(targetPosition)) - preferredRange;
		double verticalCost = Math.abs(hide.getY() - skeletonPosition.y) * 2.0;
		return travelCost + peekRangeError * peekRangeError * 1.5 + verticalCost;
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
		Vec3 center = center(feet);
		return center.add(0.0, skeleton.getEyeHeight(), 0.0);
	}

	private static Vec3 center(final BlockPos feet) {
		return Vec3.atBottomCenterOf(feet);
	}

	private static double validPreferredRange(final double configuredPreferredRange) {
		return Double.isFinite(configuredPreferredRange) && configuredPreferredRange > 0.0
			? configuredPreferredRange
			: SkeletonCombatMath.DEFAULT_PREFERRED_RANGE;
	}

	private static List<Offset> createSearchOffsets() {
		List<Offset> offsets = new ArrayList<>();
		for (int y = -1; y <= 1; y++) {
			for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
				for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
					int horizontalSquared = x * x + z * z;
					if (horizontalSquared <= SEARCH_RADIUS * SEARCH_RADIUS) {
						offsets.add(new Offset(x, y, z, horizontalSquared));
					}
				}
			}
		}
		offsets.sort(
			Comparator.comparingInt((Offset offset) -> Math.abs(offset.y()))
				.thenComparingInt(Offset::horizontalSquared)
		);
		return List.copyOf(offsets);
	}

	public record CoverPlan(BlockPos hide, BlockPos peek) {
	}

	private record ScoredPlan(CoverPlan plan, double score) {
	}

	private record Offset(int x, int y, int z, int horizontalSquared) {
	}
}

package com.wjz.mobsthinknow.ai.skeleton;

import com.wjz.mobsthinknow.ai.zombie.squad.ZombieSquadCoordinator;
import com.wjz.mobsthinknow.config.ConfigManager;
import com.wjz.mobsthinknow.config.MobsThinkNowConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 混编小队射手的预测弹道、爆炸危险区与可达侧射位检查。
 *
 * <p>只有真正准备开火或射界连续受阻时才查询。普通检查采样八段保守抛物线，而不是只连眼睛直线；
 * 烟花额外检查目标周围爆炸区。找侧射位最多尝试四个固定候选并用原版导航验证，不执行无界搜索。</p>
 */
public final class SkeletonShotSafety {
	private static final int TRAJECTORY_SEGMENTS = 8;
	private static final double CORRIDOR_QUERY_PADDING = 0.75;
	private static final double ALLY_HITBOX_PADDING = 0.20;
	private static final double FIREWORK_DANGER_RADIUS = 4.0;
	private static final double MAXIMUM_PREDICTED_TARGET_SPEED = 0.35;
	private static final double[] FIRING_LANE_OFFSETS = {1.75, 3.0};

	private SkeletonShotSafety() {
	}

	public static boolean hasClearShot(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final boolean explosive
	) {
		return assess(shooter, target, explosive).status == Status.CLEAR;
	}

	public static Assessment assess(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final boolean explosive
	) {
		return assessFrom(shooter, shooter.getEyePosition(), target, explosive);
	}

	/** 连续被挡线后调用；返回的 Path 已通过 canReach，调用方可直接交给 Navigation。 */
	public static @Nullable FiringLane findFiringLane(
		final AbstractSkeleton shooter,
		final LivingEntity target,
		final boolean explosive,
		final int preferredSide
	) {
		if (!(shooter.level() instanceof ServerLevel) || shooter.isPassenger()) {
			return null;
		}
		Vec3 towardTarget = horizontalUnit(target.position().subtract(shooter.position()));
		if (towardTarget.lengthSqr() < 1.0E-7) {
			return null;
		}
		Vec3 lateral = new Vec3(-towardTarget.z, 0.0, towardTarget.x);
		int side = preferredSide < 0 ? -1 : 1;
		for (double offset : FIRING_LANE_OFFSETS) {
			for (int direction : new int[] {side, -side}) {
				BlockPos candidate = BlockPos.containing(shooter.position().add(lateral.scale(offset * direction)));
				Path path = shooter.getNavigation().createPath(candidate, 0);
				if (path == null || !path.canReach()) {
					continue;
				}
				Vec3 destination = Vec3.atBottomCenterOf(candidate);
				Vec3 candidateEye = destination.add(0.0, shooter.getEyeHeight(), 0.0);
				if (assessFrom(shooter, candidateEye, target, explosive).status == Status.CLEAR) {
					return new FiringLane(destination, path, direction);
				}
			}
		}
		return null;
	}

	private static Assessment assessFrom(
		final AbstractSkeleton shooter,
		final Vec3 start,
		final LivingEntity target,
		final boolean explosive
	) {
		MobsThinkNowConfig config = ConfigManager.get();
		if (!config.enabled
			|| !config.skeletonAiEnabled
			|| !config.squadIgnoreFriendlyFire
			|| !(shooter.level() instanceof ServerLevel level)) {
			return Assessment.CLEAR;
		}
		ZombieSquadCoordinator coordinator = ZombieSquadCoordinator.forLevel(level);
		if (coordinator.viewFor(shooter) == null) {
			return Assessment.CLEAR;
		}

		List<Vec3> samples = trajectorySamples(start, target);
		AABB corridor = boundsOf(samples).inflate(CORRIDOR_QUERY_PADDING);
		List<Mob> corridorAllies = level.getEntitiesOfClass(
			Mob.class,
			corridor,
			ally -> isRelevantSquadmate(shooter, ally) && ally != shooter.getVehicle()
		);
		for (Mob ally : corridorAllies) {
			AABB hitbox = ally.getBoundingBox().inflate(ALLY_HITBOX_PADDING);
			// 抛物线估计会随武器与目标高差变化；同时保留直达弦作为保守下界，
			// 避免近距离高个目标把估计弧线抬过站在正中的队友头顶。
			if (hitbox.clip(start, samples.getLast()).isPresent()) {
				return new Assessment(Status.ALLY_IN_CORRIDOR, ally.getId());
			}
			for (int index = 1; index < samples.size(); index++) {
				if (hitbox.clip(samples.get(index - 1), samples.get(index)).isPresent()) {
					return new Assessment(Status.ALLY_IN_CORRIDOR, ally.getId());
				}
			}
		}

		if (explosive) {
			AABB dangerZone = target.getBoundingBox().inflate(FIREWORK_DANGER_RADIUS);
			List<Mob> endangered = level.getEntitiesOfClass(
				Mob.class,
				dangerZone,
				ally -> isRelevantSquadmate(shooter, ally)
			);
			if (!endangered.isEmpty()) {
				return new Assessment(Status.ALLY_IN_BLAST_RADIUS, endangered.getFirst().getId());
			}
		}
		return Assessment.CLEAR;
	}

	public static List<Vec3> trajectorySamples(final Vec3 start, final LivingEntity target) {
		return trajectorySamples(start, target.getEyePosition(), target.getDeltaMovement());
	}

	/** 纯数学入口，供回归测试验证抛物线采样和可见速度提前量。 */
	static List<Vec3> trajectorySamples(
		final Vec3 start,
		final Vec3 targetEye,
		final Vec3 observedTargetVelocity
	) {
		Vec3 targetVelocity = clampHorizontal(observedTargetVelocity, MAXIMUM_PREDICTED_TARGET_SPEED);
		double directDistance = start.distanceTo(targetEye);
		double predictionTicks = Math.min(8.0, Math.max(0.0, directDistance / 3.0));
		Vec3 end = targetEye.add(targetVelocity.scale(predictionTicks));
		double arcHeight = Math.min(2.5, directDistance * 0.08);
		List<Vec3> samples = new ArrayList<>(TRAJECTORY_SEGMENTS + 1);
		for (int index = 0; index <= TRAJECTORY_SEGMENTS; index++) {
			double progress = index / (double)TRAJECTORY_SEGMENTS;
			double arc = 4.0 * arcHeight * progress * (1.0 - progress);
			samples.add(new Vec3(
				lerp(progress, start.x, end.x),
				lerp(progress, start.y, end.y) + arc,
				lerp(progress, start.z, end.z)
			));
		}
		return List.copyOf(samples);
	}

	private static AABB boundsOf(final List<Vec3> points) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (Vec3 point : points) {
			minX = Math.min(minX, point.x);
			minY = Math.min(minY, point.y);
			minZ = Math.min(minZ, point.z);
			maxX = Math.max(maxX, point.x);
			maxY = Math.max(maxY, point.y);
			maxZ = Math.max(maxZ, point.z);
		}
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
	}

	private static Vec3 clampHorizontal(final Vec3 vector, final double maximumLength) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.lengthSqr() <= maximumLength * maximumLength
			? horizontal
			: horizontal.normalize().scale(maximumLength);
	}

	private static Vec3 horizontalUnit(final Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.lengthSqr() < 1.0E-7 ? Vec3.ZERO : horizontal.normalize();
	}

	private static double lerp(final double progress, final double start, final double end) {
		return start + (end - start) * progress;
	}

	private static boolean isRelevantSquadmate(final AbstractSkeleton shooter, final Mob candidate) {
		return candidate != shooter
			&& candidate.isAlive()
			&& ZombieSquadCoordinator.areSquadmates(shooter, candidate);
	}

	public enum Status {
		CLEAR,
		ALLY_IN_CORRIDOR,
		ALLY_IN_BLAST_RADIUS
	}

	public record Assessment(Status status, int blockingEntityId) {
		private static final Assessment CLEAR = new Assessment(Status.CLEAR, -1);
	}

	public record FiringLane(Vec3 destination, Path path, int side) {
	}
}

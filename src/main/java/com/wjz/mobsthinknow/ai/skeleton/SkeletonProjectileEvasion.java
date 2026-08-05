package com.wjz.mobsthinknow.ai.skeleton;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * 局部来箭感知。查询只覆盖骷髅周围七格的实体分区，并由 Goal 每三 tick 调用一次，
 * 不会形成“每只怪扫描全世界投射物”的开销。
 */
public final class SkeletonProjectileEvasion {
	public static final double SCAN_RADIUS = 7.0;
	public static final double PREDICTION_HORIZON_TICKS = 8.0;
	public static final double SAFETY_RADIUS = 1.15;

	private SkeletonProjectileEvasion() {
	}

	public static Optional<Threat> nearestIncomingArrow(final AbstractSkeleton skeleton) {
		Vec3 center = skeleton.getBoundingBox().getCenter();
		return skeleton.level()
			.getEntitiesOfClass(
				AbstractArrow.class,
				skeleton.getBoundingBox().inflate(SCAN_RADIUS),
				arrow -> arrow.isAlive() && arrow.getOwner() != skeleton
			)
			.stream()
			.map(arrow -> classify(center, arrow))
			.flatMap(Optional::stream)
			.min(Comparator.comparingDouble(Threat::ticksUntilClosestApproach));
	}

	private static Optional<Threat> classify(final Vec3 skeletonCenter, final AbstractArrow arrow) {
		Vec3 relative = skeletonCenter.subtract(arrow.position());
		Vec3 velocity = arrow.getDeltaMovement();
		double time = SkeletonCombatMath.closestApproachTime(
			relative.x,
			relative.y,
			relative.z,
			velocity.x,
			velocity.y,
			velocity.z,
			PREDICTION_HORIZON_TICKS
		);
		if (!Double.isFinite(time)
			|| !SkeletonCombatMath.isIncomingProjectile(
				relative.x,
				relative.y,
				relative.z,
				velocity.x,
				velocity.y,
				velocity.z,
				PREDICTION_HORIZON_TICKS,
				SAFETY_RADIUS
			)) {
			return Optional.empty();
		}
		return Optional.of(new Threat(arrow, time));
	}

	/**
	 * 以骷髅攻击目标的朝向为本地坐标系，选择远离预测箭路的一侧。
	 * 这样骷髅仍能正面拉弓，却不会随机侧移进本来可以避开的箭路。
	 */
	public static int preferredDodgeDirection(
		final AbstractSkeleton skeleton,
		final LivingEntity target,
		final Threat threat,
		final int fallbackDirection
	) {
		double targetX = target.getX() - skeleton.getX();
		double targetZ = target.getZ() - skeleton.getZ();
		float combatYaw = (float)(Math.toDegrees(Math.atan2(targetZ, targetX)) - 90.0);
		Vec3 center = skeleton.getBoundingBox().getCenter();
		Vec3 velocity = threat.arrow().getDeltaMovement();
		return SkeletonCombatMath.saferProjectileDodgeDirection(
			center.x,
			center.z,
			threat.arrow().getX(),
			threat.arrow().getZ(),
			velocity.x,
			velocity.z,
			threat.ticksUntilClosestApproach(),
			combatYaw,
			fallbackDirection
		);
	}

	public record Threat(AbstractArrow arrow, double ticksUntilClosestApproach) {
	}
}

package com.wjz.mobsthinknow.ai.skeleton;

import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 每只受支持骷髅家族成员稳定不变的逃跑速度因子。
 *
 * <p>原来的智力速度曲线继续作为该骷髅的绝对上限；难度只抬高随机区间下界，
 * 所以困难难度平均更快，但任何个体都不会超过改动前的最大逃跑速度。</p>
 */
public final class SkeletonEscapeSpeedProfile {
	private static final String SPEED_FACTOR_TAG = "MobsThinkNowEscapeSpeedFactor";
	private static final float MINIMUM_FACTOR = 0.68F;
	private static final float MAXIMUM_FACTOR = 1.0F;

	private SkeletonEscapeSpeedProfile() {
	}

	/** 返回该个体已经固化的随机因子；首次访问按当时世界难度掷一次。 */
	public static float factor(final AbstractSkeleton skeleton) {
		SkeletonEscapeSpeedAccess access = (SkeletonEscapeSpeedAccess)skeleton;
		float factor = access.mobsthinknow$getSkeletonEscapeSpeedFactor();
		if (factor <= 0.0F) {
			factor = factorForRoll(
				skeleton.level().getDifficulty().getId(),
				skeleton.getRandom().nextFloat()
			);
			access.mobsthinknow$setSkeletonEscapeSpeedFactor(factor);
		}
		return clamp(factor, MINIMUM_FACTOR, MAXIMUM_FACTOR);
	}

	/** 智力曲线给出旧版上限，个体因子只向下产生差异。 */
	public static double pathSpeed(final AbstractSkeleton skeleton) {
		return SkeletonCombatMath.disengagePathSpeed(SkeletonIntelligence.get(skeleton)) * factor(skeleton);
	}

	public static void initialize(final AbstractSkeleton skeleton) {
		factor(skeleton);
	}

	public static void save(final AbstractSkeleton skeleton, final ValueOutput output) {
		output.putFloat(SPEED_FACTOR_TAG, factor(skeleton));
	}

	public static void load(final AbstractSkeleton skeleton, final ValueInput input) {
		float saved = input.getFloatOr(SPEED_FACTOR_TAG, 0.0F);
		((SkeletonEscapeSpeedAccess)skeleton).mobsthinknow$setSkeletonEscapeSpeedFactor(
			saved <= 0.0F ? 0.0F : clamp(saved, MINIMUM_FACTOR, MAXIMUM_FACTOR)
		);
	}

	/** 相同随机分位下：简单 ≤ 普通 ≤ 困难；所有难度的理论最大值都严格为 1。 */
	static float factorForRoll(final int difficultyId, final float roll) {
		float minimum = switch (Math.clamp(difficultyId, 0, 3)) {
			case 0, 1 -> 0.68F;
			case 2 -> 0.76F;
			default -> 0.84F;
		};
		return minimum + (MAXIMUM_FACTOR - minimum) * clamp(roll, 0.0F, 1.0F);
	}

	private static float clamp(final float value, final float minimum, final float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}
}

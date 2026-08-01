package com.wjz.mobsthinknow.ai.skeleton;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

/** 保留各骷髅变种原版射击节奏，避免智能状态机把毒箭和虚弱箭升级成机关枪。 */
public final class SkeletonBowIntervals {
	private static final int STANDARD_HARD_INTERVAL = 20;
	private static final int STANDARD_INTERVAL = 40;
	private static final int DELIBERATE_HARD_INTERVAL = 50;
	private static final int DELIBERATE_INTERVAL = 70;

	private SkeletonBowIntervals() {
	}

	public static int vanillaInterval(final AbstractSkeleton skeleton) {
		return vanillaInterval(skeleton.getType(), skeleton.level().getDifficulty());
	}

	public static int vanillaInterval(final EntityType<?> type, final Difficulty difficulty) {
		boolean deliberateVariant = type == EntityType.BOGGED || type == EntityType.PARCHED;
		if (difficulty == Difficulty.HARD) {
			return deliberateVariant ? DELIBERATE_HARD_INTERVAL : STANDARD_HARD_INTERVAL;
		}
		return deliberateVariant ? DELIBERATE_INTERVAL : STANDARD_INTERVAL;
	}
}

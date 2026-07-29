package com.wjz.mobsthinknow.ai.skeleton;

import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;

/** 骷髅 1～10 智力值的唯一业务入口。 */
public final class SkeletonIntelligence {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private SkeletonIntelligence() {
	}

	public static int get(final AbstractSkeleton skeleton) {
		return ((SkeletonIntelligenceAccess)skeleton).mobsthinknow$getSkeletonIntelligence();
	}

	public static void set(final AbstractSkeleton skeleton, final int intelligence) {
		int clamped = clamp(intelligence);
		((SkeletonIntelligenceAccess)skeleton).mobsthinknow$setSkeletonIntelligence(clamped);
		SkeletonIntelligenceName.updateExisting(skeleton, clamped);
	}

	public static int clamp(final int intelligence) {
		return Math.max(MINIMUM, Math.min(MAXIMUM, intelligence));
	}
}

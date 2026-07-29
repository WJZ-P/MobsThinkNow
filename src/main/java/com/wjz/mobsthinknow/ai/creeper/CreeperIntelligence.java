package com.wjz.mobsthinknow.ai.creeper;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Creeper;

/** 普通苦力怕 1～10 智力值的唯一业务入口。 */
public final class CreeperIntelligence {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private CreeperIntelligence() {
	}

	public static int get(final Creeper creeper) {
		return ((CreeperIntelligenceAccess)creeper).mobsthinknow$getCreeperIntelligence();
	}

	public static void set(final Creeper creeper, final int intelligence) {
		int clamped = clamp(intelligence);
		((CreeperIntelligenceAccess)creeper).mobsthinknow$setCreeperIntelligence(clamped);
		CreeperIntelligenceName.updateExisting(creeper, clamped);
	}

	/** 难度只整体抬高出生区间，不在战斗途中重新洗点。 */
	public static int roll(final Difficulty difficulty, final RandomSource random) {
		IntRange range = rangeForDifficulty(difficulty);
		return range.minimum() + random.nextInt(range.maximum() - range.minimum() + 1);
	}

	static IntRange rangeForDifficulty(final Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL, EASY -> new IntRange(1, 7);
			case NORMAL -> new IntRange(2, 9);
			case HARD -> new IntRange(4, 10);
		};
	}

	public static int clamp(final int intelligence) {
		return Math.max(MINIMUM, Math.min(MAXIMUM, intelligence));
	}

	record IntRange(int minimum, int maximum) {
	}
}

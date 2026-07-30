package com.wjz.mobsthinknow.ai.enderman;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.EnderMan;

/** 普通末影人 1～10 智力值的唯一业务入口。 */
public final class EndermanIntelligence {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private EndermanIntelligence() {
	}

	public static int get(final EnderMan enderman) {
		return ((EndermanIntelligenceAccess)enderman).mobsthinknow$getEndermanIntelligence();
	}

	public static void set(final EnderMan enderman, final int intelligence) {
		int clamped = clamp(intelligence);
		((EndermanIntelligenceAccess)enderman).mobsthinknow$setEndermanIntelligence(clamped);
		EndermanIntelligenceName.updateExisting(enderman, clamped);
	}

	/** 难度只抬高出生区间；个体生成后不会在战斗中重新洗点。 */
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

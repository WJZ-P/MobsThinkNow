package com.wjz.mobsthinknow.ai.giant;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.Giant;

/** 巨人僵尸 1～10 智力值的唯一业务入口。 */
public final class GiantIntelligence {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private GiantIntelligence() {
	}

	public static int get(final Giant giant) {
		return ((GiantIntelligenceAccess)giant).mobsthinknow$getGiantIntelligence();
	}

	public static void set(final Giant giant, final int intelligence) {
		int clamped = clamp(intelligence);
		((GiantIntelligenceAccess)giant).mobsthinknow$setGiantIntelligence(clamped);
		GiantIntelligenceName.updateExisting(giant, clamped);
	}

	public static int roll(final Difficulty difficulty, final RandomSource random) {
		IntRange range = rangeForDifficulty(difficulty);
		return range.minimum() + random.nextInt(range.maximum() - range.minimum() + 1);
	}

	static IntRange rangeForDifficulty(final Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL, EASY -> new IntRange(2, 7);
			case NORMAL -> new IntRange(4, 9);
			case HARD -> new IntRange(6, 10);
		};
	}

	public static int clamp(final int intelligence) {
		return Math.max(MINIMUM, Math.min(MAXIMUM, intelligence));
	}

	record IntRange(int minimum, int maximum) {
	}
}

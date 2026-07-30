package com.wjz.mobsthinknow.ai.spider;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.monster.spider.Spider;

/** 普通蜘蛛 1～10 智力值的唯一业务入口。 */
public final class SpiderIntelligence {
	public static final int MINIMUM = 1;
	public static final int MAXIMUM = 10;

	private SpiderIntelligence() {
	}

	public static int get(final Spider spider) {
		return ((SpiderIntelligenceAccess)spider).mobsthinknow$getSpiderIntelligence();
	}

	public static void set(final Spider spider, final int intelligence) {
		int clamped = clamp(intelligence);
		((SpiderIntelligenceAccess)spider).mobsthinknow$setSpiderIntelligence(clamped);
		SpiderIntelligenceName.updateExisting(spider, clamped);
	}

	/** 难度只决定出生区间，已生成个体的智力不会在战斗中重新洗点。 */
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

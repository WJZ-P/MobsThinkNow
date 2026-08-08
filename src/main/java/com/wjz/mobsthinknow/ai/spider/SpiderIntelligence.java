package com.wjz.mobsthinknow.ai.spider;

import com.wjz.mobsthinknow.ai.utility.SharedDifficultyAdapter;
import com.wjz.mobsthinknow.shared.ai.IntelligenceDistribution;
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
		return IntelligenceDistribution.roll(
			SharedDifficultyAdapter.fromMinecraft(difficulty),
			random.nextDouble()
		);
	}

	static IntRange rangeForDifficulty(final Difficulty difficulty) {
		IntelligenceDistribution.IntRange shared = IntelligenceDistribution.rangeFor(
			SharedDifficultyAdapter.fromMinecraft(difficulty)
		);
		return new IntRange(shared.minimum(), shared.maximum());
	}

	public static int clamp(final int intelligence) {
		return IntelligenceDistribution.clamp(intelligence);
	}

	record IntRange(int minimum, int maximum) {
	}
}

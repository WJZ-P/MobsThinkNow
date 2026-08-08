package com.wjz.mobsthinknow.paper.ai;

import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
import org.bukkit.Difficulty;

/** Bukkit 难度到纯 Java 共享枚举的唯一适配点。 */
public final class PaperDifficultyAdapter {
	private PaperDifficultyAdapter() {
	}

	public static DifficultyTier fromBukkit(final Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> DifficultyTier.PEACEFUL;
			case EASY -> DifficultyTier.EASY;
			case NORMAL -> DifficultyTier.NORMAL;
			case HARD -> DifficultyTier.HARD;
		};
	}
}

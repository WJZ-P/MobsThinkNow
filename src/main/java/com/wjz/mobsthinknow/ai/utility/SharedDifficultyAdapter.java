package com.wjz.mobsthinknow.ai.utility;

import com.wjz.mobsthinknow.shared.ai.DifficultyTier;
import net.minecraft.world.Difficulty;

/** Fabric/Minecraft 难度枚举到平台无关共享枚举的唯一转换边界。 */
public final class SharedDifficultyAdapter {
	private SharedDifficultyAdapter() {
	}

	public static DifficultyTier fromMinecraft(final Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> DifficultyTier.PEACEFUL;
			case EASY -> DifficultyTier.EASY;
			case NORMAL -> DifficultyTier.NORMAL;
			case HARD -> DifficultyTier.HARD;
		};
	}
}

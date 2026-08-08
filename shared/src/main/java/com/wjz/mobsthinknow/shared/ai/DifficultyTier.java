package com.wjz.mobsthinknow.shared.ai;

/** 两个平台都能稳定映射的四档世界难度。 */
public enum DifficultyTier {
	PEACEFUL,
	EASY,
	NORMAL,
	HARD;

	/** Minecraft 与 Bukkit 都使用 0～3 的相同难度序号；损坏输入按最近边界夹紧。 */
	public static DifficultyTier fromNumericId(final int difficultyId) {
		return switch (Math.clamp(difficultyId, 0, 3)) {
			case 0 -> PEACEFUL;
			case 1 -> EASY;
			case 2 -> NORMAL;
			default -> HARD;
		};
	}
}

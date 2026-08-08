package com.wjz.mobsthinknow.paper;

/** 运行期不可变配置快照；所有范围校验在重载时一次完成，AI tick 只读字段。 */
public record PaperSettings(
	boolean enabled,
	boolean showIntelligenceNames,
	boolean zombieRetreatEnabled,
	int zombieRetreatMinimumIntelligence,
	double retreatHealthThreshold,
	double retreatHeavyHitThreshold,
	int retreatMaximumTicks,
	double retreatSafeDistance,
	double retreatSpeed,
	int damageMemoryTicks,
	boolean skeletonSpacingEnabled,
	int skeletonSpacingMinimumIntelligence,
	double skeletonPreferredRange,
	int skeletonDisengageMaximumTicks,
	int skeletonDisengageCooldownTicks,
	boolean creeperTacticsEnabled,
	int creeperMinimumIntelligence,
	boolean creeperFlankingEnabled,
	double creeperMaximumFuseStartDistance,
	boolean creeperMovingFuseEnabled,
	double creeperMaximumFuseMovementSpeed,
	double creeperBlastConflictRadius,
	int creeperBlastSeparationTicks,
	int creeperBlastReservationLeaseTicks,
	int creeperBlastMaximumChecks
) {
	public static PaperSettings validated(
		final boolean enabled,
		final boolean showIntelligenceNames,
		final boolean zombieRetreatEnabled,
		final int zombieRetreatMinimumIntelligence,
		final double retreatHealthThreshold,
		final double retreatHeavyHitThreshold,
		final int retreatMaximumTicks,
		final double retreatSafeDistance,
		final double retreatSpeed,
		final int damageMemoryTicks,
		final boolean skeletonSpacingEnabled,
		final int skeletonSpacingMinimumIntelligence,
		final double skeletonPreferredRange,
		final int skeletonDisengageMaximumTicks,
		final int skeletonDisengageCooldownTicks,
		final boolean creeperTacticsEnabled,
		final int creeperMinimumIntelligence,
		final boolean creeperFlankingEnabled,
		final double creeperMaximumFuseStartDistance,
		final boolean creeperMovingFuseEnabled,
		final double creeperMaximumFuseMovementSpeed,
		final double creeperBlastConflictRadius,
		final int creeperBlastSeparationTicks,
		final int creeperBlastReservationLeaseTicks,
		final int creeperBlastMaximumChecks
	) {
		return new PaperSettings(
			enabled,
			showIntelligenceNames,
			zombieRetreatEnabled,
			Math.clamp(zombieRetreatMinimumIntelligence, 1, 10),
			finiteClamp(retreatHealthThreshold, 0.05, 0.50),
			finiteClamp(retreatHeavyHitThreshold, 0.05, 1.00),
			Math.clamp(retreatMaximumTicks, 20, 200),
			finiteClamp(retreatSafeDistance, 2.0, 16.0),
			finiteClamp(retreatSpeed, 1.0, 2.0),
			Math.clamp(damageMemoryTicks, 2, 40),
			skeletonSpacingEnabled,
			Math.clamp(skeletonSpacingMinimumIntelligence, 1, 10),
			finiteClamp(skeletonPreferredRange, 6.0, 24.0),
			Math.clamp(skeletonDisengageMaximumTicks, 20, 200),
			Math.clamp(skeletonDisengageCooldownTicks, 0, 100),
			creeperTacticsEnabled,
			Math.clamp(creeperMinimumIntelligence, 1, 10),
			creeperFlankingEnabled,
			finiteClamp(creeperMaximumFuseStartDistance, 3.0, 5.0),
			creeperMovingFuseEnabled,
			finiteClamp(creeperMaximumFuseMovementSpeed, 1.0, 1.5),
			finiteClamp(creeperBlastConflictRadius, 3.0, 12.0),
			Math.clamp(creeperBlastSeparationTicks, 5, 80),
			Math.clamp(creeperBlastReservationLeaseTicks, 10, 100),
			Math.clamp(creeperBlastMaximumChecks, 4, 64)
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

package com.wjz.mobsthinknow.paper.squad;

/** 混编小队独立配置快照，避免继续扩大平台通用配置构造器。 */
public record PaperSquadSettings(
	boolean enabled,
	boolean shareTargets,
	boolean preventFriendlyFire,
	double formationRadius,
	int minimumMembers,
	int maximumMembers,
	int rawScanLimit,
	int heartbeatTicks,
	int formingTimeoutTicks,
	int briefingTicks,
	int deploymentTimeoutTicks,
	int reorganizingTicks,
	double emergencyDistance,
	double maximumSeparation,
	int targetMemoryTicks
) {
	public static PaperSquadSettings validated(
		final boolean enabled,
		final boolean shareTargets,
		final boolean preventFriendlyFire,
		final double formationRadius,
		final int minimumMembers,
		final int maximumMembers,
		final int rawScanLimit,
		final int heartbeatTicks,
		final int formingTimeoutTicks,
		final int briefingTicks,
		final int deploymentTimeoutTicks,
		final int reorganizingTicks,
		final double emergencyDistance,
		final double maximumSeparation,
		final int targetMemoryTicks
	) {
		int maximum = Math.clamp(maximumMembers, 4, 100);
		return new PaperSquadSettings(
			enabled,
			shareTargets,
			preventFriendlyFire,
			finiteClamp(formationRadius, 8.0, 32.0),
			Math.clamp(minimumMembers, 2, Math.min(8, maximum)),
			maximum,
			Math.clamp(rawScanLimit, 16, 256),
			Math.clamp(heartbeatTicks, 2, 20),
			Math.clamp(formingTimeoutTicks, 10, 200),
			Math.clamp(briefingTicks, 10, 120),
			Math.clamp(deploymentTimeoutTicks, 10, 200),
			Math.clamp(reorganizingTicks, 5, 80),
			finiteClamp(emergencyDistance, 4.0, 16.0),
			finiteClamp(maximumSeparation, 16.0, 96.0),
			Math.clamp(targetMemoryTicks, 20, 400)
		);
	}

	private static double finiteClamp(final double value, final double minimum, final double maximum) {
		return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : minimum;
	}
}

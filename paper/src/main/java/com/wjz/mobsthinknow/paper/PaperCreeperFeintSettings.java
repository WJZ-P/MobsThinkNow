package com.wjz.mobsthinknow.paper;

/** Paper 假引爆阶段的独立配置；触发几何与硬引信上限由共享内核统一。 */
public record PaperCreeperFeintSettings(
	boolean enabled,
	int cooldownTicks,
	double repositionSpeed
) {
	public static PaperCreeperFeintSettings validated(
		final boolean enabled,
		final int cooldownTicks,
		final double repositionSpeed
	) {
		return new PaperCreeperFeintSettings(
			enabled,
			Math.clamp(cooldownTicks, 40, 1200),
			Double.isFinite(repositionSpeed) ? Math.clamp(repositionSpeed, 1.0, 1.5) : 1.16
		);
	}
}

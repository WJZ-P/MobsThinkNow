package com.wjz.mobsthinknow.config;

public final class MobsThinkNowConfig {
	public boolean enabled = true;
	public boolean zombieAiEnabled = true;
	public boolean shieldFlanking = true;
	public boolean packSurrounding = true;
	public int decisionIntervalTicks = 8;
	public int targetMemoryTicks = 60;
	public int maximumCoordinatedZombies = 8;
	public double coordinationRadius = 12.0;
	public double formationRadius = 2.8;
	public double flankBehindDistance = 2.2;
	public double flankSideDistance = 2.4;
	public double tacticalSpeedModifier = 1.08;
	public boolean debugLogging = false;

	public void validate() {
		this.decisionIntervalTicks = clamp(this.decisionIntervalTicks, 4, 40);
		this.targetMemoryTicks = clamp(this.targetMemoryTicks, 20, 200);
		this.maximumCoordinatedZombies = clamp(this.maximumCoordinatedZombies, 2, 16);
		this.coordinationRadius = clamp(this.coordinationRadius, 4.0, 24.0);
		this.formationRadius = clamp(this.formationRadius, 2.0, 6.0);
		this.flankBehindDistance = clamp(this.flankBehindDistance, 1.0, 6.0);
		this.flankSideDistance = clamp(this.flankSideDistance, 1.0, 6.0);
		this.tacticalSpeedModifier = clamp(this.tacticalSpeedModifier, 0.75, 1.35);
	}

	private static int clamp(final int value, final int minimum, final int maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static double clamp(final double value, final double minimum, final double maximum) {
		if (!Double.isFinite(value)) {
			return minimum;
		}

		return Math.max(minimum, Math.min(maximum, value));
	}
}

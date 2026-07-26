package com.wjz.mobsthinknow.config;

public final class MobsThinkNowConfig {
	public static final int DEFAULT_MAXIMUM_COORDINATED_ZOMBIES = 20;
	public static final int MINIMUM_MAXIMUM_COORDINATED_ZOMBIES = 4;
	public static final int MAXIMUM_MAXIMUM_COORDINATED_ZOMBIES = 100;

	public boolean enabled = true;
	public boolean zombieAiEnabled = true;
	public boolean shieldFlanking = true;
	public boolean packSurrounding = true;
	public int decisionIntervalTicks = 8;
	public int targetMemoryTicks = 60;
	public int maximumCoordinatedZombies = DEFAULT_MAXIMUM_COORDINATED_ZOMBIES;
	public double coordinationRadius = 12.0;
	public int minimumSquadSize = 3;
	public int squadFormationIntervalTicks = 10;
	public int squadFormationTicks = 12;
	public int rallyTimeoutTicks = 60;
	public int briefingTicks = 24;
	public int deploymentTimeoutTicks = 80;
	public int regroupTicks = 15;
	public int memberHeartbeatTimeoutTicks = 40;
	public double rallyRadius = 1.8;
	public double emergencyEngageDistance = 5.0;
	public double rallyQuorum = 0.7;
	public double deploymentQuorum = 0.6;
	public double formationRadius = 2.8;
	public double flankBehindDistance = 2.2;
	public double flankSideDistance = 2.4;
	public double tacticalSpeedModifier = 1.08;
	public boolean debugLogging = false;

	public void validate() {
		this.decisionIntervalTicks = clamp(this.decisionIntervalTicks, 4, 40);
		this.targetMemoryTicks = clamp(this.targetMemoryTicks, 20, 200);
		this.maximumCoordinatedZombies = clamp(
			this.maximumCoordinatedZombies,
			MINIMUM_MAXIMUM_COORDINATED_ZOMBIES,
			MAXIMUM_MAXIMUM_COORDINATED_ZOMBIES
		);
		this.coordinationRadius = clamp(this.coordinationRadius, 4.0, 24.0);
		this.minimumSquadSize = clamp(this.minimumSquadSize, 2, this.maximumCoordinatedZombies);
		this.squadFormationIntervalTicks = clamp(this.squadFormationIntervalTicks, 4, 40);
		this.squadFormationTicks = clamp(this.squadFormationTicks, 4, 60);
		this.rallyTimeoutTicks = clamp(this.rallyTimeoutTicks, 20, 200);
		this.briefingTicks = clamp(this.briefingTicks, 8, 80);
		this.deploymentTimeoutTicks = clamp(this.deploymentTimeoutTicks, 20, 200);
		this.regroupTicks = clamp(this.regroupTicks, 5, 60);
		this.memberHeartbeatTimeoutTicks = clamp(this.memberHeartbeatTimeoutTicks, 20, 100);
		this.rallyRadius = clamp(this.rallyRadius, 1.0, 4.0);
		this.emergencyEngageDistance = clamp(this.emergencyEngageDistance, 2.0, 12.0);
		this.rallyQuorum = clamp(this.rallyQuorum, 0.5, 1.0);
		this.deploymentQuorum = clamp(this.deploymentQuorum, 0.4, 1.0);
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

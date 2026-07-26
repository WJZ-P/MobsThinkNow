package com.wjz.mobsthinknow.config;

public final class MobsThinkNowConfig {
	public static final int DEFAULT_MAXIMUM_COORDINATED_ZOMBIES = 20;
	public static final int MINIMUM_MAXIMUM_COORDINATED_ZOMBIES = 4;
	public static final int MAXIMUM_MAXIMUM_COORDINATED_ZOMBIES = 100;

	public boolean enabled = true;
	public boolean zombieAiEnabled = true;
	public boolean shieldFlanking = true;
	public boolean packSurrounding = true;
	public boolean squadVisualEffects = true;
	public boolean squadRoleNameTags = true;
	/** 诱饵勾引战术：智力 6+ 的首领派出诱饵吸引注意力，侧翼趁目标视线离开时直线突袭。 */
	public boolean baitTactics = true;
	/** 组队期间的全员移速加成（ADD_MULTIPLIED_TOTAL），离队自动移除，0 关闭。 */
	public double squadSpeedBonus = 0.10;
	/** 武装小队总开关。默认关闭：持械概率、兵种职位、破盾和包抄加速全部由它统一控制。 */
	public boolean armedSquads = false;
	public double armedChanceEasy = 0.10;
	public double armedChanceNormal = 0.30;
	/** 困难模式下"一般僵尸都持械"：基础 85%，再乘区域难度系数。 */
	public double armedChanceHard = 0.85;
	/** 持械僵尸额外获得盾牌的概率；简单难度不发盾，普通减半，困难全额。 */
	public double armedShieldChance = 0.25;
	public double armedShieldBreakSeconds = 3.0;
	public double armedFlankSpeedBonus = 0.12;
	/** 同一小队的僵尸互相误伤时不转移仇恨，继续合攻原目标。 */
	public boolean squadIgnoreFriendlyFire = true;
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
		this.squadSpeedBonus = clamp(this.squadSpeedBonus, 0.0, 0.5);
		this.armedChanceEasy = clamp(this.armedChanceEasy, 0.0, 1.0);
		this.armedChanceNormal = clamp(this.armedChanceNormal, 0.0, 1.0);
		this.armedChanceHard = clamp(this.armedChanceHard, 0.0, 1.0);
		this.armedShieldChance = clamp(this.armedShieldChance, 0.0, 1.0);
		this.armedShieldBreakSeconds = clamp(this.armedShieldBreakSeconds, 0.0, 10.0);
		this.armedFlankSpeedBonus = clamp(this.armedFlankSpeedBonus, 0.0, 0.35);
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

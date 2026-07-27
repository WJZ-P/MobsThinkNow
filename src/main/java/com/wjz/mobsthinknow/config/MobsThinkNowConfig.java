package com.wjz.mobsthinknow.config;

public final class MobsThinkNowConfig {
	public static final int DEFAULT_MAXIMUM_COORDINATED_ZOMBIES = 20;
	public static final int MINIMUM_MAXIMUM_COORDINATED_ZOMBIES = 4;
	public static final int MAXIMUM_MAXIMUM_COORDINATED_ZOMBIES = 100;
	public static final int DEFAULT_RETREAT_MAXIMUM_TICKS = 100;
	public static final int MINIMUM_RETREAT_MAXIMUM_TICKS = 20;
	public static final int MAXIMUM_RETREAT_MAXIMUM_TICKS = 200;
	public static final double DEFAULT_RETREAT_SAFE_DISTANCE = 5.0;
	public static final double MINIMUM_RETREAT_SAFE_DISTANCE = 2.0;
	public static final double MAXIMUM_RETREAT_SAFE_DISTANCE = 16.0;
	public static final double DEFAULT_RETREAT_SPEED_MODIFIER = 1.50;
	public static final double MINIMUM_RETREAT_SPEED_MODIFIER = 1.0;
	public static final double MAXIMUM_RETREAT_SPEED_MODIFIER = 2.0;
	public static final int DEFAULT_FOOD_MINIMUM_INTELLIGENCE = 6;
	public static final int MINIMUM_FOOD_MINIMUM_INTELLIGENCE = 4;
	public static final int MAXIMUM_FOOD_MINIMUM_INTELLIGENCE = 10;
	public static final int DEFAULT_TERRAIN_MINIMUM_INTELLIGENCE = 8;
	public static final int MINIMUM_TERRAIN_MINIMUM_INTELLIGENCE = 6;
	public static final int MAXIMUM_TERRAIN_MINIMUM_INTELLIGENCE = 10;
	public static final int DEFAULT_TERRAIN_BLOCK_INVENTORY_LIMIT = 8;
	public static final int MINIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT = 3;
	public static final int MAXIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT = 16;
	public static final double DEFAULT_SPECIAL_EQUIPMENT_DROP_CHANCE = 0.085;

	public boolean enabled = true;
	public boolean zombieAiEnabled = true;
	public boolean shieldFlanking = true;
	public boolean packSurrounding = true;
	public boolean squadVisualEffects = true;
	public boolean squadRoleNameTags = true;
	/** 每只普通僵尸是否获得随世界难度整体上移的速度、生命、伤害和追踪距离差异；固定声线属于表现层。 */
	public boolean individualTraits = true;
	/** 拉扯机制：受到生物攻击且生命值低于阈值后，先限时远离攻击者，再重新参战。 */
	public boolean retreatTactics = true;
	/** 以最大生命值为基准；默认 0.20 表示剩余生命值不高于 20%。 */
	public double retreatHealthThreshold = 0.20;
	/** 单次实际扣血达到最大生命值的该比例时，即使仍是高血量也会撤退；默认 30%。 */
	public double retreatHeavyHitThreshold = 0.30;
	/** 单次撤退的绝对时限；途中再次受击只更新威胁方向，不延长这个上限。 */
	public int retreatMaximumTicks = DEFAULT_RETREAT_MAXIMUM_TICKS;
	/** 与当前攻击者达到该水平距离时提前结束撤退。 */
	public double retreatSafeDistance = DEFAULT_RETREAT_SAFE_DISTANCE;
	/** 撤退寻路速度倍率；默认 1.5，让脱离动作比普通追击更果断。 */
	public double retreatSpeedModifier = DEFAULT_RETREAT_SPEED_MODIFIER;
	/** 低血高智力僵尸是否会搜索地上的食物并吃掉一份来回血。 */
	public boolean foodScavenging = true;
	/** 具备觅食能力所需的最低智力；默认 6，属于中高智力区间。 */
	public int foodMinimumIntelligence = DEFAULT_FOOD_MINIMUM_INTELLIGENCE;
	/** 高智力僵尸是否可按需采集软方块，并针对铁傀儡或高处目标搭建立柱。 */
	public boolean terrainTactics = true;
	/** 掌握采集和建造战术所需的最低智力；默认 8。 */
	public int terrainMinimumIntelligence = DEFAULT_TERRAIN_MINIMUM_INTELLIGENCE;
	/** 隐藏建筑材料槽的容量；每次只采集一块，死亡时会完整掉出。 */
	public int terrainBlockInventoryLimit = DEFAULT_TERRAIN_BLOCK_INVENTORY_LIMIT;
	/** 组队期间的全员移速加成（ADD_MULTIPLIED_TOTAL），离队自动移除，0 关闭。 */
	public double squadSpeedBonus = 0.10;
	/** 武装小队总开关。默认关闭：持械概率、兵种职位、破盾和包抄加速全部由它统一控制。 */
	public boolean armedSquads = false;
	/** 所有持剑/斧的普通僵尸按武器冷却周旋；斧手会优先尝试跳劈。 */
	public boolean weaponCombatTactics = true;
	/** 原版自然生成的持矛僵尸是否自动装备鞘翅和 16～64 枚烟花，改用空中突刺。 */
	public boolean spearAirAssault = true;
	public double armedChanceEasy = 0.10;
	public double armedChanceNormal = 0.30;
	/** 困难模式下"一般僵尸都持械"：基础 85%，再乘区域难度系数。 */
	public double armedChanceHard = 0.85;
	/** 持械僵尸额外获得盾牌的概率；简单难度不发盾，普通减半，困难全额。 */
	public double armedShieldChance = 0.25;
	public double armedShieldBreakSeconds = 3.0;
	public double armedFlankSpeedBonus = 0.12;
	/** 水桶/岩浆桶辅助兵的总开关；独立于 armedSquads，因此关闭武装小队也可以体验辅助兵种。 */
	public boolean specialEquipment = true;
	/** 普通僵尸出生时成为水桶辅助兵的基础概率；实际概率会随难度略微上移。 */
	public double waterBucketChance = 0.04;
	/** 普通僵尸出生时成为岩浆骚扰兵的基础概率；实际概率会随难度略微上移。 */
	public double lavaBucketChance = 0.02;
	/** 特殊装备死亡掉落率；默认 0.085，与原版 Mob 装备槽完全一致。 */
	public double specialEquipmentDropChance = DEFAULT_SPECIAL_EQUIPMENT_DROP_CHANCE;
	/** 是否启用放置、拉扯和回收流体的辅助战术。 */
	public boolean fluidTactics = true;
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
		this.retreatHealthThreshold = clamp(this.retreatHealthThreshold, 0.05, 0.5);
		this.retreatHeavyHitThreshold = clamp(this.retreatHeavyHitThreshold, 0.05, 1.0);
		this.retreatMaximumTicks = clamp(
			this.retreatMaximumTicks,
			MINIMUM_RETREAT_MAXIMUM_TICKS,
			MAXIMUM_RETREAT_MAXIMUM_TICKS
		);
		this.retreatSafeDistance = clamp(
			this.retreatSafeDistance,
			MINIMUM_RETREAT_SAFE_DISTANCE,
			MAXIMUM_RETREAT_SAFE_DISTANCE
		);
		this.retreatSpeedModifier = clamp(
			this.retreatSpeedModifier,
			MINIMUM_RETREAT_SPEED_MODIFIER,
			MAXIMUM_RETREAT_SPEED_MODIFIER
		);
		this.foodMinimumIntelligence = clamp(
			this.foodMinimumIntelligence,
			MINIMUM_FOOD_MINIMUM_INTELLIGENCE,
			MAXIMUM_FOOD_MINIMUM_INTELLIGENCE
		);
		this.terrainMinimumIntelligence = clamp(
			this.terrainMinimumIntelligence,
			MINIMUM_TERRAIN_MINIMUM_INTELLIGENCE,
			MAXIMUM_TERRAIN_MINIMUM_INTELLIGENCE
		);
		this.terrainBlockInventoryLimit = clamp(
			this.terrainBlockInventoryLimit,
			MINIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT,
			MAXIMUM_TERRAIN_BLOCK_INVENTORY_LIMIT
		);
		this.armedChanceEasy = clamp(this.armedChanceEasy, 0.0, 1.0);
		this.armedChanceNormal = clamp(this.armedChanceNormal, 0.0, 1.0);
		this.armedChanceHard = clamp(this.armedChanceHard, 0.0, 1.0);
		this.armedShieldChance = clamp(this.armedShieldChance, 0.0, 1.0);
		this.armedShieldBreakSeconds = clamp(this.armedShieldBreakSeconds, 0.0, 10.0);
		this.armedFlankSpeedBonus = clamp(this.armedFlankSpeedBonus, 0.0, 0.35);
		this.waterBucketChance = clamp(this.waterBucketChance, 0.0, 1.0);
		this.lavaBucketChance = clamp(this.lavaBucketChance, 0.0, 1.0);
		this.specialEquipmentDropChance = clamp(this.specialEquipmentDropChance, 0.0, 1.0);
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

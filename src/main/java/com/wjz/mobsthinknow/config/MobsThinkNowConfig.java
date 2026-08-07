package com.wjz.mobsthinknow.config;

public final class MobsThinkNowConfig {
	public static final int DEFAULT_MAXIMUM_COORDINATED_ZOMBIES = 20;
	public static final int MINIMUM_MAXIMUM_COORDINATED_ZOMBIES = 4;
	public static final int MAXIMUM_MAXIMUM_COORDINATED_ZOMBIES = 100;
	public static final int DEFAULT_RETREAT_MAXIMUM_TICKS = 100;
	public static final int MINIMUM_RETREAT_MAXIMUM_TICKS = 20;
	public static final int MAXIMUM_RETREAT_MAXIMUM_TICKS = 200;
	public static final double DEFAULT_SQUAD_CASUALTY_HEALTH_THRESHOLD = 0.30;
	public static final double MINIMUM_SQUAD_CASUALTY_HEALTH_THRESHOLD = 0.10;
	public static final double MAXIMUM_SQUAD_CASUALTY_HEALTH_THRESHOLD = 0.50;
	public static final int DEFAULT_SQUAD_CASUALTY_RESPONSE_TICKS = 60;
	public static final int MINIMUM_SQUAD_CASUALTY_RESPONSE_TICKS = 40;
	public static final int MAXIMUM_SQUAD_CASUALTY_RESPONSE_TICKS = 120;
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
	public static final double DEFAULT_ENGINEER_SPAWN_CHANCE = 0.08;
	public static final double DEFAULT_SPECIAL_EQUIPMENT_DROP_CHANCE = 0.085;
	public static final double DEFAULT_SPEAR_ROCKET_EFFICIENCY = 0.50;
	public static final double MINIMUM_SPEAR_ROCKET_EFFICIENCY = 0.0;
	public static final double MAXIMUM_SPEAR_ROCKET_EFFICIENCY = 1.0;
	public static final double DEFAULT_SKELETON_PREFERRED_RANGE = 10.0;
	public static final double MINIMUM_SKELETON_PREFERRED_RANGE = 6.0;
	public static final double MAXIMUM_SKELETON_PREFERRED_RANGE = 16.0;
	public static final double DEFAULT_SKELETON_AIM_PREDICTION_STRENGTH = 0.65;
	public static final double DEFAULT_SKELETON_CROSSBOW_CHANCE = 0.18;
	public static final double DEFAULT_SKELETON_FIREWORK_CROSSBOW_CHANCE = 0.25;
	public static final double DEFAULT_CREEPER_MAXIMUM_FUSE_START_DISTANCE = 4.0;
	public static final double MINIMUM_CREEPER_MAXIMUM_FUSE_START_DISTANCE = 3.0;
	public static final double MAXIMUM_CREEPER_MAXIMUM_FUSE_START_DISTANCE = 5.0;
	public static final double DEFAULT_CREEPER_FUSE_MOVEMENT_SPEED = 1.25;
	public static final double MINIMUM_CREEPER_FUSE_MOVEMENT_SPEED = 1.0;
	public static final double MAXIMUM_CREEPER_FUSE_MOVEMENT_SPEED = 1.5;
	public static final int DEFAULT_CREEPER_FUSE_FEINT_COOLDOWN_TICKS = 240;
	public static final int MINIMUM_CREEPER_FUSE_FEINT_COOLDOWN_TICKS = 80;
	public static final int MAXIMUM_CREEPER_FUSE_FEINT_COOLDOWN_TICKS = 600;
	public static final double DEFAULT_SPIDER_CREEPER_SEARCH_RADIUS = 8.0;
	public static final double MINIMUM_SPIDER_CREEPER_SEARCH_RADIUS = 4.0;
	public static final double MAXIMUM_SPIDER_CREEPER_SEARCH_RADIUS = 16.0;
	public static final double DEFAULT_SPIDER_CREEPER_CARRIER_SPEED = 1.40;
	public static final double MINIMUM_SPIDER_CREEPER_CARRIER_SPEED = 1.10;
	public static final double MAXIMUM_SPIDER_CREEPER_CARRIER_SPEED = 1.70;
	public static final int DEFAULT_SPIDER_WEB_TRAP_COOLDOWN_TICKS = 240;
	public static final int MINIMUM_SPIDER_WEB_TRAP_COOLDOWN_TICKS = 80;
	public static final int MAXIMUM_SPIDER_WEB_TRAP_COOLDOWN_TICKS = 600;
	public static final int DEFAULT_SPIDER_WEB_TRAP_LIFETIME_TICKS = 160;
	public static final int MINIMUM_SPIDER_WEB_TRAP_LIFETIME_TICKS = 60;
	public static final int MAXIMUM_SPIDER_WEB_TRAP_LIFETIME_TICKS = 400;
	public static final double DEFAULT_ENDERMAN_CREEPER_SEARCH_RADIUS = 16.0;
	public static final double MINIMUM_ENDERMAN_CREEPER_SEARCH_RADIUS = 6.0;
	public static final double MAXIMUM_ENDERMAN_CREEPER_SEARCH_RADIUS = 32.0;
	public static final int DEFAULT_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS = 300;
	public static final int MINIMUM_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS = 100;
	public static final int MAXIMUM_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS = 1200;
	public static final double DEFAULT_ENDERMAN_CREEPER_DROP_DISTANCE = 3.0;
	public static final double MINIMUM_ENDERMAN_CREEPER_DROP_DISTANCE = 2.0;
	public static final double MAXIMUM_ENDERMAN_CREEPER_DROP_DISTANCE = 6.0;
	public static final double DEFAULT_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE = 0.80;
	public static final double MINIMUM_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE = 0.0;
	public static final double MAXIMUM_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE = 1.0;
	public static final double DEFAULT_GIANT_ZOMBIE_SPAWN_CHANCE = 0.01;
	public static final double DEFAULT_GIANT_ZOMBIE_MAXIMUM_HEALTH = 160.0;
	public static final double MINIMUM_GIANT_ZOMBIE_MAXIMUM_HEALTH = 40.0;
	public static final double MAXIMUM_GIANT_ZOMBIE_MAXIMUM_HEALTH = 400.0;
	public static final double DEFAULT_GIANT_ZOMBIE_ATTACK_DAMAGE = 14.0;
	public static final double MINIMUM_GIANT_ZOMBIE_ATTACK_DAMAGE = 4.0;
	public static final double MAXIMUM_GIANT_ZOMBIE_ATTACK_DAMAGE = 40.0;
	public static final double DEFAULT_GIANT_ZOMBIE_MOVEMENT_SPEED = 0.16;
	public static final double MINIMUM_GIANT_ZOMBIE_MOVEMENT_SPEED = 0.08;
	public static final double MAXIMUM_GIANT_ZOMBIE_MOVEMENT_SPEED = 0.22;
	public static final int DEFAULT_SWORD_FEINT_MINIMUM_INTELLIGENCE = 7;
	public static final double DEFAULT_SWORD_FEINT_CHANCE = 0.35;
	public static final int DEFAULT_SHIELD_BASH_MINIMUM_INTELLIGENCE = 7;
	public static final double DEFAULT_SHIELD_BASH_CHANCE = 0.35;
	public static final double DEFAULT_SHIELD_BASH_DAMAGE = 2.0;
	public static final double DEFAULT_SHIELD_BASH_KNOCKBACK = 1.25;
	public static final double MAXIMUM_SHIELD_BASH_DAMAGE = 8.0;
	public static final double MAXIMUM_SHIELD_BASH_KNOCKBACK = 3.0;
	public static final double DEFAULT_BLAZE_PREFERRED_RANGE = 10.0;
	public static final double MINIMUM_BLAZE_PREFERRED_RANGE = 7.0;
	public static final double MAXIMUM_BLAZE_PREFERRED_RANGE = 16.0;
	public static final double DEFAULT_NETHER_PREDICTION_STRENGTH = 0.70;
	public static final double DEFAULT_HOGLIN_CHARGE_SPEED = 1.15;
	public static final double MINIMUM_HOGLIN_CHARGE_SPEED = 0.85;
	public static final double MAXIMUM_HOGLIN_CHARGE_SPEED = 1.45;
	public static final double DEFAULT_MAGMA_CUBE_POUNCE_SPEED = 0.68;
	public static final double MINIMUM_MAGMA_CUBE_POUNCE_SPEED = 0.35;
	public static final double MAXIMUM_MAGMA_CUBE_POUNCE_SPEED = 1.00;

	public boolean enabled = true;
	public boolean zombieAiEnabled = true;
	/** 普通持弓骷髅使用距离分带、目标朝向锁定、持续侧移与近身拉扯；关闭后委托原版弓箭 Goal。 */
	public boolean skeletonAiEnabled = true;
	/** 任意当前敌对目标贴脸时使用优先级 1 的独立 Goal 放下远程武器并正向逃跑。 */
	public boolean skeletonEmergencyDisengage = true;
	/** 普通骷髅自然生成时可替换为弩手；其他家族变种保留各自特殊箭与辨识度。 */
	public boolean skeletonCrossbows = true;
	public double skeletonCrossbowChance = DEFAULT_SKELETON_CROSSBOW_CHANCE;
	/** 仅智力 7～10 的弩手有机会携带有限爆炸烟花，耗尽后自动继续使用普通箭。 */
	public double skeletonFireworkCrossbowChance = DEFAULT_SKELETON_FIREWORK_CROSSBOW_CHANCE;
	/** 搜索附近真实掩体，在遮蔽格蓄力后移动到相邻射界格探头射击并缩回。 */
	public boolean skeletonCoverPeeking = true;
	/** 同队友军连续挡住预测弹道时，地面射手有界寻找可达侧射位，而不是原地无限蓄力。 */
	public boolean skeletonFiringLaneReposition = true;
	/** 每三 tick 查询七格内来箭，并只对八 tick 内会穿过碰撞安全半径的箭执行侧闪。 */
	public boolean skeletonProjectileDodging = true;
	/** 保留原版散布，只在水平方向对移动目标加入有上限的速度提前量。 */
	public boolean skeletonPredictiveAim = true;
	public double skeletonPreferredRange = DEFAULT_SKELETON_PREFERRED_RANGE;
	public double skeletonAimPredictionStrength = DEFAULT_SKELETON_AIM_PREDICTION_STRENGTH;
	/** 普通苦力怕使用预判截击、观察感知、绕盾和移动引信状态机。 */
	public boolean creeperAiEnabled = true;
	/** 中高智力个体被目标正面观察或举盾时，优先前往目标侧后方的稳定分流点。 */
	public boolean creeperFlanking = true;
	/** 引信鸣响后继续追向预测爆点；原版 30 tick 引信与首次嘶声保持不变。 */
	public boolean creeperMovingFuse = true;
	/** IQ 8～10 普通苦力怕可在真实起爆圈外短促点燃，主动退火后侧移骗取目标转向或举盾。 */
	public boolean creeperFuseFeints = true;
	/** 两次佯爆之间的基础冷却；个体会在 80%～120% 范围随机错峰。 */
	public int creeperFuseFeintCooldownTicks = DEFAULT_CREEPER_FUSE_FEINT_COOLDOWN_TICKS;
	/** 同队苦力怕进入引信后，其他僵尸、骷髅、苦力怕与蜘蛛会退出真实爆炸伤害范围。 */
	public boolean creeperSquadEvacuation = true;
	/** 同队同一目标或重叠区域只允许一个普通引信提交；外部强制点燃仍会被完整记录。 */
	public boolean creeperBlastReservations = true;
	/** 高智力个体只对可被爆炸破坏的第一层软墙保留引信；同时服从 mobGriefing。 */
	public boolean creeperWallBreaching = true;
	/** IQ 10 普通个体的最远起爆距离；低智力按 3 格原版距离向该值插值，带电个体另加半格。 */
	public double creeperMaximumFuseStartDistance = DEFAULT_CREEPER_MAXIMUM_FUSE_START_DISTANCE;
	/** 移动引信的绝对寻路速度上限；智力与难度只决定个体接近该上限的程度。 */
	public double creeperFuseMovementSpeed = DEFAULT_CREEPER_FUSE_MOVEMENT_SPEED;
	/** 普通蜘蛛获得 1～10 智力，并接管跳扑与贴身接敌 Goal；洞穴蜘蛛保持原版。 */
	public boolean spiderAiEnabled = true;
	/** 智力 4 以上的蜘蛛会预测目标速度，将 2.5～7 格跳扑落点放到移动前方。 */
	public boolean spiderPredictivePounce = true;
	/** 智力 5 以上的蜘蛛命中后先绕到下一次跳扑距离，而不是原地持续贴脸。 */
	public boolean spiderHitAndRun = true;
	/** IQ 7～10 蜘蛛会在目标预测路径放置有声效、会自动清理的限时蛛网；完整服从 mobGriefing。 */
	public boolean spiderWebTraps = true;
	/** 单只蜘蛛两次成功吐网之间的基础冷却；智力、难度和随机错峰只会小幅调整。 */
	public int spiderWebTrapCooldownTicks = DEFAULT_SPIDER_WEB_TRAP_COOLDOWN_TICKS;
	/** 临时蛛网存在时间；维度卸载或服务器关停时会提前恢复原方块。 */
	public int spiderWebTrapLifetimeTicks = DEFAULT_SPIDER_WEB_TRAP_LIFETIME_TICKS;
	/** 蜘蛛与普通苦力怕会限频局部配对，真实骑乘后由蜘蛛高速投送至目标身边。 */
	public boolean spiderCreeperCoordination = true;
	/** 每次配对只查询这个局部半径，默认 8 格；搜索间隔随机 10～20 tick。 */
	public double spiderCreeperSearchRadius = DEFAULT_SPIDER_CREEPER_SEARCH_RADIUS;
	/** 合体运输的寻路速度上限；智力与难度决定个体从 1.15 向该上限插值。 */
	public double spiderCreeperCarrierSpeed = DEFAULT_SPIDER_CREEPER_CARRIER_SPEED;
	/** 载人前与投送改道时有界检查路径宽度、头顶净空和危险落差；失败会安全卸载并冷却。 */
	public boolean spiderTransportRouteAssessment = true;
	/** 普通末影人保留原版中立/凝视仇恨，只在已经敌对生存玩家后启用额外战术。 */
	public boolean endermanAiEnabled = true;
	/** 敌对玩家距离足够远时，末影人可抱取附近未起爆苦力怕并传送投放。 */
	public boolean endermanCreeperDelivery = true;
	/** 末影人每轮局部候选查询的配置上限；低智力个体只使用其中一部分。 */
	public double endermanCreeperSearchRadius = DEFAULT_ENDERMAN_CREEPER_SEARCH_RADIUS;
	/** 一次成功投放后的基础冷却；智力会小幅缩短、个体随机量会重新错峰。 */
	public int endermanCreeperDeliveryCooldownTicks = DEFAULT_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS;
	/** 投放点相对玩家的期望水平距离；正面与后方候选都会检查安全落脚。 */
	public double endermanCreeperDropDistance = DEFAULT_ENDERMAN_CREEPER_DROP_DISTANCE;
	/** 投送到玩家当前视线正前方的概率；剩余概率保留后方奇袭变化。 */
	public double endermanCreeperFrontDeliveryChance = DEFAULT_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE;
	/** 原版普通僵尸出生时替换为有完整 AI 的巨人僵尸，并允许其加入混编小队。 */
	public boolean giantZombieAiEnabled = true;
	/** 普通难度的替换概率；简单乘 0.4、困难乘 2，空间容纳不下巨人时保留普通僵尸。 */
	public double giantZombieSpawnChance = DEFAULT_GIANT_ZOMBIE_SPAWN_CHANCE;
	/** 巨人的基础最大生命；生成后不会随难度重新洗点。 */
	public double giantZombieMaximumHealth = DEFAULT_GIANT_ZOMBIE_MAXIMUM_HEALTH;
	/** 巨人的基础近战伤害；原版 50 点会过度秒杀，因此使用更可控的重击值。 */
	public double giantZombieAttackDamage = DEFAULT_GIANT_ZOMBIE_ATTACK_DAMAGE;
	/** 巨人的基础移动速度；普通僵尸是 0.23。 */
	public double giantZombieMovementSpeed = DEFAULT_GIANT_ZOMBIE_MOVEMENT_SPEED;
	/** 是否启用头顶射手、双手抱取队友以及对目标的抛投战术。 */
	public boolean giantZombiePayloadThrowing = true;
	/** 以带前摇/命中帧/后摇的横扫、拍击、踩踏和双拳砸地替代原版瞬时挥拳。 */
	public boolean giantZombieMeleeActions = true;
	/** 下界战术总开关；关闭后猪灵、烈焰人、恶魂、疣猪兽与岩浆怪全部委托原版 AI。 */
	public boolean netherAiEnabled = true;
	/** 客户端按同步职业切换下界单位贴图；关闭后尊重原版或资源包贴图，战术参数保持不变。 */
	public boolean netherProfessionSkins = true;
	/** 猪灵弩手按实体散列分配射击侧翼，近战猪灵与蛮兵从不同角度接敌；不执行同伴全量扫描。 */
	public boolean piglinFormationTactics = true;
	/** 烈焰人在目标外围盘旋、近身拉开，并按难度发射二至四连发的可读弹幕。 */
	public boolean blazeCombatTactics = true;
	public double blazePreferredRange = DEFAULT_BLAZE_PREFERRED_RANGE;
	/** 烈焰弹与恶魂火球对移动目标使用有上限的速度提前量；仍保留原版碰撞与误差。 */
	public double netherPredictionStrength = DEFAULT_NETHER_PREDICTION_STRENGTH;
	/** 恶魂在开火后更换炮击位置，并将目标速度纳入下一枚火球的瞄准。 */
	public boolean ghastArtilleryTactics = true;
	/** 成年疣猪兽与僵尸疣猪兽在安全直线上先低头蓄力，再执行一次有冷却的冲撞。 */
	public boolean hoglinChargeTactics = true;
	public double hoglinChargeSpeed = DEFAULT_HOGLIN_CHARGE_SPEED;
	/** 岩浆怪起跳时预测目标短期移动，为本次跳跃加入有限水平扑击速度。 */
	public boolean magmaCubePredictivePounce = true;
	public double magmaCubePounceSpeed = DEFAULT_MAGMA_CUBE_POUNCE_SPEED;
	public boolean shieldFlanking = true;
	public boolean packSurrounding = true;
	public boolean squadVisualEffects = true;
	/** 客户端把普通僵尸按持久职业切换为九套 64x64 像素皮肤；关闭后尊重原版/资源包纹理。 */
	public boolean zombieProfessionSkins = true;
	/** 客户端播放指挥、应声、怒吼、撤退冲刺以及剑/斧战备姿势。 */
	public boolean zombieBodyLanguage = true;
	/** 战术动作互相打断或提前结束时的客户端姿势交叉淡化 tick；0 表示立即切换。 */
	public int zombieAnimationBlendTicks = 4;
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
	/** 着火时寻找可达水体并向小队水桶兵求援；白天露天时额外寻阴影，持水桶者可脚下自救。 */
	public boolean sunlightSurvival = true;
	/** 使用真实承重面规避开放机关陷阱，并允许对目标方向的一格宽沟槽执行物理跳跃。 */
	public boolean smartTraversal = true;
	/** 掌握采集和建造战术所需的最低智力；默认 8。 */
	public int terrainMinimumIntelligence = DEFAULT_TERRAIN_MINIMUM_INTELLIGENCE;
	/** 隐藏建筑材料槽的容量；每次只采集一块，死亡时会完整掉出。 */
	public int terrainBlockInventoryLimit = DEFAULT_TERRAIN_BLOCK_INVENTORY_LIMIT;
	/** 少量合格高智力僵尸是否成为正式工程兵，并周期性使用环境控制技能。 */
	public boolean engineerSkills = true;
	/** 在高智力、成年且未占用空袭职业的候选中，成为工程兵的基础概率；桶兵会直接并入工程兵。 */
	public double engineerSpawnChance = DEFAULT_ENGINEER_SPAWN_CHANCE;
	/** 工程兵技能池是否包含真实 TNT 放置、点燃与撤离；仍同时服从 mobGriefing 和 tntExplodes。 */
	public boolean engineerTntSkill = true;
	/** 工程兵技能池是否包含真实水源和岩浆源的投放、撤离与回收；仍服从 mobGriefing。 */
	public boolean engineerFluidSkills = true;
	/** 工程兵是否可近身用打火石直接点燃当前目标。 */
	public boolean engineerIgnitionSkill = true;
	/** 组队期间的全员移速加成（ADD_MULTIPLIED_TOTAL），离队自动移除，0 关闭。 */
	public double squadSpeedBonus = 0.10;
	/** 武装小队总开关。默认关闭：持械概率、兵种职位、破盾和包抄加速全部由它统一控制。 */
	public boolean armedSquads = false;
	/** 所有受支持地面僵尸家族成员持剑/斧时按武器冷却周旋；斧手会优先尝试跳劈。 */
	public boolean weaponCombatTactics = true;
	/** 高智力剑士面对正在格挡的目标时，偶尔以前后步假挥诱导目标过早放盾。 */
	public boolean swordFeints = true;
	public int swordFeintMinimumIntelligence = DEFAULT_SWORD_FEINT_MINIMUM_INTELLIGENCE;
	public double swordFeintChance = DEFAULT_SWORD_FEINT_CHANCE;
	/** 高智力盾卫成功格挡后，偶尔以副手盾击代替立即挥剑。 */
	public boolean shieldBashes = true;
	public int shieldBashMinimumIntelligence = DEFAULT_SHIELD_BASH_MINIMUM_INTELLIGENCE;
	public double shieldBashChance = DEFAULT_SHIELD_BASH_CHANCE;
	public double shieldBashDamage = DEFAULT_SHIELD_BASH_DAMAGE;
	public double shieldBashKnockback = DEFAULT_SHIELD_BASH_KNOCKBACK;
	/** 原版自然生成的持矛僵尸是否自动装备鞘翅和 16～64 枚烟花，改用空中突刺。 */
	public boolean spearAirAssault = true;
	/** 僵尸专用烟花的推进效率；1.0 对齐原版，默认 0.5 将稳定推进速度限制为约一半。 */
	public double spearRocketEfficiency = DEFAULT_SPEAR_ROCKET_EFFICIENCY;
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
	/** 交战中同一阵位连续寻路失败时，首领有界检查替代路线并发布新计划版本。 */
	public boolean dynamicSquadReplanning = true;
	/** 只根据队员真实视线内的举盾、高地、拉扯、隘口和涉水证据调整总攻方案。 */
	public boolean observableTargetTactics = true;
	/** 成员把真实失败节点写入最多 32 条的短期危险黑板，后续替代路径主动避开。 */
	public boolean squadSharedDangerMemory = true;
	/** 骷髅蓄力时发布短期弹道走廊，挡线成员在低优先级战斗阶段主动让路。 */
	public boolean squadFiringLaneReservations = true;
	/** 目标踩中本队蜘蛛的临时蛛网时，先触发短齐射，再让近战与爆破成员同步冲锋。 */
	public boolean squadWebAmbushFollowup = true;
	/** 两名以上盾卫组成多排盾墙，持阵时全员举盾，进攻窗口每次只轮换一人放盾出击。 */
	public boolean squadShieldWallRotation = true;
	/** 真实攻击者和可见新目标可吸引最多 40% 的合格成员，首领与多数成员保持主目标。 */
	public boolean squadThreatDistribution = true;
	/** 交战中只撤出生命最低的一名伤员，并由最近的健康盾兵/近战成员卡住追击路线。 */
	public boolean squadCasualtyExtraction = true;
	/** 没有健康盾卫时，允许智力至少 6 的蜘蛛背负伤员撤到安全距离。 */
	public boolean squadSpiderCasualtyTransport = true;
	public double squadCasualtyHealthThreshold = DEFAULT_SQUAD_CASUALTY_HEALTH_THRESHOLD;
	/** 单轮掩护撤离持续时间；结束后至少冷却四秒，避免低血成员永久脱离战斗。 */
	public int squadCasualtyResponseTicks = DEFAULT_SQUAD_CASUALTY_RESPONSE_TICKS;
	public int decisionIntervalTicks = 8;
	public int targetMemoryTicks = 60;
	public int maximumCoordinatedZombies = DEFAULT_MAXIMUM_COORDINATED_ZOMBIES;
	public double coordinationRadius = 12.0;
	public int minimumSquadSize = 3;
	public int squadFormationIntervalTicks = 10;
	public int squadFormationTicks = 12;
	public int rallyTimeoutTicks = 60;
	/** 完整会议默认 3.2 秒；必须给巡视、路线汇报和两侧命令留下可读间隔。 */
	public int briefingTicks = 64;
	public int deploymentTimeoutTicks = 80;
	/** 换届仪式默认 2.4 秒，包含环顾、继任怒吼和成员确认。 */
	public int regroupTicks = 48;
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
		this.briefingTicks = clamp(this.briefingTicks, 60, 100);
		this.deploymentTimeoutTicks = clamp(this.deploymentTimeoutTicks, 20, 200);
		this.regroupTicks = clamp(this.regroupTicks, 40, 80);
		this.memberHeartbeatTimeoutTicks = clamp(this.memberHeartbeatTimeoutTicks, 20, 100);
		this.rallyRadius = clamp(this.rallyRadius, 1.0, 4.0);
		this.emergencyEngageDistance = clamp(this.emergencyEngageDistance, 2.0, 12.0);
		this.rallyQuorum = clamp(this.rallyQuorum, 0.5, 1.0);
		this.deploymentQuorum = clamp(this.deploymentQuorum, 0.4, 1.0);
		this.formationRadius = clamp(this.formationRadius, 2.0, 6.0);
		this.flankBehindDistance = clamp(this.flankBehindDistance, 1.0, 6.0);
		this.flankSideDistance = clamp(this.flankSideDistance, 1.0, 6.0);
		this.tacticalSpeedModifier = clamp(this.tacticalSpeedModifier, 0.75, 1.35);
		this.squadCasualtyHealthThreshold = clamp(
			this.squadCasualtyHealthThreshold,
			MINIMUM_SQUAD_CASUALTY_HEALTH_THRESHOLD,
			MAXIMUM_SQUAD_CASUALTY_HEALTH_THRESHOLD
		);
		this.squadCasualtyResponseTicks = clamp(
			this.squadCasualtyResponseTicks,
			MINIMUM_SQUAD_CASUALTY_RESPONSE_TICKS,
			MAXIMUM_SQUAD_CASUALTY_RESPONSE_TICKS
		);
		this.squadSpeedBonus = clamp(this.squadSpeedBonus, 0.0, 0.5);
		this.zombieAnimationBlendTicks = clamp(this.zombieAnimationBlendTicks, 0, 8);
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
		this.engineerSpawnChance = clamp(this.engineerSpawnChance, 0.0, 1.0);
		this.armedChanceEasy = clamp(this.armedChanceEasy, 0.0, 1.0);
		this.armedChanceNormal = clamp(this.armedChanceNormal, 0.0, 1.0);
		this.armedChanceHard = clamp(this.armedChanceHard, 0.0, 1.0);
		this.armedShieldChance = clamp(this.armedShieldChance, 0.0, 1.0);
		this.armedShieldBreakSeconds = clamp(this.armedShieldBreakSeconds, 0.0, 10.0);
		this.armedFlankSpeedBonus = clamp(this.armedFlankSpeedBonus, 0.0, 0.35);
		this.swordFeintMinimumIntelligence = clamp(this.swordFeintMinimumIntelligence, 1, 10);
		this.swordFeintChance = clamp(this.swordFeintChance, 0.0, 1.0);
		this.shieldBashMinimumIntelligence = clamp(this.shieldBashMinimumIntelligence, 1, 10);
		this.shieldBashChance = clamp(this.shieldBashChance, 0.0, 1.0);
		this.shieldBashDamage = clamp(this.shieldBashDamage, 0.0, MAXIMUM_SHIELD_BASH_DAMAGE);
		this.shieldBashKnockback = clamp(this.shieldBashKnockback, 0.0, MAXIMUM_SHIELD_BASH_KNOCKBACK);
		this.spearRocketEfficiency = clamp(
			this.spearRocketEfficiency,
			MINIMUM_SPEAR_ROCKET_EFFICIENCY,
			MAXIMUM_SPEAR_ROCKET_EFFICIENCY
		);
		this.skeletonPreferredRange = clamp(
			this.skeletonPreferredRange,
			MINIMUM_SKELETON_PREFERRED_RANGE,
			MAXIMUM_SKELETON_PREFERRED_RANGE
		);
		this.skeletonAimPredictionStrength = clamp(this.skeletonAimPredictionStrength, 0.0, 1.0);
		this.skeletonCrossbowChance = clamp(this.skeletonCrossbowChance, 0.0, 1.0);
		this.skeletonFireworkCrossbowChance = clamp(this.skeletonFireworkCrossbowChance, 0.0, 1.0);
		this.creeperMaximumFuseStartDistance = clamp(
			this.creeperMaximumFuseStartDistance,
			MINIMUM_CREEPER_MAXIMUM_FUSE_START_DISTANCE,
			MAXIMUM_CREEPER_MAXIMUM_FUSE_START_DISTANCE
		);
		this.creeperFuseMovementSpeed = clamp(
			this.creeperFuseMovementSpeed,
			MINIMUM_CREEPER_FUSE_MOVEMENT_SPEED,
			MAXIMUM_CREEPER_FUSE_MOVEMENT_SPEED
		);
		this.creeperFuseFeintCooldownTicks = clamp(
			this.creeperFuseFeintCooldownTicks,
			MINIMUM_CREEPER_FUSE_FEINT_COOLDOWN_TICKS,
			MAXIMUM_CREEPER_FUSE_FEINT_COOLDOWN_TICKS
		);
		this.spiderCreeperSearchRadius = clamp(
			this.spiderCreeperSearchRadius,
			MINIMUM_SPIDER_CREEPER_SEARCH_RADIUS,
			MAXIMUM_SPIDER_CREEPER_SEARCH_RADIUS
		);
		this.spiderCreeperCarrierSpeed = clamp(
			this.spiderCreeperCarrierSpeed,
			MINIMUM_SPIDER_CREEPER_CARRIER_SPEED,
			MAXIMUM_SPIDER_CREEPER_CARRIER_SPEED
		);
		this.spiderWebTrapCooldownTicks = clamp(
			this.spiderWebTrapCooldownTicks,
			MINIMUM_SPIDER_WEB_TRAP_COOLDOWN_TICKS,
			MAXIMUM_SPIDER_WEB_TRAP_COOLDOWN_TICKS
		);
		this.spiderWebTrapLifetimeTicks = clamp(
			this.spiderWebTrapLifetimeTicks,
			MINIMUM_SPIDER_WEB_TRAP_LIFETIME_TICKS,
			MAXIMUM_SPIDER_WEB_TRAP_LIFETIME_TICKS
		);
		this.endermanCreeperSearchRadius = clamp(
			this.endermanCreeperSearchRadius,
			MINIMUM_ENDERMAN_CREEPER_SEARCH_RADIUS,
			MAXIMUM_ENDERMAN_CREEPER_SEARCH_RADIUS
		);
		this.endermanCreeperDeliveryCooldownTicks = clamp(
			this.endermanCreeperDeliveryCooldownTicks,
			MINIMUM_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS,
			MAXIMUM_ENDERMAN_CREEPER_DELIVERY_COOLDOWN_TICKS
		);
		this.endermanCreeperDropDistance = clamp(
			this.endermanCreeperDropDistance,
			MINIMUM_ENDERMAN_CREEPER_DROP_DISTANCE,
			MAXIMUM_ENDERMAN_CREEPER_DROP_DISTANCE
		);
		this.endermanCreeperFrontDeliveryChance = clamp(
			this.endermanCreeperFrontDeliveryChance,
			MINIMUM_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE,
			MAXIMUM_ENDERMAN_CREEPER_FRONT_DELIVERY_CHANCE
		);
		this.giantZombieSpawnChance = clamp(this.giantZombieSpawnChance, 0.0, 1.0);
		this.giantZombieMaximumHealth = clamp(
			this.giantZombieMaximumHealth,
			MINIMUM_GIANT_ZOMBIE_MAXIMUM_HEALTH,
			MAXIMUM_GIANT_ZOMBIE_MAXIMUM_HEALTH
		);
		this.giantZombieAttackDamage = clamp(
			this.giantZombieAttackDamage,
			MINIMUM_GIANT_ZOMBIE_ATTACK_DAMAGE,
			MAXIMUM_GIANT_ZOMBIE_ATTACK_DAMAGE
		);
		this.giantZombieMovementSpeed = clamp(
			this.giantZombieMovementSpeed,
			MINIMUM_GIANT_ZOMBIE_MOVEMENT_SPEED,
			MAXIMUM_GIANT_ZOMBIE_MOVEMENT_SPEED
		);
		this.blazePreferredRange = clamp(
			this.blazePreferredRange,
			MINIMUM_BLAZE_PREFERRED_RANGE,
			MAXIMUM_BLAZE_PREFERRED_RANGE
		);
		this.netherPredictionStrength = clamp(this.netherPredictionStrength, 0.0, 1.0);
		this.hoglinChargeSpeed = clamp(
			this.hoglinChargeSpeed,
			MINIMUM_HOGLIN_CHARGE_SPEED,
			MAXIMUM_HOGLIN_CHARGE_SPEED
		);
		this.magmaCubePounceSpeed = clamp(
			this.magmaCubePounceSpeed,
			MINIMUM_MAGMA_CUBE_POUNCE_SPEED,
			MAXIMUM_MAGMA_CUBE_POUNCE_SPEED
		);
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

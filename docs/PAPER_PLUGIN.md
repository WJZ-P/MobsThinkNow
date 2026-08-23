# Mobs Think Now Paper 端

## 定位

Paper 产物是纯服务端插件，目标版本为 Minecraft/Paper `26.1.2`，编译依赖固定为官方稳定 API
`26.1.2.build.74-stable`。插件只使用公开的 `MobGoals`、`Goal`、`Pathfinder`、事件和 PDC，不反射或链接
NMS 实现类，因此服务端小版本升级时具有更清晰的兼容边界。

客户端不需要安装任何 Mod。Fabric 端的自定义模型层、Mixin 渲染姿势、职业皮肤和逐实体同步动画属于
客户端能力，Paper 插件不会伪装成可以直接发送这些渲染代码；后续可选资源包只负责外观，不参与 AI。

## 仓库结构

```text
MobsThinkNow/
├─ src/       Fabric 26.1.2 Mod 与 GameTest
├─ shared/    不依赖 Minecraft/Fabric/Paper 的纯 Java 决策内核
└─ paper/     Paper 事件、PDC、MobGoals、Pathfinder 和命令适配器
```

共享层只接收不可变数值快照。任何世界、实体、方块、导航和声音操作都留在平台适配器所在的服务器线程；
这既避免把 Bukkit/NMS 对象交给子线程，也让相同判定可以用普通 JUnit 验证。

## 当前可用能力

### 持久智力

- 僵尸、尸壳、溺尸、僵尸村民，五种 `AbstractSkeleton`（含干尸与凋灵骷髅）、普通苦力怕和普通蜘蛛使用 PDC 保存 IQ `1～10`；
- 类型边界按 `EntityType` 白名单判断；Bukkit API 中继承 `Zombie` 的僵尸猪灵，以及洞穴蜘蛛等近似类型不会因 Java 继承关系误入主世界混编小队；
- 简单难度范围 `1～7`、普通 `2～9`、困难 `4～10`，与 Fabric 的对应怪物分布共用
  `IntelligenceDistribution`；
- 插件只为原本没有自定义名字的实体添加 IQ，且通过第二个 PDC 标记确认名字所有权。命名牌或其他插件
  的名字不会被覆盖；关闭名字显示时也只清除本插件拥有的名字。

### 僵尸反应式撤退

- `EntityDamageByEntityEvent` 在伤害结算后记录最终伤害，盾挡、取消事件和零伤害不会触发；
- 同一 Goal 评估前连续受击时，低血逻辑远离最近攻击者，重击逻辑远离最大单次伤害来源；
- 伤害邮箱只保存 UUID 和数值、一次消费，并在重载/卸载/关闭时清理；
- 自定义 Goal 以优先级 0 占用 `MOVE` 与 `LOOK`，在受伤触发后优先于其他战斗行为；原版浮水仍由
  `MobGoals` 的并发 Goal 类型与服务端物理共同处理；
- 路线由共享 `RetreatPlanner` 生成固定五个背向候选，再逐个交给 Paper `Pathfinder#findPath`。不存在
  “每只怪物扫描每只怪物”的平方复杂度；
- 结束时明确停止逃跑路径并恢复攻击状态，40 tick 内不会因同一低血状态反复起停。

### 僵尸持械节奏与斧手跳劈

- IQ 至少 3、主手持剑或斧的僵尸使用公开 `MobGoals` 战斗 Goal；没有支持武器时保留原版近战 Goal；
- 共享 `MeleeWeaponPlanner` 同时服务 Fabric 与 Paper，负责水平距离、周旋落点、斧手起跳带、起跳速度
  和空中有限转向。攻击后僵尸退到默认 2.8 格左右等待武器冷却，而不是持续贴脸空挥；
- 剑手冷却完成后沿真实 `Pathfinder` 接近并调用公开 `LivingEntity#attack`；Goal 被撤退或会议短暂抢占时，
  已有攻击冷却会继续保留，不能通过 Goal 重启刷新；
- IQ 至少 6 的斧手在 `1.8～3.3` 格且视线、承重和扫掠碰撞满足时，先做默认 8 tick 前摇，再施加
  `0.42` 垂直速度和可配置水平速度跳劈；下降段命中时仅在本次公开 API 攻击期间临时应用默认 `1.5x`
  `ATTACK_DAMAGE` 修饰符，攻击结束立即移除；
- 30 tick 内没有适合的跳劈窗口、路径失败、落水或飞行超时都会取消空中序列并降级为普通地面攻击；
  `status` 分别记录安装、周旋、寻路失败、前摇、起跳、实际攻击和暴击命中次数。

### 僵尸盾卫攻防博弈

- IQ 至少 4 且副手真实持盾的僵尸进入默认 6 格接敌带后，使用 Paper `startUsingItem(OFF_HAND)` 同步
  原版胸前举盾姿态；盾卫 Goal 与持械 Goal 互斥，撤退 Goal 仍以更高优先级抢占；
- 共享 `ShieldCombatPlanner` 同时服务 Fabric 与 Paper，统一随机守候/反击延迟、信号寿命和水平正面夹角。
  Paper 公共 API 可以驱动非玩家实体使用物品，但不会替其套用玩家盾牌的伤害消解，因此插件在
  `EntityDamageByEntityEvent` 中只补齐成熟举盾、正面近战/扫击/投射物的结算；背刺和其他伤害照常命中；
- 成功格挡会取消受伤事件，所以没有僵尸受击音效或红闪；同时真实损耗副手盾牌耐久、播放
  `ITEM_SHIELD_BLOCK`，并把攻击者 UUID 与 tick 写入一次性邮箱；
- 格挡后继续举盾随机等待 2～4 tick，再明确放盾并打开最多 10 tick 的攻击窗口。挥击与防御不会同时
  发生；未受攻击时则守候 12～28 tick 后主动试探一次，收招后重新举盾；
- `block.minimum-use-ticks` 默认 5，避免盾牌刚抬起便瞬间生效；`block.minimum-facing-dot` 默认 0.0，
  表示只覆盖水平正面半球。主体 yaw 与 LookControl 同步平滑转向，防止只转头却拿盾背对伤害源；
- 正面斧击不会被事件适配层吞掉：它正常造成伤害、立刻放盾并播放破盾声，默认禁盾 3 秒
  （`axe-disable-seconds`）。禁用期间持剑/斧的普通周旋 Goal 接管；窗口到期后才重新切回盾卫，避免下一
  tick 无视斧头继续举盾。禁用时刻计算同样复用共享规划器并防止 tick 溢出；
- `/mtnpaper status` 分别输出 Goal 安装/移除、守候、格挡、反击排程、攻击窗口、实际攻击、反击命中、
  寻路失败与待消费盾牌信号数；配置重载、区块卸载和插件关闭都会清空邮箱。

### 骷髅紧急脱离

- 受支持的整个 `AbstractSkeleton` 家族共用同一 Goal，敌人可以是玩家、铁傀儡或其他当前仇恨目标；
- `RangedSpacingPlanner` 与 Fabric 共用距离分带：普通持弓拉扯继续面对目标，进入紧急阈值后则明确取消
  蓄力、放下弓并面对逃生路径正向奔跑，两种姿态不会混为一谈；
- IQ 越高，识别近身危险越早、安全释放距离越远、路径刷新越敏捷；
- 每只骷髅首次需要脱离时按世界难度抽取一个 PDC 速度因子。相同随机分位满足简单 < 普通 < 困难，
  但任何个体都不超过 Fabric 原速度曲线的最大值；
- 路径节点被抬到眼睛高度再控制头和身体朝向，避免逃跑时低头盯地；找不到完整路径时只施加受碰撞
  约束的小幅水平速度，不传送、不穿墙；
- 启动阈值与安全阈值具有迟滞区，80 tick 超时后才短暂冷却，避免 Goal 在临界距离逐 tick 抖动。

### 骷髅来箭闪避

- `ProjectileEvasionPlanner` 是 Fabric/Paper 共用的纯 Java 轨迹内核：用相对位置与速度求有限时间窗内的
  三维最近交会点，排除静止、远离、超时和安全半径外的投射物，再按预测水平落点选择相反侧；
- Paper 不让每只骷髅调用全世界实体扫描。`PaperProjectileThreatBoard` 通过实体装载/移除事件登记箭，全服
  唯一主线程任务每 tick 更新最多 `maximum-tracked-projectiles`（默认 256）枚箭的 12 格三维桶。三轴桶坐标
  压入 fastutil primitive-long 键；每次感知只访问中心及相邻 26 桶，不创建 27 个临时键对象，并在
  `maximum-candidate-checks`（默认 24）个原始候选后硬停止；
- IQ 至少 4 的持弓/弩骷髅才激活优先级 0 闪避 Goal。IQ 1～10 的共享反应曲线把扫描间隔从 6 tick 缩至
  2 tick、预测窗口从 4.5 tick 拉到 8 tick，同时增加安全余量与动作时长；命中判定后先取消蓄力，再沿
  远离预测落点的可达侧横移，首选侧无路时尝试另一侧，两侧均失败才施加小幅有界横向速度；
- `/mtnpaper status` 暴露 Goal 安装/移除、真实闪避、路径失败、感知查询、候选检查、威胁命中、容量拒绝
  和当前跟踪箭数。`/mtnpaper selftest` 会从十格外发射真实 Paper 箭，除计数增长外还要求探针骷髅实际
  横移至少 0.55 格。

### 骷髅掩体探头

- Fabric/Paper 现在共用纯 Java `CoverPositionPlanner`：它只处理固定半径的格点顺序、有效射程、稳定侧向
  打散与评分；是否可站、是否被遮挡、探头处是否有射界由平台主线程适配器提供，不把 Bukkit 世界对象
  放进共享层；
- Paper 默认只让 IQ 至少 5 的持弓骷髅使用该战术。一次搜索最多检查半径四格内 96 个藏身候选，最多
  返回 4 个方案并依次交给公开 `Pathfinder`，所以方块查询和 A* 次数都有硬上限；失败后默认等待 60 tick；
- 完整状态机为“进入藏身格 → 随机等待 4～8 tick → 前往相邻射界格 → 真实举弓 20 tick →
  `RangedEntity#rangedAttack` 放箭 → 保持瞄准两 tick → 缩回”。默认每处最多两箭，目标移动超过六格、
  地形/射界变化或 240 tick 超时都会结束本轮；
- 优先级 0 的来箭闪避和优先级 1 的贴脸脱离都会抢占优先级 3 的掩体循环。进入交叉火力方案的远程成员
  则交由优先级 2 的小队射手 Goal 排阵；独行射手探头释放前仍使用有界友军胶囊检查，避免为了掩体打中队友；
- `/mtnpaper status` 显示搜索、原始候选、有效方案、循环、探头箭、回撤、寻路失败与中止次数。
  `selftest` 会搭建一面临时双高石墙，要求真实完成至少一次探头射击、回撤和 0.75 格位移，随后逐块恢复
  原世界快照。

### 骷髅交叉火力与友军射界

- `CROSSFIRE`/`COMBINED_ARMS` 进入交战后，左右射手用共享 `SquadVolleyPlanner` 取得相差半个周期的
  确定性释放时隙；同翼个体再按稳定序号加入小抖动，避免所有箭在同一 tick 生成；
- 每次蓄力前和真正放箭前，`FiringLanePlanner` 都把射手眼睛到目标眼睛视为线段，并只检查当前小队至多
  20 个友军胶囊。命中胶囊时取消蓄力，沿职责对应一侧横移并略微后撤；计算量受
  `maximum-lane-checks` 硬限制，不查询全世界实体；
- 自定义 Goal 只在交叉火力方案、交战阶段和左右射手职责同时成立时，以优先级 2 暂时接管 `MOVE/LOOK`。
  骷髅被贴身时优先级 1 的紧急脱离仍先执行；离队、换方案或关闭配置后原版弓/弩 Goal 自动恢复；
- 弓箭最终调用 Paper 公开 `RangedEntity#rangedAttack`。持弩骷髅则使用共享 `CrossbowCombatPlanner` 的
  `UNCHARGED → CHARGING → AIMING` 节奏：真实举起弩并播放装填声，装填完成后把箭写入公开
  `CrossbowMeta`，短暂瞄准移动目标的速度提前量，再通过 `World#spawnArrow` 发射禁止拾取的真实箭矢；
- 弩手即使暂时没有小队也能独立使用该状态机；加入小队后仍服从会议/交战阶段、错峰时隙和有界友军
  射界。所有实现只依赖 Paper API，没有 NMS 反射；普通事件级友伤拦截继续作为队友突然切入弹道时的
  第二层保护。
- IQ 至少 7 且副手有真实烟花火箭时，弩手会在 6～30 格射程与爆心友军检查均通过后装入烟花；释放后
  由全服唯一 `PaperFireworkBoltService` 管理。服务每 tick 对每枚弹体只做一次前向射线、总数默认硬限
  48，命中实体/方块后触发原版烟花爆炸（不破坏地形），40 tick 未命中也会空爆并回收；超容量、队友
  靠近或弹药耗尽都即时降级为普通箭，不会卡住射击状态机。配置重载、测试清理和插件关闭会移除残留弹体。
- 普通骷髅通过 `NATURAL/JOCKEY/TRAP` 出生时，还会与 Fabric 共用 `CrossbowLoadoutPlanner`：默认 18%
  弩手基础概率、25% 烟花弩基础概率再乘世界难度与持久 IQ；只改装原本持弓的普通骷髅，流浪者、沼骸、
  干尸、刷怪笼、命令和其他插件 `CUSTOM` 出生均不随机改装。无论本次是否命中概率，PDC 都写入一次性
  初始化标记，区块重载、插件重载和重复事件不会重新洗职业或重复给弹药；装备掉落率保持原版 `8.5%`。

### 苦力怕战术引信与爆点预约

- 接敌 Goal 根据 IQ 在直追、速度拦截和稳定左右侧翼之间切换；目标正看向苦力怕或举盾时，高 IQ 个体
  优先侧后切入，寻路失败按“侧翼 → 拦截 → 直追”逐级降级；
- 点火 Goal 先向空间预约板申请预计爆点和预计爆炸 tick，成功后播放嘶声并继续追踪目标的速度提前量；
- `CreeperFeintPlanner` 已进入无平台依赖的共享层。IQ 8～10、未带电且引信尚未推进的个体在真实起爆圈外
  被目标注视或举盾时，以优先级 0 执行 `6～8 tick` 可见假点燃，再退火并预测目标速度移动到 9 格侧后退出线。
  冷却默认 240 tick 并带 `80%～120%` 的确定性个体抖动；原侧路线失败只尝试一次镜像侧，工作量有硬界；
- Paper 适配器用持续引信所有权过滤重复 `CreeperIgniteEvent`，并在未被取消的玩家实体交互中识别打火石/
  火焰弹接管；假动作随即结束但不清零真实引信。原版 `swellDir` 不会随公开退火 API 立刻归零，
  因此插件用一个全服集中任务维护最多 64 个所有权条目：前 10 tick 让接敌/真引信 Goal 让位，之后允许
  正常接敌，但仍持续把伪引信压回零，直到 `PaperCreeperFuseGoal` 已取得合法目标和爆点预约并显式接管。
  失去目标的苦力怕不会在“未点燃”外观下偷偷涨引信；实体卸载、玩家真实点燃和插件关闭均精确清理条目；
- 玩家真正跑出提交距离时，插件点燃的苦力怕会退火并释放预约。由玩家打火石等外部来源强制点燃的
  苦力怕只登记强制预约，绝不被插件取消；
- 冲突个体不会在首爆中心干等，而是去目标后侧的稳定候场点；首爆预约释放或过期后才重新竞争；
- 预约板按世界与水平网格分桶，每次只扫描中心周围 `3x3` 桶，并受 `maximum-checks` 硬上限约束。
  达到上限时保守视作已占用，因此不存在“每只苦力怕扫描全服每只苦力怕”的平方退化；
- 自定义接敌 Goal 会定期检查猫和豹猫并立即让位，保留原版天敌关系。

### 蜘蛛预测扑击与侧袭

- 当前只改造普通蜘蛛；洞穴蜘蛛继续使用原版毒素攻击节奏；
- `SpiderTacticalPlanner` 同时服务 Fabric 与 Paper，统一处理直追、速度截击、观察感知侧袭、命中后重定位、
  预测落点、跳扑速度和冷却。平台层只负责实体快照、寻路与施加结果；
- IQ 至少 4 且位于 `2.5～7` 格的蜘蛛可预测目标水平速度后跳扑。水平速度硬上限 `0.60`、垂直速度
  `0.40～0.46`，不会因为 Paper 版本而获得额外伤害或传送能力；
- 起跳前只在预测点附近检查固定五个高度，必须有承重面、两格可通过空间并位于世界边界内；岩浆、火、
  营火、仙人掌、岩浆块、细雪、甜浆果和任何液体都会拒绝本次跳扑；
- 每个目标最多拥有一条 O(1) 扑击租约。冲突蜘蛛会沿稳定左右侧绕行，落地后为同目标留下默认
  `10 tick` 的错峰窗口，避免整群同时遮挡、互撞；
- IQ 至少 6 的蜘蛛发现目标正观察自己或玩家正在举盾时，从稳定侧后接近；IQ 至少 5 的个体命中后
  短暂退到侧后方，再进入下一轮攻击；
- 启用该模块时插件通过公开 `MobGoals` API 精确保存并移除原版 `LEAP_AT` 与 `SPIDER_ATTACK` Goal，
  安装自己的战斗 Goal。配置关闭、插件卸载或实体离开世界前会移除自定义 Goal 并恢复保存对象，避免
  破坏其他插件的 GoalSelector 状态。

### 蜘蛛预测临时蛛网

- IQ 至少 7、目标可见且距离 `3.25～9` 格的落地蜘蛛会复用共享 `SpiderWebTrapPlanner`。移动目标按真实
  水平速度提前，静止目标只沿视线前探不到一格；结果固定为中线、左右闪避道和前后修正 5 个中心，
  每处最多检查 `0/-1/+1/-2` 四层，因此一次决策至多读取 20 个候选方块；
- 候选必须有安全实体支撑、与蜘蛛碰撞箱分离、位于世界边界内，且蜘蛛眼睛到落点的吐丝射线无阻挡。
  天然/玩家蛛网周围不会继续堆放；动作先播放叫声并抬身，直到第 8 tick 才真实放置，玩家可读也可打断；
- 放置同时服从 `mobGriefing`，并发送可取消的 `EntityChangeBlockEvent`，领地或保护插件可在公共 API
  边界阻止修改。默认每世界最多 128 张，每只蜘蛛的普通冷却约 12 秒，智力与难度只做小幅压缩；读取
  配置时冷却夹在 `80～600 tick`、寿命夹在 `60～400 tick`，与 Fabric 的公开配置边界一致；
- `PaperWebTrapService` 只登记自己成功写入的方块。玩家/插件放置、破坏、燃烧、淡化、爆炸和活塞事件
  会先丢弃所有权，随后到期任务不会覆盖新方块；正常过期、区块/世界卸载、配置关闭和插件停用则恢复
  原始 `BlockData`。到期队列每 tick 最多处理 64 项，绝不为回滚强制加载已卸载区块；
- 服务同时维护 owner→block 反向索引；蜘蛛死亡或卸载时只访问该 owner 的少量键并立即恢复其陷阱，
  避免每次实体卸载复制和扫描全世界最多 128 条登记。`status` 同时显示活跃陷阱与活跃 owner 数；
- 对不发送上述事件的直接命令/插件改块，下一次所有权查询或同格候选检查也会核对真实方块并惰性清除
  旧登记，既不覆盖外部结果，也不会让幽灵登记长期占用世界容量；
- 玩家频繁拆除产生的过期队列节点会在超过“活跃登记四倍或 128 项”时从当前所有权表重建，因而历史
  节点也有硬性压缩边界，不会靠等待 8 秒自然过期来掩盖内存增长；
- 重载若降低每世界上限，会按最早到期顺序立即恢复超额蛛网；关闭顶层开关或蛛网子开关也在重载调用
  内同步回滚，不等待下一次调度 tick，配置边界与活跃登记始终一致；
- 管理员在运行中把某世界的 `mobGriefing` 切为 `false` 时，该世界现有临时蛛网会在下一 tick 全部恢复，
  不只阻止后续新放置；
- 同队苦力怕进入真实引信后，蜘蛛会从至多 20 名小队快照中选择引信进度最高者，排除假引爆，再用共享
  爆心预测封住目标的外逃线。每个新引信至多绕过一次普通冷却，不会因同一苦力怕反复刷网；
- `/mtnpaper status` 分别显示 windup、成功放置、恢复、地形/保护拒绝、所有权丢失、爆心封锁、当前活跃
  数量与活跃 owner 数，便于在生产服确认密度上限、索引清理和保护插件联动。

### 蜘蛛—苦力怕机动爆破

- `MixedSquadTransportPlanner` 在共享层为 `MOUNTED_BREACH`/`COMBINED_ARMS` 队伍做稳定的一对一
  `蜘蛛 → 苦力怕` 配对；显式载具和爆破职责优先，蜘蛛恰好当选首领时仍可作为后备载具。配对只遍历
  最多 20 名小队快照，不重新扫描世界；
- 进入 `ENGAGING` 后，载具蜘蛛沿 Paper `Pathfinder` 与指定苦力怕会合。接近后苦力怕先播放声效、
  粒子并完成至少 3 tick 的可见跳跃，再通过公开 passenger API 骑到蛛背，不会瞬间吸附；
- 投送速度由双方较高 IQ、难度和每只蜘蛛固定的 `88%～100%` 速度分位共同决定，默认上限 `1.35`，
  不会叠加普通跳扑。苦力怕作为乘客时停止自己的接敌寻路，但仍独立参加爆点预约和引信判断；
- 引信达到默认 `35%` 后，蜘蛛把载荷朝目标小幅抛出，并用固定五个背向候选撤离 30 tick。装配超时、
  目标消失、配对变化或被其他载具占用都会安全卸载并进入冷却，避免永久骑乘和逐 tick 重试。

### 四物种混编小队

- 僵尸家族、骷髅家族、普通苦力怕和普通蜘蛛只要靠近并锁定同一目标，就能组成默认 `2～20` 人小队；
  没有当前目标的空闲成员也可被附近小队招募，并只继承服务端已经可见、仍合法存活的共享目标；
- 首领不再绑定僵尸：四种怪物统一按 IQ 最高者选举，并列者用 UUID 派生的稳定随机票抽签。首领死亡或
  离开世界后从剩余成员自动换届，任期递增并短暂进入 `REORGANIZING`；
- 共享 `MixedSquadPlanner` 根据阵容和首领 IQ 选择 `SWARM`、`SHIELD_WEDGE`、`PIN_AND_FLANK`、
  `CROSSFIRE`、`MOUNTED_BREACH` 或 `COMBINED_ARMS`，再把成员分成正面、左右翼、左右射手、爆破、
  载具、辅助和首领职责；Fabric 的首领选举与总攻方案也已改为委托同一规划器；
- 小队依次经历 `FORMING → BRIEFING → DEPLOYING → ENGAGING`。会议时成员沿原版可达路径站到首领
  周围，首领挥手并播放对应物种叫声；部署阶段射手前往交叉射界、两翼与爆破手去各自阵位。目标进入
  默认 8 格紧急圈时会直接交战，不会为了“演完动画”挨打；
- 撤退、骷髅紧急脱离和苦力怕引信继续拥有更高优先级；蜘蛛会议期间暂停个人跳扑，进入交战后才恢复
  预测扑击。通用小队 Goal 只在非交战阶段持有 `MOVE/LOOK`，不会每 tick 传送或改坐标；
- 协调器每 `5 tick` 在主线程通过共享 `BoundedSpatialIndex` 更新实体所在桶。索引以对象身份登记并按
  世界 UUID 隔离；移动/跨世界时删除旧桶后 O(1) 登记新桶，配置重载则按新半径完整重建。桶坐标由
  无依赖的 primitive-long 开放寻址表保存，九宫格查询不创建临时坐标键；默认单次最多检查 64 个
  原始候选、接收 20 人，因此不存在全服成员两两互扫；
- 同一成员的武器、盾牌、远程、载具和通用命令 Goal 会在一个 tick 内多次读取命令。协调器按
  `tick + squad revision + shared-target flag` 缓存不可变 `PaperSquadDirective`；成员对象替换、结构/阶段
  变化或下一 tick 都会失效。缓存未命中时直接读取实体 XYZ/yaw，远程射界的线段投影和最近点也改用
  primitive 标量运算，仅在真实 Pathfinder、音效或弹体 API 边界创建 Bukkit `Location`；
- 同队成员互相设为目标时事件会被取消；可配置阻止普通近战和弹射物误伤。苦力怕的实体爆炸伤害特意
  保留，避免混编小队获得无提示免伤，也要求爆破兵继续遵守已有爆点预约；
- `/mtnpaper inspect` 会显示最近怪物的小队 ID、任期、首领、阶段、方案和职责；若最近目标是蜘蛛还会
  显示它当前登记蛛网的绝对方块坐标。`status` 会显示活跃
  小队、成员、招募、换届、目标共享、有界候选检查、阵位寻路失败、指令计算和同 tick 缓存命中计数。

## 构建与安装

wrapper 可由 JDK 17 或更高版本启动。仓库已提交 Daemon JVM 25 标准化文件并使用 Foojay toolchain
resolver；联网环境会自动检测/获取 JDK 25，完全离线时需预先安装：

```powershell
./gradlew.bat :paper:build
```

Gradle 9.5.1 wrapper 还固定官方分发 SHA-256；构建工具本身先通过完整性校验，再解析插件和源码。
配置缓存与本地构建缓存默认开启，Paper JUnit、归档校验和真实 `paperSmokeTest` 均已验证可存储；排障时
可临时传入 `--no-configuration-cache --no-build-cache`。

`paperSmokeTest` 额外把同一 Gradle JDK 25 launcher 显式交给 Python 隔离运行器，环境变量中的旧
`JAVA_HOME` 不会污染真实 Paper 子进程。

`:paper:check` 与根项目 `check` 都会直接检查最终二进制/源码归档中的共享类与源码全集、入口、元数据、许可证和平台 API
隔离；根项目还检查 Maven/Gradle 发布元数据不再引用未发布的 `com.wjz:shared`。共享模块若只留在开发
类路径、没有进入可分发 JAR，构建会立刻失败。
Paper 检查还会逐一比对全部插件源码读取路径与默认 YAML 叶节点，任一重复、缺失、闲置、缩进到错误
父节点，或代码字面 fallback 与 YAML 默认值不一致的字段都会失败；这也覆盖盾反击配置曾被误缩进到 `counter` 下的问题。

产物：

```text
paper/build/libs/mobsthinknow-paper-0.1.0-alpha.1.jar
```

把该 JAR 放入 Paper `plugins/`，启动服务器后生成 `plugins/MobsThinkNowPaper/config.yml`。Paper 端 JAR
已经内嵌无第三方依赖的共享内核，不需要额外前置。

根目录执行 `./gradlew.bat build` 会同时验证 shared、Paper 和 Fabric，防止只通过某一个平台的构建。

真实 Paper 端到端冒烟使用：

```powershell
./gradlew.bat paperSmokeTest
```

任务先构建当前插件，再调用 `tools/paper_smoke_test.py`。运行器固定 Paper `26.1.2 build 74` 及其
SHA-256，首次运行才从 Paper 官方对象地址下载；随后在 `.gradle/paper-smoke/` 跨 `clean` 复用服务端运行库，但每次
重建专属超平坦世界、清除插件配置、选择空闲的 `127.0.0.1` 端口。它等待 `Done` 后先后写入语法损坏与重复键 YAML，验证两次重载都被拒绝且旧快照
继续运行；再依次写入总开关关闭/开启配置并各发送一次 `mtnpaper reload`，从 `status` 核对四个按需调度器确实停下并重新启动；随后执行 `mtnpaper selftest`，要求结构与行为
PASS，最后发送 `status`、`stop` 并检查 Java 退出码。启动、禁用、热重载恢复、自测和停服
都有独立超时，异常路径也会先请求正常停服再强制兜底；完整控制台记录保存在
`.gradle/paper-smoke/paper-smoke.log`。可设置 `PYTHON` 指定 Python 3 可执行文件，也可直接运行脚本并用
`--offline`、`--paper-jar`、`--java`、`--keep-world` 调整本地验证环境。

需要同进程耐久回归时可运行 `./gradlew.bat paperSmokeTest -PpaperSmokeSelftestRuns=N`（`N=1～100`）。
每轮自测 PASS 后，运行器会在最多 20 次服务端命令 tick 内轮询 `status`，确认实体、投射物、烟花弹、佯爆记忆、爆点/跳扑预约、蛛网、
小队和伤害/盾牌邮箱全部回到零，再调度下一轮；这既容纳实体移除事件晚一 tick 收敛，也能发现单次重启 smoke 看不到的跨轮累积泄漏。

Paper 配置使用严格 UTF-8 YAML 解析；文件超过一百万字节会在读入内存前被拒绝，解析器还会拒绝重复键、递归键、过深嵌套、超量别名和超过一百万码点的异常输入。
`/mtnpaper reload` 会先构造并校验全部不可变设置快照，只有全部成功才一次性切换；语法损坏、读盘失败或构造异常会报告失败并继续使用上一份运行快照，
不会把空配置或半更新状态发布给 AI tick。

## 命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/mtnpaper status` | 所有人 | 查看各兵种 Goal、活跃小队/预约、战术次数、候选预算与寻路失败计数 |
| `/mtnpaper inspect` | 所有人 | 查看 12 格内最近怪物的 UUID、IQ、目标、小队阶段、方案和职责 |
| `/mtnpaper reload` | `mobsthinknow.admin` | 重载、校验配置并刷新已加载实体 |
| `/mtnpaper setiq <1-10>` | `mobsthinknow.admin` | 修改附近受支持怪物的持久 IQ |
| `/mtnpaper spawn <type> [1-100]` | `mobsthinknow.admin` | 在玩家前方事务式批量生成指定 Paper 智能怪物 |
| `/mtnpaper spawnall` | `mobsthinknow.admin` | 各生成一只当前全部 11 种受支持怪物/变种，并追加剑手、斧手、盾卫、弩手与烟花弩手预设 |
| `/mtnpaper assault [1-8]` | `mobsthinknow.admin` | 每组生成 IQ 10 僵尸、骷髅、苦力怕、蜘蛛以测试联合兵种 |
| `/mtnpaper selftest` | `mobsthinknow.admin` | 控制台可用；真实 tick 验证联合编队、错峰射击和蜘蛛载客后自动清理 |

`spawn` 类型为：`zombie`、`husk`、`drowned`、`zombie_villager`、`skeleton`、`stray`、`bogged`、
`parched`、`wither_skeleton`、`creeper`、`spider`，以及 `zombie_swordsman`、`zombie_axeman`、
`zombie_shieldguard`、`skeleton_crossbow`、`skeleton_firework_crossbow` 五个装备/IQ 预设；
另可用 `spawn assault [组数]`。生成器先为整批实体规划有承重、
符合实际身高（凋灵骷髅为三格、其余为两格）的净空、无液体/火焰/仙人掌等危险的互不重叠落点；任意实体生成失败会移除本批已经生成的实体，
不会留下半套测试阵容。

`selftest` 不要求在线玩家：它会临时保活测试区块，生成一只持剑僵尸、一名弓手与一名弩手、一只苦力怕、一只蜘蛛
和一个关闭 AI、无敌的铁傀儡观察目标。每个成员的短期可观察目标写入成队前记忆；25 tick 后先要求
五者取得同一个小队 ID、全部进入 `ENGAGING` 且方案为 `COMBINED_ARMS`，随后最多再运行 420 tick，要求
两名射手至少实际释放一发协调箭，且弩手必须累计真实举弩 tick、装填和发射，并要求苦力怕真实跳上蜘蛛。另有一只 IQ 10 斧手与关闭 AI 但仍可
攻击的铁傀儡，在有界搜索所得的同高、2～3 格间距、四格净空自然通道中独立验证真实攻击和跳劈；另一个
隔离通道生成 IQ 10 剑盾卫与铁傀儡，待盾牌连续举起至少 10 tick 后发射真实箭矢，强制验证正面格挡、
一次性事件信号和 2～4 tick 延迟反击均至少发生一次；第三条通道固定关闭举盾者 AI 并主动维持成熟举盾，
再由持铁斧的卫道士正面攻击，要求伤害不被格挡且 `shieldDisables` 至少增加一次。生产举盾 Goal 仍由上一条
独立通道完整验证；各战斗探针彼此隔离，不会因混编阵位碰撞或攻击/防守相位切换产生假阴性。
掩体探针会暂时铺设并逐块恢复一段平台和双高石墙，以验证真实探头、放箭及回撤；420 tick 上限覆盖
一次 240 tick 掩体周期、失败后的 60 tick 搜索冷却和完整重试，不会把合法重规划误报为失败。第四条相隔 160 格的
远距通道生成独行烟花弩手和 500 点生命铁傀儡，
要求副手火箭真实消耗、集中弹体服务至少发射并碰撞引爆一次，清理后活跃弹体数回到零。
第五条 6 格注视通道生成 IQ 10 苦力怕，强制验证可见假点燃、9 格侧后重定位、完成计数和残余引信归零。
混编蜘蛛还必须通过真实吐丝 Goal 放置至少一张登记蛛网；自测观察到精确 owner 后等待 10 tick，短暂把
该世界 `mobGriefing` 设为 `false`，由生产调度任务恢复原方块并把活跃登记降到零，随后立即还原原规则。
该断言不会依赖白天蜘蛛是否随机放弃某个独立目标。
混编样本中的苦力怕爆炸半径临时设为 0、引信延长到 200 tick，因此可以观察载客行为而不破坏测试世界。
无论成功、失败、重载还是插件关闭，测试实体
和临时区块票都会清理。中间结构检查输出 `[MTN SELFTEST STRUCTURE PASS]`，最终日志只以
`[MTN SELFTEST PASS]` 或 `[MTN SELFTEST FAIL]` 表示机器可识别结论。

## 配置

默认值与 Fabric 对应行为一致：

```yaml
enabled: true
coordination:
  enabled: true
  share-targets: true
  prevent-friendly-fire: true
  formation-radius: 16.0
  minimum-members: 2
  maximum-members: 20
  raw-scan-limit: 64
  heartbeat-ticks: 5
  forming-timeout-ticks: 40
  briefing-ticks: 30
  deployment-timeout-ticks: 40
  reorganizing-ticks: 20
  emergency-distance: 8.0
  maximum-separation: 48.0
  target-memory-ticks: 100
identity:
  show-intelligence-names: true
zombie:
  retreat:
    enabled: true
    minimum-intelligence: 1
    health-threshold: 0.20
    heavy-hit-threshold: 0.30
    maximum-ticks: 100
    safe-distance: 5.0
    speed: 1.50
    damage-memory-ticks: 20
  weapon-tactics:
    enabled: true
    minimum-intelligence: 3
    spacing-radius: 2.8
    movement-speed: 1.15
    repath-ticks: 6
    axe:
      minimum-intelligence: 6
      windup-ticks: 8
      preparation-timeout-ticks: 30
      horizontal-speed: 0.34
      critical-damage-multiplier: 1.50
  shield-tactics:
    enabled: true
    minimum-intelligence: 4
    raise-distance: 6.0
    lower-distance: 7.5
    movement-speed: 1.10
    repath-ticks: 6
    guard:
      minimum-ticks: 12
      maximum-ticks: 28
    counter:
      minimum-delay-ticks: 2
      maximum-delay-ticks: 4
    strike-window-ticks: 10
    block-signal-memory-ticks: 20
    block:
      minimum-use-ticks: 5
      minimum-facing-dot: 0.0
    axe-disable-seconds: 3.0
skeleton:
  crossbow:
    enabled: true
    minimum-intelligence: 3
    charge-ticks: 25
    aim:
      minimum-ticks: 4
      maximum-ticks: 10
    projectile-speed: 3.15
    projectile-spread: 2.0
    maximum-lead-ticks: 20.0
    gravity-per-tick-squared: 0.05
    firework:
      enabled: true
      minimum-intelligence: 7
      minimum-range: 6.0
      maximum-range: 30.0
      ally-danger-radius: 3.5
      maximum-ally-checks: 20
      projectile-speed: 1.6
      projectile-lifetime-ticks: 40
      maximum-active-projectiles: 48
      consume-ammunition: true
    natural-loadout:
      enabled: true
      crossbow-chance: 0.18
      firework-crossbow-chance: 0.25
  spacing:
    enabled: true
    minimum-intelligence: 1
    preferred-range: 10.0
    maximum-disengage-ticks: 80
    timeout-cooldown-ticks: 20
  projectile-evasion:
    enabled: true
    minimum-intelligence: 4
    maximum-tracked-projectiles: 256
    maximum-candidate-checks: 24
    scan-radius: 8.5
    dodge-distance: 3.25
    movement-speed: 1.35
    cooldown-ticks: 14
  cover-peeking:
    enabled: true
    minimum-intelligence: 5
    search-radius: 4
    maximum-candidate-checks: 96
    maximum-path-checks: 4
    search-cooldown-ticks: 60
    movement-speed: 1.10
    hidden-wait:
      minimum-ticks: 4
      maximum-ticks: 8
    draw-ticks: 20
    maximum-shots-per-cover: 2
    cycle-timeout-ticks: 240
    target-movement-tolerance: 6.0
  coordinated-fire:
    enabled: true
    minimum-intelligence: 4
    maximum-range: 24.0
    charge-ticks: 16
    minimum-shot-interval-ticks: 28
    friendly-lane-radius: 0.75
    maximum-lane-checks: 20
    reposition-distance: 3.0
creeper:
  tactics:
    enabled: true
    minimum-intelligence: 1
    flanking: true
    maximum-fuse-start-distance: 4.0
    moving-fuse: true
    maximum-fuse-movement-speed: 1.25
    feint:
      enabled: true
      cooldown-ticks: 240
      reposition-speed: 1.16
  blast-reservation:
    conflict-radius: 6.0
    separation-ticks: 24
    lease-ticks: 40
    maximum-checks: 32
spider:
  tactics:
    enabled: true
    minimum-intelligence: 1
    predictive-pounce: true
    hit-and-run: true
    pounce-stagger-ticks: 10
    pounce-lease-ticks: 20
    maximum-air-ticks: 40
    web-traps:
      enabled: true
      minimum-intelligence: 7
      cooldown-ticks: 240
      lifetime-ticks: 160
      maximum-active-per-world: 128
      blast-containment: true
    mounted-breach: true
    maximum-carrier-speed: 1.35
    payload-release-progress: 0.35
    assembly-timeout-ticks: 100
    remount-cooldown-ticks: 100
```

读取时统一夹紧危险值；AI tick 只读取不可变快照，不重复访问 YAML。
关闭顶层 `enabled` 会移除自定义 Goal 和本插件拥有的 IQ 名称，但保留已经写入 PDC 的 IQ，重新开启后
同一只怪物不会重新洗点。

## 跨端能力策略

| 功能类型 | Fabric | Paper |
|---|---|---|
| 纯数学决策、智力分布、队形/武器/盾牌节奏与方向 | 共享内核 | 共享内核 |
| 导航与自定义 Goal | Mojang/Fabric 实体 API | Paper `MobGoals`/`Pathfinder` |
| 持久状态 | 实体存档字段/数据附件 | PDC |
| 服务端声音、粒子、装备、骑乘 | 完整 | 公共 API 能力内实现 |
| 自定义模型、骨骼动作、Mixin 姿势 | 完整客户端增强 | 服务端插件不直接提供；可选资源包降级 |
| NMS 反射 | 不需要 | 不使用 |

后续按“共享判定 → Paper 公共 API 适配 → Fabric 继续复用”的顺序迁移混编小队黑板、职业降级表现与
服务端批量测试指令。

## 已执行的真实 Paper 验证

开发回归除 JUnit 和 Fabric GameTest 外，还在隔离目录启动官方 Paper `26.1.2` build `74`：

1. 校验下载 JAR 的 SHA-256；
2. 等待服务端输出 `Done (...)` 后才发送控制台命令，避免启动前空世界命令源；
3. 确认插件列表和 `/mtnpaper status`；
4. 执行 `/mtnpaper selftest`，确认先输出结构通过，再得到
   `state=ENGAGING, plan=COMBINED_ARMS`，且 `weaponAttacks`、`axeLeaps`、`coordinatedShots`、
   `crossbowPoseTicks`、`fireworkLaunches`、`fireworkDetonations`、`creepersMounted`、`shieldBlocks`、
   `creeperFeints`、`creeperFeintsCompleted` 与 `feintProbeCooled` 均满足真实行为断言，并要求
   `projectileDodges > 0`、独立十格来箭探针产生至少 0.55 格真实横移，并要求临时石墙探针完成至少一次
   掩体探头射击、回撤和 0.75 格实际移动，并要求临时蛛网真实放置、按 owner 回滚、恢复计数递增且
   `activeWebTraps=0`；
   `shieldCounterattacks` 与 `shieldDisables` 均大于零；
5. 隔离运行器额外把世界设为困难、自然弩手基础概率设为 100%，通过真实 `NATURAL` 出生事件断言
   `naturalLoadoutInitializations=1` 与 `naturalCrossbows=1`；自测主动再初始化一次，计数仍为 1，证明 PDC 幂等；
6. 任意 `Cannot load configuration from stream`、`InvalidConfigurationException` 或插件启用异常都会立即判失败；
7. 执行 `stop`，确认插件 `onDisable`、世界保存和 Java 进程退出码 `0`。

可复现隔离服务端位于 Git 已忽略的 `.gradle/paper-smoke/`，Paper 本体、世界和日志不会进入发布 JAR
或 Git。

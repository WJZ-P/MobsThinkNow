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

- 僵尸家族、骷髅家族、苦力怕和蜘蛛使用 PDC 保存 IQ `1～10`；
- 简单难度范围 `1～7`、普通 `2～9`、困难 `4～10`，与 Fabric 的对应怪物分布共用
  `IntelligenceDistribution`；
- 插件只为原本没有自定义名字的实体添加 IQ，且通过第二个 PDC 标记确认名字所有权。命名牌或其他插件
  的名字不会被覆盖；关闭名字显示时也只清除本插件拥有的名字。

### 僵尸反应式撤退

- `EntityDamageByEntityEvent` 在伤害结算后记录最终伤害，盾挡、取消事件和零伤害不会触发；
- 同一 Goal 评估前连续受击时，低血逻辑远离最近攻击者，重击逻辑远离最大单次伤害来源；
- 伤害邮箱只保存 UUID 和数值、一次消费，并在重载/卸载/关闭时清理；
- 自定义 Goal 以优先级 1 占用 `MOVE` 与 `LOOK`，仍让原版浮水等最高生存行为抢占；
- 路线由共享 `RetreatPlanner` 生成固定五个背向候选，再逐个交给 Paper `Pathfinder#findPath`。不存在
  “每只怪物扫描每只怪物”的平方复杂度；
- 结束时明确停止逃跑路径并恢复攻击状态，40 tick 内不会因同一低血状态反复起停。

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

### 苦力怕战术引信与爆点预约

- 接敌 Goal 根据 IQ 在直追、速度拦截和稳定左右侧翼之间切换；目标正看向苦力怕或举盾时，高 IQ 个体
  优先侧后切入，寻路失败按“侧翼 → 拦截 → 直追”逐级降级；
- 点火 Goal 先向空间预约板申请预计爆点和预计爆炸 tick，成功后播放嘶声并继续追踪目标的速度提前量；
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
  安装自己的两个 Goal。配置关闭、插件卸载或实体离开世界前会移除自定义 Goal 并恢复保存对象，避免
  破坏其他插件的 GoalSelector 状态。

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
- 协调器每 `5 tick` 在主线程更新一次实体所在桶。组队查询只访问中心及周围八个桶，默认单次最多
  检查 64 个原始候选、接收 20 人，因此不存在全服成员两两互扫；
- 同队成员互相设为目标时事件会被取消；可配置阻止普通近战和弹射物误伤。苦力怕的实体爆炸伤害特意
  保留，避免混编小队获得无提示免伤，也要求爆破兵继续遵守已有爆点预约；
- `/mtnpaper inspect` 会显示最近怪物的小队 ID、任期、首领、阶段、方案和职责；`status` 会显示活跃
  小队、成员、招募、换届、目标共享、有界候选检查和阵位寻路失败计数。

## 构建与安装

需要 JDK 25：

```powershell
./gradlew.bat :paper:build
```

产物：

```text
paper/build/libs/mobsthinknow-paper-0.1.0-alpha.1.jar
```

把该 JAR 放入 Paper `plugins/`，启动服务器后生成 `plugins/MobsThinkNowPaper/config.yml`。Paper 端 JAR
已经内嵌无第三方依赖的共享内核，不需要额外前置。

根目录执行 `./gradlew.bat build` 会同时验证 shared、Paper 和 Fabric，防止只通过某一个平台的构建。

## 命令

| 命令 | 权限 | 作用 |
|---|---|---|
| `/mtnpaper status` | 所有人 | 查看各兵种 Goal、活跃小队/预约、战术次数、候选预算与寻路失败计数 |
| `/mtnpaper inspect` | 所有人 | 查看 12 格内最近怪物的 UUID、IQ、目标、小队阶段、方案和职责 |
| `/mtnpaper reload` | `mobsthinknow.admin` | 重载、校验配置并刷新已加载实体 |
| `/mtnpaper setiq <1-10>` | `mobsthinknow.admin` | 修改附近受支持怪物的持久 IQ |

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
skeleton:
  spacing:
    enabled: true
    minimum-intelligence: 1
    preferred-range: 10.0
    maximum-disengage-ticks: 80
    timeout-cooldown-ticks: 20
creeper:
  tactics:
    enabled: true
    minimum-intelligence: 1
    flanking: true
    maximum-fuse-start-distance: 4.0
    moving-fuse: true
    maximum-fuse-movement-speed: 1.25
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
```

读取时统一夹紧危险值；AI tick 只读取不可变快照，不重复访问 YAML。
关闭顶层 `enabled` 会移除自定义 Goal 和本插件拥有的 IQ 名称，但保留已经写入 PDC 的 IQ，重新开启后
同一只怪物不会重新洗点。

## 跨端能力策略

| 功能类型 | Fabric | Paper |
|---|---|---|
| 纯数学决策、智力分布、队形/冷却 | 共享内核 | 共享内核 |
| 导航与自定义 Goal | Mojang/Fabric 实体 API | Paper `MobGoals`/`Pathfinder` |
| 持久状态 | 实体存档字段/数据附件 | PDC |
| 服务端声音、粒子、装备、骑乘 | 完整 | 公共 API 能力内实现 |
| 自定义模型、骨骼动作、Mixin 姿势 | 完整客户端增强 | 服务端插件不直接提供；可选资源包降级 |
| NMS 反射 | 不需要 | 不使用 |

后续按“共享判定 → Paper 公共 API 适配 → Fabric 继续复用”的顺序迁移混编小队黑板、职业降级表现与
服务端批量测试指令。

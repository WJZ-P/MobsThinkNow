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
| `/mtnpaper status` | 所有人 | 查看已加载受支持怪物、Goal、撤退和寻路失败计数 |
| `/mtnpaper inspect` | 所有人 | 查看 12 格内最近受支持怪物的 UUID、IQ 和目标 |
| `/mtnpaper reload` | `mobsthinknow.admin` | 重载、校验配置并刷新已加载实体 |
| `/mtnpaper setiq <1-10>` | `mobsthinknow.admin` | 修改附近受支持怪物的持久 IQ |

## 配置

默认值与 Fabric 对应行为一致：

```yaml
enabled: true
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

后续按“共享判定 → Paper 公共 API 适配 → Fabric 继续复用”的顺序迁移蜘蛛跳扑错峰、骷髅拉扯、
苦力怕爆点预约与混编小队黑板。

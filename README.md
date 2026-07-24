# 怪物不再愚蠢 / Mobs Think Now

让原版怪物观察、判断和协作，而不是单纯加血、加伤害。当前版本是面向
Minecraft Java 26.1.2 + Fabric 的首个可玩原型。

## 当前版本

- 模组版本：`0.1.0-alpha.1`
- Minecraft：`26.1.2`
- Fabric Loader：`0.19.3` 或更高的兼容版本
- Fabric API：`0.155.2+26.1.2`
- 构建所需 Java：`25`
- 当前支持的怪物：原版普通僵尸

## 僵尸已经会做什么

- 多只僵尸锁定同一目标时分配站位，一只正面施压，其余尝试包围；
- 玩家正面举盾时，非施压位僵尸选择左侧或右侧包抄；
- 丢失视线后只搜索最后目击位置，不读取墙后玩家的新位置；
- 路径失败或长时间没有位移时更换侧翼并重新决策；
- 决策按间隔错峰执行，限制服务器每 tick 的额外开销；
- 不修改原版生命、伤害、攻击间隔和生成数量。

当前不会改造尸壳、溺尸、僵尸村民等僵尸变种。详细设计见
[ARCHITECTURE_PROPOSAL.md](ARCHITECTURE_PROPOSAL.md)。

## 安装

1. 安装 Minecraft Java 26.1.2 对应的 Fabric Loader。
2. 把 Fabric API 和本模组 JAR 放进实例的 `mods` 目录。
3. 单人游戏安装在客户端；专用服务器可只安装在服务端。

首次启动后会生成：

```text
config/mobsthinknow.json
```

修改配置后执行 `/mtn reload`。使用 `/mtn status` 可查看是否启用、已安装
Goal 数、战术决策数、侧袭数、搜索数和寻路失败数；重载命令需要管理员权限。

## 构建与测试

安装 JDK 25 后执行：

```powershell
./gradlew.bat build
```

`build` 会运行 Fabric Loader JUnit 和真实 Minecraft 服务端 GameTest。可发布
JAR 位于：

```text
build/libs/mobsthinknow-0.1.0-alpha.1.jar
```

## Alpha 限制

- 侧袭点目前使用轻量几何计算，复杂建筑和高低差仍由原版寻路决定；
- 临时小队按附近同目标僵尸动态组成，不持久化到存档；
- 尚未完成大规模实体压力测试和网易环境适配验证；
- 配置字段和数值在正式版前可能调整。

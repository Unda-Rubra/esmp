# Evolve: SMP（ESMP）

ESMP 面向长期运行的 Minecraft 生存服务器：永久文明主世界和周期更新的资源域。完整玩法和实施门槛见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 当前能做什么

**插件基础架构已可构建并在 Paper 26.2 启动。玩法系统尚未启用。**

- 单个可部署 JAR，内部拆分为核心、文明、资源和 Paper 启动四个模块。
- SQLite 版本化建表、异步串行数据库访问、世界定义与物理实例登记。
- 同一个 Bukkit 世界 UUID 重启后保持 ESMP 实例身份；新的物理 UUID 获得新实例身份。
- `/esmp status` 显示启动状态、数据库版本、模块和世界映射。
- 配置冲突或数据库初始化失败时禁用插件，而不是报告就绪。

**还没有领地保护、成员管理、建筑识别、侵蚀、修复或资源重置。不要把当前 JAR 当作可保护玩家建筑的生产插件。**

工厂世界和工业玩法已从当前范围移除，以减少开发量和生存平衡、服务器性能压力。本次移除不会额外封禁主世界或资源世界的红石行为。

## 固定开发环境

| 项目 | 版本或选择 |
|---|---|
| Minecraft / Paper | 26.2 / build 121 |
| Paper API | `26.2.build.121-stable` |
| 插件语言 / 包名前缀 | Java / `xdu.undarubra.esmp` |
| JDK | 25；本次验证使用 Temurin 25.0.4.1+1 |
| 构建 | Gradle Wrapper 9.7.1，Kotlin DSL |
| SQLite JDBC | 3.53.4.0 |
| 存储测试 | JUnit Jupiter 5.12.2 |

Kotlin DSL 仅用于构建脚本，插件源代码使用 Java。依赖锁文件和带 SHA-256 校验的 Gradle Wrapper 已入库。本地下载的 JDK、Gradle 和服务器数据不入库。

## 构建与验证

先将 `JAVA_HOME` 指向 JDK 25，并确认 `"$JAVA_HOME/bin/java" -version` 输出 25。

```sh
./gradlew build
```

部署文件：`esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar`。它包含内部模块和 SQLite 驱动，不包含 Paper 本体。其他模块的 JAR 不要单独放进服务器插件目录。

只运行数据库行为测试：

```sh
./gradlew :esmp-core:test
```

测试覆盖重启身份稳定、物理世界替换、跨角色绑定冲突回滚、未来数据库版本拒绝、关闭时完成已接收写入，以及停用世界定义后保留历史身份但拒绝继续注册。

## 本地 Paper 验证服务器

1. 阅读并接受 [Minecraft EULA](https://aka.ms/MinecraftEULA)。
2. 运行 `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh`。
3. 等到控制台出现 `ESMP READY`，输入 `esmp status`。
4. 输入 `stop` 干净关闭；再次启动检查同一世界实例 UUID 保持不变。

脚本构建插件、下载并校验 Paper 26.2 build 121、复制插件，然后启动服务器。新服务器默认只绑定 `127.0.0.1:25579`，使用测试用超平坦世界；玩家客户端如需连接须使用 26.2。服务器需要 Java、curl 和 SHA-256 校验工具；首次启动需要网络下载 Paper/Minecraft 依赖。

工作目录是 `.run/dev/`，数据库位于 `.run/dev/plugins/ESMP/esmp.db`。它是本地测试环境，不是生产配置。不要提交或在正式世界上试验破坏性配置。

## 世界映射

首次启动生成 `plugins/ESMP/config.yml`：

```yaml
config-version: 2
worlds:
  MAIN: world
  RESOURCE_OVERWORLD: resource
  RESOURCE_NETHER: resource_nether
  RESOURCE_END: resource_the_end
```

只有已经加载且名称、维度类型匹配的世界会被登记；未加载世界会在状态中显示 `not loaded`。插件不会创建或加载这些世界，也不会把默认 `world_nether` / `world_the_end` 自动当成可重置资源世界。

修改映射后重启服务器。一个物理世界不能映射到多个角色；持久化的 Bukkit UUID 也不能被重新分配给另一个定义。新资源世界应使用新物理 UUID。当前状态命令只用于管理员检查，权限为 `esmp.admin`（默认 OP；服务器控制台可用）。

旧的无版本／版本 1 配置会先备份到 `config-v1.yml`，再移除 `worlds.FACTORY` 并升级到版本 2。已经存在的物理世界文件夹不会删除；数据库中的旧定义和实例身份保留为历史记录，但不出现在当前模块的状态视图中，也不能继续登记该停用角色。版本 2 中的未知角色仍会被拒绝，不提供工厂功能兼容入口。

## 模块边界与下一步

| 模块 | 当前责任 |
|---|---|
| `esmp-core` | 无 Paper 依赖的世界值对象、SQLite 生命周期与世界身份存储 |
| `esmp-civilization` | 文明域拥有的 `MAIN` 世界定义 |
| `esmp-resource` | 资源域拥有的三个世界定义 |
| `esmp-plugin` | 唯一 Paper 入口、配置、主线程世界事件、状态命令与单 JAR 打包 |

下一功能切片是 **Settlement 持久化与保护**；必须先完成事务/缓存发布边界和目标版本的保护动作映射。数据库任务只处理不可变世界标识，不得异步读取 Paper 世界对象。暂不添加空实现的游戏模块、伪造事件发件箱或未验证的自动世界修改。

## 协作与文档

完成独立、已验证的里程碑就提交一次，使用清晰的提交说明。不提交 `.run/`、`.tooling/`、数据库、日志或密钥；未获请求不推送远端。

- [ARCHITECTURE.md](ARCHITECTURE.md)：规范设计与功能启用门槛。
- [AGENTS.md](AGENTS.md)：代码、提交和交接规则。
- [.agent/DECISIONS.md](.agent/DECISIONS.md)：决策理由。
- [.agent/OPEN_QUESTIONS.md](.agent/OPEN_QUESTIONS.md)：后续功能门槛。
- [.agent/HANDOFF.md](.agent/HANDOFF.md)：当前状态和实际验证记录。

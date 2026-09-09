# Evolve: SMP（ESMP）

ESMP 面向永久文明主世界和周期更新资源世界的长期生存服务器。完整设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。工厂世界已移出当前范围。

## 当前可玩功能

- 创建、扩展、取消区块认领；四方向连通、默认 64 区块上限、独立相邻聚落。
- Owner / Manager / Member / Visitor 固定角色，邀请与接受、管理者升降级、所有权转移、退出与移除。
- 单独确认放弃聚落；持久化历史、可恢复的事件发件箱和重启后保持的权限。
- 方块、容器、实体交互保护，以及流体、活塞、漏斗、爆炸等间接修改边界。
- 玩家私有的临时粒子边界和区块切换 ActionBar。

**建筑自动识别、活跃度、侵蚀、修复、自动释放和资源重置尚未启用。** 当前可手动放弃并按正常规则重新认领；不会生成假的建筑历史或修复效果。

## 构建

| 项目 | 选择 |
|---|---|
| Minecraft / Paper | 26.2 / build 121 |
| 插件语言 / 包名 | Java / `xdu.undarubra.esmp` |
| JDK | 25；验证使用 Temurin 25.0.4.1+1 |
| 构建 | Gradle Wrapper 9.7.1，Kotlin DSL |
| SQLite JDBC / 测试 | 3.53.4.0 / JUnit Jupiter 5.12.2 |

设置 `JAVA_HOME` 指向 JDK 25，然后运行：

```sh
./gradlew build
```

部署文件：`esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar`。只部署这个 JAR，不要单独安装内部模块。Kotlin DSL 仅用于构建脚本，插件源代码是 Java。

```sh
./gradlew :esmp-core:test :esmp-civilization:test
```

测试覆盖持久化身份、事务回滚、连通性、认领上限、角色权限、邀请失效、所有权转移、放弃确认修订、历史和事件确认。

## 现在进入游戏验证

1. 阅读并接受 [Minecraft EULA](https://aka.ms/MinecraftEULA)，运行 `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh`。
2. 等到控制台显示 `ESMP READY` 和 `Settlement protection enabled`。使用 **Minecraft Java 26.2** 连接 **`127.0.0.1:25579`**。
3. 控制台可输入 `gamemode creative <你的用户名>` 获取测试材料。创建聚落不需要 OP；控制台诊断用 `esmp status`。
4. 游戏内输入 `/settlement create Oakfall`，走到共享边的区块后输入 `/settlement claim Oakfall`；用 `/settlement view` 查看边界。
5. 完成后在控制台输入 `stop`。再次启动后，认领、团队、待处理邀请和历史仍应存在。

本机已下载的 JDK 可这样使用（其他机器请使用自己的 JDK 25）：

```sh
export JAVA_HOME="$PWD/.tooling/jdk-25.0.4.1+1/Contents/Home"
ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh
```

脚本使用 `.run/dev/`，默认仅绑定回环地址，保持正版登录验证。测试用超平坦世界不属于正式地图。开发服的原版出生点保护设为 0，避免它干扰聚落权限测试；插件不会修改生产服务器的出生点保护设置。

**热重载不受支持。** 启动存储失败或运行中的持久化失败会停止服务器，而不是留下失去保护的主世界。修改配置后请完整重启，不要使用 `/reload` 或插件管理器热卸载。

## 两名玩家的验证清单

用两个真实账号验证权限；单账号仍可验证认领、连通性、历史和边界。

1. 玩家 A 创建 Oakfall 并放置箱子、门和方块；玩家 B 作为 Visitor 尝试破坏方块、打开箱子，应该被拒绝，但门、按钮和拉杆应正常使用。
2. A 执行 `/settlement invite Oakfall <B>`。B 接受之前没有权限；点击邀请消息或执行 `/settlement accept <offer-id>` 后可以建造和使用箱子。
3. A 执行 `/settlement promote Oakfall <B>`，B 接受后可以扩展认领；B 不能管理其他 Manager、转移所有权或放弃聚落。
4. B 打开箱子时，A 执行 `/settlement kick Oakfall <B>`。箱子界面应关闭，B 立即恢复 Visitor 权限。相邻但独立的聚落不继承 B 的成员身份。
5. A 执行 `/settlement abandon Oakfall`，确认警告后执行 `/settlement confirm <offer-id>`。变更认领或超过确认期限后，旧确认必须失效；最后一个区块不能用普通 unclaim 删除。

粒子只发送给查看者，持续 10 秒：白色表示当前区块，绿色表示有团队建造权限，红色表示 Visitor。只绘制附近已加载区块，每次最多 256 个粒子，不加载远处区块，也不放置标记方块。

## 命令

聚落名为 1–32 个字母、汉字、数字、下划线或连字符，不含空格；当前持有的名字不区分大小写且不重复。

| 操作 | 命令 |
|---|---|
| 认领与查看 | `/settlement create <name>`、`claim <name>`、`unclaim`、`view [name]` |
| 团队与信息 | `/settlement info [name]`、`list`、`members [name]`、`rename <name> <new-name>` |
| 邀请与角色 | `/settlement invite\|promote\|demote\|kick\|transfer <name> <player或UUID>` |
| 待办与退出 | `/settlement offers`、`accept\|decline\|cancel <offer-id>`、`leave <name>` |
| 放弃与历史 | `/settlement abandon <name>`、`confirm <offer-id>`、`history <name或旧聚落UUID>` |

表格中省略的命令都以 `/settlement` 开头。玩家名只解析在线或服务器已缓存的账号；未知账号可使用 UUID，不进行阻塞式网络查询。

只有显式 `/settlement bypass on` 才会绕过直接保护，要求 `esmp.admin` 权限；OP 或创造模式本身不绕过保护。用 `bypass off` 关闭，退出游戏也会清除。它不绕过认领拓扑和所有权操作规则。

## 保护边界

- Visitor 允许通行、门/活板门/栅栏门、按钮、拉杆和压力板；不整体取消右键，也不切换游戏模式。私人背包和普通食物使用不作为受保护容器处理。
- 保护检查覆盖放置/破坏、多方块放置、桶、采收、告示牌、考古刷、容器打开及操作、展示实体/载具/动物交互，以及投射物、药水和引燃造成的相关影响。
- 流体、活塞、漏斗/发射器和结构生长不得跨越不同认领边界；同一聚落内部的正常机械仍可工作。同一 Owner 的两个相邻聚落仍是不同边界。
- 无法归属到有权限玩家的实体爆炸不能破坏认领方块；不明来源的展示实体破坏会被拒绝。访客可用的公共控制器仍可能触发聚落内部机械，设计时不要把危险开关当成私有权限。
- PvP、地面物品拾取和自然生物移动不由此聚落权限系统重新定义。控制台命令、WorldEdit 或其他插件直接改世界需要各自的权限与集成；本插件不是对可信服务器代码的沙箱。

## 配置与数据

首次启动生成 `plugins/ESMP/config.yml`，世界映射仍只绑定已加载的正确维度：

```yaml
config-version: 2
worlds:
  MAIN: world
  RESOURCE_OVERWORLD: resource
  RESOURCE_NETHER: resource_nether
  RESOURCE_END: resource_the_end
settlements:
  chunk-cap-enabled: true
  max-chunks: 64
  invitation-seconds: 86400
  transfer-seconds: 1800
  promotion-seconds: 86400
  abandon-seconds: 60
```

旧配置缺少 `settlements` 段时使用同样的默认值。关闭上限用 `chunk-cap-enabled: false`，不要将数量设为 0。已经生成的邀请记录保存自己的到期时间，不会因之后修改默认值而延长。

数据库为 `plugins/ESMP/esmp.db`。Core 世界身份版本仍为 1，Civilization 独立维护 `civilization_schema` 版本 1，以及 `settlements`、`settlement_members`、`settlement_chunks`、`settlement_offers` 和 `settlement_events`。

命令通过异步 SQLite 事务执行；提交并在服务器线程更新索引之后才公布成功。在变更期间，目标认领和待新增区块暂时拒绝受保护操作，避免旧缓存放行。历史与事件发件箱在同一事务中记录；`SettlementCommittedEvent` 只在提交后同步发布，监听方按事件 ID 去重并自行处理其消费失败，原始历史不会因投递确认而删除。

## 模块与协作

- `esmp-core`：无 Paper 依赖的世界身份与有界 SQLite 执行器。
- `esmp-civilization`：Settlement 领域模型、应用事务、拓扑、角色、邀请和历史。
- `esmp-resource`：资源世界定义，重置玩法仍未启用。
- `esmp-plugin`：Paper 生命周期、命令、保护事件适配、边界展示与单 JAR 打包。

完成已验证的里程碑后提交，不提交 `.run/`、`.tooling/`、数据库、日志或密钥；未获请求不推送远端。

[架构](ARCHITECTURE.md) · [代理规则](AGENTS.md) · [决策](.agent/DECISIONS.md) · [未决事项](.agent/OPEN_QUESTIONS.md) · [交接](.agent/HANDOFF.md)

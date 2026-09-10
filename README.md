# Evolve: SMP（ESMP）

ESMP 面向永久文明主世界和周期更新资源世界的长期生存服务器。完整设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。工厂世界已移出当前范围。

**版本边界：当前 JAR 仍运行已实现的旧版聚落角色、免费认领、玩家命名与活跃度规则。新版架构中的付费认领、Governor/Citizen/Public、禁令、Parcel 与管理工具尚未实现。下方现有命令和测试清单用于当前版本，不代表新版迁移已经完成。**

## 当前可玩功能

- 创建、扩展、取消区块认领；四方向连通、默认 64 区块上限、独立相邻聚落。
- Owner / Manager / Member / Visitor 固定角色，邀请与接受、管理者升降级、所有权转移、退出与移除。
- 单独确认放弃聚落；持久化历史、可恢复的事件发件箱和重启后保持的权限。
- 方块、容器、实体交互保护，以及流体、活塞、漏斗、爆炸等间接修改边界。
- 玩家私有的临时粒子边界；移动时只在进入另一聚落或离开聚落时显示 ActionBar，不在同一聚落的每个区块重复提示。
- 共享游戏时钟与聚落活跃度：持久化评分、每日贡献上限、衰减/恢复阈值，以及 `/settlement activity [name]` 查询。

**建筑自动识别、侵蚀、修复和资源重置尚未启用。** 活跃度只提供资格状态，不会自动侵蚀或产出物品。MVP 不按不活跃或废墟状态自动释放聚落；动态聚落周期是 MVP 稳定后必须推进的下一大阶段，不是可无限延期的可选功能。

## 新版 MVP 目标与后续阶段（尚未实现）

- **付费认领与命名**：创建/扩张消耗货币，边际成本随现有认领面积增加；初始使用稳定编号和可选地理标签，正式命名及之后改名由管理员批准并保留历史。
- **治理与授权**：Owner / Governor / Citizen / 派生 Public；BANNED 是聚落级负面覆盖，不是成员角色。禁令不阻止通行，管理员绕过禁令；普通交互由聚落或 Parcel 策略决定。
- **Parcel**：聚落内部世界对齐的 8×8×8 单元集合，六面连通、不重叠；允许零个或多个 Holder，Public 也可持有。已分配地块的撤销、移除持有人或改边界须治理方和全部 Holder 同意，纠纷由管理员处理；不建模租金和合同期限。
- **完整命令与工具**：所有操作都有命令；Management Book 与 Parcel Wand 只是可替换的便利入口。魔杖配合副手命名牌选择单元，粒子预览后原子提交，权限属于使用者而非物品。
- **建筑与安全修复**：自动发现、稳定身份、内外部空间、开口和支撑关系；翻修改变后，只停用冲突/过时的修复片段并保留历史，不穿过新建室内恢复旧外墙。先采用小规模材质/倒塌规则，不实现完整物理。

经济、简化设施、生产、市场和运输仍在后续范围内；资源重置与季节表现按安全门槛独立推进。MVP 后必须设计动态维护容量、老城/新城激励及渐进边缘释放。人为地貌的粗粒度表达是后续方向；灾害/Epoch 仍待讨论，不是动态周期的前提。完整季节配色继续延期。

所有玩法计时统一使用游戏刻/游戏日时钟，不按现实时间推进；`advance_time=false` 时停止时间驱动的变化，停服期间不补算。调整 tick 速率只改变现实中的快慢，不改变每个游戏日的活跃度/侵蚀规则。“离线生产”仅指玩家离线或区块未加载、但游戏时钟仍在推进的情况。

共享时钟取配置的主世界时间；睡眠及所有向前时间跳转均计入，向后跳转不倒扣。首次安装从计时刻 0 开始；重启从数据库检查点继续，不补算停服时间。主世界 UUID 改变会拒绝启动，避免误用另一世界的时间。现有邀请、边界显示和保护提示节流已迁移到游戏刻。

近期顺序调整为：**付费认领事务与定价接口 → 治理角色迁移和禁令 → Parcel 存储/几何/授权及完整命令 → 管理书、魔杖与正式命名管理 → 建筑识别/空间几何 → 可恢复的侵蚀修复。** 认领扣款需要真实最小账本及失败原子性，不为此提前铺开整套市场/生产系统。建筑检测研究保留，但不再是紧接着开发的第一项。

### 当前实现的活跃度默认规则（旧版授权语义）

- 初始 30 分、上限 100，半衰期为 7 个游戏日；低于 20 分进入 INACTIVE，达到 30 分恢复 ACTIVE。
- 建造/拆除 +1；团队容器打开或公共控件交互 +0.25；Visitor 合法公共控件交互 +0.02。登录、走动、挂机、踩压力板及自动机器不加分。
- 同一玩家/类别间隔至少 200 游戏刻；每玩家每日上限 5、聚落每日上限 20，Visitor 上限为玩家上限的 1/20。冻结时间时不积累贡献。
- 不活跃后 72000 游戏刻进入生产暂停资格；目前没有实际生产。转让不重置分数，新建聚落以成立时刻为起点，旧聚落以迁移时刻为起点。
- `info` 或 `activity` 显示当前评分、状态、当日贡献、最近类别、不活跃起点和恢复所需分数。配置见 `activity` 段；已持久化策略不能直接改值重启，须显式迁移，否则插件安全拒绝启动。

Governor/Citizen/Holder/Public 与禁令/Parcel 对活跃度的影响仍须随授权迁移一起定义和验证，不能仅重命名枚举或把 Holder 当成更高一级角色。

完整边界见 [架构 §16A](ARCHITECTURE.md#16a-economy-transport-and-seasons)，依赖顺序见 [开发路线 §27](ARCHITECTURE.md#27-recommended-development-order)。

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

测试覆盖原有聚落事务与权限，并新增游戏时钟冻结/前跳/回拨/重启、旧邀请期限迁移、活跃度衰减与宽限边界、每日上限、陈旧权限拒绝及恢复区间。

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

脚本使用 `.run/dev/`，默认仅绑定 `127.0.0.1`，并关闭 `online-mode` 与 `enforce-secure-profile`，方便多个不同名称的本地测试客户端加入。**仅限本机测试：离线模式允许冒用名称，不能直接暴露到公网。生产服务器应保持身份验证。**

从在线模式切换到离线模式会改变玩家 UUID，原有背包、OP 和聚落所有权不会按同名自动迁移；原数据没有被删除。如需迁移，先备份并明确账号对应关系。当前已经运行的服务器需要控制台 `stop` 后完整重启，配置和新版 JAR 才生效。

测试用超平坦世界不属于正式地图。开发服的原版出生点保护设为 0，避免干扰聚落权限测试；插件不修改生产服务器的出生点保护设置。

**热重载不受支持。** 启动存储失败或运行中的持久化失败会停止服务器，而不是留下失去保护的主世界。修改配置后请完整重启，不要使用 `/reload` 或插件管理器热卸载。

## 两名玩家的验证清单

用两个不同名称的本地测试客户端验证权限；单客户端仍可验证认领、连通性、历史和边界。

1. 玩家 A 创建 Oakfall 并放置箱子、门和方块；玩家 B 作为 Visitor 尝试破坏方块、打开箱子，应该被拒绝，但门、按钮和拉杆应正常使用。
2. A 执行 `/settlement invite Oakfall <B>`。B 接受之前没有权限；点击邀请消息或执行 `/settlement accept <offer-id>` 后可以建造和使用箱子。
3. A 执行 `/settlement promote Oakfall <B>`，B 接受后可以扩展认领；B 不能管理其他 Manager、转移所有权或放弃聚落。
4. B 打开箱子时，A 执行 `/settlement kick Oakfall <B>`。箱子界面应关闭，B 立即恢复 Visitor 权限。相邻但独立的聚落不继承 B 的成员身份。
5. A 执行 `/settlement abandon Oakfall`，确认警告后执行 `/settlement confirm <offer-id>`。变更认领或超过确认期限后，旧确认必须失效；最后一个区块不能用普通 unclaim 删除。

粒子只发送给查看者，持续 200 游戏刻（20 TPS 下通常 10 秒）：白色为当前区块，绿色为团队，红色为 Visitor。时间冻结时期限不推进。只绘制附近已加载区块，每次最多 256 个粒子，不加载远处区块，也不放置标记方块。

## 命令

聚落名为 1–32 个字母、汉字、数字、下划线或连字符，不含空格；当前持有的名字不区分大小写且不重复。

| 操作 | 命令 |
|---|---|
| 认领与查看 | `/settlement create <name>`、`claim <name>`、`unclaim`、`view [name]` |
| 团队与信息 | `/settlement info [name]`、`list`、`members [name]`、`rename <name> <new-name>` |
| 活跃度 | `/settlement activity [name]`；`info` 也显示活跃度 |
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
config-version: 3
worlds:
  MAIN: world
  RESOURCE_OVERWORLD: resource
  RESOURCE_NETHER: resource_nether
  RESOURCE_END: resource_the_end
settlements:
  chunk-cap-enabled: true
  max-chunks: 64
  invitation-ticks: 1728000
  transfer-ticks: 36000
  promotion-ticks: 1728000
  abandon-ticks: 1200
```

旧配置升级会先备份 `config-before-game-clock.yml`，再按每旧秒 20 游戏刻换算期限。数据库中的旧邀请按迁移时剩余期限换算一次，不重置邀请 ID 或延长期限。关闭区块上限使用 `chunk-cap-enabled: false`。

数据库为 `plugins/ESMP/esmp.db`。Core 世界身份版本仍为 1，Civilization 版本为 2；新增 `game_clock`、`activity_policy`、`settlement_activity`、`activity_actors`、`activity_pauses`，以及成立/历史游戏刻字段。旧历史时间保留，新事件同时记录游戏刻。

正常停服会刷新时钟与待提交贡献；平时每 200 游戏刻保存时钟，涉及游戏时间的领域写入也在同一事务中保存检查点。崩溃可能丢失未提交的纯计时进度/合并贡献；必须配套备份世界和数据库，不能混用无关快照。

命令通过异步 SQLite 事务执行；提交并在服务器线程更新索引之后才公布成功。在变更期间，目标认领和待新增区块暂时拒绝受保护操作，避免旧缓存放行。历史与事件发件箱在同一事务中记录；`SettlementCommittedEvent` 只在提交后同步发布，监听方按事件 ID 去重并自行处理其消费失败，原始历史不会因投递确认而删除。

## 模块与协作

- `esmp-core`：无 Paper 依赖的世界身份、有界 SQLite 执行器与中立时钟原语。
- `esmp-civilization`：Settlement 领域模型、应用事务、拓扑、角色、游戏刻邀请、历史和活跃度。
- `esmp-resource`：资源世界定义，重置玩法仍未启用。
- `esmp-plugin`：Paper 生命周期、命令、保护事件适配、边界展示与单 JAR 打包。

完成已验证的里程碑后提交，不提交 `.run/`、`.tooling/`、数据库、日志或密钥；未获请求不推送远端。

[架构](ARCHITECTURE.md) · [代理规则](AGENTS.md) · [决策](.agent/DECISIONS.md) · [未决事项](.agent/OPEN_QUESTIONS.md) · [交接](.agent/HANDOFF.md)

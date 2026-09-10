# Evolve: SMP（ESMP）

ESMP 面向永久文明主世界和周期更新资源世界的长期生存服务器。完整设计见 [ARCHITECTURE.md](ARCHITECTURE.md)。工厂世界不在当前范围内。

## 当前可玩功能

- 创建、扩展、取消区块认领；四方向连通、默认 64 区块上限、相邻聚落保持独立。
- Owner / Governor / Citizen / Public，邀请、接受、升降级、转让、退出及聚落级禁令。Holder 是独立的 Parcel 关系，不自动成为 Citizen。
- 数字认领价格：默认 `100 + 25 × 当前认领区块数`。**现在只显示和记录价格，不扣任何货币、余额或物品。** 真实货币系统等选定后再接入。
- 聚落内 8×8×8 单元组成的 Parcel，四类权限、零个或多个 Holder、持久化协商批准，以及命令和书/木棍工具。
- 共享游戏时钟、活跃度、历史、重启恢复，以及直接操作和活塞/漏斗等间接边界保护。

新聚落显示 `S#编号`，命令可用编号、`#编号`、当前名称或内部 UUID 定位。内部 UUID 不变；旧名称在迁移时保留为已认证正式名称。新正式名需管理员先认证命名资格，再批准名称。

**建筑识别、侵蚀修复、生产、完整经济、资源重置和季节表现尚未启用。** MVP 不会因低活跃度或废墟状态自动释放聚落；动态聚落周期是 MVP 稳定后必须推进的下一大阶段。

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

产物为 `esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar`。只部署这个 JAR，不单独安装内部模块。

```sh
./gradlew :esmp-core:test :esmp-civilization:test
```

测试包含原有聚落/时钟/活跃度行为，以及角色迁移、数字价格、命名认证、Parcel 连通/重叠、多人协商、陈旧/过期提案和禁令。

## 现在进入游戏验证

1. 已运行的开发服先输入 `stop`，不要热重载。阅读并接受 [Minecraft EULA](https://aka.ms/MinecraftEULA)，运行 `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh`。
2. 等到控制台出现 `ESMP READY` 和 `Settlement protection enabled`。使用 **Minecraft Java 26.2** 连接 **`127.0.0.1:25579`**。
3. 输入 `/settlement create`，记下返回编号。走到相邻区块后输入 `/settlement price 1` 和 `/settlement claim 1`；将示例 `1` 换成自己的编号。
4. 输入 `/esmp tools management_book` 获取管理书；等待至少 40 个推进中的游戏刻后，用 `/esmp tools parcel_wand` 获取 Parcel 木棍。普通玩家可以领取，不需要 OP。
5. 完成后控制台 `stop`。再次启动后，角色、Parcel、提案、时钟与活跃度继续保留。

本机已有工具链可使用：

```sh
export JAVA_HOME="$PWD/.tooling/jdk-25.0.4.1+1/Contents/Home"
ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh
```

脚本使用 `.run/dev/`，默认仅绑定本机，并关闭 `online-mode` 与 `enforce-secure-profile`。**仅限本机测试：离线模式允许冒用名称，不能直接暴露到公网。** 在线/离线 UUID 不同，迁移身份必须先备份并明确映射，不能按同名自动替换所有权。

本次没有自动更新或重启你正在运行的开发服。测试用超平坦世界不属于正式地图；原版出生点保护为 0。启动/运行中的致命存储错误会停止服务器，而不是留下无保护主世界。禁止 `/reload` 或插件管理器热卸载。

## 管理书与 Parcel 木棍

管理书右键打开当前聚落的信息和命令链接：认领、角色、禁令、Parcel/权限、提案及命名管理。物品只带工具标记，**权限取决于使用者**；捡到别人的工具不会得到对方权限。工具可丢弃补领，每种在背包中已有一个时不重复发放；禁止通过合成、铁砧或砂轮把它变成其他有价值物品。

使用木棍：

1. 将铁砧命名后的 Name Tag 放在副手，名称作为 Parcel 名称。
2. 主手持工具木棍，左键选第一个方块，右键选第二个方块。坐标自动对齐到 8×8×8 单元。
3. 查看新单元/已占用冲突预览，再执行 `/settlement parcel commit`。已有同名 Parcel 时是扩张；已属于其他 Parcel 的单元会跳过，但最终不连通则整次拒绝。

不使用工具也能完整操作：`/settlement parcel pos1 [x y z]`、`pos2 [x y z]`，然后 `/settlement parcel add 1 Workshop`、`commit`。不填坐标时使用脚下位置；填写的是方块坐标，不是单元编号。

预览持续 1200 游戏刻；期间角色/土地/Parcel 状态变化可能要求重新预览。选择仅存在于会话，不存储在物品中，退出或重启即清除。青色为当前单元、绿色为选择、蓝色为现有 Parcel 表面、红色为冲突/移除单元；使用有界角点粒子，不加载远处区块。

## Parcel 权限与协商

Parcel 必须位于同一聚落内，单元不重叠、六面连通，名称在本聚落内唯一。当前限额为每 Parcel/选择 4096 单元、每聚落 128 个 Parcel、每 Parcel 32 个 Holder、每聚落 64 个待处理提案。

四类权限为 `BUILD`、`INTERACT`、`CONTAINER`、`AUTOMATION`，允许集合来自 `OWNER,GOVERNOR,CITIZEN,HOLDER,PUBLIC`。例如：

```text
/settlement parcel policy 1 Workshop CONTAINER OWNER,GOVERNOR,HOLDER
/settlement parcel holders 1 Workshop Alice,Bob
/settlement parcel requests 1
/settlement parcel approve 1 <提案UUID>
/settlement parcel reject 1 <提案UUID>
```

新 Parcel 默认允许 Owner/Governor/Citizen/Holder 的全部四类操作，不默认允许 Public。Public 可以成为 Holder。一个人同时是 Citizen 和 Holder 时，任意适用关系被允许即可通过；Owner 也不隐式绕过 Parcel 权限。`none` 表示空允许集合或清空 Holder 列表。

无 Holder 的 Parcel 可由治理方直接管理。分配后，几何、Holder、策略与名称变更要经治理方和所有当前/新 Holder 批准。发起者的批准自动记录，提案有效期为 24000 游戏刻；状态改变、过期或已处理后不能重放。管理员可有审计地直接处理纠纷。**测试协商时不要给普通 Owner 测试号 OP，否则管理员覆盖会绕过协商。**

单元移除先用 `parcel remove` 预览，再 `commit`，仅允许移除表面且不破坏剩余连通性。全 Parcel 用 `delete`；不能绕过协商直接取消其父区块或放弃聚落，必须先按批准流程移除受影响 Parcel。

禁令优先于普通角色/Holder 权限，不阻止进入、离开和查看必要信息。管理员按在线 `esmp.admin` 权限或离线 OP 身份识别；不能将已识别管理员加入禁令。禁令豁免本身不是建筑权限豁免，普通世界保护的管理员绕过仍需显式 `/settlement bypass on`。

## 两名玩家的验证清单

完整待验收记录见 [临时测试清单](.agent/TEST_CHECKLIST.md)。旧版本已确认的结果保留为历史，不代表新迁移自动通过用户验收。

1. A 创建聚落；B 仍为 Public，在未分配区允许安全控件，但不能破坏或打开持久容器。
2. A 创建 Workshop，提议把 B 加为 Holder；B 批准后可以按 Parcel 策略建造/开箱，但在 Parcel 外仍是 Public。
3. B 打开 Parcel 箱子时，A 执行 `/settlement ban 1 B`：箱子应关闭，之后操作被拒绝，但 B 仍能移动。`unban` 恢复正常策略。
4. A 尝试删除已分配 Parcel：先产生提案，B 批准前土地仍保留。加入多个 Holder 后必须全部批准。
5. 相邻 Parcel 的漏斗默认不能跨边界传输；只开放一侧的 `PUBLIC AUTOMATION` 仍不能传输，两侧都明确开放才允许。

## 命令

聚落参数接受编号/名称/UUID。官方名和 Parcel 名使用 1–32 个字母、汉字、数字、下划线或连字符。玩家名仅解析在线/已缓存账号；其他情况使用 UUID，不进行阻塞网络查询。

| 操作 | 命令 |
|---|---|
| 创建/认领 | `/settlement create`、`price [聚落]`、`claim <聚落>`、`unclaim` |
| 查看 | `/settlement info [聚落]`、`activity [聚落]`、`list`、`members [聚落]`、`view [聚落]` |
| 成员与治理 | `/settlement invite\|promote\|demote\|kick\|transfer <聚落> <玩家>`、`leave <聚落>` |
| 禁令 | `/settlement ban\|unban <聚落> <玩家>`、`bans <聚落>` |
| 邀请/确认 | `/settlement offers`、`accept\|decline\|cancel <offer-id>`、`abandon <聚落>`、`confirm <offer-id>` |
| 命名/历史 | `/settlement certify <聚落> <true\|false>`、`rename <聚落> <正式名>`（均需管理员）、`history <聚落或历史UUID>` |
| 工具 | `/esmp tools <parcel_wand\|management_book>`；操作帮助为 `/settlement parcel help` |
| Parcel 选择 | `/settlement parcel pos1\|pos2 [x y z]`、`clear`、`add <聚落> <名>`、`commit` |
| Parcel 查看 | `/settlement parcel list <聚落>`、`info\|view <聚落> <Parcel>` |
| Parcel 修改 | `/settlement parcel remove <聚落> <Parcel> <x y z>`、`delete <聚落> <Parcel>`、`rename <聚落> <Parcel> <新名>` |
| Holder/权限 | `/settlement parcel holders <聚落> <Parcel> <玩家逗号列表\|none>`、`policy <聚落> <Parcel> <权限域> <关系逗号列表\|none>` |
| Parcel 提案 | `/settlement parcel requests <聚落>`、`approve\|reject <聚落> <提案UUID>` |

表格省略前缀的命令继承所在行的 `/esmp`、`/settlement` 或 `/settlement parcel`。工具只能由玩家领取，使用 `esmp.settlement` 权限（默认开放）；`/esmp status` 仍仅限管理员。旧领取入口和物品材质名参数不再作为别名保留；已有工具继续可用。

## 活跃度与保护边界

活跃度仍为初始 30、上限 100、七游戏日半衰期，低于 20 不活跃，达到 30 恢复；三游戏日后出现生产暂停资格，但实际生产未启用。团队 BUILD +1、INTERACTION +0.25；Public（包括非 Citizen Holder）合法贡献 +0.02，日上限 0.25。团队玩家日上限 5，聚落上限 20，同人同类间隔 200 游戏刻。冻结时间、登录、走动、压力板通行和自动机器不加分。

SQL 会重验角色、禁令、Parcel 策略及相关修订，不能只凭缓存或旧排队事件获得贡献。迁移保留原评分和上限记录；任意修改已安装的 Activity 数值策略仍需显式迁移，否则启动安全拒绝。

未归属玩家的内部机械使用 Holder 自动化许可（未分配 Parcel 使用 Owner 许可）；跨保护边界需两侧明确允许 Public 自动化。容器权限与建造权限独立；权限撤销会关闭已打开的非法容器。原有 PvP、地面拾取和自然生物移动范围不变；其他插件/控制台直接改世界仍需要各自权限与集成。

## 配置与数据

当前 config version 为 4，Core 世界 schema 为 1，Civilization schema 为 3。旧配置备份到 `config-before-parcels.yml`；迁移替换角色名称并保留 UUID、待处理邀请和历史，同时分配稳定数字编号。旧正式名称保留，新聚落先无正式名。

```yaml
config-version: 4
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
  claim-base-price: 100
  claim-price-step: 25
```

`activity.public-credit` 替代旧 `visitor-credit`，数值等价的策略会安全迁移。所有玩法期限使用共享游戏刻；睡眠和向前时间跳转计入，回拨不倒扣，停服不补算。正常关闭刷新时钟/待提交观察；崩溃仍可能丢失未检查点的纯计时进度和未提交观察，必须配套备份世界与数据库。

数据库仍是 `plugins/ESMP/esmp.db`。新增 `settlement_details`、`settlement_bans`、`parcels`、`parcel_cells`、`parcel_holders`、`parcel_policy`、`parcel_requests`、`parcel_approvals`。所有治理/Parcel变更经过 SQLite 事务和主线程提交后快照发布，不能绕过保护发布门槛。

## 下一步与协作

下一步是建筑检测评估及稳定身份、内外部空间和翻修冲突建模。真实货币接入等你选定系统；不提前扩展完整市场/生产。MVP 后必须推进动态聚落周期，灾害和完整季节配色另行讨论。

完成已验证的里程碑后提交；不提交 `.run/`、`.tooling/`、数据库、日志或密钥，未获请求不推送远端。

[架构](ARCHITECTURE.md) · [代理规则](AGENTS.md) · [决策](.agent/DECISIONS.md) · [未决事项](.agent/OPEN_QUESTIONS.md) · [交接](.agent/HANDOFF.md) · [测试清单](.agent/TEST_CHECKLIST.md)

# Evolve: SMP（ESMP）

ESMP 面向永久文明主世界和可更新资源世界。当前唯一设计依据是 [2026-09-12 冻结 MVP 架构包](ESMP%20MVP%20ARCHITECTURE%202026-09-12/README.md)，包括[架构](ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md)、[需求冻结](ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md)和[严格路线图](ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md)。根目录旧架构已删除，不再作为执行依据。

**冻结路线图 Phases 0–8 的核心服务端门禁已通过，里程碑标签为 `mvp-v1.0`。** 材料／游戏时间修复和 History/Era 闭环已验证；原生客户端与玩家验收仍单独记录。下一步只冻结 MVP+1 Dynamic Settlement Cycle 的设计，不直接开始自动释放土地。

## 当前已验证核心功能（玩家验收另列）

- 创建、扩展、取消区块认领；四方向连通、默认 64 区块上限、相邻聚落保持独立。
- Owner / Governor / Citizen / Public，邀请、接受、升降级、转让、退出及聚落级禁令。Holder 是独立的 Parcel 关系，不自动成为 Citizen。
- 付费认领：先报价再确认；首次建镇固定 25，后续按不可变地价、土地组合、规模、活跃度和个人优惠计价。缺失自然估值时拒绝购买。
- 聚落内 8×8×8 单元组成的 Parcel，四类权限、零个或多个 Holder、持久化协商批准，以及命令和书/木棍工具。
- 共享游戏时钟、聚落总活跃度、区块级局部活跃度与派生支持区域、历史、重启恢复，以及直接操作和活塞/漏斗等间接边界保护。
- 整数钱包、聚落金库、转账、预算内发行及首次加入资金已存在。建镇从创始人钱包付款；所有扩张都从聚落金库付款，无论操作者是 Owner 还是 Governor，均按当前 Owner 的土地组合与优惠评估。
- 新生成主世界区块的自然估值固定保存；`/settlement landvalue` 查看当前区块。后续挖填建造不改估值，未捕获的旧区块明确显示未知。
- Owner/Governor 显式注册有体积上限的建筑；合法放置、拆除等建造操作更新基线，旧版本保留。每栋建筑可设置一个修复站。
- 已注册建筑按局部支持与宽限期发生有限 WEATHER / DROP / CAVE_IN；恢复活跃会停止后续衰败，但不会自动修好已有损伤。

新聚落显示 `S#编号`，内部 UUID 和公开编号稳定。新正式名需要先满足认证条件、提交申请并由管理员批准；已 VERIFIED 的正式改名仍由管理员办理。旧审批标记或已有正式名会明确迁移为 VERIFIED，不追造历史活跃贡献。

修复站接受真实普通材料，按游戏时间分批恢复当前损伤；符合条件的 RUINED→Repair→INTACT 周期使 Era 加一，同一周期不会重复增加。衰败不会自动释放土地；建筑的 ACTIVE/RETIRED 是注册生命周期，INTACT/DAMAGED/RUINED 才是损伤状态。

## 冻结 MVP 的执行顺序

| 阶段 | 交付目标 |
|---|---|
| Phase 0 | Liquibase 5.0.4 结构化 changelog、已验证旧库桥接、临时 SQLite → jOOQ 生成、UnitOfWork/Repository 边界、JUnit 6.1.2、ArchUnit 和依赖校验元数据 |
| Phase 1 | 重验付费认领；建镇从钱包付款，所有扩张从金库付款，按当前 Owner 评估 |
| Phase 2 | UNVERIFIED/VERIFIED 申请与审核；默认年龄 3 游戏日、当前非 INACTIVE、终身有效活跃贡献 20 |
| Phase 3 | 接收方接受、土地购置费报价、管理员批准、费用与所有权原子提交 |
| Phase 4 | Claim 区块级 Local Activity、有限邻近支持、派生 ActivityRegion；不释放土地 |
| Phase 5 | Owner/Governor 显式注册有体积上限的建筑，建立可随合法改造更新的基线和几何 |
| Phase 6 | 仅注册建筑的 WEATHER/DROP/CAVE_IN，持久化任务、冲突检查、不强加载、不掉落物 |
| Phase 7 | 整栋建筑修复站＋真实材料＋游戏时间；RUINED 经修复回到 INTACT 后每周期 Era 仅加一次 |
| Phase 8 | 不加功能，完成完整验收、性能、配套世界/数据库恢复、迁移和发布门禁 |

每一阶段通过 clean check、迁移、持久化/架构测试及相关 Paper 场景后才进入下一阶段。Phase 0 必须识别当前已经存在的付费认领/补贴数据，不能按过时快照把它们删除或退回免费认领。

自动认领释放／Dynamic Settlement Cycle 是 **MVP v1.0 验收并打标签后的强制 MVP+1**，不是当前任务。Server Trade、设施、自动资源重置、传送、货运、季节、灾害、自动全城建筑发现等均不进入 MVP；不建占位表或模块。资源世界 MVP 使用经过验证的管理员维护流程。

## 构建

| 项目 | 选择 |
|---|---|
| Minecraft / Paper | 26.2 / build 121 |
| 插件语言 / 包名 | Java / `xdu.undarubra.esmp` |
| JDK | 25；验证使用 Temurin 25.0.4.1+1 |
| 构建 | Gradle Wrapper 9.7.1，Kotlin DSL |
| SQLite JDBC / 测试 | 3.53.4.0 / JUnit 6.1.2 / ArchUnit 1.5.0 |
| 类型化持久化 / schema 源 | jOOQ 3.21.8 / Liquibase Community 5.0.4 XML changelog |

设置 `JAVA_HOME` 指向 JDK 25，然后运行：

```sh
./gradlew build
```

产物为 `esmp-plugin/build/libs/esmp-0.1.0-SNAPSHOT.jar`。只部署这个 JAR，不单独安装内部模块。

`build`/`check` 会先在临时 SQLite 中应用 `schema/db/changelog/db.changelog-master.xml`，再生成各模块的 `build/generated-src/jooq`。不要手改/提交生成目录。完整验收运行 `./gradlew clean check build`；依赖版本锁和 `gradle/verification-metadata.xml` 校验文件须随更新提交。新增 schema 变更写结构化 Liquibase changeSet，不再添加旧 SQL 迁移。

```sh
./gradlew :esmp-core:test :esmp-civilization:test :esmp-economy:test
```

测试包含原有聚落/时钟/活跃度行为，以及角色迁移、数字价格、命名认证、Parcel 连通/重叠、多人协商、陈旧/过期提案和禁令。
账本回归覆盖供给守恒、销毁、预算、重复交易、启动资金防重、溢出、并发透支、事务回滚、权限及重启。

## 现在进入游戏验证

1. 已运行的开发服先输入 `stop`，不要热重载。阅读并接受 [Minecraft EULA](https://aka.ms/MinecraftEULA)，运行 `ESMP_ACCEPT_EULA=true ./scripts/run-dev.sh`。
2. 等到控制台出现 `ESMP READY` 和 `Settlement protection enabled`。使用 **Minecraft Java 26.2** 连接 **`127.0.0.1:25579`**。
3. 到有自然估值的新区域输入 `/settlement create`，查看价格，点击 `[Confirm]` 并执行 `/settlement purchase <报价UUID>`。记下聚落编号；到相邻区块用 `/settlement claim 1` 报价，按报价金额用 `/esmp treasury 1 deposit <金额>` 给金库注资，再确认购买。将示例 `1` 换成自己的编号。
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
2. 主手持木棍瞄准：能触及方块时，蓝色表面标出该方块所在单元；朝向空气或方块超出触及时，标出视线方向上玩家所在单元之外的第一个单元。
3. 左键确定起点，移动视线查看蓝色边框预览，右键确定终点；方块和空气都可以点击。点击聊天中的 `[Confirm]` 或执行 `/settlement parcel commit`。

也可使用 `/settlement parcel pos1 [x y z]`、`pos2 [x y z]`，然后 `/settlement parcel add 1 Workshop`、`commit`。不填坐标时使用当前瞄准单元；填写时是方块坐标。

**蓝色边框是预览，绿色边框是已有 Parcel，红色边框是无效/排除的选择。** 当前目标使用更密的蓝色粒子表面。第一点选定后预览跟随视线变化；无效选择保持红色并给出简短原因。已有同名 Parcel 时扩张它；被其他 Parcel 占用的单元会排除，最终形状仍须连通。

目标每两服务器刻更新；瞄准变化时立即重绘，静止时每四刻刷新。每个查看者每帧最多 1536 个粒子，附近边框和校验结果使用缓存。预览有效期为 1200 游戏刻，确认后清除；退出或重启清除会话选择。工具说明在加入、切换物品和使用时更新。

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

禁令优先于普通角色/Holder 权限，不阻止进入、离开和查看必要信息。管理员按在线 `esmp.admin` 权限或离线 OP 身份识别；不能将已识别管理员加入禁令。禁令豁免本身不是建筑权限豁免，普通世界保护的管理员绕过仍需显式 `/settlement bypass on`；测试后用 `/settlement bypass off` 关闭。

## 两名玩家的验证清单

完整待验收记录见 [临时测试清单](.agent/TEST_CHECKLIST.md)。旧版本已确认的结果保留为历史，不代表新迁移自动通过用户验收。

1. A 创建聚落；B 仍为 Public，在未分配区允许安全控件，但不能破坏或打开持久容器。
2. A 创建 Workshop，提议把 B 加为 Holder；B 批准后可以按 Parcel 策略建造/开箱，但在 Parcel 外仍是 Public。
3. B 打开 Parcel 箱子时，A 执行 `/settlement ban 1 B`：箱子应关闭，之后操作被拒绝，但 B 仍能移动。`unban` 恢复正常策略。
4. A 尝试删除已分配 Parcel：先产生提案，B 批准前土地仍保留。加入多个 Holder 后必须全部批准。
5. 相邻 Parcel 的漏斗默认不能跨边界传输；只开放一侧的 `PUBLIC AUTOMATION` 仍不能传输，两侧都明确开放才允许。

## 命令

聚落参数接受编号/名称/UUID。官方名和 Parcel 名使用 1–32 个字母、汉字、数字、空格、下划线或连字符。只修剪首尾空白，中间的连续空格保留，重名比较仍不区分大小写。命令中的多词名称用双引号，例如 `/settlement rename 1 "New Haven"`、`/settlement parcel info "New Haven" "Tool Store"`；Name Tag 直接命名为 `Tool Store`，无需引号。玩家名仅解析在线/已缓存账号；其他情况使用 UUID，不进行阻塞网络查询。

| 操作 | 命令 |
|---|---|
| 报价/购买/退地 | `/settlement create`、`price [聚落]`、`claim <聚落>`、`purchase <报价UUID>`、`unclaim` |
| 查看 | `/settlement info [聚落]`、`activity [聚落]`、`list`、`members [聚落]`、`view [聚落]` |
| 成员与治理 | `/settlement invite\|promote\|demote\|kick <聚落> <玩家>`、`leave <聚落>` |
| 禁令 | `/settlement ban\|unban <聚落> <玩家>`、`bans <聚落>` |
| 邀请/确认 | `/settlement offers`、`accept\|decline\|cancel <offer-id>`、`abandon <聚落>`、`confirm <offer-id>` |
| 认证申请/审核 | `/settlement certification <聚落>`、`apply <聚落> [建议名称]`、`applications <聚落>`、`review <聚落> <申请UUID> <approve\|deny> [说明]`（审核需管理员）、`verified` |
| 所有权购置 | `/settlement transfer <聚落> <接收方>`、`acquisitions <聚落>`、`acquisition <购置UUID>`、`accept-transfer <购置UUID> <报价金额>`、`review-transfer <购置UUID> <approve\|deny> [说明]`（管理员）、`cancel-transfer <购置UUID>` |
| 命名/历史 | `/settlement rename <已认证聚落> <正式名>`（管理员）、`history <聚落或历史UUID>` |
| 工具 | `/esmp tools <parcel_wand\|management_book>`；操作帮助为 `/settlement parcel help` |
| Parcel 选择 | `/settlement parcel pos1\|pos2 [x y z]`、`clear`、`add <聚落> <名>`、`commit` |
| Parcel 查看 | `/settlement parcel list <聚落>`、`info\|view <聚落> <Parcel>` |
| Parcel 修改 | `/settlement parcel remove <聚落> <Parcel> <x y z>`、`delete <聚落> <Parcel>`、`rename <聚落> <Parcel> <新名>` |
| Holder/权限 | `/settlement parcel holders <聚落> <Parcel> <玩家逗号列表\|none>`、`policy <聚落> <Parcel> <权限域> <关系逗号列表\|none>` |
| Parcel 提案 | `/settlement parcel requests <聚落>`、`approve\|reject <聚落> <提案UUID>` |


## 聚落认证与正式命名

默认申请条件：聚落年龄至少 3 游戏日、当前活跃度不是 INACTIVE、终身实际获得的有效活跃贡献至少 20。条件只允许申请，不会自动认证。终身贡献只累计通过权限、冷却和上限检查后真正获得的分数；初始分数、登录/挂机和管理员改方块不算，旧版本衰减分数不会补造成终身贡献。

Owner/Governor 执行 `/settlement apply 1 "建议名称"`；也可不填名字。管理员用 `/settlement applications 1` 查看申请 ID，再执行 `/settlement review 1 <申请UUID> approve "说明"` 或 `deny "说明"`。多词名称/说明使用双引号。拒绝保留土地、权限和资金，仍可重新申请；批准时再次核对条件、申请人权限、所有权/命名修订及名字冲突。

`/settlement certification 1` 显示状态、年龄、终身贡献和待审申请；`/settlement verified` 是插件内已认证正式名列表，不是网站。审批人、时间、说明与结果持久化。重复同一审批不会重复写历史，也不会覆盖之后的改名。已认证聚落不会因后来不活跃而自动撤销。

旧 `/settlement certify ... true/false` 已移除。配置为 `certification.minimum-age-ticks: 72000`、`minimum-lifetime-credit: 20.0`；这是数值调参，不是跳过管理员审核。认证不收费；所有权购置另按下节收取土地费用，未实现网站。

## 所有权购置

Owner 用 `/settlement transfer 1 Bob` 提议当前 Citizen/Governor 接手；`acquisitions 1` 列出最近 50 条，`acquisition <UUID>` 查看指定记录。默认费用为当前认领区块不可变基础地价合计的 10%，向上取整为整数；不使用旧购地优惠，不估算建筑或劳动价值，缺少估值就拒绝报价。

接收方执行 `/settlement accept-transfer <购置UUID> <报价金额>`，必须确认准确金额。此时**不扣款、不换 Owner**。管理员执行 `/settlement review-transfer <购置UUID> approve "说明"` 才重新核对角色、禁令、所有权/区块/估值/配置及余额，并在同一事务内从接收方钱包销毁费用、转移 Owner、将旧 Owner 改为 Citizen、保存历史。金库身份、余额与历史保留；个人优惠不重置。新 Owner 不保留重复成员行。

余额不足不会部分扣款或转移；在原报价有效期内补足后可重试。报价过期或上下文改变需取消并重新提议，不能静默改价。管理员 `deny` 拒绝，双方或管理员可 `cancel-transfer` 取消；拒绝/取消不收费。重复同一批准（同一审核人及说明）只返回原结果，不重复扣款。

`acquisition.offer-ticks` 默认 36000 游戏刻，`fee-basis-points` 默认 1000（10000=100%）。升级到 config 10 时保存 `config-before-acquisition.yml`，旧 `settlements.transfer-ticks` 迁至新期限字段。旧待处理 TRANSFER 提案明确取消，不沿用旧同意；已接受的历史提案保留，普通邀请不受影响。


## 付费认领与个人优惠

`create`、`claim` 和 `price` 都只生成报价，不扣款；确认购买才提交。报价默认有效 1200 游戏刻，换报价会替换本人之前未购买的报价。购买时须在原世界/区块；过期、权限/土地/配置变动或余额不足会拒绝，不能部分扣款。已成功的报价可以重试，不会重复收费。

首次建镇 25；之后第 2–5 次购地默认乘 .50/.65/.80/.90，后续无优惠。另建附近聚落才加邻近系数；普通扩张没有邻近附加。取消认领、放弃、转让均不退款、不重置个人已用次数。所有扩张从金库扣款，Owner 钱包有钱也不会自动代付；先用 `/esmp treasury 1 deposit 50` 存入需要的资金。Governor 还必须有足够授权额度，不使用 Governor 的个人优惠。

`claim-pricing` 用整数基点配置系数（10000=1倍）；最终金额只向上取整一次。报价显示主要原因和付款账户。已有开发存档按旧 FOUNDED/CHUNK_CLAIMED 事件的操作者迁移已用次数，不追扣旧认领费用。完整默认配置见插件 `config.yml`。

Phase 1 升级不会改写已付款凭据或个人历史；旧版本未购买报价会提示上下文已变，需要重新报价。个人已购次数同时作为单调递增的 progression revision，购买成功才推进；失败、重复确认和转让均不重置它。这次无需新列或重复计数器，schema/config 版本保持不变。
表格省略前缀的命令继承所在行的 `/esmp`、`/settlement` 或 `/settlement parcel`。工具只能由玩家领取，使用 `esmp.settlement` 权限（默认开放）；`/esmp status` 仍仅限管理员。旧领取入口和物品材质名参数不再作为别名保留；已有工具继续可用。

`/esmp status` 在状态行之后立即显示累计游戏日（从第 0 日开始）、当日刻数、总 Game tick，以及 `advancing`/`paused`。下一行是主世界昼夜时钟 `HH:mm`（原版 tick 0 对应 06:00）。累计时间与天空时间不是同一概念：冻结或回拨天空时间不会倒扣累计时间。这里尚未启用公元日期或季节日历。

## 钱包、金库与发行预算

```text
/balance
/pay Bob 25
/esmp economy history [玩家]
/esmp treasury 1 balance
/esmp treasury 1 deposit 30
/esmp treasury 1 withdraw 10
/esmp treasury 1 pay Bob 5
/esmp treasury 1 history
/esmp treasury 1 budget GovernorName 50
/esmp treasury 1 allowance [Governor]
```

钱包只能由本人支出；金库提款仅限 Owner。Governor 支付需要 Owner 用 `budget` 设置剩余额度（不是增加额度），每次支付消耗额度；换 Owner 或降级后重新晋升都会使旧额度失效。金库有余额时不能放弃聚落。普通玩家不能发行货币。
查看其他玩家的钱包历史需要管理员权限；Owner 可用 `allowance <Governor>` 查看指定 Governor 的剩余额度。

管理员测试发放：`/esmp economy grant Alice 100`；直接发给金库用 `grant-treasury 1 100`。查看发行与总供给用 `/esmp economy budget`。金额必须是正整数；额度允许设为 0。转账、发放、存取、金库支付和额度变更可在末尾附交易/操作 UUID；重复提交同一 UUID 和同一内容只返回旧凭据，不重复扣款。点击成功消息可复制凭据 UUID。

`economy.currency` 配置显示名称、符号和格式；默认货币 ID 为 `esmp:coin`。默认预算周期为七游戏日，管理员按实际活跃规模选择 `issuance-tier`：small 2000、medium 5000、large 10000、very_large 20000。预算不自动发完、不结转，跳过周期不补发；首次访问周期时固定该期额度，后续配置变更只影响尚未建立的周期。冻结游戏时间也冻结周期。

首次加入服务器的玩家默认自动领取 100 启动资金（`economy.starting-funds`，正整数）；以加入事件中的 `hasPlayedBefore` 判断，不给已有玩家补发。每个 UUID 只发一次，重连或重启不会重复领取。预算不足时显示待发提示，并在后续货币周期自动重试，离线也能到账；已登记的金额不受后续配置改动影响。到账消息显示金额、最终余额和 `[Balance]` 链接；离线到账可回来后查钱包/流水。时间冻结不会生成新周期，挂机和在线人数不会自动增加发行额度。

## 自然土地估值

输入 `/settlement landvalue`，或使用管理书的 `Natural land value` 链接，查看当前主世界区块的基础数值、类型和估值版本。其他维度不参与。这个数值还不是认领报价，也不会扣款。

捕获从 `ESMP READY` 后的新区块生成完成事件开始；此前生成的出生区等旧地形显示 `unknown`，不会拿玩家改造过的地形补算。请走到真正未生成的新区域测试。

初始规则采样每区块 4×4 个地表点：默认 `100 - min(高差 × 2, 50)`；部分采样点有水时加 25，全水区不加。只保存结果及版本，不扫描所有方块。`land-values` 可配置 `version`、`base-value`、`relief-penalty`、`max-relief-penalty`、`shore-bonus`；最大扣减必须小于基础值。改变系数必须使用新版本，只影响以后生成的区块。

捕获同时写入区块 PDC 日志，再异步保存到 SQLite。加载或查看区块会重放已有日志，不重新采样；已有估值出现冲突时拒绝覆盖。正常保存/备份仍须同时保留世界与数据库。没有日志且没有 SQL 记录的区块保持未知，不能伪装成已有自然估值。

## 建筑注册、改造与历史

Owner/Governor 可注册已经存在或新建的建筑。整个选择必须属于同一个聚落，并且使用者对覆盖的 Parcel/空间都有 BUILD 权限；Owner 不会绕过 Holder 策略。所有触及区块必须已经自然加载，注册和调整范围不会代为加载。

```text
/settlement building pos1 [x y z]
/settlement building pos2 [x y z]
/settlement building preview
/settlement building register 1 "Workshop"
```

不填坐标时取玩家脚下所在方块坐标。两点均包含在选择中；默认最多 4096 格，`buildings.max-volume` 可设 1–32768，绝对上限 32768。活动建筑的选择体积不能重叠；大型结构可拆成不重叠的建筑。名字在同一聚落的活动建筑中唯一，内部 UUID 稳定。选择在离线后清除，不能靠物品获得管理权限。

用 `/settlement building list [聚落] [页码]` 查看活动建筑（每页 32 条），`info <建筑UUID> [修订号]` 查看基线几何，`cell <建筑UUID> <x y z> [修订号]` 查看某格。省略修订号或填 0 表示当前版本；`history <建筑UUID>` 列出最近 50 次修订，已知更早版本仍可直接查询。

基线保存原版 BlockData 与必要的几何标记，不保存箱子物品、告示文本等方块实体 NBT。几何由有界快照导出：占据格、外表面、封闭内部空气、门/活板门等开口与窄孔、简单竖直支撑关系。它不是完整物理模拟或完美建筑识别器。封闭区域的外部空气采用面连通判定，宽敞敞开空间不会被臆造为封闭室内。

合法放置、拆除、桶操作、相关工具改造和玩家触发的生长会更新受影响基线；Citizen 或 Public Holder 只要当前 BUILD 策略允许，也能产生合法改造。拆墙是设计变更，不是衰败损伤；历史墙体不会因此成为当前修复目标。扩大/收缩注册范围时重新选两点，执行 `resize <建筑UUID>`，保留原 UUID 并建立新修订。

大幅重新解释建筑时用 `retire <建筑UUID> confirm`。它不删方块或历史；之后可从世界当前状态重新注册，获得新 UUID。取消覆盖的 Claim 或放弃聚落前，须先退役受影响的活动建筑。

设置修复站：在建筑内放置工作台，待基线更新后执行 `station <建筑UUID> <x y z>`；`station <建筑UUID> clear` 清除标记。站点和方块实体不会被自动衰败／修复。合法拆除工作台会清除当前标记，旧修订仍保留原站点；已有修复任务会重新检查站点与权限。

以上省略前缀的子命令都使用 `/settlement building`。编辑观察先写区块 PDC 日志，再经过现有提交/保护快照发布协调器；稳定操作 ID 防止重启回放旧改造。积压时相关操作会暂时拒绝，不能静默丢弃编辑。恢复只读取自然加载区块；世界实例、权限或边界不一致会安全停止并保留日志，不能删日志强行跳过。仍须配套备份世界与数据库，不能把它们当成一个原子存储。
`/settlement building clear` 清除当前选择；`/settlement building help` 和 `/settlement repair help` 显示完整操作入口。

## 材料修复与 Era

在修复站附近执行：

```text
/settlement repair inspect <建筑UUID>
/settlement repair prepare <建筑UUID>
/settlement repair supply <任务UUID>
/settlement repair status <任务UUID>
/settlement repair start <任务UUID>
/settlement repair cancel <任务UUID>
/settlement repair refund <任务UUID>
/settlement repair jobs <建筑UUID>
```

Owner/Governor 准备整栋建筑修复；供料者是该任务的发起人。供料要求距离站点不超过 6 格，并有站点 INTERACT、待修格 BUILD 权限。只使用背包里的普通对应方块物品，不消耗命名或附带自定义数据的物品。可分次供料，材料齐备才开始；暂停后可用 `start` 重验并继续。默认每 200 游戏刻调度有界批次；时间冻结或区块未自然加载时不会推进，不会为修复加载区块。

只修复当前有效基线的损伤，不重建已经改造、拆除或退役的旧设计。冲突格保留玩家状态，其余有效格可以继续。取消不撤销已经完成的世界修复；`refund` 退回未使用的预留材料，背包容量不足时可分次领取。待恢复的世界操作必须先完成对账，不能先退走其材料。

背包保存、世界保存与数据库提交并非原子操作。持久化意图、玩家收据和区块标记负责重启对账；异常期间操作可能暂时锁定。请配套备份世界、玩家数据和数据库，不要单独回滚其中一份。

Era 初始为 0，只有符合条件的整次废墟修复回到 INTACT 才加一。普通损伤修复、手工重建不加 Era，也没有战力或物品奖励。`/settlement history-catalog` 查看结构化历史类型，`/settlement history <聚落或UUID>` 查看记录；不是聊天存档或历史场景重放。

## 活跃度与保护边界

活跃度仍为初始 30、上限 100、七游戏日半衰期，低于 20 不活跃，达到 30 恢复；三游戏日后出现生产暂停资格，但实际生产未启用。团队 BUILD +1、INTERACTION +0.25；Public（包括非 Citizen Holder）合法贡献 +0.02，日上限 0.25。团队玩家日上限 5，聚落上限 20，同人同类间隔 200 游戏刻。冻结时间、登录、走动、压力板通行和自动机器不加分。

SQL 会重验角色、禁令、Parcel 策略及相关修订，不能只凭缓存或旧排队事件获得贡献。迁移保留原评分和上限记录；任意修改已安装的 Activity 数值策略仍需显式迁移，否则启动安全拒绝。

### 局部活跃度与支持区域

每次通过现有权限、冷却和全部上限检查的实际贡献，同时记入聚落总分和动作所在 Claim 区块。局部分数从 0 开始，默认沿用七游戏日半衰期与总分上限；旧总分不会被拆分成虚构的空间历史。没有周期性全区块写入，查询只按共享游戏时间计算。

默认支持值为：本区块分数 ＋ 四个正交相邻、仍属于同一聚落的区块原始分数之和 × 0.25，最高不超过总分上限。不跨聚落、不算对角、不递归传播。`local-activity.neighbor-weight` 允许 0–0.25，设 0 关闭邻近支持；`support-threshold` 默认 20。两者只是查询时调参，不改已有原始分数。

管理员执行 `/settlement local-activity [聚落] [页码]` 查看测量游戏刻、各区块原始分数/支持值及派生区域；每页 32 个区块，省略聚落时查看当前位置。支持值达到阈值的 Claim 按四方向连通形成 ActivityRegion；显示编号只属于当前快照，不是稳定身份，不能创建、命名或分配成员。

取消认领会在同一事务中删除对应局部桶，重新认领从 0 开始；放弃聚落清除其局部桶，历史仍保留。所有权购置保留聚落的局部数据。认证、付费认领的活跃度修正和普通状态仍使用原聚落总活跃度，不被局部结果替换。

开发接口 `ActivityService.localSupport(settlementId, worldInstanceId, footprint, gameTick)` 按足迹面积加权返回支持值；`footprint` 是 `Map<ChunkPosition,Integer>`，每项代表该区块内 1–256 列方块面积。所有区块必须仍属该聚落及世界实例。接口可投影未来，但不推造早于已记录锚点的历史；另有使用已有 `UnitOfWork.Transaction` 的重载，不加载世界区块、不计算无关区域、不写分数。

未归属玩家的内部机械使用 Holder 自动化许可（未分配 Parcel 使用 Owner 许可）；跨保护边界需两侧明确允许 Public 自动化。容器权限与建造权限独立；权限撤销会关闭已打开的非法容器。原有 PvP、地面拾取和自然生物移动范围不变；其他插件/控制台直接改世界仍需要各自权限与集成。

## 注册建筑衰败

用 `/settlement decay <建筑UUID>` 查看局部支持、衰退起点、资格、损伤格数和状态。系统只处理已注册且未退役的建筑；初次启用时不会把旧建筑立即追溯成已过宽限期。

默认局部支持低于 20 开始衰退，满 72000 游戏刻（三游戏日）后允许衰败；达到 30 才恢复。20–30 之间保留原来的衰退／恢复状态。有效贡献会即时重验恢复阈值，不依赖下一次轮询。冻结游戏时间停止新的物理衰败，停服时间不计入。

- **WEATHER**：仅明确材质替换，例如石砖变裂纹石砖、圆石变苔石；不是任意材质转化。
- **DROP**：一个允许的完整结构方块向下移动一格，目的地必须为空气且仍在该建筑内。
- **CAVE_IN**：最多四个同层面连通方块使用相同的竖直移动原语；每批最多八个源／目标格。

默认效果间隔为 24000 游戏刻，延期检查间隔为 1200。材料配置采取固定白名单；遇到方块实体、站点、危险附件、流体、重力方块或目的地实体会拒绝或等待。源与目的地都按当前 Owner 的 BUILD 权限检查，不借管理员身份或“同一聚落”绕过 Parcel 策略。系统不生成物品／下落方块实体，也不扫描或改变注册范围外的自然地形。

每次先保存稳定操作 ID、基线／权限／活跃度上下文、原状态和目标状态，再在主线程检查并变更。区块未加载就等待，不添加加载票。区块保存标记与 SQL 结果分别恢复：已完成的目标不重复执行，部分批次可以收敛；玩家冲突优先，不覆盖意外新方块。新任务不会越过同区块尚未完成的恢复。

损伤与基线分开保存。无有效损伤为 INTACT，有损伤为 DAMAGED；原可变占据格中达到默认 50% 变为空气时为 RUINED，单纯风化不会误报结构坍塌。活跃恢复不会减少损伤。玩家手动重建、移除残块或合法改造会更新当前设计／损伤并推进基线修订，避免旧衰败意图覆盖新作品；手工修复不消耗站点预留材料，也不会推进 Era。

## 配置与数据

当前 config 14；旧兼容标记 Core 1、Civilization 3、Economy 2、自然估值 1、Claim pricing 1 保持不变。Liquibase `phase7-repair` 增加修复任务、材料、转移意图、世界操作及 Era 记录；旧建筑和历史保留，Era 从 0 开始，不追造历史修复。最新配置备份为 `config-before-repair.yml`，此前备份保留。

```yaml
config-version: 14
worlds:
  MAIN: world
  RESOURCE_OVERWORLD: resource
  RESOURCE_NETHER: resource_nether
  RESOURCE_END: resource_the_end
settlements:
  chunk-cap-enabled: true
  max-chunks: 64
  invitation-ticks: 1728000
  promotion-ticks: 1728000
  abandon-ticks: 1200
acquisition:
  offer-ticks: 36000
  fee-basis-points: 1000
local-activity:
  neighbor-weight: 0.25
  support-threshold: 20.0
buildings:
  max-volume: 4096
repair:
  batch-ticks: 200
decay:
  grace-ticks: 72000
  interval-ticks: 24000
  check-ticks: 1200
  inactive-threshold: 20.0
  recovery-threshold: 30.0
  ruined-fraction: 0.5
```

`activity.public-credit` 替代旧 `visitor-credit`，数值等价的策略会安全迁移。所有玩法期限使用共享游戏刻；睡眠和向前时间跳转计入，回拨不倒扣，停服不补算。正常关闭刷新时钟/待提交观察；崩溃仍可能丢失未检查点的纯计时进度和未提交观察，必须配套备份世界与数据库。

数据库仍是 `plugins/ESMP/esmp.db`。新增 `settlement_details`、`settlement_bans`、`parcels`、`parcel_cells`、`parcel_holders`、`parcel_policy`、`parcel_requests`、`parcel_approvals`。所有治理/Parcel变更经过 SQLite 事务和主线程提交后快照发布，不能绕过保护发布门槛。
账本使用同一数据库中的 `economy_metadata`、`economy_accounts`、`economy_budgets`、`economy_transactions`、`economy_allowances`、`economy_allowance_events` 和 `economy_starting_grants`。余额、预算消费、凭据及启动资金完成状态在同一 SQL 事务提交。已安装的货币 ID 或预算周期长度不能直接修改；需显式迁移，否则启动拒绝。
自然估值保存在 `land_value_metadata`、`land_value_policies` 和 `land_value_profiles`；区块 PDC 中的 `esmp:natural_land_value` 保留原始捕获结果，供持久化恢复使用。

## 持久化开发约定

Phase 0 已将应用服务改为领域接口/Repository 门面。跨模块事务传递不透明的 `UnitOfWork.Transaction`；只有持久化适配器能访问作用域中的 jOOQ context，事务关闭或跨线程使用会拒绝。ArchUnit 检查 Core/玩法/Paper 依赖、跨领域持久化访问及 jOOQ 类型泄漏。

新数据库通过 Liquibase 创建；旧数据库先由隔离的 legacy 迁移器到达已知版本，验证实际列、类型亲和性、主外键、索引与 CHECK 约束后，才建立 Liquibase baseline。`finishMigration` 完成前不开放玩法。旧迁移器只作兼容，不再接收新变更。

`SQLiteConfig`/`SQLiteDataSource` 配置外键、WAL 与超时；jOOQ 拥有数据库事务。Liquibase 在独立的启动迁移边界运行，并显式使用插件 classloader，避免 Paper 线程找不到迁移服务。SQLite 无法由结构化 changeType 表达的旧 CHECK/复合约束保留为 changelog 内注释说明的少量厂商例外，并由实际 SQLite 对照/拒绝测试约束，不能照此扩展普通业务 SQL。

升级前备份世界与数据库，完整 stop/start。若 schema 与桥接版本不匹配，服务器停止而不是无条件 changelogSync。不要删除校验文件、放宽校验或手动把损坏库标记为已迁移；保留副本和错误供检查。

## 下一步与协作

Phase 8 核心服务端门禁已通过，参见[发布与迁移指南](RELEASE.md)。下一步只冻结 MVP+1 Dynamic Settlement Cycle 的边界释放、Parcel/Holder 权利、财产处置与历史规则，再按批准的设计实施；不提前加入其他后续玩法。

已确定一条边界：自动回收不会使已分配 Parcel 的 Holder 同意权过期，也不能通过取消父 Claim 间接解除保护；长期离线纠纷由管理员明确、留痕处理。这可能长期阻止部分外围退缩。其他回收规则仍待定，尚未实现自动回收。

注册建筑也可进入候选回收，但必须整栋为 RUINED，所占 Claims 能作为一个整体安全退出，且不触犯 Holder 权利；同组涉及的其他注册建筑也必须满足这些条件。不按区块切开建筑，不自动拆除实体，保留历史。具体释放流程仍待定，当前运行时尚不会自动释放。

地块内箱子等容器先经过通知和搬迁宽限期，再随土地改变保护状态；非空不单独阻止回收。容器和内容原地保留，不自动搬运、删除或生成掉落物。真正释放后按未认领土地／新聚落权限访问，未搬走的物品可能被别人取走；玩家背包和末影箱个人物品不受影响。通知方式、宽限期长度及跨边界存储等细节仍待定，不能绕过 Holder 同意权。

没有 Holder 的 Parcel 可以随符合条件的土地整体结束权限划分并保留历史，但整个 Parcel 必须都在同一个安全释放组内，且提交时仍无 Holder。不切开 Parcel，也不在通知时提前取消保护；期间新增 Holder 后，不能再按“无 Holder”直接释放，须遵守已确定的同意规则。

也已选定：如果全部剩余土地满足条件，允许释放最后一个 Claim 或完整剩余释放组，结束聚落的土地所有权并保留历史，不强制留下一块保护地。整座结束也不能绕过 Holder、建筑或 Parcel 规则；成员及未完成修复任务如何收尾仍须另定。普通 MVP 的最后区块限制和当前运行时保持不变。

聚落结束后，剩余金库余额留在历史金库，由最后一任 Owner 手动领取，不自动转入钱包，也不送给后来认领土地的人。有余额本身不要求保留土地；账号和流水保留，领取只转移已有货币，不增发或销毁。聚落结束后的查看／领取入口尚未实现，修复材料不按这条金库规则处理。

完成已验证的里程碑后提交；不提交 `.run/`、`.tooling/`、数据库、日志或密钥，未获请求不推送远端。

[冻结架构](ESMP%20MVP%20ARCHITECTURE%202026-09-12/ARCHITECTURE.md) · [冻结需求](ESMP%20MVP%20ARCHITECTURE%202026-09-12/MVP_REQUIREMENTS_FREEZE.md) · [执行路线图](ESMP%20MVP%20ARCHITECTURE%202026-09-12/IMPLEMENTATION_ROADMAP.md) · [代理规则](AGENTS.md) · [决策](.agent/DECISIONS.md) · [当前阶段问题](.agent/OPEN_QUESTIONS.md) · [交接](.agent/HANDOFF.md) · [历史验收](.agent/TEST_CHECKLIST.md)

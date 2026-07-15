# 第44条主线：AutoPilot 分层补货与同轮续跑效率优化任务计划书

**Goal:** 将 AutoPilot 在 Ready 为空时的常见补货路径从“所有候选统一进入最长20分钟高推理 Planner、补货提交后退出、再次启动才实施”缩短为“权威 ReadySpec 确定性生成、严格校验、同一 APPLY 实例续跑”，同时补齐 Planner 可合法拒绝全部候选、阶段心跳、超时和恢复证据，并治理主线负责人 Skill 的过宽触发、动态事实重复和 AutoPilot 行为契约未绑定指纹/context pack 问题；验收方向是已经携带完整权威 ReadySpec 的存量问题不再调用 Ready Planner，补货提交后无需第二次人工启动即可进入该 Ready 的实施，负责人流程与易变控制面事实分层清晰，所有 stop/pause、知识图谱候选证据游标、Ready、Git 双基线、fencing、策略指纹、金丝雀和迭代计数门禁保持有效。

**Architecture:** 采用“权威 ReadySpec 快速路径 + 有界 Planner 慢路径 + 同轮续跑交接 + 治理契约分层”的最小改造，复用现有知识图谱筛选、Ready 严格解析、run.lock/fencing、RuntimeContext、Run Coordinator、StageResult、唯一 transition writer、本地提交能力及现有 AutoPilot 插件 references；以 `candidateEvidenceHead` 绑定候选发现证据、以 `executionBaseCommit` 绑定补货提交后的实施基线，StageResult v2 明确区分 RUN 与 ISSUE 作用域，所有持久化迁移继续由唯一 transition writer 写入现有 state/checkpoint；主线 Skill 只保留稳定负责人流程，插件内策略 manifest 统一引用 AutoPilot 行为契约，配置/Schema/批准记录提供动态事实，策略版本与哈希同时进入指纹、context pack 和验收证据；不引入第二套任务来源、外部队列、数据库、并行调度器或新的重复策略文档，不在本主线自动刷新产品情报或修改 Blocked，不降低图谱健康与来源核验要求，不把补货计入实施型迭代数，也不以效率为由绕过控制面金丝雀、验证、Reviewer 或两阶段收口。

**Depends On:** 第41条主线的知识图谱优先补货与 Ready 契约、第42条及第42-1条的恢复和执行模式、第43条主线的 Run Coordinator、StageResult 与唯一 transition writer。第43条金丝雀只作为实施前控制面健康证据；第44条修改控制面指纹后，无论旧指纹是否通过，都必须由用户明确启动一次新指纹单任务金丝雀，旧结果不得用于第44条放量。

**计划状态:** Implemented / Canary Pending；代码、契约、治理分层与自动化验收已完成，未提交、未 push、未启动真实 AutoPilot；新控制面指纹的单 Issue 金丝雀仍须用户另行明确授权。

## 1. 背景与根因

当前 Ready 为空后的补货链路包含：checkpoint、知识图谱状态与游标核验、候选筛选、Ready Planner、Schema 导入、严格 Ready 校验、仅 Ready 文件提交，然后结束当前 Runner；下一次 Runner 才会选中新 Ready 并开始实施。

安全门禁本身大多必要，主要冗长点来自执行路由与交接方式：

1. 所有候选无差别进入高推理 Planner，即使存量问题已经具备唯一键、优先级、来源、摘要和验收标准。
2. Planner 超时为1200秒，等待期间缺少可被监控识别的阶段心跳和语义进度。
3. Planner 被要求拒绝不合格候选，但导入契约要求至少返回1条 Ready，无法表达“全部拒绝且正常结束”。
4. 补货成功并提交后，Coordinator 立即退出；再次启动会重复初始化、锁、图谱和调度判断。
5. Focus 解阻与产品情报刷新在规则中有顺序要求，但它们涉及独立事实核验、正式载体写入和外部证据边界；本主线只把这些分支固化为结构化安全终态，不在补货效率改造中顺带实施内容刷新。

因此本主线不删除必要检查，而是把检查按候选成熟度分层，并消除补货到实施之间不必要的进程边界。

## 2. 决策结论

### 2.1 携带权威 ReadySpec 的存量问题走确定性快速路径

仅当知识图谱候选同时满足以下条件时，允许不调用 Ready Planner：

- 图谱健康且 Git 游标覆盖进入补货时的当前 HEAD。
- 候选已通过既有 blocking、状态、分类、聚合父项、去重和优先级过滤。
- 存在唯一 `issueKey`、非空 `sourceRefs`、问题摘要和可执行验收标准。
- 候选自身或 `sourceRefs` 指向的当前正式载体包含唯一权威 `readySpec`；`readySpec` 必须完整提供 `readyIssueId`、`taskNature`、`goal`、`nonGoals`、`allowedPaths`、`forbiddenPaths`、`acceptanceCriteria`、`validationCommands`、`migration`、`dependencies`、`riskLevel`、`runtimeRequirement`、`reviewerRequirement` 和 `archiveReport`。
- `readyIssueId` 符合现有严格格式且未与 Ready/Blocked/Done 重复；所有路径、命令和归档报告值均来自权威 `readySpec`，不得从标题、摘要或通用目录约定猜测。
- `sourceRefs` 可在当前分支解析到允许读取的当前事实载体；明确文件或符号未命中时必须按工具召回不足补充 `rg` 核验，不能推断代码不存在。
- 生成后的 Ready 通过现有严格 parser/linter、范围矛盾检查、验证入口存在性检查和队列上限检查。

快速路径字段映射固定如下：

| Ready 内容 | 唯一来源 |
|---|---|
| 标题、问题摘要、`[stock:<issueKey>]`、`sourceRefs` | 图谱候选原值 |
| Issue ID、任务性质、目标、非目标、范围、验收、验证、依赖、风险、运行态、Reviewer、归档报告 | 权威 `readySpec` 原值 |
| 状态 | 固定为 `Ready` |
| 候选证据提交 | 来源锚点固定附加 `candidateEvidenceHead=<40位Git SHA>` |

快速路径只按该映射生成排名第一的一条 Ready，不让生成器自由扩展问题范围、业务目标或验收标准。缺少任一权威字段、字段冲突或来源无法读回时，必须降级到慢路径或合法拒绝；不得以默认目录、通用命令或模型推断补齐。

### 2.2 Planner 只处理需要判断的候选

以下情况才允许进入有界 Planner：

- Ad-hoc `ReadyToSplit` 或已通过决策门的 Product Candidate 需要从产品目标拆成最小实施闭环或补齐权威 ReadySpec。
- 存量问题来源存在冲突、验收标准需要最小等价替换或跨模块影响需要判断。

Planner 不再是常规补货必经阶段。配置默认超时由1200秒收敛到600秒；每30秒写入一次不改变业务阶段的运行心跳，并在产生候选核验、拒绝或 Ready 草案等事实时写入语义进度。超时只结束本次 Planner，不得伪造 Ready 或直接升级为业务代码失败。最初300秒预算在真实高推理候选核验中连续两次达到边界，故按实测证据提高为600秒，未改为无界等待。

### 2.3 补货后在同一 APPLY 实例续跑

补货写入并本地提交后，Coordinator 不退出，而是执行一次受控交接：

1. 读回补货提交和 Ready 文件，确认只修改允许的治理文件；Ready 的来源锚点和 run event 必须同时记录取候选时的 `candidateEvidenceHead`。
2. 将补货提交后的新 HEAD 记录为 `executionBaseCommit`，刷新运行上下文中允许变化的 Git 基线，不复用提交前的范围哈希。
3. 再次检查 run.lock/fencing、控制面指纹、stop.flag、pause.flag 和 enabled.flag。
4. 重新严格解析 Ready，并确认刚生成的 Issue 仍是可选任务。
5. 返回 StageResult v2：`scope=RUN`、`subjectId=runInstanceId`、`stage=REFILL`、`outcome=SUCCEEDED`、`nextStage=READY_RESELECT`；不得伪造 Issue ID，也不得在选单前创建 Issue checkpoint。
6. 由 `autopilot-transition.ps1` 中唯一 Run transition writer 校验 `REFILLING/REFILL_COMMITTED → PLANNING/READY_RESELECT`、fencing token、控制面指纹及 `transitionId + generation` 写后读回；底层 state 模块只负责序列化与原子存储。
7. 以同一 `runInstanceId + leaseEpoch + controlPlaneFingerprint` 进入正常 Issue 选择与生命周期；选中 Ready 后才切换为 `scope=ISSUE` 的 StageResult 和 Issue checkpoint。
8. 交接阶段不得再次从图谱拉取候选；图谱游标只需精确覆盖 `candidateEvidenceHead`。若实现确需重新查询候选，必须先刷新并确认游标覆盖当时 HEAD，否则 fail-close。
9. 补货本身仍不增加 `启动迭代-N` 完成计数，只有实施型 Ready 完成正式收口后才计数。

若补货提交后命中 stop/pause，必须保留已经提交的 Ready 并安全停止，不得启动实施；下次启动直接消费该 Ready，不得重复补货。

### 2.4 Planner 允许合法返回零条 Ready

Ready Plan 契约升级为显式、逐候选可核对的 v2 决策：

```text
schemaVersion = 2
decision = CREATED | REJECTED | BLOCKED
candidateDecisions = 1..5 × { candidateRef, outcome, reason, readyIssueId? }
readyBlocks = 0..5
failureCategory / stopReason（仅 BLOCKED）
```

- 每个输入候选必须在 `candidateDecisions` 中精确出现一次；不得遗漏、重复或返回未知 `candidateRef`。
- `CREATED` 必须至少有1条 Ready；每个 `outcome=CREATED` 的候选必须用唯一 `readyIssueId` 对应且仅对应一个 Ready block。
- `REJECTED` 必须为0条 Ready，且全部候选均为 `outcome=REJECTED` 并有非空原因；它是正常业务决策，不是 Schema 或工具失败。
- `BLOCKED` 必须为0条 Ready，至少一个候选为 `outcome=BLOCKED`，并提供现有规范可接受的 `failureCategory` 与 `stopReason`。映射固定为：工具缺失=`tool_config/STOP_READY_PLANNER_TOOL_CONFIG`，环境不可用=`environment_prereq/STOP_READY_PLANNER_ENVIRONMENT`，候选或 Ready 契约失真=`ready_issue_config/STOP_READY_PLAN_INVALID`，需要人工确认=`quality_security/STOP_NEEDS_CONFIRMATION`；本主线不新增另一套失败分类词表。
- Schema 使用 `oneOf`/条件约束禁止全局 `decision`、逐候选 `outcome`、`readyBlocks` 数量和 `readyIssueId` 互相矛盾；Importer 还需做 Schema 无法表达的候选集合一致性检查。
- 零结果不得写入 Ready、不得创建空提交、不得计入迭代数。

## 3. 目标流程与时间预算

### 3.1 常见快速路径

```text
Ready为空
→ stop/pause/enabled checkpoint
→ 图谱健康与HEAD游标门禁
→ 选择排名第一且携带权威ReadySpec的存量问题
→ 确定性生成Ready
→ 严格校验并本地提交
→ 刷新Git基线并再次checkpoint
→ 同一Runner进入实施
```

验收目标：fixture 中不得启动 Planner 子进程；不含外部图谱刷新和实际实施耗时的补货交接，10次重复测试的中位数不高于10秒、最大值不高于30秒。真实本地环境以“常见补货在2分钟内进入实施阶段”为观察目标，不把机器绝对耗时直接写入任务评分扣分规则。

### 3.2 有界慢路径

```text
候选需要语义判断
→ Planner（最长600秒，30秒心跳）
→ CREATED / REJECTED / BLOCKED（逐候选完整决策）
→ CREATED时严格导入并同轮续跑
→ 其余结果按分类安全停止或正式承接
```

同一批候选只允许一次 Planner 调用；超时、进程失败或输出不符合 Schema 时不得在同一 run 内无界重启。

### 3.3 Focus 阻塞与无候选路径

- 当前 Focus 存在前置阻塞：返回结构化 `UNBLOCK_REQUIRED` 并安全停止，不在本主线自动修改 Blocked、创建解阻 Ready 或执行环境/事实核验。
- 没有存量或已通过决策门的 Ad-hoc/Product Candidate：返回明确的 `NO_CANDIDATES` 并安全停止，不从长期增强计划凑任务，不在同一 run 自动刷新产品情报。
- 产品情报刷新、Focus 解阻和正式 Blocked 写回继续遵守各自授权门与载体规则，作为独立任务处理；形成新的合格 Ready 后，由下一次用户明确启动消费。

这些低频分支只要求稳定终态、调用次数上限和无自旋证据，不纳入本主线的同轮续跑目标。

## 4. 范围与非目标

### 4.1 本主线范围

- 为存量问题增加确定性 Ready 快速生成器和适用性判定。
- 将 Ready Planner 改为按需调用，并缩短超时、增加心跳与语义进度。
- 升级 Ready Plan Schema，支持合法零结果和明确失败分类。
- 升级 StageResult 为 RUN/ISSUE 双作用域，并由唯一 transition writer 持久化 Run 与 Issue 的合法迁移。
- 引入 `candidateEvidenceHead` 与 `executionBaseCommit` 双基线，明确候选证据、治理提交和实施基线的绑定关系。
- 增加补货提交后的同一 Runner 续跑交接。
- 固化 Focus 阻塞和无候选分支的结构化安全终态，禁止自旋和隐式内容刷新。
- 增加快速路径、慢路径、零结果、stop/pause、恢复、Git 和迭代计数测试。
- 收窄主线负责人 Skill 的触发范围，移除评分版本、权重、阈值、超时和当前状态机字段等动态事实副本，并按分析/计划/实施/验收/上线裁决区分最小输出。
- 在现有 AutoPilot 插件 references 上建立唯一策略 manifest，把行为策略版本/哈希绑定到控制面指纹、context pack、StageResult/checkpoint 证据和制品完整性校验。
- 增加轻量/标准/高风险计划 profile 的结构校验入口，只校验可机器判断的格式、引用和动态事实边界，不替代负责人对方案合理性的判断。
- 更新控制面指纹、项目规则、Skill、项目地图和正式验收报告。

### 4.2 非目标

- 不改变知识图谱优先级和 fail-close 原则。
- 不放宽 Ready 必填字段、范围矛盾、验证入口、权限、安全、数据重置或 Git 门禁。
- 不取消 Ready 本地提交，不把未提交 Ready 带入 Issue worktree。
- 不并行补货和实施，不一次补满5条任务；默认仍只保证至少1条合格 Ready。
- 不在本主线自动刷新 Project Map、竞品证据或 Evolution Decision，不自动修改 Blocked，也不自动执行 Focus 解阻；项目地图仅在主线正式收口时回写本轮已经实现的控制面事实。
- 不以固定行数作为 Skill 或计划书验收标准，不为了“瘦身”删除仍未被其他强制加载载体可靠承接的最高级安全边界。
- 不新建与现有插件 `owner-boundary.md`、`role-contracts.md`、`rerun-policy.md`、`loop-budget-policy.md` 平行重复的规则副本；策略 manifest 只定义权威引用、读取顺序和裁决方法。
- 不在本轮大规模重写 `AGENTS.override.md` 或 `AGENTS.md`；只允许为唯一事实源引用、触发边界和策略绑定做最小调整，后续去重必须以加载、指纹、context pack 和金丝雀证据为前置。
- 不改变任务评分 v2 权重、20任务回顾和两阶段提交规则。
- 不自动 push、不发布生产、不连接生产数据库。
- 不以产品情报刷新为名访问禁止目录或把历史归档当成当前事实。

## 5. 实施任务

### Task 1：建立补货性能与行为基线

**Files:**

- Modify: `scripts/codex-autopilot/test-refill.ps1`
- Modify: `scripts/codex-autopilot/tests/test-runner-compatibility.ps1`
- Create: `scripts/codex-autopilot/tests/test-refill-continuation.ps1`

**Tasks:**

- 固化当前快速候选仍调用 Planner、补货后退出、零结果失败、stop/pause 和无候选分支的表征测试。
- 为 Planner 启动次数、阶段耗时、心跳、Ready 写入次数和 Runner 续跑次数增加可断言记录。
- 测试夹具使用确定性本地 Git/EOL 配置，不继承机器级 `core.autocrlf`。
- 性能测试只测本地 fixture 和 mock Planner，不依赖网络、真实模型响应或机器 CPU 百分比。

**验收:** 修改生产代码前测试能稳定复现现状；关闭已有关键门禁时对应测试稳定失败。

### Task 2：实现结构完整度判定与确定性 Ready 生成器

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/autopilot-ready.ps1`
- Modify: `scripts/codex-autopilot/test-refill.ps1`

**Tasks:**

- 新增纯函数判定候选是否满足快速路径条件，并返回结构化拒绝原因。
- 只按第2.1节映射表复制图谱候选与权威 `readySpec`，并把 `candidateEvidenceHead` 写入来源锚点；缺少任一字段时禁止猜测。
- Ready parser 必须把来源锚点中的 `candidateEvidenceHead=<40位Git SHA>` 解析为结构化属性，并验证它等于本次图谱快照覆盖的 HEAD；恢复时从已提交 Ready 读回该属性。
- 对 `sourceRefs`、文件路径、明确符号和验证入口做当前分支核验；召回不足时按规则补充只读检索。
- 生成内容必须经过现有严格 Ready parser/linter 和范围矛盾检查，不创建第二套宽松校验器。
- 快速路径只生成排名第一的一条 Ready，禁止自由扩写。

**验收:** 携带完整权威 ReadySpec 的存量 fixture 生成稳定且字节级可重复的 Ready，Planner 调用次数为0；任一必需字段、来源证据或候选证据提交缺失都会降级或拒绝，测试证明生成器未使用默认目录或通用命令补值。

### Task 3：升级 Ready Plan 契约并收敛 Planner

**Files:**

- Modify: `plugins/cgc-pms-autopilot/schemas/ready-plan.schema.json`
- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `scripts/codex-autopilot/test-refill.ps1`

**Tasks:**

- 增加 `schemaVersion=2`、逐候选 `candidateDecisions` 和 `CREATED / REJECTED / BLOCKED` 判别联合约束，禁止决策、候选集合、`readyIssueId` 与 `readyBlocks` 数量矛盾。
- Planner prompt 仅接收经过筛选的有界候选，并要求逐候选返回创建或拒绝证据。
- Importer 必须显式接收本次 Planner 输入的 `ExpectedCandidateRefs`，逐项核对 `candidateDecisions`，不得仅凭 Planner 输出自行认定候选集合。
- 默认超时改为600秒；增加30秒运行心跳和语义进度记录。
- Planner stderr、退出码、超时和 Schema 错误继续按原生命令契约分类，不把 warning 误判为失败。
- 同一候选同一 run 最多调用一次 Planner，失败后保留诊断证据并安全停止；按第2.4节固定映射到现有失败分类。

**验收:** 零 Ready 的 `REJECTED` 可正常结束且不写文件；遗漏、重复或伪造候选决策会被拒绝；`NEEDS_CONFIRMATION` 映射不扩展失败分类词表；超时在预算内退出；持续心跳不能冒充候选核验或 Ready 生成进度。

### Task 4：实现补货提交后的同轮续跑交接

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-run-coordinator.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-coordinator-support.ps1`
- Modify: `scripts/codex-autopilot/autopilot-runtime-context.ps1`
- Modify: `scripts/codex-autopilot/autopilot-stage-result.ps1`
- Modify: `scripts/codex-autopilot/autopilot-transition.ps1`
- Modify: `scripts/codex-autopilot/tests/test-refill-continuation.ps1`
- Modify: `scripts/codex-autopilot/tests/test-stage-result-contract.ps1`
- Modify: `scripts/codex-autopilot/tests/test-transition-writer.ps1`

**Tasks:**

- 将 `BACKLOG_SPLIT_APPLIED` 从终止动作改为受控 `REFILL_COMMITTED → READY_RESELECT` 交接。
- StageResult v2 增加 `scope` 与 `subjectId`：RUN 结果不得要求或伪造 Issue ID，ISSUE 结果保持现有 Issue 绑定并兼容读取 v1。
- 补货提交读回后保留 `candidateEvidenceHead`，刷新 `executionBaseCommit`、Ready 哈希和允许变化的运行上下文；两者不可覆盖或混用。
- 复用现有 loop state 的 `transitionId/generation` 记录 Run transition，所有 Run 状态合法边仍由 `autopilot-transition.ps1` 唯一校验和写入，底层 state 模块不得路由。
- 交接前后分别验证 fencing token、控制面指纹和 stop/pause/enabled。
- 重新解析 Ready，并通过正常选单入口进入 Issue 生命周期，禁止直接跳到实现函数。
- 保证补货不占用 iterationLimit；实施完成后只增加一次完成计数。
- 补货提交后若崩溃，下一 run 能从已提交 Ready 及其 `candidateEvidenceHead` 正常开始，不重复生成或重复提交。

**验收:** 单次 Runner 调用可完成“空 Ready → 补货提交 → Run transition 读回 → 选择新 Ready → 启动实施”；命中 stop/pause 时只保留 Ready 并停止；旧 token、旧 `candidateEvidenceHead` 伪装成实施基线、旧 `executionBaseCommit` 或非法 Run transition 均不能继续派发。

### Task 5：固化低频安全终态并防止循环

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-coordinator.ps1`
- Modify: `scripts/codex-autopilot/test-refill.ps1`

**Tasks:**

- Focus 存在阻塞时返回稳定 `UNBLOCK_REQUIRED`，不修改 Blocked、不创建 Ready、不执行解阻动作。
- 无候选时返回稳定 `NO_CANDIDATES`，不刷新产品情报、不回退长期计划、不自旋。
- 两个终态均写入可分类 Run result/state，且同一 run 调用次数为1；不得把安全停止误报为业务代码失败。
- 若未来独立任务解除阻塞或刷新产品情报，本主线只消费其已经形成且通过严格门禁的 Ready，不隐式接管对应写入职责。

**验收:** 各来源优先级不变；每个低频分支均有调用次数上限、明确终态和可复现测试；fixture 证明 Blocked、Project Map、竞品证据、Evolution Decision 和 Ad-hoc 文件均未被修改。

### Task 6：治理契约分层与主线 Skill 减负

**Files:**

- Modify: `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- Create: `plugins/cgc-pms-autopilot/references/control-plane-policy.md`
- Modify: `plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `scripts/codex-autopilot/autopilot-context.ps1`
- Modify: `scripts/codex-autopilot/autopilot-stage-result.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-verify.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/context-pack.schema.json`
- Modify: `plugins/cgc-pms-autopilot/schemas/issue-checkpoint.schema.json`
- Modify: `plugins/cgc-pms-autopilot/schemas/evidence.schema.json`
- Modify: `scripts/codex-autopilot/test-control-plane-fingerprint.ps1`
- Modify: `scripts/codex-autopilot/test-context-isolation.ps1`
- Modify: `scripts/codex-autopilot/test-tool-routing.ps1`
- Modify: `scripts/codex-autopilot/tests/test-stage-result-contract.ps1`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`
- Modify: `scripts/codex-autopilot/test-evidence-verification.ps1`
- Create: `scripts/codex-autopilot/test-mainline-owner-flow.ps1`

**Tasks:**

- 将主线 Skill 的触发条件收窄为：用户明确要求按主线、Backlog 或 AutoPilot 治理推进，或要求正式验收、上线裁决、跨模块收口；普通单文件/单模块交互任务不自动升级为主线流程。
- 主线 Skill 只保留授权、Git 前置、主线目标与边界、直接执行/派工判断、阶段重新评估、正式验收和零悬空收口；删除评分版本、具体权重、回顾阈值、Planner/Reviewer 超时、模型档位和状态机字段等动态值副本。
- 动态事实统一从 `codex-autopilot.config.json`、实际 Schema 和 `approvalSource` 读取并交叉核验；Skill 只描述读取与裁决方法，不复制当前有效数值。
- 在主线 Skill 中增加任务类型输出矩阵：分析输出结论/证据/待确认项，计划输出计划状态/范围/验收/实施前置，实施输出修改/验证/Git 状态，验收输出通过与阻塞裁决，上线裁决才要求是否可上线与回滚条件。
- 新建插件内 `control-plane-policy.md` 作为策略 manifest，只引用并规定现有 `owner-boundary.md`、`role-contracts.md`、`rerun-policy.md`、`loop-budget-policy.md`、配置和 Schema 的权威顺序；不得复制动态配置值。
- 配置声明行为策略路径和策略版本；策略 manifest 及其受控引用必须进入 `fingerprintPaths` 和插件制品校验，引用缺失、未纳入指纹或哈希不一致均按 `tool_config` fail-close。
- context pack 增加 `controlPlanePolicyVersion`、`controlPlanePolicyHash` 和 `controlPlanePolicyRefs`，并与 RuntimeContext/控制面指纹交叉核验；Executor 即使只读取 context pack，也能获得当前策略绑定，StageResult/checkpoint/evidence 记录同一策略哈希。
- 增加 `Light / Standard / HighRisk` 计划校验 profile：检查命名、Goal/Architecture、范围、非目标、验收、风险、回滚、计划状态、引用存在性和重复主线编号；编号检查只扫描 `docs/plans/` 当前层并允许显式 `Mx`/子主线命名，只在当前 Skill/活动策略中检查动态值副本，历史计划与正式报告中的历史数值不报错。
- 维护信息只保留契约版本、规则来源、动态事实来源、策略 manifest 和验证入口；不维护容易再次漂移的手工“最后复核日期”，不以80～120行等行数目标判定通过。

**验收:** 普通交互修复不触发主线 Skill，明确主线/Backlog/AutoPilot/正式裁决场景仍能触发；调整评分版本、权重、阈值或超时无需修改主线 Skill；任一策略行为变更都会改变策略哈希和控制面指纹，并出现在 context pack 与阶段证据中；策略缺失或未绑定时安全停止；三类计划 profile 的正反 fixture 均稳定通过。

### Task 7：全量回归、规则回写与正式收口

**Files:**

- Modify: `scripts/codex-autopilot/test-control-plane.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `AGENTS.override.md`
- Modify: `AGENTS.md`
- Modify: `docs/backlog/current-focus.md`
- Modify: `docs/product-intelligence/project-map.md`
- Create: `docs/quality/mainline-44-autopilot-tiered-refill-acceptance-YYYY-MM-DD.md`

**Tasks:**

- 控制面指纹覆盖快速生成器、Planner 契约、StageResult v2、Run transition、同轮续跑、策略 manifest/context pack 绑定和相关测试文件。
- 固化“权威 ReadySpec 完整候选走确定性快速路径、语义候选才调用有界 Planner”的长期规则。
- 对 `AGENTS.override.md` 与 `AGENTS.md` 只做最小权威引用和去重，不删除尚未被强制加载策略承接的安全边界。
- 运行补货、控制面、连续 Runner、状态机、恢复、context pack、策略指纹、主线 Skill、closeout 和评分相关回归。
- 统计新增后续项、关闭后续项和后续项净变化，所有发现项完成修复、正式承接或有依据关闭。
- 仅回写本轮已实现的控制面能力到项目地图，不在收口步骤刷新竞品或 Evolution Decision。
- 用户明确启动一次新指纹 `启动迭代-1` 金丝雀；读回补货提交、`candidateEvidenceHead`、`executionBaseCommit`、Run transition、策略版本/哈希、context pack、implementationCommit、closeoutCommit、ledger、state、知识图谱最终游标和金丝雀登记。

**验收:** 正式报告能支持第44条通过/不通过和 N>1 放量裁决；没有会话级悬空风险。

## 6. 阶段顺序与门禁

### M0：行为冻结

- 完成 Task 1。
- 阶段类型：验收/测试治理。
- 门禁：现状可稳定复现，性能证据不依赖真实模型或网络。

### M1：快速路径与 Planner 契约

- 完成 Task 2～3。
- 阶段类型：控制面实现。
- 门禁：只有权威 ReadySpec 完整的快速候选不调用 Planner；慢路径逐候选决策完整、可合法零结果并有界退出。

### M2：同轮续跑与低频分支

- 完成 Task 4～5。
- 阶段类型：跨模块状态机实现。
- 门禁：fencing、双 Git 基线、RUN/ISSUE StageResult、唯一 transition writer、stop/pause、恢复和计数测试全部通过，不存在第二套阶段写入口。

### M3：治理契约分层

- 完成 Task 6。
- 阶段类型：控制面治理实现。
- 门禁：Skill 触发与动态事实边界、策略 manifest、指纹、context pack 和计划 profile 校验全部通过；策略缺失时 fail-close。

### M4：正式验收与金丝雀

- 完成 Task 7。
- 阶段类型：验收/审计。
- 门禁：全量回归、静态边界、性能证据、正式报告和真实单任务金丝雀全部通过。

## 7. 验收矩阵

| 场景 | 预期结果 |
|---|---|
| 已有合格 Ready | 不补货，直接走正常选单 |
| 携带完整权威 ReadySpec 的存量问题 | Planner 调用0次，确定性生成1条 Ready |
| 存量候选缺少 ReadySpec 任一字段、验收或来源 | 不伪造字段，降级慢路径或 REJECTED |
| Ad-hoc/Product Candidate | 进入最长600秒 Planner 慢路径 |
| Planner 拒绝全部候选 | `REJECTED + 0 readyBlocks`，不写 Ready、不提交 |
| Planner 遗漏/重复/伪造候选决策 | Schema/Importer 拒绝，不写 Ready、不提交 |
| Planner 超时 | 600秒内终止，分类为工具/运行问题，不判业务代码失败 |
| Planner 等待 | 每30秒有运行心跳，只有事实变化算语义进度 |
| 补货提交成功 | 同一 Runner 刷新双基线、读回 Run transition 并进入选单；选单前不创建 Issue checkpoint |
| 补货提交推进 HEAD | 图谱证据仍绑定 `candidateEvidenceHead`，实施绑定 `executionBaseCommit`，二者不混用 |
| Run 交接 | `REFILL_COMMITTED → READY_RESELECT` 经唯一 transition writer 写后读回 |
| 提交后出现 pause/stop | 保留 Ready，停止实施，不重复补货 |
| 补货后进程崩溃 | 下次启动消费已提交 Ready，不重复生成 |
| 图谱游标陈旧 | 最多刷新一次；仍陈旧则 fail-close |
| 工作区存在无关修改 | 禁止补货提交，不混入用户 diff |
| 旧 fencing token | 禁止写状态、提交或派发 |
| iterationLimit=1 | 补货不计数，随后完成1个实施型 Ready 后退出 |
| Focus 前置阻塞 | 返回 `UNBLOCK_REQUIRED`，不修改 Blocked 或执行解阻 |
| 无任何候选 | 返回 `NO_CANDIDATES`，不刷新产品情报并稳定停止 |
| 普通单文件/单模块交互任务 | 不自动触发主线负责人 Skill，仍遵守仓库通用授权与 Git 规则 |
| 明确主线、Backlog、AutoPilot、正式验收或上线裁决 | 触发对应负责人流程并使用任务类型输出矩阵 |
| 配置中的版本、权重、阈值或超时变化 | 主线 Skill 无需同步修改，运行时读取并核验配置与 `approvalSource` |
| 策略 manifest 或受控引用变化 | 策略哈希和控制面指纹变化，context pack 绑定新版本，要求新金丝雀 |
| 策略文件缺失、未入指纹或 context pack 哈希不一致 | `tool_config` fail-close，不派发 Executor |
| 历史计划记录旧评分值 | 计划校验器不报动态值重复；只检查当前 Skill 与活动策略 |

## 8. 验证命令

实施时至少执行以下 PowerShell 7 验证；若真实入口变化，先按当前文件核实后使用最小等价命令并记录替换依据：

```powershell
pwsh -NoProfile -File scripts/codex-autopilot/test-refill.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-refill-continuation.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-control-plane.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-continuous-runner.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-state-machine.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-stage-result-contract.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-transition-writer.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-phase-recovery.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-closeout.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-task-scoring.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-control-plane-fingerprint.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-context-isolation.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-mainline-owner-flow.ps1
pwsh -NoProfile -File plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1
git diff --check
```

真实金丝雀必须由用户在控制面变更提交后明确发出：

```text
启动迭代-1
```

金丝雀通过前不得启动 N>1 或无界连续迭代。

## 9. 风险与控制

### 9.1 确定性模板生成错误范围

- 风险：权威 ReadySpec 字段完整但实际问题跨模块，原始载体仍可能低估影响范围。
- 控制：快速路径只接受来源、范围和验收均可解析且与当前分支核验一致的 ReadySpec；跨模块、冲突或不确定项强制降级 Planner，不允许猜测。

### 9.2 同轮续跑复用陈旧 Git 基线

- 风险：补货提交改变 HEAD 后混用候选证据提交、实施 baseCommit、范围哈希或图谱游标，造成恢复或派发不一致。
- 控制：`candidateEvidenceHead` 只证明候选发现时的图谱一致性，`executionBaseCommit` 只作为补货后的实施基线；两者写入 Ready/run evidence 并分别读回，任何不一致安全停止。

### 9.3 心跳被误当作进度

- 风险：Planner 只输出心跳即可绕过 stall/超时。
- 控制：运行心跳只表示进程存活；语义进度必须绑定候选核验、决策或 Ready 草案事实，硬超时不因心跳延长。

### 9.4 快速路径改变治理语义

- 风险：为了速度绕过用户价值、证据、去重或验收判断。
- 控制：仅对权威 ReadySpec 完整且全部门禁可确定核验的存量问题启用；其余继续慢路径，严格 Ready parser/linter 仍是最终写入门禁。

### 9.5 控制面指纹变化

- 风险：本主线修改调度、补货、状态交接和恢复行为，旧金丝雀不能覆盖新指纹。
- 控制：完成自动化验证后只允许用户明确启动一次 `启动迭代-1`；新指纹金丝雀未登记成功前禁止 N>1 和无界模式。

### 9.6 RUN 与 ISSUE 状态边界混淆

- 风险：为了同轮续跑伪造 Issue ID、提前创建 Issue checkpoint，或在 Coordinator/state 模块中形成第二套 Run 迁移逻辑。
- 控制：StageResult v2 用 `scope/subjectId` 明确作用域；Run transition 与 Issue transition 均由同一 `autopilot-transition.ps1` 校验和写入，选中 Ready 前禁止创建 Issue checkpoint。

### 9.7 策略拆分后形成新漂移或未被 Executor 加载

- 风险：新建策略文档后继续保留旧副本，或只把策略加入指纹却未绑定 context pack，造成“指纹变化但执行行为未加载”的半闭环。
- 控制：只建立插件内唯一策略 manifest，动态事实继续由配置/Schema/批准记录提供；manifest 及受控引用同时进入制品校验、指纹和 context pack，StageResult/checkpoint/evidence 读回同一策略哈希后才允许派发。

## 10. 回滚方案

- 快速路径通过单一配置开关可关闭，关闭后仍保留有界 Planner，不回退到20分钟默认超时。
- 同轮续跑若发现一致性问题，可回退为“补货提交后安全退出”，但必须保留新版零结果契约、心跳和失败分类。
- StageResult v2 必须兼容读取现有 v1；Run transition 复用现有 loop state 的 `transitionId/generation`，回滚同轮续跑时仍保留 RUN 作用域解析能力，不把已有 Run transition 降级解释成 Issue checkpoint。
- 回滚不得删除已经提交的 Ready；下次启动按正常 Ready 选单消费。
- 不回滚已产生的 implementationCommit、closeoutCommit 或正式 ledger；发现状态不一致时按 durable checkpoint 恢复或进入 quarantine。
- 所有回滚均需更新控制面指纹并重新执行单任务金丝雀。
- 治理分层若需回滚，只回退 Skill 触发、策略 manifest 或 context pack 绑定的对应提交；不得以回滚为由恢复动态值多处硬编码。策略绑定不一致时保持 fail-close，回滚指纹仍需重新执行单任务金丝雀。

## 11. 完成定义

只有同时满足以下条件，第44条主线才可判定通过：

- 携带完整权威 ReadySpec 的常见存量补货不启动 Planner，fixture 性能目标达成；缺失字段不会被猜测补齐。
- Planner 慢路径有600秒硬上限、30秒运行心跳和独立语义进度。
- Planner 可以合法返回0条 Ready，不产生假失败或空提交。
- 补货提交后同一 Runner 能按双 Git 基线和 RUN 级合法迁移进入实施。
- 主线 Skill 只保留稳定负责人流程，普通交互任务不误触发，动态配置变化无需同步修改 Skill。
- AutoPilot 行为策略具有唯一 manifest，策略版本/哈希同时绑定制品校验、控制面指纹、context pack 和阶段证据。
- `Light / Standard / HighRisk` 计划 profile 校验通过，历史计划中的历史动态值不被误报。
- stop/pause、图谱、Ready、Git、fencing、恢复、迭代计数和两阶段收口门禁无回退。
- 自动化回归与新指纹真实 `启动迭代-1` 金丝雀通过。
- 本轮发现项全部归入“本轮修复并复验、正式承接、证据不足关闭”之一。
- 正式报告记录 `新增后续项`、`关闭后续项`、`后续项净变化`，无悬空问题。

预期最终裁决格式：

```text
结论=通过 / 不通过
阻塞=阻塞 / 非阻塞
是否可放量=N>1可启动 / 仅允许单任务金丝雀 / 不可启动
依据=自动化回归 + 性能证据 + 真实金丝雀读回
剩余风险=已修复 / 已正式承接 / 已关闭
```

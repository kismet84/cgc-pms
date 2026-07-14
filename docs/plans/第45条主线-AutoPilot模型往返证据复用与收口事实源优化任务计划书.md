# 第45条主线：AutoPilot 模型往返、证据复用与收口事实源优化任务计划书

**Goal:** 在不削弱 AutoPilot 授权门、Ready 契约、Git/worktree 隔离、stop/pause、fencing、验证、Reviewer、恢复、两阶段提交和金丝雀门禁的前提下，减少重复模型往返与上下文重建，扩大可证明有效的验证证据复用，将最终状态、提交、证据、审查和后续项收敛到可投影的逻辑单一事实源，并仅在基线证明存在可消除的模型往返时启用低风险 Owner 快速通道候选。验收方向是先形成真实耗时与调用基线，再以同一 Issue 的模型调用数、context 构造数、高成本验证执行数和报告事实冲突数证明改进，同时保持全部现有安全与恢复回归通过；快速通道若不能证明相对标准路径的净收益，则以有证据关闭候选作为正确结果。

**Architecture:** 采用“Phase 0a 只读盘点 → Phase 0b 可观测实现与基线 → 不可变基础上下文 + 阶段增量 → Evidence v2 指纹与有界复用 → 提交前 PreCloseout Facts 投影 + 提交后 Closeout Record v2 登记 → Owner Fast Path 证据门”的渐进改造，复用第43条的 RuntimeContext、StageResult、Issue checkpoint、唯一 transition writer 和第44条的策略指纹、双 Git 基线及同轮续跑；`state.json` 继续只表示运行摘要，checkpoint 继续表示活动 Issue 阶段事实，验证证据继续由 `autopilot-verify.ps1` 生成，`closeouts.ndjson` 扩展为最终收口登记源，正式 Markdown 只投影不包含自身提交哈希的提交前结构化事实并保留必要人工裁决区。不引入数据库、外部缓存、第二套状态机、第二个路由器、总括 `gatekeeper.ps1` 或全局 Light/Full 模式，不跨 Issue 共享测试通过结论，不用历史成功率放宽任何硬门禁。

## 实施状态（2026-07-14）

- M0b、M1、M2、M3 与自动化 M4 验收已完成，正式证据见 `docs/quality/mainline-45-autopilot-efficiency-evidence-closeout-acceptance-2026-07-14.md`。
- Task 4 证据门结论为 `NO_MEASURABLE_BENEFIT`：低风险标准路径已经是 1 次 Executor、0 次 Reviewer，未新增 Owner 快速通道路由、配置、Skill 或测试。
- 最终自动化矩阵 24/24 通过，换行 warning 为 0；当前正式 v2 closeout 样本为 0/20，聚合按契约返回 `insufficient_sample`。
- 新控制面指纹的真实单 Issue 金丝雀尚未执行；本轮遵守普通交互边界未启动 AutoPilot。用户另行明确发出 `启动迭代-1` 并成功登记前，N>1/无界放量继续阻塞。

**Depends On:** 第43条控制面模块化基线；第44条自动化验收完成且新控制面指纹的 `启动迭代-1` 金丝雀成功登记。M0a 可在此前进行只读基线盘点；M0b～M4 在第44条金丝雀通过前不得修改控制面，除非用户明确调整前置并重新批准合并后的金丝雀策略。

**计划状态:** Implementation Complete / New Fingerprint Canary Pending；实现与提交前复验已完成，本计划随控制面基线提交纳入版本管理；真实单 Issue 金丝雀尚未执行，未 push。

## 1. 背景与现状证据

第45条不是从零建设。当前控制面已经具备以下能力：

- `autopilot-context.ps1` 已绑定 `baseCommit`、`executionBaseCommit`、`candidateEvidenceHead`、`readyContentHash`、`diffHash` 和控制面策略哈希，并能拒绝陈旧上下文。
- `autopilot-verify.ps1` 已为每条验证命令生成结构化 JSON，记录 Issue、base/commit、diffHash、命令、耗时、退出码、分类、摘要和原始日志路径。
- Issue checkpoint 已保存 `evidencePaths`、`verificationDiffHash`、`reviewDiffHash`、implementation/closeout commit、阶段派发次数、恢复次数和阶段耗时。
- 恢复到 `VALIDATED` 及之后阶段时，Issue lifecycle 已能复用 checkpoint 中的验证证据，并再次检查 Issue、baseCommit、通过状态和 diffHash。
- `autopilot-route.ps1` 已按风险、跨模块、运行态和 docs/tests 范围决定执行角色、推理档位、是否需要 Reviewer 及串行要求。
- `autopilot-metrics.ps1` 已能从事件计算 queue、active、verify、review、closeout 阶段耗时；任务评分已读取阶段派发、恢复、重试和 wall clock 指标。
- `state.json`、Issue checkpoint、transition writer、result/evidence、正式质量报告和 `closeouts.ndjson` 已形成分层事实，但 `closeouts.ndjson` 当前只登记 `key + registeredAt`，不足以独立生成收口投影。

当前可确认的真实缺口：

1. context pack 每次 implement/repair 重新构造；没有稳定 `contextPackId`、基础包/增量包关系或 pack 内容哈希。
2. `ChangedPaths` 虽已作为 context 构造参数并限制数量，但没有写入 context 对象和 Schema。
3. Evidence 只绑定 Issue/base/diff/原始命令，缺少命令哈希、环境指纹、证据标识、覆盖的验收标准和显式复用结论。
4. 恢复复用已经存在，但没有按证据类别区分便宜检查、高成本构建、集成测试和浏览器验收。
5. 关键终态事实分布在 checkpoint、result、正式报告、Git 提交和 closeout ledger；Markdown 仍可独立描述同一事实。
6. 路由已有风险分层，但没有显式 `OWNER_FAST_PATH` 契约和升级原因；是否真的存在过多模型调用仍缺实际基线。
7. 当前指标没有区分 executor、reviewer、planner 等真实模型调用，也没有记录 context 构造次数、验证新执行/复用次数和跨任务 p50/p95。

## 2. 决策结论

### 2.1 先测量，后启用优化

- 不接受未经测量的“提速30%～50%”承诺。
- M0 必须记录至少一次完整 fixture 窗口；真实任务数据不足时只报告样本量，不伪造 p50/p95。
- 后续每个优化必须能关联到一个基线指标，不能只用文件行数或主观感受验收。

### 2.2 复用现有模块，不新建平行入口

- Ready、范围、状态边、fencing、上下文新鲜度、证据新鲜度和收口完整性继续由现有专业模块负责。
- 不新增总括 `gatekeeper.ps1`；如需统一结果，只新增结构化聚合函数，底层仍调用现有门禁。
- 不新增 `validate-evidence.ps1`；Evidence v2 构造、校验和复用判断继续收敛在 `autopilot-verify.ps1`。
- 不新增第二个 route 模块；快速通道扩展现有 `Get-AutopilotRoute`。

### 2.3 证据复用只在当前 Issue 的可证明边界内生效

- 首期只允许同一 Issue、同一 Ready、同一 worktree/checkpoint 的阶段恢复复用。
- 不建设跨 Issue、跨分支或全局测试结果缓存。
- 便宜且确定的检查默认重跑；高成本验证满足全部身份条件后才可复用。
- 时间窗口只是辅助门禁，不能替代代码、命令和环境指纹。

### 2.4 单一事实源是逻辑单一，不是单个大文件

- `state.json` 继续是可覆盖的运行摘要，不承担永久历史。
- checkpoint 继续负责活动 Issue 恢复，关闭后允许退休。
- transition/event journal 继续记录状态迁移。
- Evidence manifest 负责验证事实。
- Closeout Record 负责最终状态、提交、证据、Reviewer、报告和后续项摘要。
- Markdown 是投影视图，不得重新定义结构化字段。

### 2.5 快速通道不跳过控制面

- 快速通道减少模型/派工层级，不取消 worktree、checkpoint、验证、范围检查、final diff、`git diff --check` 或正式收口。
- 高风险、跨模块、数据库、权限、安全、租户、金额、审批、数据一致性和运行态任务不得进入首期快速通道。
- 实际 diff 升级风险时必须退出快速通道并进入标准 Reviewer 路径。
- M0b 必须先用同一低风险 fixture 测量现有 `STANDARD_EXECUTOR`；只有确认候选至少减少一次真实模型进程调用，才允许实现并启用 `OWNER_FAST_PATH` candidate。
- 若标准路径已经是一次 Executor 调用且无 Reviewer，快速通道候选判定为 `NO_MEASURABLE_BENEFIT` 并关闭，不新增 route 分支、配置开关或长期维护面。

## 3. 范围与非目标

### 3.1 本主线范围

- 增加模型调用、context 构造、验证执行/复用和投影一致性的可观测指标。
- 将 context pack 升级为一次生成的不可变基础包与按阶段生成的增量包。
- 升级 Evidence v2，增加命令、环境、验收覆盖和复用决定的可验证身份。
- 在不改变现有 checkpoint/transition 职责的前提下扩展 Closeout Record v2。
- 将正式质量报告中的机器事实区改为从 Closeout Facts 确定性渲染。
- 对 Owner 快速通道执行证据门裁决；仅在基线证明至少可减少一次真实模型调用时，才在现有 route 上增加候选态快速通道、升级触发器和关闭开关。
- 补齐兼容、恢复、幂等、故障注入、报告一致性和性能回归测试。
- 更新控制面指纹、插件策略引用、项目地图和正式验收报告。

### 3.2 非目标

- 不修改业务功能、生产配置或数据库。
- 不改变 Ready 来源优先级、补货策略、任务评分权重或20任务回顾阈值。
- 不取消第44条的 `candidateEvidenceHead`、`executionBaseCommit` 或同轮续跑语义。
- 不引入 SQLite、Redis、外部队列、远程缓存或第三方工作流框架。
- 不让模型输出替代真实命令证据；Executor 自述“测试通过”不进入复用池。
- 不将同类任务历史成功率作为硬门禁豁免；首期只记录，不参与自动放宽。
- 不为追求速度跳过独立 Reviewer 的适用风险审查。
- 不在长期规则中新增固定模型名称、固定机器耗时或未经批准的动态参数副本。
- 不自动 push、不发布生产、不连接生产数据库。

## 4. 目标契约

### 4.1 Context Base Pack v3

每个活动 Issue 只生成一次基础包，建议最小字段：

```text
schemaVersion=3
packKind=BASE
contextPackId
contentHash
issueId
readyContentHash
candidateEvidenceHead
baseCommit
controlPlanePolicyVersion/hash/refs
goal/nonGoals/acceptanceCriteria
allowedPaths/forbiddenPaths
requiredCommands
archiveReport
relevantSymbols
createdAt
```

约束：

- `contentHash` 对排除 `createdAt/contextPackId/contentHash` 后的规范化 JSON 计算 SHA-256。
- `contextPackId` 固定为 `issueId + readyContentHash + baseCommit + contentHash` 的 SHA-256。
- 基础包写入后不可覆盖；同一 checkpoint 发现同 ID 不同内容时进入 `integrity_conflict`。

### 4.2 Context Delta v1

每个 implement/repair/review 阶段只生成增量：

```text
schemaVersion=1
packKind=DELTA
contextDeltaId
contentHash
baseContextPackId
phase
executionBaseCommit
diffHash
changedPaths
previousPhaseSummary
acceptedDecisions
openRisks
longRunningCommands
generatedAt
```

约束：

- `contentHash` 对排除 `generatedAt/contextDeltaId/contentHash` 后的规范化 JSON 计算 SHA-256。
- `contextDeltaId` 固定为 `baseContextPackId + phase + executionBaseCommit + diffHash + contentHash` 的 SHA-256；同 ID 不同内容进入 `integrity_conflict`。
- `diffHash` 表示该阶段派发前、相对 `executionBaseCommit` 的当前差异；阶段执行后产生的新 diff 由验证证据和下一阶段 delta 重新绑定，不覆写原 delta。
- `changedPaths` 必须真正写入对象，最多20项。
- `previousPhaseSummary` 继续限制为5 KB。
- Executor 启动时校验 base/delta 绑定后在内存中物化上下文；不得复制出第二份长期权威包。
- Reviewer 请求必须引用并校验同一 base 与 review delta，但 review delta 不得携带 Implementer 自由推理或原始会话；只允许结构化 Ready 目标、最终 diff、验证证据、已接受决策和开放风险。
- v2 单包入口保留只读兼容，新的 Issue 只写 v3 base + v1 delta。

### 4.3 Evidence v2

在现有字段上增加：

```text
evidenceId
evidenceClass
readyContentHash
baseContextPackId
contextDeltaId
contextContentHash
candidateEvidenceHead
executionBaseCommit
commandHash
environmentFingerprint
environmentDescriptor
acceptanceCriteriaRefs
executionMode=EXECUTED|REUSED
sourceEvidenceId|null
reuseDecision
maxAgeSeconds|null
controlPlanePolicyHash
```

其中：

- `commandHash` 仅对命令行换行规范化并 Trim 后计算，不重排引号、参数或管道。
- `environmentFingerprint` 按验证类别采集最小相关环境，不对无关命令启动完整工具链探测。
- `reuseDecision` 固定返回 `REUSABLE` 或明确拒绝原因码，不依赖自由文本路由。
- `EXECUTED` 证据生成唯一 `evidenceId`，`sourceEvidenceId=null`；`REUSED` 引用记录生成新的唯一 `evidenceId`，并以 `sourceEvidenceId` 指向原始 `EXECUTED` 证据。
- 复用判断必须同时核对 Issue、Ready、base context、当前阶段 delta、candidate evidence head、execution base、diff、命令、最小环境、策略和验收引用；任一身份缺失或变化均拒绝复用。
- 被引用的原始证据保持不可变，不得覆写原 `evidenceId`、执行时间、环境或结果。

证据类别和默认策略：

| 类别 | 示例 | 默认策略 |
|---|---|---|
| `STATIC_CHEAP` | `git diff --check`、Schema 解析、路径检查 | 每阶段重跑，不缓存 |
| `UNIT_BUILD` | Maven 单测、前端类型检查/构建 | 同 Issue、同指纹可复用 |
| `INTEGRATION` | 本地服务、数据库集成测试 | 同指纹且健康/数据前置一致才可复用 |
| `BROWSER` | dev-login、页面验收 | 必须绑定前后端运行实例；默认重新执行 |

### 4.4 Closeout Record v2

`closeouts.ndjson` 的每条正式记录至少包含：

```text
schemaVersion=2
key
issueId
readyContentHash
outcome
implementationCommit
closeoutCommit
reportPath
reportHash
resultHash
preCloseoutFactsHash
evidenceManifestHash
reviewRequired
reviewDecision
verifiedDiffHash
followupsAdded
followupsClosed
followupsNetChange
metricsSummary
metricsHash
controlPlaneFingerprint
graphCursorRequired
graphGitCursor
registeredAt
```

约束：

- Closeout key 继续幂等；同 key 同 payload 返回已登记，同 key 不同 payload 进入 `integrity_conflict`。
- 幂等 payload hash 排除 `registeredAt`；同 key、同规范化 payload 的重试返回原记录及原 `registeredAt`，不得因重试时间变化制造冲突。
- 无 `schemaVersion` 且只有 `key + registeredAt` 的历史记录按 v1 只读处理，不原地升级、不追加同 key v2；恢复命中时返回 `LEGACY_REGISTERED`，不得伪称具备 v2 事实，也不进入 v2 最近20项指标样本。
- `metricsSummary` 保存每 Issue 的 Executor/Reviewer 调用、关联但不扇出计数的 RUN Planner 引用、context 构造、验证执行/复用、阶段耗时和投影计数；最近20个有效收口只从去重后的 v2 Closeout Record 聚合，不依赖可清理的 run 日志。
- 最近20项仅选择 `schemaVersion=2`、硬门禁通过、正式登记完成且 closeout key 唯一的实施型记录，按 `registeredAt` 倒序取20条；p50/p95 使用 nearest-rank（升序后索引 `ceil(p*n)-1`），空值不进入样本并显式报告实际样本量。
- `graphCursorRequired=false` 时 `graphGitCursor` 可为空；要求金丝雀或正式图谱游标门禁时必须为已读回且等于合并后 HEAD 的 Git cursor。
- `resultHash` 只计算登记前已冻结的 final result snapshot，排除 ledger 登记结果、`REGISTERED` 状态和 `resultHash` 自身；ledger 写入后不得再修改该 snapshot。
- 不把一次性 PID、临时日志路径或会话文本写入正式记录。

收口写入顺序固定为：

1. 完成验证/Reviewer 与 implementation commit，生成不含 `closeoutCommit/reportHash/resultHash/REGISTERED` 的 `PreCloseoutFacts`。
2. 从 `PreCloseoutFacts` 确定性渲染报告自动事实区，完成 Ready/Done 写回和 closeout commit。
3. 读回 closeout commit、报告哈希、`CLOSEOUT_COMMITTED` checkpoint；完成本地合并后，按门禁读回知识图谱 Git cursor。
4. 写入并冻结包含 closeout commit 的 final result snapshot，计算 `resultHash`；此后不得为了 ledger 或评分再改写该 snapshot。
5. 组装 Closeout Record v2 并幂等写入 ledger；同 key 不同 payload 进入 quarantine。
6. ledger 写后读回成功后，才经唯一 transition writer 迁移到 `REGISTERED`，刷新 state/heartbeat 并读回；失败恢复从首个未完成步骤继续。

`PreCloseoutFacts` 与 Closeout Record 是同一逻辑事实链的前后两个原子产物，不是两套状态机；前者只服务于无自引用的提交内投影，后者才是包含 closeout commit 的最终登记源。

### 4.5 Markdown 投影边界

正式质量报告分为：

1. 人工裁决区：目标、风险解释、上线建议和需要确认项。
2. 自动事实区：Issue、implementation commit、验证、Reviewer、范围、后续项统计、指标摘要和指纹。

自动事实区使用固定标记生成；同一字段不得在模板其他位置手工维护。重复渲染必须字节稳定。报告不得写入包含自身内容的 `closeoutCommit`、`reportHash` 或 ledger 登记状态；这些提交后事实只由 Closeout Record v2 承担。

### 4.6 Route v2（条件产物）

只有 M0b 证明同一低风险 fixture 的标准路径存在至少一次可消除的真实模型调用时，才在现有 route 返回值上增加：

```text
route=OWNER_FAST_PATH|STANDARD_EXECUTOR|REVIEWED_EXECUTOR
riskReasons[]
upgradeTriggers[]
routeVersion
fastPathEligible
```

首期 `OWNER_FAST_PATH` 必须同时满足：

- 单模块、低风险、无迁移、无运行态前置。
- 不涉及权限、安全、租户、金额、审批、数据库或数据一致性。
- allowedPaths 有界，验证命令真实存在且可确定执行。
- 不要求独立 Reviewer。
- 历史成功率不参与硬门禁。

实际 diff 命中高风险或跨模块时，重新路由为 `REVIEWED_EXECUTOR`。

若证据门结果为 `NO_MEASURABLE_BENEFIT`，本节字段不实施，现有 route 保持不变，并在正式报告记录候选关闭依据。

## 5. 实施任务

### Task 0：建立 Phase 0 性能与调用基线

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-metrics.ps1`
- Modify: `scripts/codex-autopilot/autopilot-runtime-context.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-coordinator.ps1`
- Modify: `scripts/codex-autopilot/autopilot-exec-issue.ps1`
- Modify: `scripts/codex-autopilot/autopilot-review.ps1`
- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`
- Modify: `scripts/codex-autopilot/test-metrics.ps1`
- Create: `scripts/codex-autopilot/tests/test-efficiency-observability.ps1`

**Tasks:**

- [ ] 在三个模型进程成功 `Start()` 后分别记录 `executorInvocationCount`、`reviewerInvocationCount`、`plannerInvocationCount`；每次调用生成稳定 `invocationId`，启动前失败不得计数，重复事件按 `invocationId` 去重。
- [ ] invocation 明确 `scope=ISSUE|RUN`：Executor/Reviewer 绑定单一 Issue，Planner 绑定 RUN 与候选引用集合；不得把一次可生成多条 Ready 的 Planner 调用复制计入每个 Issue。
- [ ] 记录 `contextBaseBuildCount`、`contextDeltaBuildCount`、`validationExecutedCount`、`validationReusedCount` 和 `reportProjectionCount`。
- [ ] 保留现有 dispatch/recovery/wall clock 指标，并定义新旧字段映射。
- [ ] 工具未返回 token 时写 `null/not_available`，不得伪造为0。
- [ ] 实现单 Issue 指标与对显式样本集合计算 p50/p95 的纯函数；最近20个有效收口的生产聚合延后到 Task 3，由 Closeout Record v2 的 `metricsSummary` 提供持久样本。
- [ ] 用 fixture 固化未优化路径的基线，正式报告只保留摘要，不提交原始运行日志。

**验收:** 重复事件不重复计数；跨 run 恢复仍归属于同一 Issue；计数与实际 mock 调用次数一致；时钟回退或缺失时间不会生成伪造耗时。

### Task 1：实现基础上下文与阶段增量

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-context.ps1`
- Modify: `scripts/codex-autopilot/autopilot-exec-issue.ps1`
- Modify: `scripts/codex-autopilot/autopilot-review.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/context-pack.schema.json`
- Create: `plugins/cgc-pms-autopilot/schemas/context-delta.schema.json`
- Create: `plugins/cgc-pms-autopilot/schemas/review-request.schema.json`
- Modify: `plugins/cgc-pms-autopilot/schemas/issue-checkpoint.schema.json`
- Modify: `scripts/codex-autopilot/test-context-isolation.ps1`
- Create: `scripts/codex-autopilot/tests/test-context-delta.ps1`

**Tasks:**

- [ ] 为 context canonicalization、content hash、base ID 和 delta ID 建立纯函数及测试向量。
- [ ] 新 Issue 创建 checkpoint 时只生成一次 base pack；阶段调度只生成 delta。
- [ ] 将 `ChangedPaths` 正式加入 delta Schema 和 Executor 输入。
- [ ] checkpoint artifacts 保存 base/delta 路径及哈希；恢复时读回并核验，不重新生成同一 base。
- [ ] Executor 兼容 v2 `-ContextPath`，新路径使用 base + delta；冲突时 fail-close。
- [ ] Reviewer request v2 绑定 base/review delta 的路径与哈希，只暴露独立审查所需的结构化 Ready 目标、最终 diff、验证证据和开放风险，不携带 Implementer 自由推理或原始会话。
- [ ] 控制 context prompt 预算，证明 repair/review 不再重复注入完整 Ready 与策略正文；Reviewer 的独立性和只读边界保持不变。

**验收:** 同一 Issue 的 base 构造次数为1；不同阶段只增加 delta；篡改 base/delta、Ready、diff 或策略哈希均被拒绝；主工作区脏改动不进入 Issue context。

### Task 2：升级 Evidence v2 与有界复用

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-verify.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-stage-result.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/evidence.schema.json`
- Modify: `plugins/cgc-pms-autopilot/schemas/issue-checkpoint.schema.json`
- Modify: `scripts/codex-autopilot/test-evidence-verification.ps1`
- Create: `scripts/codex-autopilot/tests/test-evidence-reuse.ps1`

**Tasks:**

- [ ] 保留 Evidence v1 只读兼容，新执行统一写 v2。
- [ ] 增加 Ready/base context/delta/candidate evidence head/execution base 身份、证据类别、命令哈希、最小环境描述、环境指纹、验收标准引用和策略哈希。
- [ ] 实现 `Test-AutopilotEvidenceReusable`，返回稳定 reason code。
- [ ] `EXECUTED` 与 `REUSED` 各自生成唯一 `evidenceId`；复用记录只通过 `sourceEvidenceId` 引用不可变的原执行证据。
- [ ] `STATIC_CHEAP` 始终重跑；首期只允许 `UNIT_BUILD` 在同一 Issue/checkpoint 中自动复用。
- [ ] `INTEGRATION/BROWSER` 默认不自动复用，直到对应健康与测试数据指纹具备完整测试。
- [ ] 恢复路径逐证据判断；只复用有效项，缺失项单独重跑，不因一条失效重跑全部高成本命令。
- [ ] result/StageResult 明确区分新执行与复用证据数量、来源和拒绝原因。

**验收:** 同 diff、同命令、同环境的高成本证据可复用；任一身份变化使对应证据失效；旧 v1 不被错误提升为具备环境指纹；复用不会改变原证据文件。

### Task 3：扩展 Closeout Record 并生成报告投影

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-closeout.ps1`
- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- Modify: `plugins/cgc-pms-autopilot/templates/quality-closeout.md`
- Modify: `plugins/cgc-pms-autopilot/templates/iteration-report-entry.md`
- Create: `plugins/cgc-pms-autopilot/schemas/closeout-record.schema.json`
- Create: `scripts/codex-autopilot/autopilot-report-projection.ps1`
- Modify: `scripts/codex-autopilot/test-closeout.ps1`
- Modify: `scripts/codex-autopilot/tests/test-closeout-consistency.ps1`
- Create: `scripts/codex-autopilot/tests/test-report-projection.ps1`

**Tasks:**

- [ ] 在 implementation commit 后从 checkpoint/result/evidence/review 生成无自引用的 `PreCloseoutFacts`，明确排除 closeout commit、report hash、result hash 和 REGISTERED 状态。
- [ ] 用 `PreCloseoutFacts` 渲染正式报告自动事实区；人工裁决区保持明确边界，报告不得声称自身 closeout commit。
- [ ] closeout commit/报告/checkpoint/必要图谱游标读回后，先冻结 final result snapshot，再写入 Closeout Record v2；旧 `key + registeredAt` 记录继续只读兼容。
- [ ] 将每 Issue 的 `metricsSummary` 与哈希写入 v2 Record；最近20个有效收口按 closeout key 去重后聚合，样本不足输出样本量和 `insufficient_sample`。
- [ ] 同 key 同内容保持幂等；同 key 不同内容进入 quarantine，不静默覆盖。
- [ ] done/ready/current issue 写回继续复用现有 closeout 流程，但引用同一个 issueId、提交和报告路径。
- [ ] ledger 写后读回成功后才迁移 `REGISTERED`；增加 PreCloseout Facts、报告、ledger、final result snapshot、checkpoint、提交、state 和图谱游标之间的一致性与故障窗口恢复校验。

**验收:** 同一 PreCloseout Facts 重复渲染字节一致；提交前投影不包含自引用字段；final result snapshot 在 ledger 登记前冻结且登记后不再变化；同 key 不同 payload 被隔离；REGISTERED 只在 ledger 读回后出现；checkpoint 退休后 ledger 仍足以证明正式关闭。

### Task 4：裁决 Owner 快速通道候选（证据门）

只有 M0b 证明标准低风险路径存在至少一次可消除的真实模型调用时，以下文件才进入修改范围；否则 Task 4 以 `NO_MEASURABLE_BENEFIT` 关闭，不产生任何 Task 4 相关的 route/config/Skill 变更，也不创建快速通道测试文件。

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-route.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-coordinator-support.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- Modify: `scripts/codex-autopilot/test-ready-routing.ps1`
- Create: `scripts/codex-autopilot/tests/test-owner-fast-path.ps1`

**Tasks:**

- [ ] 先用同一低风险 fixture 对比标准路径，记录真实 invocationId、Reviewer 调用和完整 context 构造；没有至少一次模型调用净减少时关闭候选。
- [ ] 证据门通过后，Route v2 输出明确 route、风险原因、升级触发器和版本，不只返回模型/角色档位。
- [ ] 快速通道使用 candidate/disabled 开关，M0b 没有证明净收益前不得实现或启用。
- [ ] Owner 快速通道只表示单 Owner 承担低风险分析与实现，不允许在内部机械派发 A～F 子线程。
- [ ] 验证、范围复核、适用的 D/E 检查和 F 收口仍由现有控制面执行。
- [ ] 实际 diff 触发高风险、跨模块或 Reviewer 条件时自动升级，升级记录进入 route evidence。
- [ ] 连续失败、证据不足、范围扩大或环境前置升级时退出快速通道。
- [ ] 历史成功率只记录为观测字段，不改变首期路由结果。

**验收:** 若标准路径已经是一次实现模型调用且无 Reviewer，输出 `NO_MEASURABLE_BENEFIT` 并证明未新增 Task 4 route 分支和配置；若证据门通过，快速通道相对同 fixture 至少减少一次真实模型调用，同时高风险 fixture 不进入、实际 diff 升级后 Reviewer 必须执行、关闭开关可完整回退标准路径。

### Task 5：控制面、回归、金丝雀与正式收口

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-control-plane-fingerprint.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `plugins/cgc-pms-autopilot/references/control-plane-policy.md`
- Modify: `plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1`
- Modify: `docs/backlog/current-focus.md`
- Modify: `docs/product-intelligence/project-map.md`
- Create: `docs/quality/mainline-45-autopilot-efficiency-evidence-closeout-acceptance-YYYY-MM-DD.md`

**Tasks:**

- [ ] 指纹覆盖 context/evidence/closeout Schema、review request、策略、实现和关键测试；只有 Task 4 证据门通过并实际实施 Route v2 时才加入 route 相关文件。
- [ ] 策略 manifest 只描述权威来源与复用边界，不复制当前动态数值。
- [ ] 运行目标测试、控制面回归和全量 Runner 兼容测试。
- [ ] 对同一 fixture 比较优化前后模型调用、context 构造、高成本验证执行和报告冲突。
- [ ] 统计新增后续项、关闭后续项和后续项净变化，逐项修复、正式承接或关闭。
- [ ] 更新项目地图，只记录已经实现并复验的能力。
- [ ] 用户明确启动新指纹 `启动迭代-1`；读回 context、evidence、双提交、Closeout Record、冻结 final result、state 和知识图谱游标；Route v2 仅在实际实施时读回。

**验收:** 自动化回归和真实单 Issue 金丝雀均通过；新指纹未登记前禁止 N>1 或无界连续迭代。

## 6. 阶段顺序与门禁

### M0a：只读现状盘点

- 只读取现有事件、checkpoint、结果和 fixture，列出当前可测与不可测指标；不修改控制面，不声称完成 Task 0。
- 阶段类型：审计。
- 前置：可在第44条金丝雀前执行。
- 出口：形成观测缺口清单；现有证据无法测量的指标标注 `not_available`，不得据此生成伪基线。

### M0b：可观测实现与正式基线

- 完成 Task 0。
- 阶段类型：测试治理实现。
- 前置：第44条金丝雀通过，控制面基线干净。
- 出口：真实调用点计数与 fixture 基线可信，明确最大瓶颈；最近20项生产聚合接口待 M3 接入 Closeout Record v2。

### M1：Context 增量

- 完成 Task 1。
- 阶段类型：控制面实现。
- 前置：第44条金丝雀通过，控制面基线干净。
- 出口：base 只构造一次，delta 可恢复且兼容 v2。

### M2：Evidence 复用

- 完成 Task 2。
- 阶段类型：验证/恢复实现。
- 出口：高成本验证按证据身份复用，失效边界和 reason code 稳定。

### M3：收口事实与投影

- 完成 Task 3。
- 阶段类型：高风险收口一致性实现。
- 出口：Closeout Record v2、报告投影和幂等读回全部通过。

### M4：快速通道与放量裁决

- 完成 Task 4 的证据门裁决与 Task 5；Task 4 可合法以 `NO_MEASURABLE_BENEFIT` 关闭。
- 阶段类型：调度实现 + 正式验收。
- 出口：若证据门通过，则 candidate 开关下净收益成立且全部升级触发器通过；若未通过，则不新增快速通道维护面；两种路径都必须完成真实金丝雀。

## 7. 验收矩阵

| 场景 | 预期结果 |
|---|---|
| 同一 Issue 首次 implementation | base context 构造1次，delta 构造1次 |
| 同一 Issue repair | 复用原 base，只新增 repair delta |
| 同一 Issue review | Reviewer request 只引用已核验 base + review delta、最终 diff 和证据，不读取 Implementer 自由推理 |
| Ready、base/delta、candidate evidence head 或策略哈希变化 | 旧 context/evidence 拒绝，进入安全停止或 quarantine |
| 同 diff、同命令、同环境的 UNIT_BUILD 恢复 | 复用证据，不重跑命令 |
| 命令文本、diff、base 或环境任一变化 | 只使对应证据失效并重跑 |
| `git diff --check` | 始终重新执行，不走缓存 |
| v1 evidence | 可读取并验证旧字段，但不能冒充 v2 环境完整证据 |
| REUSED evidence | 生成新 `evidenceId`，`sourceEvidenceId` 指向不可变原证据 |
| checkpoint 已到 VALIDATED | 逐条读回并核验证据，不重复执行全部命令 |
| Closeout key 重复且内容相同 | 幂等返回已登记 |
| Closeout key 相同但 payload 不同 | `integrity_conflict`，不得覆盖 |
| PreCloseout Facts 重复生成 | 报告字节一致，且不包含 closeout commit/report hash 自引用 |
| final result 已冻结 | ledger 写入和 REGISTERED 迁移不得再改变其哈希 |
| 最近20个有效收口 | 只从去重后的 v2 Record `metricsSummary` 聚合，样本不足显式报告 |
| 标准低风险路径已是一次模型调用 | `NO_MEASURABLE_BENEFIT`，不实施 OWNER_FAST_PATH |
| 快速通道证据门通过 | 相对同 fixture 至少减少一次真实模型调用后，才允许 candidate 开关启用 |
| 实际 diff 命中权限或跨模块 | 立即升级 REVIEWED_EXECUTOR |
| 快速通道中验证失败 | 先分类；不因快速路径直接判业务失败或跳过修复边界 |
| stop/pause 在任一阶段出现 | 只安全收口，不启动下一阶段或任务 |
| 模型/token 指标不可获得 | 标记 unavailable，不记录伪0 |
| 第45条控制面指纹变化 | 必须完成新的 `启动迭代-1` 后才允许放量 |

## 8. 验证命令

实施时至少执行以下 PowerShell 7 验证；入口变化时先核实真实文件，再记录最小等价替换：

```powershell
pwsh -NoProfile -File scripts/codex-autopilot/test-metrics.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-efficiency-observability.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-refill.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-executor-stall.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-context-isolation.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-context-delta.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-review-repair.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-evidence-verification.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-evidence-reuse.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-closeout.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-closeout-consistency.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-report-projection.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-ready-routing.ps1
if (Test-Path -LiteralPath scripts/codex-autopilot/tests/test-owner-fast-path.ps1) { pwsh -NoProfile -File scripts/codex-autopilot/tests/test-owner-fast-path.ps1 }
pwsh -NoProfile -File scripts/codex-autopilot/test-state-machine.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-stage-result-contract.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-transition-writer.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-phase-recovery.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-control-plane-fingerprint.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-control-plane.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-runner-compatibility.ps1
pwsh -NoProfile -File plugins/cgc-pms-autopilot/scripts/validate-loop-artifacts.ps1
git diff --check
```

真实金丝雀必须由用户在第45条控制面变更提交后明确发出：

```text
启动迭代-1
```

## 9. 效率验收指标

### 9.1 必须报告

- 每 Issue executor/reviewer 模型调用数，以及每 RUN planner 调用数与候选引用；Planner 不按产出 Issue 扇出重复计数。
- context base/delta 构造数和输入字节数。
- 验证命令新执行数、复用数、拒绝复用原因分布。
- active/verify/review/closeout/wall clock 的样本量、p50、p95。
- 报告投影次数和一致性失败数。
- Owner 快速通道证据门结论；仅在实际实施后报告进入、升级、失败和回退次数。

### 9.2 通过阈值

- 同一 Issue 的 base context 构造次数固定为1。
- repair/review 不再重复写入完整 base context。
- 可复用的 UNIT_BUILD 恢复场景，高成本命令重复执行次数从1降为0。
- 同一 PreCloseout Facts 重复生成的报告冲突数为0，报告不包含自身 closeout commit 或 report hash。
- v2 Closeout Record 的 metricsSummary 可稳定聚合最近20个有效收口，且不依赖 run 临时日志。
- OWNER_FAST_PATH 只有在相对标准路径至少减少一次真实模型调用时才可实施；否则必须以 `NO_MEASURABLE_BENEFIT` 关闭且不新增维护面。
- 所有提速指标必须同时满足现有质量、安全、恢复测试零回退。

绝对耗时只作为趋势指标，不以固定分钟数直接判失败；环境波动必须单独分类。

## 10. 风险与控制

### 10.1 缓存误命中

- 风险：只看 diffHash，忽略命令、依赖或运行环境变化。
- 控制：Evidence v2 同时绑定 Issue、Ready、base context、delta、candidate evidence head、execution base、diff、命令、环境、策略和验收引用；首期不跨 Issue 复用。REUSED 记录使用新 ID 并只引用不可变原证据。

### 10.2 Context 哈希自引用或不稳定

- 风险：时间字段、字段顺序或自身 hash 导致同内容不同 ID。
- 控制：明确 canonicalization，排除 volatile/self 字段，建立固定测试向量和跨进程重复测试。

### 10.3 Ledger 变成第二套状态机

- 风险：Closeout Record 开始驱动活动阶段迁移。
- 控制：ledger 只在正式关闭后登记；活动阶段仍只由 transition writer + checkpoint 决定。

### 10.4 报告投影覆盖人工裁决

- 风险：重生成报告时删除人工风险说明，或把报告自身 closeout commit/report hash 写入投影形成自引用。
- 控制：自动事实区使用固定标记并限制写入范围；人工区不由 renderer 改写；提交前投影只读取 PreCloseout Facts，自引用字段只进入提交后的 Closeout Record。

### 10.5 快速通道隐藏风险

- 风险：初始路径看似低风险，实际 diff 扩大到权限或跨模块。
- 控制：实施后重新路由；命中升级条件必须执行 Reviewer，不允许用初始路由覆盖实际事实。

### 10.6 观测本身增加明显开销

- 风险：同步写大量事件抵消提速收益。
- 控制：只记录结构化计数和阶段边界；原始日志不进入正式报告；观测开销纳入基线测试。

## 11. 回滚方案

- Context v3 失败时关闭增量写入，继续读取并生成兼容 v2 单包；不得删除已生成 checkpoint 证据。
- Evidence v2 失败时停止自动复用并全部重跑，保留 v1 读取兼容；不得把未知证据判为通过。
- Closeout Record v2 失败时停止新任务并保留 worktree/checkpoint；不得只写旧 key 后宣布关闭。
- 报告投影失败时不进入 closeout commit；保留 PreCloseout Facts 和现场供恢复。
- OWNER_FAST_PATH 未通过证据门时不存在回滚动作；通过后可用单一 candidate 开关关闭并恢复现有标准 Executor 路径。
- 任何回滚都必须更新控制面指纹并重新完成 `启动迭代-1`，不得沿用回滚前金丝雀。
- 已产生的 implementationCommit、closeoutCommit 和正式 ledger 不删除、不改写；冲突进入 quarantine。

## 12. 完成定义

只有同时满足以下条件，第45条主线才可判定通过：

- 第44条新指纹金丝雀已完成，或用户明确批准合并后的替代金丝雀前置。
- M0a 给出只读观测缺口，M0b 在第44条金丝雀后给出可信基线，未把 unavailable 指标伪造为0。
- Context base/delta 契约、哈希、恢复和兼容测试通过。
- Evidence v2 能精确证明可复用与不可复用，且没有跨 Issue 缓存。
- PreCloseout Facts、Closeout Record v2、冻结 final result、报告投影、幂等、写入顺序和冲突隔离测试通过。
- 快速通道完成证据门裁决：无收益时不实施；有收益时默认受 candidate 开关控制且所有升级触发器通过。
- stop/pause、Ready、范围、fencing、Reviewer、恢复、双提交、评分和回顾门禁无回退。
- 自动化回归与第45条新指纹真实 `启动迭代-1` 金丝雀通过。
- 正式报告记录优化前后指标、验证证据和控制面读回。
- 本轮所有发现项均已修复并复验、正式承接或有依据关闭。
- 正式报告给出 `新增后续项`、`关闭后续项`、`后续项净变化`，不存在会话级悬空问题。

最终裁决格式：

```text
正式交付物=Context v3 + Evidence v2 + PreCloseout Facts/Closeout Record v2 + Route v2（仅证据门通过时）+ 验收报告
验收证据=结构化指标 + 自动化回归 + 单 Issue 金丝雀读回
结论=通过 / 不通过
阻塞=阻塞项或无
是否可放量=N>1可启动 / 仅允许单任务金丝雀 / 不可启动
剩余风险=已修复 / 已正式承接 / 已关闭
```

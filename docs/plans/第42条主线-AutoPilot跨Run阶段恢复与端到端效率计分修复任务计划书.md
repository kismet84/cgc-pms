# 第42条主线：AutoPilot 跨 Run 阶段恢复与端到端效率计分修复任务计划书

**Goal:** 修复 AutoPilot 在实施已经完成、验证已经通过或 Reviewer 仅因 `tool_config` 阻塞后仍重新进入 B/C 的根因，建立可校验、可恢复、不会丢失现有 diff 的跨 run 阶段检查点；同时把任务执行效率纳入下一评分版本的正式五维评分，使跨 run 重复派发、阶段回退、人工恢复和工具重试能够真实影响效率分，并进入20任务回顾。验收方向是同一 Issue 在实现证据仍有效时 `implementationDispatchCount` 精确为1，恢复只从 D/E/F 的第一个未完成阶段继续，任何 Reviewer 工具故障都不得触发业务重新实现。
**Architecture:** 复用现有 `autopilot-run-continuous.ps1`、`autopilot-recover.ps1`、worktree、验证 evidence、Reviewer request/result、两阶段 Git closeout、state 与 closeout ledger；新增原子写入的 Issue 阶段检查点和确定性恢复决策矩阵，不引入第二任务数据库、不把 `.codex-autopilot` 运行态升级为正式事实源。恢复前必须重新校验 Ready 内容哈希、基线提交、worktree/branch、diff 哈希、允许/禁止路径和证据绑定；证据有效时保留 worktree 并从 `VALIDATE`、`REVIEW` 或 `CLOSEOUT` 恢复，证据不一致时进入 `QUARANTINE`，不得静默删除后重跑。现行 `autopilot-task-score/v1` 与35/25/20/10/10权重及历史结果保持不变；v2 目标模型把现有10分 `cycleEfficiency` 明确升级为10分 `taskExecutionEfficiency`，先通过 disabled 影子回放证明口径，再按版本、五维权重和生效时间批准门激活。

**Depends On:** 第41条与第41-1条主线、`ISSUE-040-022` 异常恢复复盘、当前已修复的 durable stall progress 门禁。
**Tech Stack:** Windows PowerShell 5.1/PowerShell 7兼容语法、JSON/JSON Schema、Git worktree、现有 AutoPilot runner、Reviewer、任务评分与回顾周期状态。

**Implementation Status:** M0–M4 已于2026-07-13完成实现、故障注入、完整回归和独立 Reviewer 验收；正式报告见 `docs/quality/mainline-42-autopilot-cross-run-recovery-and-efficiency-acceptance-2026-07-13.md`。用户随后批准 `autopilot-task-score/v2`、35/25/20/10/10和自下一项新实施型 Ready 生效；真实 `启动迭代-1` 金丝雀仍需用户明确启动。
**计划状态:** Completed；跨 run 恢复、效率证据和 v2 正式激活均已完成，批量放量只剩控制面单任务金丝雀门。

## 1. 正式承接的问题

本计划是以下两个问题的唯一计划载体；后续若拆入 `current-issues.json`、Ready 或其他治理载体，必须保留相同稳定键并引用本计划，不得重复立项。

| 稳定键 | 优先级 | 当前状态 | 问题 | 解除条件 |
| --- | --- | --- | --- | --- |
| `AUTOPILOT-CROSS-RUN-PHASE-RECOVERY` | P0 | Planned / blocking | 已完成实现或验证的 Issue 在 runner 重启后可能走 `RESTART_ISSUE`，删除残留 worktree 并重新派发 B/C；Reviewer `tool_config` 失败没有与业务实现隔离 | 故障注入证明 implement 后、review 后、closeout 前三类中断均只从首个未完成阶段恢复，业务实现派发次数精确为1 |
| `AUTOPILOT-END-TO-END-EFFICIENCY-EVIDENCE` | P1 | Completed / v2 active | v1 的 `avoidableReworkCount` 主要来自单 run 的 `attempt`，未覆盖跨 run 重复派发、人工恢复和阶段重启，95分可能掩盖端到端低效 | 正式记录跨 run 指标；v2 将任务执行效率作为10分正式维度并可确定性回放，激活参数已获用户批准 |

在 P0 恢复能力验收前，`pause.flag` 应继续阻止无人值守多任务派发；允许执行 dry-run、测试夹具和用户明确启动的单任务金丝雀，不自动恢复剩余9项。

## 2. 当前基线与根因

### 2.1 已确认事实

- `ISSUE-040-022` 的业务实现、后端46项、前端31项、类型检查和差异检查在第一次有效实现后已经通过；后续没有真实质量或安全失败证据。
- 第一类失败是 post-closeout 后重新执行 Ready lint，因 Ready 已变为 Done 被误判为 `READY_CONTRACT_INVALID`；该点已在前序修复中关闭并保留回归。
- Reviewer 两次失败均为 Windows sandbox 辅助进程初始化失败，分类为 `tool_config`，不是业务代码失败。
- 当前 `Get-AutopilotRecoveryDecision` 会对未合并提交返回 `RESTART_ISSUE`；runner 收到该决策后会删除残留 worktree，再回到普通选单和 `Invoke-IssueExecutor`。
- 当前 state 虽记录 `phase`、`lastAction`、`worktree` 和验证状态，但没有一份可独立校验并直接驱动恢复的完整阶段检查点。
- 当前 v1 评分器把 `avoidableReworkCount` 默认映射为 executor `attempt`，跨 run 的第二次、第三次 implementation dispatch 不会自然进入周期效率扣分。
- stall 指纹已删除子进程 CPU 累计值并通过实际 CPU-only 回归；本计划只防止回退，不重新设计 stall 门槛。

### 2.2 根因分层

1. **恢复粒度错误：** 以整个 Issue/run 为恢复单位，没有以 `IMPLEMENTED → VALIDATED → REVIEWED → CLOSEOUT_PENDING` 阶段恢复。
2. **证据没有成为恢复输入：** diff、验证 evidence、Reviewer request/result 和 implementation commit 已存在，但 recovery 只看进程、worktree 是否脏及提交是否未合并。
3. **工具故障和业务实现耦合：** Reviewer 失败后缺少 `REVIEW_TOOL_BLOCKED` 的暂停/重试分支，重新启动 runner 会再次派发 implementation。
4. **缺少跨 run 幂等门：** 没有稳定键约束“同一 Issue、同一 Ready 内容、同一基线和同一 diff 只能有一个有效 implementation dispatch”。
5. **效率证据口径不完整：** 单 run attempt 无法表示跨 run dispatch、阶段回退、人工恢复与总时长。
6. **放量顺序不合理：** 控制面行为变更后直接执行 `启动迭代-10`，缺少绑定当前控制面指纹的 `-1` 金丝雀门。

## 3. 范围与非目标

### 3.1 本主线范围

- 为每个活动 Issue 建立原子阶段检查点，记录可恢复阶段、证据摘要、Git/worktree 边界和端到端指标。
- 把 recovery 从 `RESTART_ISSUE` 二元逻辑改为可解释的阶段决策矩阵。
- Reviewer `tool_config` 失败只允许重试 Reviewer 或进入暂停，不得重新调用 implementation executor。
- 为 implementation、validation、review、closeout 分别增加幂等 dispatch 计数和阶段状态。
- 将跨 run 指标写入正式验收报告的结构化摘要；原始日志和临时路径仍不进入正式交付物。
- 保持 v1 正式评分不变；v2 必须把任务执行效率作为正式计分维度，并提供确定性影子回放与批准门。
- 增加控制面指纹与金丝雀证明，未证明的新控制面不得直接进入 `启动迭代-N`（N>1）。
- 更新 AutoPilot 规范、Owner Skill、插件说明、项目地图和正式验收报告。

### 3.2 非目标

- 不重写整个 runner，不引入外部队列、数据库、分布式锁或跨系统 ACID 事务。
- 不让恢复逻辑跳过测试、独立 Reviewer、权限/租户/安全审查或两阶段 Git closeout。
- 不复用陈旧 diff、陈旧 Ready、错误基线或无法绑定到当前 worktree 的 evidence。
- 不自动把 Reviewer 工具失败降级为“评审通过”。
- 不修改 `autopilot-task-score/v1` 的既有评分、权重、历史记录或正式回顾计数。
- 不自动批准或激活 v2，不因本计划存在就改变评分版本。
- 不连接生产、不发布生产、不自动 push、不修改业务模块和数据库 migration。

## 4. 目标阶段模型

```text
READY_SELECTED
  → IMPLEMENTING
  → IMPLEMENTED
  → VALIDATING
  → VALIDATED
  → REVIEWING
      ├─ PASS → REVIEWED
      ├─ NEEDS_REPAIR → REPAIRING（只处理明确 finding）
      └─ TOOL_BLOCKED → REVIEW_TOOL_BLOCKED / PAUSED
  → CLOSEOUT_PENDING
  → IMPLEMENTATION_COMMITTED
  → SCORE_BOUND
  → CLOSEOUT_COMMITTED
  → MERGED_AND_REGISTERED
  → CHECKPOINT
```

恢复只允许前进或停在当前阶段；除 `NEEDS_REPAIR` 且 finding 明确要求业务修复外，不得从 `VALIDATED`、`REVIEWING`、`REVIEW_TOOL_BLOCKED`、`CLOSEOUT_PENDING` 回到 `IMPLEMENTING`。

### 4.1 Issue 阶段检查点

建议运行态路径：`.codex-autopilot/checkpoints/<issueId>.json`。它是可删除、可重建的本地运行态，不进入 Git，不替代正式报告、backlog 或 closeout ledger。

最小字段：

```json
{
  "schemaVersion": 1,
  "issueId": "ISSUE-...",
  "readyContentHash": "...",
  "baseCommit": "...",
  "worktree": "...",
  "branch": "...",
  "completedPhase": "VALIDATED",
  "nextPhase": "REVIEWING",
  "diffHash": "...",
  "implementationCommit": null,
  "validationEvidence": [],
  "reviewedDiffHash": null,
  "reviewDecision": null,
  "implementationDispatchCount": 1,
  "validationDispatchCount": 1,
  "reviewDispatchCount": 0,
  "closeoutDispatchCount": 0,
  "runResumeCount": 0,
  "phaseRestartCount": 0,
  "manualRecoveryCount": 0,
  "toolConfigBlockCount": 0,
  "environmentRetryCount": 0,
  "startedAt": "...",
  "lastDurableProgressAt": "...",
  "phaseDurationsSeconds": {}
}
```

写入规则：

- 每个阶段启动前与成功后各原子写入一次；写入后必须读回并通过 schema 校验。
- evidence 只保存命令摘要、状态、提交和 diff 哈希；不复制原始日志正文。
- `lastDurableProgressAt` 只由工作树内容、正式 evidence、review result 或 Git 状态变化刷新，不因 CPU、心跳或 MCP 辅助进程活动刷新。
- closeout 成功并登记后删除 checkpoint；删除前必须先读回 closeout ledger 和 state，禁止先删证据再写状态。

### 4.2 恢复决策矩阵

| 当前可验证事实 | 恢复决策 | 允许动作 | 禁止动作 |
| --- | --- | --- | --- |
| 无 checkpoint、无残留 worktree/提交 | `START_IMPLEMENTATION` | 正常进入 B/C | 伪造恢复证据 |
| worktree 存在、Ready/基线/diff 一致，implementation 完成但验证未齐 | `RESUME_VALIDATION` | 只跑缺失验证 | 重跑 implementation |
| 验证齐全且绑定当前 diff，Reviewer 未开始 | `RESUME_REVIEW` | 创建/复用 review request | 重跑 B/C、删除 worktree |
| Reviewer 因 `tool_config` 阻塞 | `RETRY_REVIEW` 一次；再次失败则 `PAUSE_REVIEW_TOOL_BLOCKED` | 只重试评审或等待人工独立评审 | 把工具故障改判业务失败、重跑 B/C |
| Reviewer pass，implementation commit 未产生 | `RESUME_IMPLEMENTATION_COMMIT` | 固化当前已评审 diff | 再修改实现；如需修改必须使旧 review 失效并重新评审 |
| implementation commit 存在，评分/报告未完成 | `RESUME_SCORE_AND_CLOSEOUT` | 复用同一 implementation commit 和评分幂等键 | 产生第二个 implementation commit |
| closeout commit 存在但未 merge/ledger | `RESUME_MERGE_AND_REGISTER` | 只做基线、合并和 ledger 门禁 | 重跑实现/验证 |
| Ready 内容、基线、diff、worktree 或 evidence 任一不一致 | `QUARANTINE_REVIEW_REQUIRED` | 保留现场、暂停并输出差异 | 静默删除 worktree、自动选下一任务 |

### 4.3 防重复派发硬门

- `(issueId, readyContentHash, baseCommit)` 组成 implementation dispatch 稳定键。
- 检查点已经记录 `implementationDispatchCount=1` 且 diff/evidence 有效时，任何新 run 调用 `Invoke-IssueExecutor -Phase implement` 都必须失败为 `DUPLICATE_IMPLEMENTATION_DISPATCH_BLOCKED`。
- 只有以下两种情况允许第二次业务实现派发：
  1. Reviewer/验证返回真实 `NEEDS_REPAIR`，并给出明确 finding、剩余范围和旧 diff 失效原因；
  2. checkpoint 证据被确定性判为不可用，主线程确认放弃旧现场并显式产生新的恢复代次。
- Reviewer sandbox、工作树清理、报告写入、评分、ledger、图谱和 state 故障都不构成重新派发 B/C 的理由。

## 5. 端到端效率证据与任务执行效率评分

### 5.1 先采集、后激活

第一阶段只新增指标，不改变 v1 正式得分：

- `implementationDispatchCount`
- `validationDispatchCount`
- `reviewDispatchCount`
- `repairDispatchCount`
- `closeoutDispatchCount`
- `runResumeCount`
- `phaseRestartCount`
- `manualRecoveryCount`
- `toolConfigBlockCount`
- `environmentRetryCount`
- `wallClockSeconds`
- `phaseDurationsSeconds`
- `resumedFromPhase`
- `duplicateDispatchBlockedCount`

正式报告只保存聚合结果和关键分类，不保存 run id、进程号、临时日志路径或截图名。

### 5.2 v2 正式目标口径

候选版本暂定 `autopilot-task-score/v2-candidate`。用户已经确认“任务执行效率必须纳入评分”；目标五维仍采用35/25/20/10/10，不新增第六维，也不改变硬门禁：

| 维度 | 目标权重 | v2 处理 |
| --- | ---: | --- |
| `deliveryCorrectness` | 35 | 沿用 v1 |
| `zeroDanglingIssues` | 25 | 沿用 v1 |
| `firstPassAcceptance` | 20 | 沿用 v1 |
| `taskExecutionEfficiency` | 10 | 替代 v1 的 `cycleEfficiency`，使用跨 run 阶段证据正式计分 |
| `stockIssueReduction` | 10 | 沿用 v1 |

`taskExecutionEfficiency` 的确定性计分规则为：

| 任务执行效率得分 | 确定性条件 |
| ---: | --- |
| 10/10 | `implementationDispatchCount=1`、validation/Reviewer/closeout 派发次数符合任务路由、`runResumeCount=0`、`phaseRestartCount=0`、`manualRecoveryCount=0`、`toolConfigBlockCount=0`、`duplicateDispatchBlockedCount=0`，且端到端证据完整 |
| 5/10 | implementation 仍只派发一次且未发生阶段回退/人工恢复，但只发生一次已分类的工具或环境重试，或一次重复派发被门禁成功阻断 |
| 0/10 | implementation 重复派发、发生阶段回退或人工恢复、工具/环境重试超过一次，或端到端证据不完整 |

`wallClockSeconds` 和各阶段耗时必须进入20任务回顾的趋势与异常值分析，但不按固定分钟数直接扣分，避免复杂任务天然吃亏。效率扣分只依据可确定归因的重复工作、恢复成本和执行链路浪费；效率低分不得降低 `deliveryCorrectness`，也不得把 `tool_config` 伪装成业务质量失败。

### 5.3 批准门

- 可以实施指标采集、schema、测试和 disabled 影子回放。
- 用户已批准 `scoringVersion=autopilot-task-score/v2`、权重35/25/20/10/10、`taskExecutionEfficiency=10`，自批准配置提交后的下一项新实施型 Ready Issue 生效。
- v2 active 每个新任务只写一个正式评分键和一个回顾计数，不再生成 v1 正式分或 candidate shadow 双计数。
- 批准前 shadow 保持历史回放证据，不迁移到正式 closeout ledger。
- 历史 v1 分数不回算、不覆盖；`ISSUE-040-022` 的95分保留原始事实，回顾中另注明跨 run 盲区。

## 6. 控制面金丝雀门

- 对 runner、recovery、closeout、Reviewer 路由、state schema、评分证据和 `codex-autopilot.config.json` 本身等有行为影响的稳定文件计算 `controlPlaneFingerprint`。
- 本地 state 记录最近一次成功单任务金丝雀的 `lastCanaryFingerprint` 与正式报告路径，不记录原始日志。
- 当两者不一致且用户请求 `启动迭代-N`（N>1）时，runner 在选任务前返回 `CONTROL_PLANE_CANARY_REQUIRED`，不得自动降级执行第一个任务，也不得把 N 改写为1后偷偷继续。
- 测试夹具全部通过后，仍需用户明确执行 `启动迭代-1` 完成真实单任务金丝雀；只有 closeout ledger、state 与知识图谱 Git cursor 全部读回且绑定合并后 HEAD，才登记该指纹成功，随后才允许相同指纹执行更大批次。
- dry-run、Ready 补货、health gate 和失败的金丝雀不更新 `lastCanaryFingerprint`。

## 7. 实施任务

### Task 1：以故障注入建立 RED 基线

**Files:**

- Create: `scripts/codex-autopilot/test-phase-recovery.ps1`
- Modify: `scripts/codex-autopilot/test-recovery.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `scripts/codex-autopilot/test-closeout.ps1`
- Modify: `scripts/codex-autopilot/test-task-scoring.ps1`

**Tasks:**

- [ ] 模拟 implementation 已完成且验证 evidence 绑定当前 diff 后杀死 runner；重启应在现实现上错误重派 B/C，从而形成 RED。
- [ ] 模拟 Reviewer request 已生成但 sandbox 返回 `tool_config`；重启不得再次启动 implementation executor。
- [ ] 模拟 Reviewer pass 后、implementation commit 后、closeout commit 后三个中断点。
- [ ] 模拟 Ready 内容变化、基线前移、diff 被修改、worktree 分支错误和 evidence 哈希不一致，期望进入 quarantine。
- [ ] 断言当前 recovery 删除残留 worktree 的行为在有效证据场景下失败。
- [ ] 增加跨 run dispatch/人工恢复指标缺失导致 v2 shadow 无法计算的 RED。

**RED 验收:** 新增测试稳定证明当前实现会重派 B/C、删除可恢复现场，且 v1 现有 evidence 无法表示跨 run 返工。

---

### Task 2：实现原子阶段检查点与 schema

**Files:**

- Create: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Create: `plugins/cgc-pms-autopilot/schemas/issue-checkpoint.schema.json`
- Create: `plugins/cgc-pms-autopilot/examples/issue-checkpoint.example.json`
- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/loop-state.schema.json`
- Modify: `plugins/cgc-pms-autopilot/examples/loop-state.example.json`
- Modify: `scripts/codex-autopilot/test-state-machine.ps1`

**Tasks:**

- [ ] 实现 checkpoint 的原子写入、读回、schema 校验和幂等更新。
- [ ] 统一计算 Ready 内容哈希、基线、diff 哈希、阶段 evidence 摘要和 dispatch 指标。
- [ ] state 只保存 checkpoint 指针、当前阶段和最后心跳，详细恢复证据放独立 checkpoint，避免无限膨胀 state。
- [ ] v3 state 读取保持兼容；新增字段必须有确定性默认值，不把历史 run 猜测为可恢复。
- [ ] checkpoint 不进入 Git；closeout 成功后按读回顺序安全删除。

**GREEN 验收:** 检查点写入中断不会产生半文件；同一阶段重复写入不增加 dispatch 计数；不受支持版本 fail-close。

---

### Task 3：实现阶段感知恢复与 worktree 保留

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-worktree.ps1`
- Modify: `scripts/codex-autopilot/autopilot-closeout.ps1`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`
- Modify: `scripts/codex-autopilot/test-recovery.ps1`

**Tasks:**

- [ ] 用第4.2节矩阵替换有效现场上的通用 `RESTART_ISSUE`。
- [ ] recovery 决策前完成 Ready、Git、worktree、scope、diff 和 evidence 六类核验。
- [ ] 有效现场只调用缺失阶段函数；不得再次调用 `Invoke-IssueExecutor -Phase implement`。
- [ ] worktree 只有在 closeout 已登记、用户显式放弃或确定性证明不可恢复后才能删除。
- [ ] quarantine 输出不包含原始日志，只给失败分类、冲突字段、恢复条件和安全保留路径摘要。
- [ ] closeout 复用同一 implementation commit、评分幂等键和 ledger key。

**GREEN 验收:** implement、validate、review、closeout 四个中断点逐一恢复；`implementationDispatchCount` 始终为1，最终 implementation/closeout commit 不重复。

---

### Task 4：隔离 Reviewer 工具故障与业务修复

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-review.ps1`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/review-result.schema.json`

**Tasks:**

- [ ] Reviewer 结果明确区分 `PASS`、`NEEDS_REPAIR`、`TOOL_BLOCKED`。
- [ ] `TOOL_BLOCKED` 只允许一次同 diff Reviewer 重试；重试仍失败则状态为 `PAUSED/REVIEW_TOOL_BLOCKED`。
- [ ] 只有 `NEEDS_REPAIR` 且 finding 具备文件、风险、所需证据和范围时才允许 repair executor。
- [ ] Reviewer 重试必须验证 request/diff 哈希不变；diff 变化后旧 Reviewer 结果自动失效。
- [ ] 人工独立 Reviewer 可通过结构化结果恢复 E/F，但必须绑定同一 diff 哈希和正式证据。

**GREEN 验收:** 连续两次 Reviewer sandbox 失败后 implementation dispatch 仍为1、worktree 保留、下一任务未派发；人工 Reviewer pass 后从 closeout 继续。

---

### Task 5：采集端到端指标并实现任务执行效率评分

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-task-score.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/task-score.schema.json`
- Modify: `scripts/codex-autopilot/test-task-scoring.ps1`
- Modify: `scripts/codex-autopilot/test-retrospective-cycle.ps1`
- Modify: `docs/standards/14-AutoPilot任务评分与自动改进回顾规范.md`

**Tasks:**

- [ ] 从 checkpoint 聚合第5.1节指标，禁止用模型主观判断补齐缺失值。
- [x] v1 历史按原批准口径保留；新增指标进入正式报告、周期回顾输入和 v2 `taskExecutionEfficiency` 计分证据。
- [x] 实现 disabled v2 shadow 评分，五维总权重固定校验为100，按同一输入重复计算结果完全一致。
- [x] v2 shadow 缺少端到端证据时任务执行效率为0，不得猜测为首次成功。
- [x] v2 激活后每个新任务只能产生一个正式评分版本和一个回顾计数，不允许 v1/v2 双计数。
- [x] 影子分不得写入 activeVersion、closeout ledger 正式评分键或 reviewCycleScoreKeys。
- [x] 增加用户批准门回归，未批准配置无法激活。

**GREEN 验收:** 使用 `ISSUE-040-022` 的结构化回放样本时，v1 历史95分保持不变；v2 shadow 能识别重复 implementation dispatch/人工恢复并给出任务执行效率0，且不增加正式回顾计数。批准门测试还需证明激活后该10分维度进入正式总分和20任务聚合。

---

### Task 6：增加控制面指纹与单任务金丝雀门

**Files:**

- Create: `scripts/codex-autopilot/autopilot-control-plane-fingerprint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Modify: `scripts/codex-autopilot/test-unbounded-state.ps1`

**Tasks:**

- [ ] 对影响调度/恢复/验收/收口的稳定文件集合计算控制面 SHA256。
- [ ] 指纹变化且无成功 `-1` 金丝雀证明时，N>1 在选任务前安全停止。
- [ ] 金丝雀只有在实施、验证、Reviewer、两阶段提交、ledger、state 和图谱游标全部通过后才登记。
- [ ] 金丝雀失败保持暂停，不自动继续剩余 N，不把 dry-run 当作成功。
- [ ] 配置允许显式关闭该门仅用于测试夹具，生产项目配置默认开启；不得由 runner 自行关闭。

**GREEN 验收:** 修改 runner 后直接请求 `启动迭代-10` 被稳定拒绝为 `CONTROL_PLANE_CANARY_REQUIRED`；完成同指纹 `启动迭代-1` 后才允许更大批次。

---

### Task 7：规范、Skill、验收和正式收口

**Files:**

- Modify: `AGENTS.override.md`
- Modify: `AGENTS.md`
- Modify: `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/skills/cgc-pms-autopilot-owner/SKILL.md`
- Modify: `plugins/cgc-pms-autopilot/references/rerun-policy.md`
- Modify: `plugins/cgc-pms-autopilot/references/owner-boundary.md`
- Modify: `docs/backlog/current-focus.md`
- Modify: `docs/product-intelligence/project-map.md`
- Modify: `docs/product-intelligence/evolution-decision.md`
- Create: `docs/quality/第42条主线-AutoPilot跨Run阶段恢复与端到端效率计分修复验收报告.md`

**Tasks:**

- [ ] 固化“工具故障只恢复当前阶段、有效 diff 不重跑 B/C、有效现场不先删 worktree”的规则。
- [ ] 记录模型/路由经验：长生命周期 implementation 不应承担恢复守候；短生命周期独立 Reviewer 适合权限/租户正式裁决；Reviewer 工具阻塞不应升格为业务重做。
- [ ] 更新项目地图和迭代决策，明确 v2 是候选还是已批准，不使用含糊的“已升级”。
- [ ] 正式报告统计新增后续项、关闭后续项和后续项净变化；P0/P1 稳定键必须关闭、转待确认或正式承接。
- [ ] 本地主线提交后刷新知识图谱并验证 Git cursor 追平；不 push。

**收口验收:** 当前分支干净、正式报告存在、图谱游标追平、P0 恢复问题关闭；v2 批准状态、active 配置与生效边界必须一致，不能为了“零悬空”伪装批准或激活。

## 8. 实施顺序与阶段门

### M0：RED 与现场保护

- 完成 Task 1。
- 在任何实现前确认 `pause.flag=true`，不启动新的业务 Ready。
- RED 必须复现“有效 implementation 被重派”的当前问题，不能只做源码字符串断言。

### M1：跨 run 恢复闭环

- 完成 Task 2–4。
- 阶段类型：运维治理/实现，跨模块但不涉及业务数据。
- 阶段门：全部故障注入通过，`implementationDispatchCount=1`，Reviewer tool failure 不触发 B/C。
- M1 未通过时不得进入评分改造或真实金丝雀。

### M2：效率证据与 v2 候选

- 完成 Task 5。
- 阶段类型：审计/评分治理，结果影响未来正式评分，证据强度提高。
- 阶段门：v1 正式结果不变，v2 仅 shadow，批准状态可读回。

### M3：控制面放量门

- 完成 Task 6 和测试夹具验收。
- 用户未明确启动前，不自动执行真实 `启动迭代-1`。
- 首个真实金丝雀成功后，才允许同控制面指纹的 N>1。

### M4：正式验收与决策

- 完成 Task 7。
- 独立 Reviewer 复核恢复矩阵、Reviewer 工具边界、v1/v2 隔离和 zero-dangling。
- 用户已确认 v2 的正式版本名、五维权重和生效时间；正式激活回归通过。

## 9. 验收矩阵

| 场景 | 必须结果 |
| --- | --- |
| implement 完成后 runner 被终止 | 重启只进入 validation；implementation dispatch=1 |
| validation 全过后 runner 被终止 | 重启只进入 Reviewer；不再跑 B/C |
| Reviewer sandbox 第一次失败 | 同 diff 只重试 Reviewer 一次 |
| Reviewer sandbox 第二次失败 | `PAUSED/REVIEW_TOOL_BLOCKED`，worktree/diff 保留，下一任务不派发 |
| 人工独立 Reviewer pass | 绑定同 diff 后进入 closeout，不重新实现 |
| Reviewer `NEEDS_REPAIR` | 只按 finding 做有界 repair；旧 review 失效，修后重新验证/评审 |
| implementation commit 后中断 | 复用同一 commit 评分并 closeout |
| closeout commit 后中断 | 只做 merge/ledger/state/图谱读回 |
| Ready 或基线变化 | quarantine，禁止静默复用或删除现场 |
| worktree 长路径清理 | 仅在正式收口或显式放弃后执行，目录与 branch 均移除 |
| CPU-only 辅助进程持续活动 | 不刷新 durable progress；既有 stall 测试继续通过 |
| v1 正式评分 | 历史评分继续按原版本保留，不被 v2 重算或覆盖 |
| v2 正式评分 | `taskExecutionEfficiency` 能识别跨 run 重派、阶段回退、人工恢复和工具重试，自批准配置提交后的下一项新实施型 Ready 起计入正式20任务周期 |
| v2 批准后新任务 | 任务执行效率10分进入正式总分和20任务聚合，同一任务不得同时记 v1/v2 |
| 控制面指纹变化后请求 N>1 | 金丝雀未通过前安全停止，不启动任务 |

## 10. 建议验证命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-phase-recovery.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-recovery.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-state-machine.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-closeout.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-task-scoring.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-retrospective-cycle.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-executor-stall.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File plugins/cgc-pms-autopilot/scripts/test-autopilot-loop-runner.ps1
git diff --check
```

所有 PowerShell 子进程命令必须检查 `$LASTEXITCODE`；首次失败先按 `tool_config`、`environment_prereq`、`quality_security` 分类并用最小等价验证复现，不能因外层 PowerShell 最终退出0而误报全绿。

## 11. 风险与回滚

### 风险1：错误复用陈旧 diff

- 控制：Ready 内容哈希、baseCommit、branch、diffHash、allowed/forbidden 和 evidence 必须同时一致。
- 失败：进入 quarantine，保留现场并停止，不自动重做。

### 风险2：恢复绕过必要验收

- 控制：恢复从“首个未完成阶段”继续，不能直接跳到 closeout；diff 变化会使旧验证和 Reviewer 失效。

### 风险3：checkpoint 与 state 不一致

- 控制：单文件原子写入、写后读回、显式阶段序号；冲突时 fail-close，不做双向猜测合并。

### 风险4：v2 未批准却影响正式评分

- 控制：candidate/active 分离、不同 schema key、shadow 不写 ledger 和正式周期计数；配置回归强制 approvalStatus。

### 风险5：金丝雀门阻塞正常任务

- 控制：指纹只覆盖控制面行为文件；同指纹成功一次即可复用。门禁失败只阻止新任务，不破坏已有 closeout。

### 最小回滚

1. 回退阶段恢复、checkpoint、指标和金丝雀相关提交。
2. 保留当前 durable stall progress 修复、Ready/Reviewer/closeout 已有回归和正式业务提交。
3. 恢复前一版 runner 后保持 `pause.flag`，不得直接恢复 N>1。
4. v2 未激活时只删除候选配置和 shadow 输出；v1 历史评分与20任务计数不受影响。
5. 无数据库、生产数据、schema migration 或远端 Git 回滚。

## 12. 完成定义

本主线只有同时满足以下条件才可判定通过：

- P0 跨 run 阶段恢复全部故障注入通过，同一有效 implementation 的派发次数精确为1。
- Reviewer `tool_config` 连续失败只导致 Reviewer 暂停，不导致 B/C 重跑或 worktree 删除。
- implementation、validation、review、closeout 四个中断点均可幂等恢复。
- durable stall、长路径 worktree、两阶段 closeout、Ready lint 和评分 v1 回归全部通过。
- 端到端指标进入正式报告、回顾输入和 v2 任务执行效率计分证据，不含临时路径、run id 或原始日志。
- v2 正式模型包含10分 `taskExecutionEfficiency`；已按35/25/20/10/10批准激活，activeVersion 为 v2，v1 历史不回算。
- 控制面指纹金丝雀门通过自动化测试；真实 `启动迭代-1` 只在用户明确启动后执行。
- 正式报告、Current Focus、项目地图、迭代决策、Git 和知识图谱游标一致。
- 新增后续项、关闭后续项和后续项净变化已统计；不存在只有会话备注而没有唯一承接载体的问题。
- `git status --short` 为空、`git diff --check` 通过、本地提交成功、未 push、未连接或发布生产。

## 13. 决策建议

1. 先批准实施 M0–M3 的 P0 恢复、指标采集和金丝雀基础设施；这些动作不改变正式评分版本。
2. M2 shadow 回放和批准门均已完成；用户已确认 `autopilot-task-score/v2`、35/25/20/10/10和自下一项新实施型 Ready 生效。
3. M1 未通过前不再执行 `启动迭代-10`；M3 自动化通过后先执行一次用户明确启动的 `启动迭代-1`。
4. 本计划不自动启动实施、不移除当前 `pause.flag`、不提交、不 push；进入实施需用户再次明确授权。

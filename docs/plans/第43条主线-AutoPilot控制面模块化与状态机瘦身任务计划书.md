# 第43条主线：AutoPilot 控制面模块化与状态机瘦身任务计划书

**Goal:** 在不改变 AutoPilot 对外入口、Ready 契约、恢复语义、质量门禁和两阶段收口规则的前提下，将当前集中在大型 PowerShell 脚本中的启动、协调、Issue 生命周期、Executor 监管、状态迁移与测试夹具职责拆分为可独立验证的模块；降低修改半径、恢复歧义和事故定位成本，使控制面后续演进不再依赖持续向单一 Runner 追加条件分支。验收方向是行为等价、单一状态写入口、阶段结果契约稳定、测试场景隔离、真实单任务金丝雀通过，并显著降低主 Runner 与核心函数的体量和耦合度。

**Architecture:** 采用“表征测试锁定行为 → 纯提取拆分 → 统一阶段结果 → 单一 transition writer → 删除兼容重复”的绞杀式改造，复用第42条、第42-1条已落地的 PowerShell 7、执行模式、原生命令、run.lock、fencing token、durable checkpoint、semantic stall、Reviewer、评分、知识图谱和 closeout 能力；保留一个薄入口负责启动和高层循环，将现有模块作为唯一底层事实源，不引入外部队列、数据库、工作流框架或第二套状态机，不以重构名义放宽任何安全、测试、权限、数据或 Git 门禁。

**Depends On:** 第42条主线、第42-1条主线已完成并形成稳定控制面基线；第43条实施期间不得与其他 AutoPilot 控制面功能主线并行修改同一批核心文件。

**计划状态:** Implemented / Canary Pending；用户已授权实施与测试，自动化结构改造和回归已完成；真实 `启动迭代-1` 金丝雀仍须用户另行明确启动，未授权提交或 push。

## 1. 背景与问题判断

当前 AutoPilot 并非所有脚本都超过1000行，体量主要集中在两个文件：

- `scripts/codex-autopilot/autopilot-run-continuous.ps1`：真实控制面入口，兼具运行协调器、Issue 生命周期编排器和部分状态机职责。
- `scripts/codex-autopilot/test-continuous-runner.ps1`：集中承载临时仓库、Runner 调用、模拟 Executor、状态构造和大量端到端场景。

主 Runner 已经加载多个专业模块，但仍直接承担以下职责：

1. PowerShell 宿主、配置、分支和控制面指纹初始化。
2. `DRY_RUN / EXPLAIN / APPLY` 模式及权能门禁。
3. stop、pause、enabled、迭代上限和回顾边界。
4. Ready 解析、选单、冲突识别、批次与并行判断。
5. run.lock、fencing token、state/checkpoint/result 恢复。
6. worktree、Executor、semantic stall、重试和退役进程管理。
7. validation、Reviewer、repair、closeout、评分和登记。
8. 知识图谱游标、控制面金丝雀、下一任务派发和最终退出。

这些职责长期以事故驱动方式增量加入同一主流程。单个补丁通常合理，但累积后产生以下结构风险：

- 修改一个阶段时必须理解整个 Runner 的全局变量和隐式顺序。
- 多处直接写 state/checkpoint，容易重新产生阶段结论分裂。
- 失败分类、暂停、阻塞、quarantine 和 repair 路由容易互相穿透。
- 测试夹具与场景混在一个文件，单个场景失败时定位成本高。
- 新模块虽已提取，但旧入口仍保留同类逻辑，形成过渡性重复。
- 继续追加功能会扩大每次控制面指纹变化和金丝雀验证成本。

因此第43条不是一次代码美化，而是对控制面修改半径、状态一致性和长期恢复可靠性的结构治理。

## 2. 决策结论

### 2.1 采用渐进拆分，不进行整体重写

- 现有第42-1行为是正式基线，先用测试锁定，不重新设计产品语义。
- 每个实施提交只允许完成一个可独立说明的提取或替换。
- 提取阶段不得顺便改变状态、失败分类、重试次数和退出码。
- 新旧实现不得长期双写；每个阶段完成后立即确定唯一入口。

### 2.2 状态写入统一，但不伪造跨文件事务

- state 是调度摘要，checkpoint 是阶段证据，result/evidence 是事实输入。
- 使用统一 transition writer 顺序写入、读回和校验，不实现伪 ACID。
- 中断时仍按第42-1事实优先级恢复：绑定当前 Issue/diff/fencing token 的完整 result/evidence 与 checkpoint 优先于陈旧 state。

### 2.3 行数是观察指标，不是唯一验收标准

主 Runner 目标降至约200～350行，但不得通过无意义拆文件、超长参数列表或复制逻辑来达标。真正验收标准是：

- 模块职责单一。
- 依赖方向清晰。
- 共享状态只有一个写入口。
- 阶段可独立测试。
- 旧测试和真实金丝雀行为一致。

## 3. 范围与非目标

### 3.1 本主线范围

- 拆分启动上下文、循环协调、Issue 生命周期和 Executor 监管职责。
- 建立统一 `StageResult` 内部契约。
- 建立唯一 transition writer，统一状态迁移与读回。
- 收敛全局变量为显式 `RuntimeContext`。
- 将大型连续 Runner 测试拆分为主题测试，共用确定性 fixture。
- 保留兼容测试入口，避免计划任务、文档和现有验证命令同时失效。
- 删除确认无调用的旧函数、重复分支和过渡兼容逻辑。
- 更新控制面指纹、项目规则、开发说明和正式验收材料。

### 3.2 非目标

- 不改变 `启动预演`、`启动迭代`、`启动迭代-N`、`停止迭代` 的用户语义。
- 不改变 Ready Issue 字段、A–F职责、评分权重和20任务回顾规则。
- 不改变 Reviewer 对真实 `NEEDS_REPAIR` 的裁决边界。
- 不改变 stop/pause、数据重置、权限、租户、安全、Git merge 和 no-push 门禁。
- 不引入类框架、依赖注入容器、外部消息队列、数据库或第三方工作流引擎。
- 不重写业务模块，不处理与控制面结构无关的产品功能。
- 不将行数指标作为绕过验证或删除必要证据的理由。

## 4. 目标模块边界

### 4.1 薄入口

保留：

- `scripts/codex-autopilot/autopilot-run-continuous.ps1`

目标职责仅包括：

1. 参数声明。
2. PowerShell 7和基础错误策略。
3. 加载模块。
4. 创建 `RuntimeContext`。
5. 调用协调器。
6. 输出最终结果和退出码。

入口不得直接实现 Git 裁决、checkpoint 迁移、Reviewer 路由或 closeout。

### 4.2 新增或收敛模块

建议新增：

- `autopilot-runtime-context.ps1`
  - 解析配置和执行模式。
  - 保存只读运行上下文、当前 fencing token 和路径集合。
  - 禁止模块继续依赖大量 `$script:*` 隐式变量。

- `autopilot-run-coordinator.ps1`
  - 负责 run 级 checkpoint、迭代上限、Ready 选择、回顾边界和退出。
  - 不处理单个 Issue 内部实现细节。

- `autopilot-issue-lifecycle.ps1`
  - 按当前事实调用 implement、validate、review、repair、closeout 阶段。
  - 只消费和返回 `StageResult`，不直接落盘共享状态。

- `autopilot-executor-supervisor.ps1`
  - 负责子进程启动、stdout/stderr、超时、semantic stall、退役和有界重试。
  - 不决定业务阶段和 quarantine。

- `autopilot-transition.ps1`
  - 唯一共享状态迁移入口。
  - 负责 fencing、transitionId、generation、控制面指纹、原子写入和读回。

继续复用并明确边界：

- `autopilot-execution-mode.ps1`：执行模式与权能。
- `autopilot-native-command.ps1`：原生命令结果。
- `autopilot-run-lock.ps1`：锁与fencing token。
- `autopilot-state.ps1`：state模型、序列化和校验，不负责业务阶段决策。
- `autopilot-issue-checkpoint.ps1`：checkpoint模型、序列化和校验，不负责调度循环。
- `autopilot-recover.ps1`：事实核验和恢复决策，不负责直接派发。
- `autopilot-ready.ps1`、`autopilot-refill.ps1`：Ready发现与补货。
- `autopilot-worktree.ps1`、`autopilot-context.ps1`：Git隔离和上下文。
- `autopilot-verify.ps1`、`autopilot-review.ps1`：验证与评审。
- `autopilot-closeout.ps1`、`autopilot-task-score.ps1`：收口与评分。

不得创建与现有模块语义重叠的第二套 recovery、lock、Git或score实现。

## 5. 内部契约

### 5.1 RuntimeContext

建议最小字段：

```text
repoRoot
configPath
config
executionMode
baseBranch
autoDir
readyPath
controlPlaneFingerprint
runLock
runInstanceId
leaseEpoch
iterationLimit
startedAt
```

约束：

- 初始化后配置、模式、baseBranch和启动指纹只读。
- 运行中变化的 Issue、阶段和计数存放在独立 `RunProgress`，不得回写配置对象。
- 所有允许写共享状态的函数必须显式接收 context 或 fencing token。

### 5.2 StageResult

所有阶段统一返回：

```text
schemaVersion
issueId
stage
outcome
nextStage
failureCategory
stopReason
reason
semanticProgress
evidencePaths
retryKey
transitionIntent
```

其中：

- `outcome` 只允许 `SUCCEEDED / RETRYABLE / PAUSED / BLOCKED / TERMINAL`。
- `failureCategory` 只允许现行明确分类，不允许依赖自由文本决定路由。
- `semanticProgress` 必须绑定阶段、diff、result、evidence或提交事实。
- `transitionIntent` 只是迁移请求，阶段函数不得自行写state/checkpoint。
- 临时日志、PID和run id可用于运行诊断，但不得写入正式长期报告。

### 5.3 Transition

统一 transition writer 必须：

1. 验证当前 `runInstanceId + leaseEpoch + controlPlaneFingerprint`。
2. 验证旧阶段与目标阶段之间存在合法边。
3. 创建唯一 `transitionId` 并递增generation。
4. 按确定顺序写checkpoint与state。
5. 读回并确认两者引用同一transition。
6. 失败时保留可恢复现场，不进行第二次隐式业务派发。

## 6. 实施任务

### Task 1：建立结构基线和行为表征测试

**Files:**

- Modify: `scripts/codex-autopilot/test-control-plane.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Create: `scripts/codex-autopilot/tests/autopilot-test-fixture.ps1`
- Create: `scripts/codex-autopilot/tests/test-runner-compatibility.ps1`
- Create: `scripts/codex-autopilot/tests/test-stage-result-contract.ps1`

**Tasks:**

- [x] 记录主入口参数、控制台关键标记、退出码和状态终态基线。
- [x] 覆盖dry-run、explain、apply、stop、pause、disabled、iteration-limit和canary-required。
- [x] 固化恢复、Reviewer tool_config、合法repair、semantic stall、closeout和回顾边界。
- [x] 建立共用fixture，统一临时仓库、Git换行配置、`.gitattributes`、Ready、state、checkpoint和mock Executor。
- [x] 证明测试可以捕获入口未持锁派发、重复implementation和stderr warning误判等历史缺陷。
- [x] 输出职责清单：现有每个函数归入目标模块或明确标记删除候选。

**验收:** 不修改生产行为时新增测试全部通过；人为关闭一个关键门禁时对应测试稳定失败。

### Task 2：拆分测试场景并保留兼容入口

**Files:**

- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Create: `scripts/codex-autopilot/tests/test-execution-modes.ps1`
- Create: `scripts/codex-autopilot/tests/test-lock-and-fencing.ps1`
- Create: `scripts/codex-autopilot/tests/test-recovery-reconciliation.ps1`
- Create: `scripts/codex-autopilot/tests/test-semantic-stall.ps1`
- Create: `scripts/codex-autopilot/tests/test-review-routing.ps1`
- Create: `scripts/codex-autopilot/tests/test-closeout-consistency.ps1`

**Tasks:**

- [x] 将大型测试中的fixture构造与断言助手提取到共享文件。
- [x] 每个主题测试可单独运行、独立清理临时目录并返回明确失败场景。
- [x] 旧`test-continuous-runner.ps1`保留为薄聚合入口，依次执行主题测试。
- [x] 普通fixture不输出CRLF warning；专项warning场景独立保留。
- [x] 测试不得读取用户目录、私有智能体目录或正式AutoPilot运行态。

**验收:** 聚合入口与所有分拆入口结果一致；任一主题失败可独立复现。

### Task 3：引入显式RuntimeContext

**Files:**

- Create: `scripts/codex-autopilot/autopilot-runtime-context.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-command.ps1`
- Modify: `scripts/codex-autopilot/autopilot-control-plane-fingerprint.ps1`

**Tasks:**

- [x] 将配置、路径、模式、分支和指纹初始化移入context工厂。
- [x] 明确只读配置与可变RunProgress的边界。
- [x] 将核心函数对全局变量的依赖改为显式参数。
- [x] 兼容现有命令行参数及配置字段。
- [x] 缺少PowerShell 7、配置错误、分支错误继续按现行分类fail-close。

**验收:** 入口初始化代码明显缩短；模式矩阵与控制面指纹测试不变。

### Task 4：提取Run Coordinator

**Files:**

- Create: `scripts/codex-autopilot/autopilot-run-coordinator.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-refill.ps1`

**Tasks:**

- [x] 移出stop/pause/enabled、迭代计数、Ready池、回顾边界和最终停止判断。
- [x] 协调器只选择下一动作，不直接实现Issue阶段。
- [x] 所有选单前继续执行checkpoint和canary门禁。
- [x] 保留Ready为空时的知识图谱、阻塞解除、Candidate和产品情报顺序。
- [x] 禁止为降低代码量合并或删除现有安全停止条件。

**验收:** 主入口不再包含Ready解析和多分支停止状态机；选单与停止条件回归全部通过。

### Task 5：提取Executor Supervisor

**Files:**

- Create: `scripts/codex-autopilot/autopilot-executor-supervisor.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-progress.ps1`

**Tasks:**

- [x] 移出ProcessStartInfo、输出流、超时、进程树终止和退出结果。
- [x] 保留semantic progress与liveness分离。
- [x] 保留声明式长命令豁免和总超时上限。
- [x] 保留同一failure fingerprint一次自动恢复预算。
- [x] Supervisor只报告执行事实，不决定repair、quarantine或Done。

**验收:** CPU/MCP活动不刷新进度；结果、checkpoint、evidence和diff变化仍可刷新；退役进程不可复用。

### Task 6：建立StageResult与Issue Lifecycle

**Files:**

- Create: `scripts/codex-autopilot/autopilot-stage-result.ps1`
- Create: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-review.ps1`
- Modify: `scripts/codex-autopilot/autopilot-verify.ps1`
- Modify: `scripts/codex-autopilot/autopilot-closeout.ps1`

**Tasks:**

- [x] 定义构造、校验和序列化StageResult的纯函数。
- [x] 将implement、validate、review、repair和closeout封装为阶段处理器。
- [x] 缺F只进入closeout恢复；Reviewer tool_config只暂停/重试E；完整needs_repair才进入repair。
- [x] 每个阶段显式返回下一阶段和失败分类，不通过自由文本推断。
- [x] 阶段处理器不得直接写共享state/checkpoint。

**验收:** 同一输入事实得到确定StageResult；历史路由矩阵结果不变。

### Task 7：引入唯一Transition Writer

**Files:**

- Create: `scripts/codex-autopilot/autopilot-transition.ps1`
- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-lifecycle.ps1`

**Tasks:**

- [x] 建立合法阶段边表和迁移校验。
- [x] state/checkpoint写入函数降级为模型存储层，不自行决定阶段。
- [x] 所有阶段迁移统一经过transition writer。
- [x] 写入前验证fencing和控制面指纹，写后读回transitionId/generation。
- [x] 保持result/evidence/checkpoint优先于陈旧state的恢复顺序。
- [x] 无法证明一致性时才quarantine；tool_config和环境错误不得借transition writer升级为完整性冲突。

**验收:** 静态检索证明业务阶段不再从多个位置直接写共享状态；故障注入可以确定性恢复。

### Task 8：删除重复逻辑并缩减入口

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: 本主线涉及的模块与测试入口

**Tasks:**

- [x] 删除已迁移函数、不可达分支和旧全局变量。
- [x] 删除不再需要的双写、兼容判断和重复失败分类。
- [x] 保留所有公开参数、控制台协议和正式状态字段。
- [x] 主Runner目标约200～350行；单个核心函数原则上不超过100行。
- [x] 行数超出目标时必须说明职责无法继续安全拆分的原因，禁止机械压缩格式。

**验收:** 无重复实现、无循环dot-source、无未使用模块；入口只做薄编排。

**实施说明（2026-07-14）:** 对外 Runner 最终为20行，低于原观察目标200～350行，因为参数、PowerShell 7门禁和协调器调用已经构成完整薄入口，并非压缩格式或删除门禁。`Invoke-AutopilotRunCoordinator` 与 `Invoke-IssueExecutor` 仍分别约407行和424行；两者保留单一顺序的 run checkpoint 与 Issue D/E/F/closeout 编排，内部事实执行继续复用 verify、review、closeout、recover 等专业模块。继续为追求100行机械切碎会重新引入隐式共享变量或改变中断窗口，因此本轮以“入口、职责模块、StageResult边界、唯一transition writer和独立主题测试”作为结构验收，不把纯行数偏好制造成后续 backlog。

### Task 9：规则、指纹、文档与正式收口

**Files:**

- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `AGENTS.override.md`
- Modify: `AGENTS.md`
- Modify: `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- Modify: `docs/backlog/current-focus.md`
- Modify: `docs/product-intelligence/project-map.md`
- Create: `docs/quality/mainline-43-autopilot-control-plane-modularization-acceptance-YYYY-MM-DD.md`

**Tasks:**

- [x] 控制面指纹覆盖所有新增协调、生命周期、transition和测试契约文件。
- [x] 固化“阶段函数返回结果、transition writer唯一落盘”的长期规则。
- [x] 更新架构说明和项目地图，不记录一次性run id、PID或日志路径。
- [x] 统计新增后续项、关闭后续项和后续项净变化。
- [ ] 全量回归通过后，由用户明确启动一次同指纹`启动迭代-1`金丝雀。
- [ ] 金丝雀完成implementationCommit、closeoutCommit、ledger、state、KG cursor和指纹登记读回。

**验收:** 正式报告可支持通过/不通过和N>1放量裁决；不存在会话级悬空问题。

## 7. 实施顺序与阶段门

### M0：行为冻结与职责盘点

- 完成Task 1。
- 阶段类型：审计/测试治理。
- 门禁：现有行为可重放，职责归属清单完整。

### M1：测试夹具和低风险纯提取

- 完成Task 2～3。
- 阶段类型：测试/结构实现。
- 门禁：外部行为零变化，兼容入口全部通过。

### M2：协调器与Executor监管拆分

- 完成Task 4～5。
- 阶段类型：控制面实现。
- 门禁：运行循环、stall、stop/pause和选单行为保持一致。

### M3：生命周期与状态写入口收敛

- 完成Task 6～7。
- 阶段类型：高风险状态机实现。
- 门禁：StageResult和transition故障注入全部通过，旧进程与无效token不可写。

### M4：删除重复、正式验收与金丝雀

- 完成Task 8～9。
- 阶段类型：验收/审计。
- 门禁：全量回归、静态边界检查、正式报告和真实单任务金丝雀全部通过。

## 8. 验收矩阵

| 场景 | 必须结果 |
| --- | --- |
| 旧命令行参数调用 | 输出、退出码和权能与第42-1基线一致 |
| DRY_RUN / EXPLAIN | 不持锁、不写state、不建worktree、不派发 |
| APPLY启动 | 持锁并完成恢复后才可派发 |
| stop/pause/enabled变化 | Coordinator在原有checkpoint安全停止 |
| Executor正常完成 | Supervisor返回事实，Lifecycle决定下一阶段 |
| Executor无语义进度 | 达到阈值后退役，不因CPU/MCP活动续命 |
| child result已完成、父进程退出 | Recovery消费结果，不重复implementation |
| Reviewer tool_config | 停在E并有界重试，不进入B/C |
| Reviewer完整needs_repair | Lifecycle允许一次有界repair |
| 缺失F报告 | 只进入closeout/F恢复 |
| state/checkpoint写入中断 | Transition读回或Recovery确定性收敛 |
| stale writer苏醒 | fencing失败，禁止共享写入和Git收口 |
| Git exitCode=0 + stderr warning | 成功并保留诊断，不改变阶段 |
| closeout中断 | 不重复implementationCommit、closeoutCommit或ledger key |
| 控制面指纹变化 | 旧进程停止，新指纹需用户启动单任务金丝雀 |
| 聚合测试入口 | 与所有主题测试结果一致 |

## 9. 验证命令建议

```pwsh
pwsh -NoProfile -File scripts/codex-autopilot/test-control-plane.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-continuous-runner.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-runner-compatibility.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-execution-modes.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-lock-and-fencing.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-recovery-reconciliation.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-semantic-stall.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-review-routing.ps1
pwsh -NoProfile -File scripts/codex-autopilot/tests/test-closeout-consistency.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-task-scoring.ps1
pwsh -NoProfile -File scripts/codex-autopilot/test-retrospective-cycle.ps1
git diff --check
```

新增测试文件在对应实施任务完成前不存在，M0阶段应先校验入口真实性，再逐步启用验证命令。

## 10. 迁移与提交策略

- 每个Task原则上形成一个可独立回滚的本地提交。
- 纯提取提交与行为变化提交必须分开。
- 不允许跨M阶段积累大量未提交控制面变更。
- 每次提交前至少运行受影响主题测试和`git diff --check`。
- M3状态机变更完成后必须运行全部恢复、Reviewer和closeout测试。
- M4全量验证与文档提交完成后，才允许用户启动真实金丝雀。
- `autoPush=false`保持不变；无用户明确授权不得push。

## 11. 风险与回滚

### 风险1：提取过程中改变隐式执行顺序

- 控制：先用表征测试冻结事件、状态和退出码；一次只移动一个职责。
- 回滚：回退对应提取提交，不回退第42-1安全基线。

### 风险2：显式context造成参数扩散

- 控制：只传递最小RuntimeContext和RunProgress，不构建万能对象。
- 回滚：保留稳定纯函数参数，撤回无价值的包装层。

### 风险3：StageResult成为第二套状态模型

- 控制：StageResult仅表达一次阶段调用结果，不持久化为新的权威事实源。
- 回滚：继续由checkpoint作为阶段证据，StageResult退化为内部返回对象。

### 风险4：Transition Writer形成新的超大模块

- 控制：writer只负责合法边、fencing、写入和读回，不承担Ready、Reviewer或closeout业务判断。
- 回滚：保留state/checkpoint存储函数，撤回过度集中业务逻辑。

### 风险5：测试拆分后聚合入口遗漏场景

- 控制：建立场景清单和兼容聚合测试，比较拆分前后测试数量与关键事件。
- 回滚：暂时保留原场景块，直到新主题测试可独立证明覆盖。

### 风险6：重构频繁触发控制面指纹金丝雀门

- 控制：按M阶段形成稳定提交，阶段内部不运行真实业务Ready；M4只对最终同一指纹执行一次用户启动金丝雀。
- 回滚：保持N>1禁止，不为减少验证成本绕过金丝雀。

## 12. 完成定义

第43条主线只有同时满足以下条件才能判定通过：

- 第42-1全部安全语义保持不变。
- 主Runner只保留参数、模块加载、context创建、协调器调用和最终输出。
- RuntimeContext与RunProgress边界明确，核心流程不再依赖大量隐式全局变量。
- implement、validate、review、repair、closeout统一返回StageResult。
- 共享state/checkpoint阶段迁移只有一个transition writer入口。
- Recovery仍以绑定事实优先，不重复implementation，不把tool_config升级为quarantine。
- Executor Supervisor不参与业务裁决，CPU/MCP活动不计semantic progress。
- 连续Runner测试已拆分为独立主题，旧入口仍可聚合执行。
- 普通Git fixture不继承机器换行配置，warning专项测试保持独立。
- 静态检查未发现重复lock、recovery、Git、评分或状态写实现。
- 主Runner目标约200～350行；未达到时有基于职责边界的书面说明。
- 全量控制面、恢复、Reviewer、closeout、评分和回顾测试通过。
- 用户明确启动的同指纹`启动迭代-1`真实金丝雀通过。
- 正式验收报告、项目地图和Current Focus已更新。
- 新增后续项、关闭后续项和后续项净变化已统计，不存在悬空问题。
- `git diff --check`通过；本地提交和push分别遵循用户授权。

## 13. 决策建议

1. 将第43条作为第42-1稳定后的独立结构治理主线，不与新产品能力迭代混做。
2. 优先完成测试夹具拆分和纯提取，再进入StageResult与transition writer高风险改造。
3. 不以一次大重写追求整洁；每个阶段都必须可回滚、可比较、可单独验收。
4. 将“阶段函数只返回结果、共享状态统一迁移”作为本主线最重要的不变式。
5. M3完成前不得删除旧恢复证据或兼容入口；M4验收后再删除确认不可达逻辑。
6. 最终是否允许N>1，仍由同一控制面指纹下的用户启动单任务金丝雀裁决。

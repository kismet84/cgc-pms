# 第42-1条主线：AutoPilot 执行模式、Git 告警与恢复状态一致性补充修复任务计划书

**Goal:** 对真实 `启动迭代-1` 金丝雀暴露的 AutoPilot 控制面缺陷进行统一修复：消除 `Apply/DryRun` 模式分裂、pwsh 将 Git 成功命令的 stderr warning 升级为异常、本地 checkpoint 污染主工作区、state/checkpoint 结论分裂、旧进程跨运行锁或控制面热修继续裁决、无业务进展的子进程活动绕过 stall 门槛，以及已完成阶段被错误回退为 B/C repair 的问题。验收方向是执行模式、运行锁、恢复决策、原生命令结果、阶段 checkpoint、父子进程终态与 Git 收口只有一套可重放事实；同一 Issue 不再因控制面异常重复实现，不会因无害 warning 进入 quarantine，也不会因无界恢复长时间占用执行窗口。
**Architecture:** 在现有 `autopilot-run-continuous.ps1`、`autopilot-recover.ps1`、Issue checkpoint、worktree、Reviewer、两阶段 closeout 和 v2 效率证据上做最小增量收敛：启动时将原始开关解析为不可变执行模式，先原子获取或接管带 fencing token 的运行锁，再在持锁条件下重读并收敛 state/checkpoint/result；用统一 Git 调用封装分离 stdout/stderr/exitCode，用 transition generation、运行实例 token 与控制面指纹阻止 stale writer；只把阶段迁移和可验证产物视为业务进度，并对相同失败指纹实施有界重试。不引入外部队列、数据库或第二套调度器，不重写业务 Issue，不放宽 Ready、Reviewer、权限/租户、两阶段提交或知识图谱游标门禁。

**Depends On:** 第42条主线、`ISSUE-040-022`、`ISSUE-040-023` 真实金丝雀现场、`autopilot-task-score/v2` 已批准口径。
**Tech Stack:** pwsh 7（`pwsh`，缺失时按 `tool_config` 安全停止）、Git worktree、JSON/JSON Schema、现有 AutoPilot runner/Reviewer/closeout/knowledge-graph 链路；不再支持 Windows pwsh 5.1 作为控制面宿主。
**计划状态:** Implementation Verified / Canary Pending；实现与自动化回归已完成，真实 `启动迭代-1` 因用户明确禁止使用自动迭代系统而未执行；未授权本地提交或 push。

**实施回写（2026-07-14）:** M0–M2 与 M3 的代码、规则、fixture、评分取证和正式验收报告已完成；控制面固定为 PowerShell 7 `pwsh`，普通 fixture 与 CRLF warning 专项 fixture 已分离。自动化证据见 `docs/quality/mainline-42-1-autopilot-control-plane-consistency-acceptance-2026-07-14.md`。当前唯一未满足的完成定义是用户另行启动的新指纹单任务金丝雀及其 implementationCommit、closeoutCommit、ledger、state、KG cursor、指纹读回；完成前 N>1/无界放量保持阻塞。

## 1. 背景与重新打开结论

第42条主线已完成夹具级跨 Run 恢复、Reviewer 工具故障隔离、两阶段 closeout、效率取证和控制面金丝雀门。真实 `ISSUE-040-023` 金丝雀最终已于 2026-07-14 完成实施提交、评分/收口提交、state 清理和指纹登记；但真实执行过程中连续暴露了夹具未覆盖的 Windows、进程边界和状态收敛问题。

因此：

- 第42条主线的“夹具级实现完成”历史事实保留；
- “控制面已可稳定放量”的结论必须由本补充主线重新验证；
- 已提交的应急补丁是本计划的 baseline，不等于系统性根因已关闭。

## 2. 已确认问题清单

| 稳定键 | 优先级 | 现状 | 问题 | 解除条件 |
| --- | --- | --- | --- | --- |
| `AUTOPILOT-EXECUTION-MODE-SPLIT` | P0 | 直接分支已补丁 | `-ApplyBacklogSplit` 同时被当作真实执行开关；未传参时摘要是 dry-run，后续却检查原始 `$DryRun` 并派发 Executor，跳过恢复门与运行锁 | 模式组合矩阵证明非 APPLY 绝不写状态/建 worktree/派发 Executor；APPLY 派发前必定已恢复并持锁 |
| `AUTOPILOT-GIT-STDERR-WARNING` | P0 | 特定命令已补丁 | Git 返回0但输出“LF 将转 CRLF”，全局 `$ErrorActionPreference='Stop'` 将 stderr warning 升级为异常 | 所有参与状态、恢复、提交、合并和收口裁决的 Git 调用仅以 exitCode 与命令约定裁决；exitCode=0 的 stderr 只记诊断，不写 quarantine |
| `AUTOPILOT-CHECKPOINT-GIT-HYGIENE` | P0 | `.gitignore` 已补丁 | `.codex-autopilot/checkpoints/` 未忽略，使主工作区变脏，后续快进合并拒绝 | `git check-ignore` 回归、控制面指纹覆盖 `.gitignore`，运行态文件不出现于主工作区 status |
| `AUTOPILOT-STATE-CHECKPOINT-SPLIT` | P0 | 未系统关闭 | state 可停在 `EXECUTING/BLOCKED`，checkpoint 却已是 `REPAIRING/QUARANTINED`，两者的 reason 和心跳可互相矛盾 | 过渡代次可读回；父进程中断后可用 checkpoint/result 确定性收敛；不再出现死 PID+永久 EXECUTING |
| `AUTOPILOT-LOCK-TAKEOVER-RACE` | P0 | 未关闭 | 当前 Runner 在取得新锁前可删除旧锁、修改 checkpoint 或写 state，两个恢复进程可能同时裁决同一现场 | 恢复前只读检查；原子获取/接管锁后必须重读现场；未持锁不得修改共享运行态 |
| `AUTOPILOT-STALE-WRITER-FENCE` | P0 | 未关闭 | 同一控制面指纹下旧父进程可能在新 Runner 接管后苏醒并覆盖新状态 | lock/state/checkpoint/result 绑定 `runInstanceId/leaseEpoch`；token 不匹配的进程禁止写入、合并和收口 |
| `AUTOPILOT-QUARANTINE-SEMANTICS` | P0 | 未系统关闭 | 通用 `catch` 将 Git warning、tool_config 或旧配置异常直接写入 `quarantineReason` | 只有 Ready/base/branch/diff/evidence/scope 完整性冲突可 quarantine；工具、环境和诊断 warning 进入独立分类 |
| `AUTOPILOT-HOTFIX-GENERATION` | P0 | 未系统关闭 | 活动 Runner 仍持有热修前的配置/指纹集合，却继续对热修后 HEAD 做 base-advance 裁决 | run/checkpoint 绑定启动指纹；指纹改变时旧进程不得继续派发或收口，必须安全停止并由新进程重读配置 |
| `AUTOPILOT-PHASE-ROUTING-PRECISION` | P0 | 部分关闭 | 缺失 F 报告、Reviewer tool_config 或证据路径问题可被误路由为 BC repair | 缺 F 只恢复 F；tool_config 只停在当前阶段；只有绑定同一 diff 且 finding 完整的 `NEEDS_REPAIR` 可进入有界 repair |
| `AUTOPILOT-RESULT-RECONCILIATION` | P0 | 未系统关闭 | Executor 已产生 `done` result 并退出，父 Runner/state 仍可留在 `EXECUTING` | 父进程和下次恢复都能幂等消费子结果，收敛到唯一终态 |
| `AUTOPILOT-SEMANTIC-STALL` | P0 | 未关闭 | 子进程树或 MCP 的微小 CPU 活动可持续刷新“进度”，即使阶段、result 和证据均未变化 | CPU 只证明存活；只有阶段迁移、绑定结果、验证证据、diff 或提交等语义证据才能刷新进度 |
| `AUTOPILOT-RECOVERY-BUDGET` | P0 | 未关闭 | 相同 tool_config、恢复原因或 failure fingerprint 可多次重启，恢复流程本身成为主要工作负载 | 同一 failure fingerprint 自动恢复最多一次；再次出现时保留当前阶段并暂停，不回退 B/C |
| `AUTOPILOT-CONTROL-OVERHEAD-EVIDENCE` | P1 | v2 已有部分指标 | 总耗时与业务实施、验证、Reviewer、恢复、等待、热修的耗时没有稳定分开 | 正式报告与20任务回顾能区分业务工作和控制面返工，不用固定分钟数主观扣分 |

## 3. 已落地应急补丁基线

以下提交作为本计划的已知 baseline，实施时不重复造轮子，但必须纳入系统回归：

- `f6e2089b9`：统一用 `$applyMode/$dryRunMode` 控制恢复、state 与 Executor 派发。
- `f155528a5`：收口路径的 Unicode 哈希处理。
- `e01486401`：对部分 Git 恢复命令固定 CRLF 配置，忽略 checkpoint 运行态并扩展控制面指纹。
- `53a14d819`：绑定 Ready lint 证据并消除 CRLF 差异噪声。
- `9459e79a4`：清理已关闭 Issue 在 state 中的残留绑定。
- `0f96d9e28`：修复 v2 评分对 repair 次数的保留。

这些补丁已帮助 `ISSUE-040-023` 最终收口，但尚未替代统一原生命令语义、状态收敛和热修代次模型。

## 4. 范围与非目标

### 4.1 本主线范围

- 执行模式与权能不变式：dry-run、explain、apply 三种模式只在启动时解析一次。
- 统一 Git/原生命令执行结果：分离 stdout、stderr、exitCode、timeout 和分类。
- state、checkpoint、child result、run.lock 与控制面指纹的幂等收敛。
- run.lock 的原子获取/接管、运行实例 fencing token 与 stale writer 拒绝。
- 明确 quarantine、tool_config、environment_prereq、quality_security 的边界。
- 阶段精确恢复与合法 repair 门，区分明确 Reviewer finding 与控制面故障。
- 基于阶段证据的语义进度检测、相同失败指纹的重试预算与恢复熔断。
- 用真实金丝雀故障注入替代仅依赖正常路径的单元夹具。
- 将业务执行耗时与控制面返工耗时分开纳入 v2 取证和20任务趋势。

### 4.2 非目标

- 不重写整个 Runner，不引入 Redis、消息队列、外部调度平台或新数据库。
- 不以修复控制面为由跳过 Reviewer 对真实业务并发一致性、权限、租户或安全 finding 的 repair。
- 不重算 v1 历史分数，不改变已批准 v2 权重和生效边界。
- 不连接生产、不发布生产、不自动 push、不删除可恢复 worktree。
- 不将原始日志、run id、PID 或一次性截图写入长期规则。

## 5. 目标不变式

### 5.1 执行模式

```text
DRY_RUN  -> 只读发现/输出，不写 state，不持锁，不创建 worktree，不派发
EXPLAIN  -> 只读说明下一动作，不修改运行态
APPLY    -> 只读检查现状，原子获取/接管 run.lock，持锁后重读并完成恢复门，最后才可选单/派发/收口
```

硬不变式：

- `canDispatch=true` 推导出 `mode=APPLY`、`lockOwned=true`、`recoveryCheckedAfterLock=true`、`fenceValid=true`。
- 解析完成后不再用 `$DryRun`、`$ApplyBacklogSplit` 等原始开关参与后续业务分支。
- 模式、日志、state 和派发记录必须显示同一值。
- `DryRun/Explain/Apply` 冲突组合必须 fail-close；不得静默降级或用参数顺序决定权能。
- `-ApplyBacklogSplit` 只作为兼容入口，内部模式、状态和事件统一记录为 `APPLY`。

### 5.2 Git/原生命令

- `exitCode=0` 即命令成功；stderr 非空只记录 diagnostic/warning。
- `exitCode!=0` 才进入失败分类；分类依据包含命令、阶段、超时与可重试性，不仅依赖文本关键词。
- `core.autocrlf=false/core.safecrlf=false` 用于控制差异稳定性，不能代替正确的 exitCode/stderr 语义。
- Git warning 不得更改 checkpoint phase、`quarantineReason`、failureCategory 或 Done 裁决。

### 5.3 状态与恢复

- 每次阶段过渡生成 `transitionId`、`generation`、`controlPlaneFingerprint`。
- 每次成功获取或接管运行锁生成不可复用的 `runInstanceId/leaseEpoch`，并写入 state、checkpoint 与 child result。
- checkpoint 保存阶段证据，state 保存调度摘要；两者指向同一 transition，写后必须读回。
- 如果父进程死亡而 child result 已完整落盘，下次恢复先消费 result，不猜测重做。
- 指纹改变或 fencing token 失效后，旧进程只能写入绑定旧 token 的不可变诊断事件并退出，不得再改共享 state/checkpoint、做 base-forward、合并、收口或新派发。
- 可恢复证据不一致才可 `QUARANTINE`；`tool_config` 和 `environment_prereq` 只可暂停对应阶段。

### 5.4 语义进度与恢复预算

- PID 存活、CPU 时间变化、MCP 心跳或日志追加只属于 liveness，不得刷新 `lastProgressAt`。
- 只有合法阶段迁移、绑定当前 issue/diff 的 child result、验证/评审证据、业务 diff 变化、implementation/closeout commit 等可核验事件才属于 semantic progress。
- 同一 `failureFingerprint + phase + diffHash` 自动恢复最多一次；第二次出现时写入稳定暂停状态并保留首个未完成阶段。
- tool_config、environment_prereq 与控制面 warning 的重试预算分别计数，不得通过更换 reason 文本规避熔断。

## 6. 实施任务

### Task 1：建立真实故障注入 RED/GREEN 基线矩阵

**Files:**

- Modify: `scripts/codex-autopilot/test-control-plane.ps1`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`
- Modify: `scripts/codex-autopilot/test-continuous-runner.ps1`
- Create: `scripts/codex-autopilot/test-native-command-semantics.ps1`
- Create: `scripts/codex-autopilot/test-execution-mode-matrix.ps1`

**Tasks:**

- [x] 覆盖无参数、`-DryRun`、`-ExplainNextAction`、`-ApplyBacklogSplit` 及冲突组合，断言非 APPLY 不产生任何可变更副作用。
- [x] 模拟 Git 在 `diff`、`merge --ff-only`、`status`、`add/commit` 中 stderr 输出 CRLF warning 但返回0。
- [x] 普通控制面 fixture 的临时 Git 仓库必须设置确定的本地 `core.autocrlf/core.eol/core.safecrlf`，并写入或复制 `.gitattributes`，不得继承机器级 Git 换行策略。
- [x] 将普通“零告警” fixture 与故意设置 `core.autocrlf=true`、写入 LF 文件的 Git warning 专项 fixture 分离；后者断言 warning 可保留但 exitCode=0 仍成功。
- [x] 将 `test-continuous-runner.ps1` 统一为单一换行格式，避免 here-string 把 mixed EOL 继续传播到临时仓库。
- [x] 所有 AutoPilot 入口、子进程、测试与计划命令统一使用 `pwsh`；PowerShell 7 不可用时归类为 `tool_config/AUTOPILOT_POWERSHELL7_REQUIRED`，不得回退到 Windows PowerShell 5.1。
- [x] 模拟 checkpoint 目录未忽略，证明当前 merge gate 会被主工作区 dirty 阻断。
- [x] 模拟 child result 已 `done` 后父 Runner 被终止，重启必须消费已存在结果。
- [x] 模拟 Runner 启动后控制面指纹改变，旧进程必须停止而不是用内存中旧允许路径裁决新 HEAD。
- [x] 模拟 state/checkpoint 写入中断与 reason 冲突，得到确定恢复结果。
- [x] 模拟两个 APPLY 并发恢复同一现场，以及旧父进程在新 Runner 接管后重新写入。
- [x] 模拟子进程 CPU/MCP 持续活动但阶段、result、diff 和证据均不变化。
- [x] 模拟相同 `failureFingerprint + phase + diffHash` 连续出现两次，第二次必须暂停。

**RED/GREEN 基线验收:** 对尚未修复的问题，当前代码新增测试必须稳定 RED；对已由应急提交关闭的问题，新增防回归测试在当前代码上必须 GREEN，并能通过隔离 fixture、故障注入或临时关闭保护条件证明测试确实能捕获历史缺陷。不得为了制造 RED 回退正式补丁；夹具不依赖真实用户目录，不修改正式 backlog 或业务代码。
普通 fixture 不得输出 CRLF warning；专项 warning fixture 必须稳定捕获 stderr warning 且命令分类仍为成功。

### Task 2：收敛为单一执行模式对象

**Files:**

- Create: `scripts/codex-autopilot/autopilot-execution-mode.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `scripts/codex-autopilot/test-execution-mode-matrix.ps1`

**Tasks:**

- [x] 将原始开关一次性解析为 `DRY_RUN/EXPLAIN/APPLY` 与 `canWriteState/canAcquireLock/mustRecover/canDispatch`。
- [x] 删除解析后对 `$DryRun/$ApplyBacklogSplit` 的直接分支依赖。
- [x] 冲突参数直接拒绝；`-ApplyBacklogSplit` 作为兼容入口映射到内部 `APPLY`。
- [x] 恢复前只允许只读探测；原子获取或接管 run.lock 后重新读取 state/checkpoint/result，再做权威恢复。
- [x] 禁止以 `Remove-Item run.lock -> New-RunLock` 的无锁窗口完成接管；接管必须由单一原子协议完成。
- [x] 在 Executor 派发前断言恢复门已在持锁后完成、当前进程真正持锁且 fencing token 有效。
- [x] event、state、控制台摘要和实际权能全部从同一模式对象读取。

**GREEN 验收:** 模式矩阵全部通过；任何非 APPLY 路径都无 Executor PID、worktree、run.lock 或 state 修改；两个并发 APPLY 最多一个获得有效 fencing token，另一个只读退出。

### Task 3：统一 Git/原生命令语义

**Files:**

- Create: `scripts/codex-autopilot/autopilot-native-command.ps1`
- Modify: `scripts/codex-autopilot/autopilot-context.ps1`
- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-worktree.ps1`
- Modify: `scripts/codex-autopilot/autopilot-closeout.ps1`
- Modify: `scripts/codex-autopilot/autopilot-control-plane-fingerprint.ps1`
- Modify: `scripts/codex-autopilot/test-native-command-semantics.ps1`

**Tasks:**

- [x] 用 `ProcessStartInfo` 或等价封装独立采集 stdout、stderr、exitCode、timeout，避免 pwsh 原生 stderr 被 `$ErrorActionPreference` 控制。
- [x] 将所有参与恢复、diff 哈希、scope、worktree、commit、closeout、merge、ledger 和状态裁决的 Git 调用迁移到统一封装；纯诊断命令允许后续迁移，但不得参与裁决。
- [x] 保留可诊断 stderr 摘要，但禁止用 stderr 非空直接触发失败或 quarantine。
- [x] 对 `git diff --no-index` 的0/1合法退出码、`merge-base --is-ancestor` 的1业务语义做命令级显式配置。

**GREEN 验收:** CRLF、advice 和其他 exitCode=0 warning 不再改变状态；真实非零退出码仍 fail-close。

### Task 4：实现 state/checkpoint/result 过渡收敛

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/issue-checkpoint.schema.json`
- Modify: `plugins/cgc-pms-autopilot/schemas/loop-state.schema.json`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`

**Tasks:**

- [x] 每次阶段过渡写入统一 `transitionId/generation/controlPlaneFingerprint`。
- [x] lock、state、checkpoint、child result 同时绑定 `runInstanceId/leaseEpoch`；每次共享状态写入和 Git 收口前校验当前 fencing token。
- [x] 定义单向事实优先级：绑定当前 diff 的完整 child result 与 checkpoint evidence 优先于陈旧 state 摘要。
- [x] 增加父进程正常结束和下次恢复的同一幂等 reconciliation，不重复增加 dispatch 计数。
- [x] 状态写入继续使用临时文件+原子替换；写入中断后以 fencing token 有效、generation 较新且证据完整的记录收敛，冲突无法证明时才 quarantine。
- [x] 收口后清理 Issue 绑定前先读回 implementationCommit、closeoutCommit、ledger、state、KG cursor 和金丝雀指纹。

**GREEN 验收:** 死父进程+已完成 child result 可在下一次启动收敛；state/checkpoint 不再各自保留不同阻塞 reason；旧进程在新 token 接管后无法覆盖任何共享运行态或执行 Git 收口。

### Task 5：隔离 quarantine、阶段恢复与合法 repair

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/autopilot-review.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `plugins/cgc-pms-autopilot/schemas/review-result.schema.json`
- Modify: `scripts/codex-autopilot/test-review-repair.ps1`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`

**Tasks:**

- [x] 用显式失败类型替换恢复主路径的通用 `catch -> quarantine`。
- [x] 只允许 Ready/base/branch/scope/diff/evidence 完整性冲突进入 quarantine。
- [x] 缺失 F 产物只恢复 closeout；Ready lint/evidence 绑定问题只恢复缺失验证。
- [x] Reviewer `tool_blocked` 只重试 E 一次；第二次暂停 E，不触发 BC。
- [x] Reviewer `needs_repair` 必须绑定同一 issue/diff，且 finding 包含 severity、file/line、risk、requiredEvidence，才能进入有界 repair。
- [x] 正常 repair 完成后必须使旧验证/评审失效，但不增加 implementation dispatch 计数。
- [x] 将 PID、CPU、MCP 活动降级为 liveness；只用阶段迁移、绑定 result、验证/评审证据、diff 或提交刷新 semantic progress。
- [x] 对 `failureFingerprint + phase + diffHash` 设一次自动恢复预算；第二次命中时暂停原阶段，不改写为新 reason、不触发 B/C。
- [x] 增加“CPU 持续变化但无语义证据”和“相同 tool_config 连续出现”的故障注入。

**GREEN 验收:** 真实业务 finding 仍可 repair；所有控制面、warning、tool_config 和缺 F 情形都不再回退 B/C；无语义进展可稳定命中 stall，相同失败不会进入第二轮自动恢复。

### Task 6：绑定控制面代次与热修边界

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-control-plane-fingerprint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-recover.ps1`
- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `scripts/codex-autopilot/test-control-plane.ps1`
- Modify: `scripts/codex-autopilot/test-phase-recovery.ps1`

**Tasks:**

- [x] run.lock、state、checkpoint 同时记录启动时控制面指纹。
- [x] 每个新派发、新阶段和合并前比对当前指纹；变化时旧进程只做安全现场保留并退出。
- [x] 每次共享状态写入和 Git 收口同时校验控制面指纹与 fencing token；任一失效即停止写入。
- [x] 新进程重读配置后才能判断哪些 base-advance path 可允许，不复用旧进程内存中的路径集合。
- [x] 项目规则固化“活动 Issue 不热修控制面”；紧急修复先 pause/安全收集 checkpoint，再提交并由用户重新启动金丝雀。

**GREEN 验收:** 旧配置进程不会对新 HEAD 产生“超出允许路径”的假 quarantine，也不会跨指纹或跨 fencing token 继续派发、写状态、合并或收口。

### Task 7：效率证据、金丝雀与正式收口

**Files:**

- Modify: `scripts/codex-autopilot/autopilot-task-score.ps1`
- Modify: `scripts/codex-autopilot/test-task-scoring.ps1`
- Modify: `docs/standards/14-AutoPilot任务评分与自动改进回顾规范.md`
- Modify: `AGENTS.override.md`
- Modify: `AGENTS.md`
- Modify: `.agents/skills/cgc-pms-mainline-owner-flow/SKILL.md`
- Modify: `docs/backlog/current-focus.md`
- Modify: `docs/product-intelligence/project-map.md`
- Create: `docs/quality/mainline-42-1-autopilot-control-plane-consistency-acceptance-2026-07-14.md`

**Tasks:**

- [x] 区分 implementation/validation/review/repair/closeout 业务耗时与 recovery/tool wait/hotfix/manual intervention 控制面耗时；单独记录 liveness 与 semantic progress，不把 CPU 活动计作有效推进。
- [x] 保留已批准 v2 35/25/20/10/10；不按固定分钟数扣分，仅对可归因的重复派发、阶段回退、人工恢复和证据缺失计分。
- [ ] 运行全部控制面与故障注入回归，并进行一次用户明确启动的真实 `启动迭代-1` 金丝雀。（自动化回归已完成；真实金丝雀待用户启动）
- [ ] 金丝雀必须完成 implementationCommit、closeoutCommit、ledger/state/KG cursor 读回和指纹登记；中途热修则本次无效。（待真实金丝雀）
- [x] 正式验收报告统计新增后续项、关闭后续项与净变化；更新第42条主线的真实放量结论。

**收口验收:** 同一指纹金丝雀零错误阶段回退、零控制面 quarantine、零重复 implementation dispatch；金丝雀登记后才允许 N>1。

## 7. 实施顺序与阶段门

### M0：现场固化与 RED/GREEN 基线（已完成）

- 完成 Task 1，固化已发生的失败模式。
- 不再启动业务 Ready，直到未修复问题形成稳定 RED、已补丁问题形成可信 GREEN 防回归基线。
- 阶段类型：审计/运维治理；不涉及业务数据。

### M1：单一模式与原生命令语义（已完成）

- 完成 Task 2–3。
- 阶段门：非 APPLY 零变更；并发 APPLY 只有一个有效 token；恢复重读发生在持锁后；exitCode=0 warning 零状态影响。

### M2：状态收敛与恢复语义（已完成）

- 完成 Task 4–6。
- 阶段门：state/checkpoint/result 故障注入全部收敛；stale writer 被 fencing token 拒绝；热修跨代禁止继续执行；相同失败自动恢复不超过一次。

### M3：效率与自动化验收已完成；真实金丝雀待用户启动

- 完成 Task 7。
- 阶段类型：验收/审计；输出直接用于通过/不通过和 N>1 放量裁决。
- 阶段门：全部回归+真实金丝雀+图谱游标+两阶段提交全部读回。

## 8. 验收矩阵

| 场景 | 必须结果 |
| --- | --- |
| 无参数启动 Runner | 只读 dry-run，不写 state、不持锁、不派发 |
| `-ExplainNextAction` | 只输出决策，不改变运行态 |
| `-ApplyBacklogSplit` | 映射为 APPLY；只读探测后原子持锁，持锁重读并恢复，最后才可派发 |
| 模式参数冲突 | fail-close，不写 state、不持锁、不派发 |
| 两个 APPLY 并发启动 | 只有一个获得有效 fencing token，另一个只读退出 |
| Git exitCode=0 + CRLF stderr | 记 diagnostic，不失败、不 quarantine |
| Git exitCode!=0 | 按命令语义与阶段分类，必要时 fail-close |
| checkpoint 运行态存在 | 主工作区 status 仍干净，合并门不被影响 |
| child result 已 done、父进程退出 | 下次启动消费结果并从首个未完成阶段继续 |
| state/checkpoint 过渡中断 | 依 transition/generation 幂等收敛，不产生两个当前阶段 |
| 旧父进程在新 Runner 接管后苏醒 | fencing token 失效，禁止写 state/checkpoint、合并或收口 |
| 旧 Runner 运行期间控制面指纹改变 | 旧 Runner 安全停止，不跨代派发/合并 |
| 缺失 F 报告 | 只恢复 F，不派发 BC |
| Reviewer `tool_blocked` | 有界重试 E，耗尽后暂停 E |
| Reviewer 完整 `needs_repair` | 允许有界 repair，保留原 implementation dispatch 计数 |
| Reviewer finding 缺字段/未绑定 diff | 拒绝 repair，安全暂停 |
| CPU/MCP 持续活动但无阶段或证据变化 | 只记 liveness，达到门槛后命中 semantic stall |
| 同一失败指纹第二次出现 | 保留当前阶段并暂停，不再自动恢复、不回退 B/C |
| closeout 各个原子点中断 | 不重复生成 implementationCommit、closeoutCommit 或 ledger key |
| 控制面指纹变化后 N>1 | 真实 `-1` 金丝雀未登记前安全拒绝 |

## 9. 建议验证命令

```pwsh
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-execution-mode-matrix.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-native-command-semantics.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-phase-recovery.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-review-repair.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-control-plane.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-continuous-runner.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-closeout.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-task-scoring.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/codex-autopilot/test-retrospective-cycle.ps1
git diff --check
```

首次失败必须先归类为 `tool_config`、`environment_prereq`、`quality_security` 或完整性 quarantine；不得将单次 stderr、命令包装或测试选择器错误直接定性为业务失败。

## 10. 风险与回滚

### 风险1：原生命令封装改变既有特殊退出码语义

- 控制：按命令显式配置可接受退出码，先迁移恢复/closeout 高风险路径。
- 回滚：回退封装提交，保留现有 `core.autocrlf/core.safecrlf` 应急补丁与回归。

### 风险2：双文件过渡仍可在写入中断时短暂不一致

- 控制：transitionId/generation+写后读回+确定性恢复，不尝试跨文件 ACID 伪实现。
- 回滚：保留 checkpoint 为唯一阶段证据，state 只降级为可重建摘要。

### 风险3：指纹变化安全停止使开发期热修需要额外重启

- 控制：只在阶段边界比对，保留已落盘证据；新进程从首个未完成阶段继续。
- 回滚：保留金丝雀门，禁止为提高便利性恢复跨代自动执行。

### 风险4：严格 repair 门阻断合法业务整改

- 控制：保留 `needs_repair` 明确通道，只要 issue/diff/finding/证据完整即可执行。
- 回滚：允许主线负责人基于完整 Reviewer 证据人工触发单次有界 repair，仍禁止工具故障触发。

### 风险5：锁接管或 fencing token 实现错误导致合法恢复被拒绝

- 控制：用双 Runner 并发、旧父进程复活和相同指纹重启三组故障注入验证；锁接管前只读，接管后必须重读。
- 回滚：安全停止自动执行并保留 checkpoint/result；不得回滚到无锁删除旧 `run.lock` 的实现。

### 风险6：语义进度定义过严导致长测试误判 stall

- 控制：验证/评审执行期间允许由受信任阶段心跳携带当前命令、证据游标和子结果更新时间，但单纯 CPU 变化无效。
- 回滚：只调整各阶段 stall 阈值或受信任证据类型，不恢复“任意子进程活动即进度”。

## 11. 完成定义

本补充主线只有同时满足以下条件才能判定通过：

- 执行模式矩阵证明非 APPLY 没有可变更副作用，APPLY 派发前必定已恢复并持锁。
- 恢复前不修改共享运行态；两个并发 APPLY 只有一个取得有效 fencing token，旧进程无法在接管后写回。
- 所有参与状态、恢复、hash、scope、提交、合并、ledger 和收口裁决的 Git 调用统一使用 exitCode 与命令约定语义，exitCode=0 的 stderr warning 不再中断状态机。
- `.codex-autopilot/checkpoints/` 及其他明确运行态不会污染主工作区或阻断 merge。
- state/checkpoint/result 故障注入全部可幂等收敛，死进程不保留假 `EXECUTING`。
- CPU/MCP 活动不会绕过 semantic stall；相同失败指纹最多自动恢复一次，耗尽后停在原阶段。
- quarantine 仅用于完整性冲突，tool_config、环境和 warning 不写 quarantineReason。
- 缺 F、Reviewer tool_config、已完成验证不会重新派发 B/C；合法 `needs_repair` 仍可有界执行。
- 旧指纹进程无法跨控制面热修继续裁决；新进程重读配置后才能恢复。
- v2 效率证据能分离业务执行与控制面返工，不修改已批准权重、不回算 v1。
- 全部回归通过后，由用户明确启动的真实 `启动迭代-1` 在无中途热修的同一指纹下收口。
- 正式验收报告、implementationCommit、closeoutCommit、ledger、state、知识图谱 Git cursor 和金丝雀指纹全部读回一致。
- 新增后续项、关闭后续项与后续项净变化已统计；不存在只停留在报告或会话中的悬空问题。
- `git diff --check` 通过，本地提交需另行获得用户授权，全程不 push、不发布生产。

## 12. 决策建议

1. 将本计划作为第42条主线真实金丝雀后续问题的唯一计划载体，不再分别新建 CRLF、checkpoint、ApplyMode 等孤立计划。
2. 先完成 M0–M2，在系统性回归通过前不再运行 N>1 或无界迭代。
3. 已有应急补丁保留，实施以封装、过渡收敛和故障注入补齐根因，不做无关的 Runner 大重写。
4. 完成自动化后仍须用户明确启动一次新指纹 `启动迭代-1`；该次不允许边运行边热修控制面。
5. 实施与本地测试已获授权并完成；本地提交与 push 仍需用户另行明确授权。

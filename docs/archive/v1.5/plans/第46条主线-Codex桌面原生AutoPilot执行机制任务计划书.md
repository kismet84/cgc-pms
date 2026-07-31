# 第46条主线：Codex 桌面原生 AutoPilot 执行机制任务计划书

**Goal:** 将 AutoPilot 从“PowerShell 脚本启动 Planner / Executor / Reviewer CLI 子进程并轮询状态”的执行方式迁移为“Codex 桌面端主线程直接编排，必要时使用短生命周期子智能体隔离上下文，PowerShell 只提供确定性原子能力”的执行方式。验收方向是消除嵌套 `codex exec`、多层超时、PID/CPU/文件时间猜测和不透明等待，同时完整保留 Ready、知识图谱、stop/pause、run lock、fencing、控制面指纹、worktree、范围门禁、验证、Reviewer、两阶段提交、Closeout Record、评分、回顾和单 Issue 金丝雀门禁。

**Architecture:** 采用“桌面主线程唯一编排者 + 可选短生命周期子智能体 + 原子 PowerShell 工具 + durable checkpoint 恢复事实 + legacy CLI 可回滚”的渐进迁移。桌面主线程负责选题、阶段推进、失败分类、路由调整、验收与最终裁决；子智能体只承担边界明确的实现、独立审查或上下文隔离任务，不承担长期循环；脚本只执行 checkpoint、Ready 写入、worktree、transition、验证、失败分类、收口、提交和图谱读回等确定性动作，不再发起模型调用或自行决定下一业务阶段。迁移不引入新服务、数据库、消息队列或独立可视化平台，不删除现有状态与证据契约，不在真实金丝雀前移除 legacy runner。

## 实施状态

- 计划状态：Implemented / Automated Acceptance Passed / Canary Pending。
- M0～M3、M5 已完成：生产默认宿主切换为 `desktop-native`；桌面主线程触发协议、旧模型进程门禁、原子入口、durable checkpoint 宿主事实、兼容 Schema 和 legacy 显式回退均已落地。
- M4 自动化回归已通过；本次实施指令不是精确触发词 `启动迭代-1`，因此未启动真实 Ready 金丝雀，也未提交、未 push。
- 实施前发现 `ISSUE-040-025` 仍有 `REPAIRING` checkpoint 与保留 worktree，已设置 `pause.flag` 防止新旧宿主并发；现场未清理、未重派、未合并。
- 本计划属于控制面行为变更；实施后必须更新控制面指纹，并由用户明确启动且成功收口一次 `启动迭代-1` 后，才允许 N>1 或无界执行。
- 实施前必须先确认不存在仍在写入的活动 AutoPilot Issue；若存在有效 checkpoint/worktree，只允许安全收口、正式暂停或按恢复契约完成当前阶段，不得让新旧编排器并发接管。

## 1. 背景与问题定义

当前配置将 `scripts/codex-autopilot/autopilot-run-continuous.ps1` 作为控制面，并由脚本继续启动以下模型子进程：

- Ready Planner：脚本构造 Prompt、Schema 和超时，再调用 `codex exec`。
- Issue Executor：脚本通过 `autopilot-exec-issue.ps1` 启动独立 CLI 进程。
- Reviewer：脚本启动另一个模型进程并等待结构化结果。
- stall supervisor：通过 PID、工作区指纹、文件时间和超时判断模型是否仍有进展。

这种模式已经形成以下结构性问题：

1. 用户只能看到粗粒度 heartbeat，无法直接知道模型正在检索、修改、安装依赖、运行测试还是等待输出。
2. 桌面任务、runner、Planner、Executor、Reviewer 和测试命令分别拥有生命周期与超时，容易出现外层已超时、内层仍运行的错位。
3. 模型思考、真实长命令、依赖安装和 CLI 停滞都可能表现为“没有 durable 文件变化”，导致 stall/repair 误判。
4. 状态分散在 `state.json`、Issue checkpoint、events、进程树、worktree 和结果文件中，人工需要拼接事实才能判断当前阶段。
5. 为确认任务是否仍活跃，需要持续轮询 PID、CPU、文件时间和事件日志，观测成本本身已经影响执行效率。
6. 脚本同时承担安全门禁、状态机、模型编排、进程监督和 Git 收口，职责过宽，后续每次优化都会扩大控制面回归面。

## 2. 决策与边界

### 2.1 决策

- Codex 桌面端主线程成为 AutoPilot 的唯一业务编排者和最终裁决者。
- 普通实现由主线程直接完成；只有上下文隔离、独立证据、专业隔离、明确并行收益或长耗时卸载的净收益成立时才使用子智能体。
- 子智能体必须是短生命周期、单阶段、边界明确的任务，不得长期持有多轮连续迭代。
- PowerShell 保留为确定性原子工具，不再启动 Planner、Executor 或 Reviewer 模型进程。
- `state.json` 和 Issue checkpoint 继续作为恢复与一致性事实源，不再作为桌面端进度展示的主要载体。
- `events.jsonl` 继续保存可审计事件，但桌面端直接展示阶段、动作、证据和下一门禁，用户无需读取日志判断进度。

### 2.2 非目标

- 不建设常驻后台服务、Web 控制台、数据库状态机或消息队列。
- 不把 AutoPilot 改为关闭桌面端后仍能无人值守运行的守护进程。
- 不取消 Ready、知识图谱、stop/pause、fencing、权限/数据边界、Reviewer 或两阶段收口门禁。
- 不把 A–F 职责机械映射为六个子智能体。
- 不为追求速度降低高风险任务的证据与复核强度。
- 不在本主线修改业务功能、生产环境、生产数据库或自动 push 策略。
- 不在金丝雀成功前物理删除 legacy runner 和历史状态兼容逻辑。

## 3. 目标执行流程

```text
用户输入 启动迭代-N
  -> 桌面主线程读取规则、配置和控制面策略
  -> 原子 checkpoint：branch/status/flags/lock/fingerprint/active checkpoint
  -> 若存在活动 Issue：核验后从首个未完成阶段恢复
  -> 若无活动 Issue：Ready / KG / Candidate 有界选择
  -> 主线程直接拆解 Ready；必要时调用原子 Ready writer
  -> 主线程判断直接执行或短生命周期子智能体
  -> 原子 worktree / scope gate
  -> 主线程或子智能体实现
  -> 主线程调用原子验证入口
  -> 主线程审查；高风险或独立证据需要时派审查子智能体
  -> 必要时有 finding 约束的补修
  -> 最终复验
  -> 原子 closeout / commit / ledger / KG cursor read-back
  -> 检查 stop/pause/iteration limit/retrospective
  -> 进入下一 Issue 或总验收退出
```

桌面端不得通过单个长时间阻塞命令包住整轮流程。每个阶段由主线程显式调用原子工具，工具完成后立即获得结果并决定下一动作。

## 4. 职责划分

### 4.1 桌面主线程

- 解释用户触发词与授权边界。
- 读取 Ready、配置、控制面策略和 checkpoint。
- 决定任务范围、非目标、验收标准、执行顺序和风险等级。
- 判断直接执行、单派或多派是否具有净收益。
- 调用原子工具并读取结构化结果。
- 先做失败分类，再决定复跑、补修、暂停或阻塞。
- 在实现、验收、审计和上线裁决阶段切换时重新评估路由与证据强度。
- 负责最终验收、后续项零悬空、Git 收口和对用户汇报。

### 4.2 子智能体

仅允许承担以下任务：

- 独立 worktree 内的单一实现阶段。
- 上下文较大且与主线程决策可隔离的只读分析。
- 权限、安全、金额、租户、数据一致性等高风险 diff 的独立审查。
- 与实现者分离后具有明确证据价值的验收复核。

每个派工单必须包含角色边界、目标、范围、禁止事项、模型、推理强度、选择理由和验收输出。子智能体完成后必须返回：

- 修改文件或只读命中范围。
- 已执行命令及结果摘要。
- 未完成项和阻塞原因。
- 风险、finding 与建议下一动作。

禁止事项：

- 不得让单个子智能体长期执行多个 Ready Issue。
- 不得在同一 worktree 并发修改同一模块、业务域、权限或数据链。
- 子智能体超时、无回传或两次上下文不足时，不得继续复用同一悬挂线程硬等。
- 子智能体不得自行修改 AutoPilot 状态机、合并主分支或做最终通过裁决，除非派工范围明确授权相应原子动作。

### 4.3 PowerShell 原子工具

保留或形成以下原子能力；若现有函数可直接复用，不重复创建第二套入口：

- `autopilot-checkpoint`：输出 branch、status、stop/pause/enabled、run lock、指纹、活动 checkpoint。
- `autopilot-ready.ps1` / `ready-issue-writer`：解析、校验并写入有界 Ready，不调用模型。
- `autopilot-worktree.ps1`：创建、核验、读取和安全移交 worktree。
- `autopilot-transition.ps1`：唯一 Issue 阶段迁移写入器。
- `autopilot-verify.ps1`：运行验证并生成 Evidence。
- `test-failure-classifier`：根据退出码、命令契约和证据分类失败。
- `autopilot-review.ps1`：只负责审查请求/结果 Schema 与事实落盘，不启动 Reviewer 模型。
- `issue-closeout` / `autopilot-closeout.ps1`：生成正式事实、报告、ledger 和最终状态。
- `local-commit-closeout`：执行已通过全部前置的本地提交与快进合并。
- `autopilot-control-plane-fingerprint.ps1`：计算并读回控制面指纹。

任何原子工具都不得：

- 启动 `codex exec` 或其他模型进程。
- 依据自由文本自行选择下一阶段。
- 绕过 `autopilot-transition.ps1` 直接迁移活动 Issue。
- 在缺少 fencing token、范围门禁或 stop/pause checkpoint 时写状态或执行 Git 变更。

## 5. 状态、恢复与可观测性

### 5.1 状态事实

- 继续复用现有 `state.json`、Issue checkpoint、StageResult、Evidence 和 Closeout Record。
- 配置增加 `executionHost=desktop-native|cli-legacy`；初始阶段保留 legacy 回滚能力。
- checkpoint 增加或确认可表达 `executionHost`，但不引入第二套状态机。
- 现有 `runInstanceId + leaseEpoch + controlPlaneFingerprint` fencing 继续生效。
- 桌面任务中断后，下一次相同触发先读取活动 checkpoint；事实一致时只从首个未完成阶段恢复，事实不一致进入 quarantine。
- CPU、模型 heartbeat 和文件时间不再作为“智能体业务进度”；只有明确工具结果、diff、result、Evidence 或 checkpoint 迁移构成 durable progress。

### 5.2 桌面透明度契约

主线程至少在以下节点向用户提供一次有效更新：

- 任务选择完成。
- 即将修改代码。
- 实现完成并进入验证。
- 首次失败分类完成。
- 进入审查或补修。
- 最终复验与收口。
- 出现阻塞或需要用户决策。

推荐最小格式：

```text
当前阶段=IMPLEMENTING
当前动作=实现角色新建入口及权限负向测试
执行主体=主线程/子智能体
最新证据=已修改 4 个文件，尚未运行目标测试
下一门禁=目标测试 + 权限负向断言 + git diff --check
```

长耗时命令超过 60 秒没有返回时，更新必须说明具体命令、已知阶段、是否存在可中断点和下一判断条件；不得只输出“仍在运行”或无业务含义 heartbeat。

## 6. 实施阶段

### M0：安全边界与基线

任务：

1. 核对 branch、status、worktree、flags、run lock、state 和活动 Issue checkpoint。
2. 若存在有效活动 Issue，先安全收口、正式暂停或按恢复契约完成当前阶段。
3. 记录 legacy 基线：首次可见动作时间、Planner/Executor/Reviewer 调用数、嵌套进程数、stall/repair 次数、模型等待时间、验证时间和总耗时。
4. 固化“新旧编排器不得并发写状态或 Git”的回归测试。

出口门禁：主工作区与活动 worktree 归属清晰，不存在两个 APPLY 控制面同时持有有效 fencing token。

### M1：原子化脚本边界

任务：

1. 从 continuous runner、exec issue、review 和 closeout 中提取或暴露确定性原子入口。
2. 原子入口统一返回可校验结构，不以控制台自由文本路由。
3. `autopilot-review.ps1` 分离“Schema/落盘”与“调用模型”职责。
4. stall supervisor 仅监督真实命令进程；模型任务由桌面端或子智能体生命周期管理。
5. 增加“desktop-native 模式不得启动 codex 子进程”的测试。

出口门禁：所有原子入口可独立调用、幂等或具有明确幂等键；原有安全测试无回退。

### M2：桌面执行协议与兼容双轨

任务：

1. 在控制面策略中新增桌面执行宿主职责、子智能体边界和透明度契约。
2. 在动态配置中增加 `executionHost` 与 legacy fallback 开关；不复制模型或超时事实到长期规则。
3. 调整 `AGENTS.override.md`、`AGENTS.md` 的精确触发协议：`启动迭代-N` 由桌面主线程执行本计划流程，不再优先调用 continuous runner。
4. legacy runner 在 desktop-native 模式下只返回兼容提示或 dry-run 状态，不再启动模型子进程。
5. 保证同一 Ready、checkpoint、StageResult、Evidence 和 closeout Schema 可被两种宿主读取。

出口门禁：desktop-native 与 cli-legacy 不可同时 APPLY；切换宿主不破坏历史状态读取与恢复。

### M3：桌面主线程与子智能体执行闭环

任务：

1. 实现主线程直接完成 Ready 语义核验，不再调用 Ready Planner CLI。
2. 实现主线程直接修改或按净收益创建短生命周期子智能体。
3. 实现主线程直接审查或按风险派独立审查子智能体。
4. 子智能体回传结果必须由主线程核对当前 diff、范围和 Evidence，不直接信任口头结论。
5. 补修必须绑定明确 finding、Issue、diff 和验收标准；工具故障不得触发业务 repair。
6. 桌面中断后通过 durable checkpoint 恢复，不重新派发已完成实现阶段。

出口门禁：一个 fixture Issue 能完成选择、实现、验证、审查、收口和恢复，全程没有嵌套 `codex exec`。

### M4：自动化回归与真实单 Issue 金丝雀

任务：

1. 运行控制面、StageResult、transition、recovery、scope、review routing、Evidence、closeout、评分和回顾相关回归。
2. 验证 stop/pause、N 上限、Ready 为空、图谱异常、环境前置和工具配置失败分支。
3. 验证桌面主线程中断后恢复、子智能体超时后收回、审查工具故障和真实 `NEEDS_REPAIR` 隔离。
4. 更新控制面指纹覆盖清单。
5. 用户明确执行一次新指纹 `启动迭代-1`。
6. 读回 implementation commit、closeout commit、ledger、state、评分、知识图谱 Git cursor 和 flags。

出口门禁：单 Issue 金丝雀成功登记；N>1/无界门禁才可解除。

### M5：默认切换与 legacy 退役门

任务：

1. 将 `desktop-native` 设为默认执行宿主。
2. 将 continuous runner 和 exec issue 标记为 legacy/fallback-only。
3. 更新文档、快速开始、负责人流程和故障排查入口。
4. 使用前端、后端及权限/数据边界代表性任务验证桌面原生路径。
5. 在代表性样本证据充分前保留 legacy 回滚入口；物理删除另行裁决，不与默认切换捆绑。

出口门禁：常规 AutoPilot 不再生成嵌套模型 CLI 进程；legacy 仅作为显式回滚选择。

## 7. 预计文件范围

### 稳定规则与策略

- Modify: `AGENTS.override.md`
- Modify: `AGENTS.md`
- Modify: `plugins/cgc-pms-autopilot/references/control-plane-policy.md`
- Create: `plugins/cgc-pms-autopilot/references/desktop-execution-policy.md`
- Modify: `plugins/cgc-pms-autopilot/references/owner-boundary.md`
- Modify: `plugins/cgc-pms-autopilot/references/rerun-policy.md`

### 配置、Schema 与控制面

- Modify: `scripts/codex-autopilot/codex-autopilot.config.json`
- Modify: `scripts/codex-autopilot/autopilot-run-continuous.ps1`
- Modify: `scripts/codex-autopilot/autopilot-run-coordinator.ps1`
- Modify: `scripts/codex-autopilot/autopilot-exec-issue.ps1`
- Modify: `scripts/codex-autopilot/autopilot-executor-supervisor.ps1`
- Modify: `scripts/codex-autopilot/autopilot-review.ps1`
- Modify: `scripts/codex-autopilot/autopilot-transition.ps1`
- Modify: `scripts/codex-autopilot/autopilot-issue-checkpoint.ps1`
- Modify: `scripts/codex-autopilot/autopilot-state.ps1`
- Modify: `scripts/codex-autopilot/autopilot-control-plane-fingerprint.ps1`
- Create or expose: `scripts/codex-autopilot/autopilot-checkpoint.ps1`
- Create or expose: `scripts/codex-autopilot/ready-issue-writer.ps1`
- Create or expose: `scripts/codex-autopilot/test-failure-classifier.ps1`
- Create or expose: `scripts/codex-autopilot/issue-closeout.ps1`
- Create or expose: `scripts/codex-autopilot/local-commit-closeout.ps1`
- Modify only if required: `plugins/cgc-pms-autopilot/schemas/loop-state.schema.json`
- Modify only if required: `plugins/cgc-pms-autopilot/schemas/issue-checkpoint.schema.json`

“Create or expose”表示优先复用现有函数并提供薄入口；若现有脚本已满足原子契约，不新建重复实现。

### 测试与正式报告

- Create: `scripts/codex-autopilot/tests/test-desktop-execution-host.ps1`
- Create: `scripts/codex-autopilot/tests/test-no-nested-codex.ps1`
- Create: `scripts/codex-autopilot/tests/test-desktop-checkpoint-recovery.ps1`
- Create: `scripts/codex-autopilot/tests/test-desktop-subagent-boundary.ps1`
- Modify: 既有 control plane、runner compatibility、transition、recovery、review routing、closeout 与 canary 测试
- Create: `docs/quality/mainline-46-codex-desktop-native-autopilot-acceptance-<date>.md`

## 8. 验收矩阵

| 场景 | 验收标准 |
| --- | --- |
| 启动迭代-N | 桌面主线程直接进入 checkpoint，不启动 continuous runner 模型链 |
| Planner | 主线程直接完成有界语义核验；不得出现 Planner `codex exec` 子进程 |
| 普通实现 | 主线程直接修改；不为机械任务强制创建子智能体 |
| 上下文隔离 | 子智能体只处理单阶段、独立 worktree、明确范围任务 |
| 独立审查 | finding 绑定 Issue/diff/文件/证据；无 finding 不触发 repair |
| 工具故障 | 分类为 `tool_config` 或 `environment_prereq`，不得伪装为业务失败 |
| stall | 模型任务不再由文件时间推断 stall；真实命令可按命令身份与预算监督 |
| stop/pause | 所有既有 checkpoint 生效；已启动 Issue 只安全收口，不启动下一任务 |
| 中断恢复 | durable checkpoint 一致时从首个未完成阶段继续，不重派已完成实现 |
| 范围门禁 | allowed/forbidden、最大文件数和 Git diff 继续 fail-close |
| 权限/安全 | 正负样本和独立复核强度不低于 legacy 路径 |
| 验证 | 目标测试、关键静态核对、`git diff --check` 和适用运行态验收通过 |
| Closeout | 两阶段提交、ledger、state、评分和 KG cursor 全部写后读回 |
| 透明度 | 每个阶段显示当前动作、执行主体、最新证据和下一门禁 |
| 进度更新 | 超过 60 秒的等待必须映射到具体命令或具体子智能体任务 |
| 进程边界 | desktop-native 流程的嵌套模型 CLI 调用数为 0 |
| 金丝雀 | 新指纹真实 `启动迭代-1` 成功后才允许 N>1/无界 |

## 9. 效率指标

本主线只承诺消除编排开销，不伪称可以消除真实构建、测试、浏览器验收或依赖下载耗时。

必须采集并比较：

- `timeToFirstVisibleActionSeconds`：用户触发到首次具体动作的时间，目标不高于 10 秒。
- `nestedModelCliInvocationCount`：desktop-native 路径目标为 0。
- `plannerInvocationCount`：不再存在独立 Planner 模型进程；语义决策由主线程完成一次。
- `executorInvocationCount`：按主线程直接执行或短生命周期子智能体真实计数，不按工具命令扇出。
- `reviewerInvocationCount`：仅在风险或独立证据需要时发生。
- `unattributedIdleSeconds`：无法归因到具体命令、子智能体或用户决策的等待目标为 0。
- `stallRepairCount`：模型文件时间误判产生的 repair 目标为 0。
- `contextBaseBuildCount` 与 `contextDeltaBuildCount`：恢复不得重复构造已有效上下文。
- `validationExecutedCount` 与 `validationReusedCount`：继续遵守 Evidence v2 复用边界。
- 端到端总耗时：只与相同 Ready、相同验证范围的 legacy 基线比较，不设置脱离机器与测试规模的固定分钟门槛。

## 10. 风险与控制

### 风险 1：桌面端关闭后不能后台持续执行

- 控制：明确将桌面原生 AutoPilot 定义为“桌面任务存活期间持续执行”。
- 恢复：依赖 durable checkpoint，而不是依赖后台进程继续运行。
- 裁决：这是有意的产品语义调整，不伪装为无人值守守护进程。

### 风险 2：子智能体共享文件系统导致冲突

- 控制：实现型子智能体必须使用独立 worktree；同一业务域、权限链或数据链串行。
- 门禁：派工前核对 branch/status/worktree；回收后主线程核对 diff 和范围。

### 风险 3：桌面主线程上下文压缩或中断

- 控制：Ready、checkpoint、diff、Evidence 和 StageResult 是恢复事实；会话描述不是唯一事实源。
- 门禁：恢复前重新核验 base、worktree、branch、范围、diff、fencing 和指纹。

### 风险 4：新旧执行宿主并发

- 控制：同一 run lock 和 fencing token 只允许一个 APPLY 宿主。
- 门禁：切换宿主前必须处于安全任务边界或正式暂停状态。

### 风险 5：为了透明度输出过量日志

- 控制：只在阶段变化、真实证据变化、阻塞或用户决策点更新。
- 禁止：不展示纯 heartbeat、计时命令或无业务含义的轮询输出。

### 风险 6：原子脚本重新演变成第二套编排器

- 控制：原子脚本不得选择下一阶段、调用模型或形成内部循环。
- 测试：加入“无 nested codex”“无脚本自由文本路由”“transition writer 唯一迁移”的结构测试。

## 11. 回滚方案

1. 迁移期间保留 `cli-legacy` 执行宿主和既有状态读取能力。
2. Schema 变更优先采用向后兼容的可选字段；不原地破坏历史 checkpoint。
3. desktop-native 失败时，只能在任务安全边界切回 legacy；不得由两个宿主同时接管活动 Issue。
4. 已有 worktree、commit、Evidence 和 ledger 不删除，按恢复策略读回。
5. 回滚行为本身会改变控制面指纹；回滚后 N>1/无界仍需重新完成单 Issue 金丝雀。
6. legacy 物理删除必须另行裁决；默认切换与删除旧代码分开进行。

## 12. 实施顺序与授权门

推荐顺序：

1. M0 只读基线与安全边界。
2. M1 原子化，不改变默认执行宿主。
3. M2 增加 desktop-native 双轨和稳定策略。
4. M3 完成桌面主线程/子智能体闭环。
5. M4 自动化回归与真实 `启动迭代-1`。
6. M5 切换默认宿主，legacy 保留为显式回滚。

本计划的写入不等于实施授权。进入 M0 只读盘点无需修改授权；任何代码、配置、规则、文档、Git 或运行环境变更前，必须获得用户明确的“执行/实现/修改/运行测试/提交”等授权，并重新核对 branch 与 status。

## 13. 正式收口要求

- 正式交付物：控制面策略、动态配置、原子入口、桌面执行协议、自动化测试、计划和验收报告。
- 验收证据：自动化矩阵、无嵌套模型 CLI 证明、恢复测试、权限/数据边界证据、真实单 Issue 金丝雀读回。
- 临时产物：run 日志、原始模型输出、截图、缓存和构建产物不进入正式交付。
- Git：只允许本地提交；未经用户另行明确授权不得 push。
- 后续项：每个发现项只能归入本轮修复并复验、超出范围并正式承接、证据不足或无价值关闭。
- 收口统计：必须给出新增后续项、关闭后续项和后续项净变化；存在口头悬空项时不得判定通过。
- 最终裁决：只有自动化回归、真实单 Issue 金丝雀、ledger/state/KG cursor/flags 读回全部通过，才允许判定 desktop-native 默认执行宿主可用。

# 第43条主线：AutoPilot 控制面模块化与状态机瘦身验收报告

**验收日期:** 2026-07-14
**验收分支:** `develop/1.5`
**实施基线:** `880c8b6d3`
**结论:** 自动化结构改造与回归通过；主线最终裁决为“需要确认”，阻塞项仅为用户尚未明确启动同指纹 `启动迭代-1` 真实金丝雀。金丝雀通过前禁止 N>1 或无界放量。

## 1. 正式交付物

- 连续 Runner 入口由1725行缩减为20行，只保留参数、PowerShell 7门禁、模块加载与协调器调用。
- 新增 RuntimeContext、run coordinator、coordinator support、Issue lifecycle、Executor supervisor、StageResult 与 transition writer 模块。
- RuntimeContext统一配置、模式、base branch、路径和控制面指纹初始化；Issue生命周期所有退出路径返回并校验 `StageResult`。
- 活动 Issue checkpoint 的运行时阶段迁移只允许经 `Move-AutopilotIssuePhase`；底层 `Set-AutopilotIssueCheckpointPhase` 在运行时代码中仅由 transition writer 调用。
- transition writer验证合法阶段边，并对 `transitionId + generation` 做写后读回；既有 checkpoint 原子存储继续负责 fencing token 与控制面指纹校验。
- 原1158行连续 Runner场景矩阵迁移为兼容测试；旧测试入口缩为21行聚合器，并新增执行模式、锁/fencing、恢复、semantic stall、Reviewer、closeout、StageResult和transition独立主题测试。
- 控制面指纹已覆盖新增行为模块和测试契约；项目规则、主线Skill、Current Focus和项目地图已同步更新。

## 2. 行为与静态证据

| 证据 | 结果 |
| --- | --- |
| PowerShell解析 | 新增/修改控制面脚本通过PowerShell 7解析 |
| 控制面自检 | `test-control-plane.ps1`通过 |
| 旧完整兼容矩阵 | `tests/test-runner-compatibility.ps1`通过 |
| StageResult契约 | 成功、repair、Reviewer tool_config和非法结果拒绝均通过 |
| transition writer | 合法迁移、单调generation、transitionId读回和非法边拒绝均通过 |
| 恢复 | `test-recovery.ps1`、`test-phase-recovery.ps1`通过 |
| semantic stall | CPU/MCP活动不算语义进度，持久证据变化可推进；主题测试通过 |
| Reviewer/repair | tool_config、完整needs_repair和失败路由主题测试通过 |
| closeout | closeout及完成计数一致性主题测试通过 |
| 评分/回顾 | `test-task-scoring.ps1`、`test-retrospective-cycle.ps1`通过 |
| checkpoint写入口静态检查 | 运行时只有transition writer调用底层phase setter |
| 最终聚合回归 | 控制面、兼容矩阵、全部主题、评分、回顾及`git diff --check`连续通过（205.8秒） |
| 启动readiness复验 | 模块化Runner能力与拆分测试覆盖均可被识别；15项通过、1项Ready为空警告、0项失败 |

完整兼容矩阵在纯提取后及RuntimeContext/StageResult/transition改造后均通过。主题聚合首次运行暴露 `test-executor-stall.ps1` 仍只读取旧入口文本；后续真实启动前readiness又暴露能力与覆盖门禁仍只扫描薄入口/聚合器。两者均归类为测试契约路径遗漏，已改为组合扫描职责模块并由控制面自检锁定，不属于业务代码失败。

## 3. 结构裁决

- Runner低于计划观察值并非机械压缩：入口职责已经完整迁出，继续保留200～350行反而会重复协调逻辑。
- coordinator与Issue lifecycle仍保留较长的顺序编排。该顺序包含checkpoint、恢复、验证、Reviewer、两阶段提交、ledger、state和知识图谱读回的中断窗口；本轮不为满足行数偏好再次切碎共享局部状态。专业事实执行仍由既有verify、review、closeout、recover模块承担，StageResult与transition writer已提供稳定边界。
- 未创建第二套lock、recovery、Git、score或checkpoint存储实现；`autopilot-recover.ps1`只请求transition迁移。

## 4. 失败分类与剩余风险

- 已修复：静态测试仍绑定旧巨型Runner文本，分类为`test_config`。
- 已修复：readiness能力与覆盖门禁仍绑定旧单文件布局，分类为`tool_config / modular scan`；修复后15项通过、0项失败。
- 已修复：普通phase recovery fixture继承不确定换行策略，分类为`tool_config / fixture isolation`；现已固定本地`core.autocrlf=false`，CRLF warning专项继续由native-command语义测试独立覆盖。
- 未执行：真实业务Ready、真实implementationCommit/closeoutCommit、closeout ledger、state、知识图谱Git cursor与金丝雀指纹联合读回。
- 剩余风险：自动化可证明行为兼容和状态迁移不变，但无法替代真实单Issue的跨进程、Git和图谱联合收口证据。

## 5. 非阻塞问题零悬空

- 本轮修复并复验：3项（测试契约路径、readiness模块扫描、普通fixture换行隔离）。
- 超出当前范围并正式承接：1项（真实单Issue金丝雀，唯一承接载体为`docs/backlog/current-focus.md`，解除条件为用户明确执行`启动迭代-1`且六类读回一致）。
- 证据不足或无明确价值而关闭：1项（继续按100行阈值机械拆分协调器/生命周期；现有职责边界和回归证据已满足风险目标，继续拆分会增加迁移风险）。
- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 悬空问题：0。

## 6. 最终门禁

- 自动化结构验收：通过，非阻塞。
- 真实金丝雀：未执行，阻塞主线最终“通过”与N>1/无界放量。
- 生产发布：本主线不涉及，未执行。
- Git提交/push：用户本轮未明确授权，未执行。
- 收口运行态：`stop.flag=false`、`pause.flag=false`、`enabled.flag=false`；现存state为历史`LIMIT_REACHED`，本轮未启动AutoPilot、未派发Ready，也未改写正式运行态。

金丝雀验收标准：同一控制面指纹下，由用户明确启动`启动迭代-1`；Issue产生不同的implementationCommit与closeoutCommit，ledger/state/知识图谱Git cursor/金丝雀指纹全部读回一致，且无stop/pause门禁冲突。满足后可把本报告结论更新为“通过”。

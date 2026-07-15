# AutoPilot 任务评分 v2 正式启用验收报告

## 1. 裁决

- 结论：通过。
- 阻塞：无实施阻塞；N>1/无界连续迭代仍受控制面单任务金丝雀门限制。
- 生效版本：`autopilot-task-score/v2`。
- 权重：35/25/20/10/10，10分维度为 `taskExecutionEfficiency`。
- 生效边界：批准配置提交后的下一项新实施型 Ready Issue。
- 运行边界：未启动真实 Ready，`pause.flag` 保持存在，未连接生产、未发布、未 push。

## 2. 正式交付物

- `scripts/codex-autopilot/codex-autopilot.config.json`：v2 active、`APPROVED`、批准时间、生效边界和批准来源。
- `scripts/codex-autopilot/autopilot-task-score.ps1`：正式 v2 评分生成与 candidate shadow 分离。
- `scripts/codex-autopilot/autopilot-closeout.ps1`、`autopilot-run-continuous.ps1`：按 activeVersion 只生成一个正式评分版本，并绑定两阶段提交与回顾计数。
- `scripts/codex-autopilot/autopilot-retrospective.ps1`：同一20任务周期内分别聚合 v1 `cycleEfficiency` 与 v2 `taskExecutionEfficiency`。
- `plugins/cgc-pms-autopilot/schemas/task-score.schema.json`、`retrospective.schema.json`：正式 v2 与混合版本周期报告契约。
- `docs/iterations/AutoPilot任务评分v2批准决策-2026-07-13.md`：用户批准事实、权重、生效边界和兼容边界。

## 3. 验收证据

以下测试均通过：

- `test-task-scoring.ps1`
- `test-closeout.ps1`
- `test-recovery.ps1`
- `test-phase-recovery.ps1`
- `test-state-machine.ps1`
- `test-review-repair.ps1`
- `test-control-plane-fingerprint.ps1`
- `test-control-plane.ps1`
- `test-retrospective-cycle.ps1`
- `test-continuous-runner.ps1`

专项断言覆盖：v2 正式激活、候选版本不可冒充 active、10/5/0效率分、正式分与 shadow 幂等键隔离、两阶段 closeout、恢复后评分读回、单任务单版本计数、现存 v1 周期任务保留，以及混合周期按版本分别聚合效率维度。

## 4. 工具与交叉核验

- CodeGraph 两次未召回目标 PowerShell/JSON 控制链并误命中业务任务模型，归类为工具召回不足。
- 精确 `rg` 补查了 activeVersion、candidate、评分生成、closeout、回顾与 schema 调用点。
- 只读 codebase-memory 命中 `Test-AutopilotTaskScoringActive`、`Complete-AutopilotIssueCloseout`、`Add-AutopilotReviewCycleIssue` 及 runner 登记链；与当前文件和测试结果交叉一致。

## 5. 问题处置与剩余风险

- 本轮发现“当前周期已有 v1 任务时，旧回顾器固定读取 `cycleEfficiency`”的问题；已本轮修复并复验，没有转为悬空后续项。
- 新增后续项：0。
- 关闭后续项：0。
- 后续项净变化：0。
- 已正式承接的既有放量前置：控制面指纹变化后，必须由用户明确执行并成功收口一次 `启动迭代-1`；该前置继续由 `docs/backlog/current-focus.md` 唯一承接。

## 6. 回滚

回退本次 v2 激活提交即可恢复 v1 active 配置；不得删除或重算既有 v1/v2 评分事实。回滚后保持 `pause.flag`，重新执行评分、closeout、回顾周期和连续运行器测试，不涉及数据库或业务数据回滚。

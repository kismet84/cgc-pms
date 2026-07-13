# 第42条主线：AutoPilot 跨 Run 阶段恢复与端到端效率计分修复验收报告

## 结论

- 实施结论：通过。
- 阻塞判断：当前实现与自动化验收无阻塞；独立 Reviewer 三轮提出的6项当前 diff 缺陷均已本轮修复并复验。
- 放量边界：当前 `pause.flag=true`，本轮未启动真实 Ready、未执行真实单任务金丝雀。控制面指纹变化后，N>1 或无界执行仍由 `CONTROL_PLANE_CANARY_REQUIRED` 阻断；必须由用户明确启动一次 `启动迭代-1`，并在 closeout ledger、state、知识图谱 Git cursor 全部读回后，才允许同指纹放量。
- 评分边界：正式 active 仍为 `autopilot-task-score/v1`；`autopilot-task-score/v2-candidate` 保持 disabled / `NEEDS_CONFIRMATION`，不进入正式20任务周期。

## 正式交付物

- 原子 Issue phase checkpoint、JSON Schema 与示例，绑定 Ready 内容哈希、base、worktree/branch、范围、diff/evidence、阶段产物和端到端指标。
- 恢复决策矩阵：从 validation、Reviewer、closeout、merge/register/finalize 的首个未完成阶段继续；证据不一致进入 `QUARANTINE` 并保留现场。
- closeout commit 先 checkpoint 后 merge；Ready 已变 Done 且主分支已包含 closeout commit 时，可从保留 worktree 重建合同并幂等完成 ledger/state/图谱登记。
- Reviewer `tool_blocked` 隔离、一次自动重试、二次暂停、绑定同一 Issue/diff 的人工结构化 PASS 接管，以及 `needs_repair` finding 完整性门禁。
- v2 disabled shadow：35/25/20/10/10候选口径中的 `taskExecutionEfficiency=10` 读取 implementation/validation/review/repair/closeout dispatch、`runResumeCount`、阶段重启、人工恢复、工具阻塞、环境重试和重复派发拦截；最终 shadow 写入独立本地候选账本，不污染 v1 正式 closeout ledger。
- 控制面指纹与单任务金丝雀门；指纹覆盖 runner、recovery、checkpoint、Reviewer、closeout、state、评分、schemas 和行为配置本身。
- 项目规则、Owner Skill、插件 Skill、重跑策略、评分规范、Current Focus、项目地图和演进决策同步更新。

## 恢复与失败分类矩阵

| 场景 | 裁决 |
| --- | --- |
| implementation 完成、验证未齐 | `RESUME_VALIDATION`，不重派 implementation |
| validation 完成、Reviewer 未齐 | `RESUME_REVIEW`，复用同一 diff/evidence |
| Reviewer Windows sandbox/tool_config | 只重试 Reviewer；第二次失败暂停，不进入业务 repair |
| 人工 Reviewer 提供绑定 PASS | 原子转为 `REVIEWED`，记录人工恢复，从 closeout 继续 |
| closeout commit 未合并 | `RESUME_MERGE_AND_REGISTER` |
| closeout 已合并但登记未完成 | `RESUME_FINALIZE`，归一化 Done 合同并完成 ledger/state/图谱读回 |
| 一次环境前置验证失败 | 记录 `environmentRetryCount=1`，保留实现并仅重跑 validation，效率维度为5分 |
| 第二次环境失败 | `PAUSE_ENVIRONMENT_RETRY_EXHAUSTED`，不无限重试 |
| Ready/base/worktree/branch/diff/evidence 不一致 | `QUARANTINE`，保留现场，禁止重跑 B/C |

## 验收证据

以下专项与回归均通过：

- `test-phase-recovery.ps1`：跨 Run 恢复矩阵、重复 implementation 阻断、人工 Reviewer PASS、环境重试、合并后崩溃恢复与 Ready 变化隔离。
- `test-recovery.ps1`：运行锁、未知残留提交隔离、closeout 幂等键、候选 shadow 账本幂等覆盖。
- `test-state-machine.ps1`：state 扩展、迁移和读回。
- `test-review-repair.ps1`：tool_config 隔离、diff/Issue 绑定、finding 完整性和 repair 预算。
- `test-task-scoring.ps1`：v1 回归、v2 10/5/0分、工具重试5分、环境重试5分、跨 Run 返工0分。
- `test-closeout.ps1`：两个不同提交、checkpoint-ready closeout merge、恢复合并与幂等重试。
- `test-control-plane-fingerprint.ps1`、`test-control-plane.ps1`：配置变更使指纹失效、N>1门禁与控制面契约。
- `test-continuous-runner.ps1`：完整连续 runner 回归通过。
- `test-retrospective-cycle.ps1`、`test-executor-stall.ps1`：20任务回顾与 durable stall 回归通过。
- `test-evidence-verification.ps1`、`test-final-scope-gate.ps1`、`test-worktree-untracked-scope.ps1`：证据新鲜度和最终范围门禁通过。
- `plugins/cgc-pms-autopilot/scripts/test-autopilot-loop-runner.ps1`：插件 runner 回归通过。
- 修改 PowerShell AST 解析、修改 JSON 解析和 `git diff --check` 均通过。

测试均使用临时 fixture；未解除真实 `pause.flag`，未连接生产、未发布、未 push。

## 独立 Reviewer 闭环

首次独立复核发现6项当前 diff 阻塞：合并后崩溃不可恢复、指纹未覆盖配置、checkpoint 退役顺序不安全、人工 PASS 无法接管、repair finding 不完整、效率证据未覆盖真实跨 Run 路径。二次复核确认前5项关闭，并指出环境重试缺少独立计数。第三次复核确认 `environmentRetryCount`、一次恢复/二次暂停和5分口径全部关闭，最终裁决为通过，允许本地提交。

本轮路由回看：跨模块实现由主线程直接承担，避免把长生命周期控制面改造交给悬挂线程；正式验收阶段使用短生命周期只读 Reviewer，独立证据收益高于派工成本。Reviewer 工具不支持显式模型/推理参数，真实记录为 `model=unsupported`、`thinking=unsupported`。首次6项发现证明正式控制面审查需要较高证据强度；固定测试回传和格式核对可由主线程低成本直接完成，不需要统一高档派工。

## 图谱与跨文件交叉核验

- CodeGraph 查询目的：核对 recovery → runner → Reviewer/score → closeout/state/canary 的调用链和影响面。结果返回185个符号/40个文件，但未召回预期 PowerShell 主链，混入无关业务模块，归类为“工具召回不足”，未据此判断代码不存在。
- `codebase-memory-mcp` 首次使用项目名 `cgc-pms` 返回未索引，归类为 `tool_config`；按返回的实际只读项目名 `D-projects-test-cgc-pms` 重试后，命中 recovery、runner、Reviewer、评分、closeout、status 和专项测试消费者。
- 最终事实以当前分支 diff、PowerShell/JSON 解析、专项测试、完整 runner 回归和独立 Reviewer 结论为准。

## 失败分类与本轮修正

- 早期连续 runner 失败来自旧测试 fixture 未提供新的 durable closeout 结果字段，分类为测试夹具配置问题；补齐 fixture 后完整回归通过。
- Done 合同归一化初版正则吞掉行尾空白，使合并后恢复测试进入 quarantine，属于真实实现缺陷；改为不消费换行的精确替换后故障注入通过。
- 独立 Reviewer 提出的6项均与当前目标和当前 diff 风险直接相关，全部本轮修复，没有以“非阻塞建议”转存量。

## 后续项零悬空

- 本轮审查发现：6项；本轮修复并复验：6项；证据不足关闭：0项。
- 正式新增后续项：0。
- 正式关闭后续项：0。
- 正式后续项净变化：0。
- 已正式承接的既有决策前置：v2 正式版本名、35/25/20/10/10完整权重和生效时间仍为 `NEEDS_CONFIRMATION`；真实 `启动迭代-1` 金丝雀仍需用户明确启动。两项均记录在 `docs/backlog/current-focus.md`，不是会话悬空备注。

## 剩余风险与回滚

- 剩余风险1：自动化故障注入已通过，但真实控制面指纹尚未执行用户启动的单任务金丝雀。它阻断 N>1/无界放量，不阻断本次代码验收；解除条件是用户明确执行 `启动迭代-1` 且 ledger/state/KG cursor 读回成功。
- 剩余风险2：v2 仍是 disabled candidate，不具备正式计分效力。解除条件是用户明确批准正式 `scoringVersion`、五维权重和生效时间；批准前 v1 仍是唯一正式评分。
- 最小回滚：回退第42条主线提交并继续保留 `pause.flag`；不回退 v1 正式评分、现有业务提交或 durable stall 修复。无数据库迁移、生产数据或远端 Git 回滚。

## 最终裁决

- 实现与自动化验收：通过。
- 阻塞：无当前实现阻塞。
- 是否允许 N>1/无界运行：不允许，需先完成用户明确启动的单任务金丝雀。
- 是否可本地提交：允许。
- 是否可 push/发布生产：未授权且未执行。

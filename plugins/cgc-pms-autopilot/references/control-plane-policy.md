# AutoPilot Control Plane Policy

Policy-Version: 2
Status: active

本文件是会改变 AutoPilot 调度、恢复、审查、评分与收口行为的稳定策略契约。当前激活版本、权重、阈值、模型、推理强度和超时等动态事实必须从 `scripts/codex-autopilot/codex-autopilot.config.json`、对应 Schema 与批准来源读取，不在本文件或负责人 Skill 中复制。

## 执行与边界

- 默认执行宿主由配置的 `executionHost` 决定；生产配置固定为 `desktop-native`，详细契约见 `desktop-execution-policy.md`。
- 桌面原生宿主由当前 Codex 桌面主线程直接编排，PowerShell 仅执行确定性原子动作；不得由 runner 启动嵌套 Planner、Executor 或 Reviewer 模型进程。
- `cli-legacy` 只作为显式兼容与紧急回退路径，不能成为 `desktop-native` 的静默降级。
- 控制面固定使用 PowerShell 7；缺失时按 `tool_config` fail-close。
- APPLY 派发、状态落盘和 Git 变更前必须核验 run lock、fencing token 与控制面指纹。
- stop/pause 在每个关键 checkpoint 生效；已启动 Issue 只允许安全收口，不得据此启动下一任务。
- 补货只能消费有界候选。权威 ReadySpec 通过确定性校验时可直接生成；否则调用有硬超时和心跳的语义 Planner。
- Planner 必须逐候选返回 CREATED、REJECTED 或 BLOCKED；只有 CREATED 可写入 Ready，零 Ready 是合法终态。
- 补货产生 Ready 后可在同一 run 内重新经过 checkpoint 并继续选择；不得绕过迭代上限、回顾门禁或金丝雀门禁。

## 状态、恢复与审查

- 阶段处理器只返回通过校验的 StageResult；RUN 与 ISSUE 作用域必须显式区分。
- 活动 Issue 的阶段迁移只允许经 transition writer 完成，并核验合法边、fencing、控制面指纹以及 `transitionId + generation` 写后读回。
- durable checkpoint 的 Ready 内容、执行基线、worktree/branch、范围、diff 和 evidence 一致时，才允许从首个未完成阶段恢复；不一致进入 quarantine 并保留现场。
- 候选取证提交与执行基线提交是不同事实，必须分别记录，禁止用新基线覆盖候选证据来源。
- Reviewer `tool_config` 与业务 `NEEDS_REPAIR` 隔离；工具重试耗尽后暂停，不得触发实现补修。

## 评分、回顾与金丝雀

- 评分只对通过全部硬门禁且完成两阶段提交的实施型 Ready Issue 生效；低分不改变 DONE 裁决。
- active 评分版本、批准状态、权重、生效点和回顾阈值均从配置及 `approvalSource` 交叉核验。
- 回顾完成前保持暂停；报告、唯一问题事实源、知识图谱游标和稳定 Episode 未全部读回时不得清零。
- 本策略、控制面脚本、配置或 Schema 的行为性变化必须改变控制面指纹；新指纹进入多任务或无界执行前必须通过用户明确启动的单 Issue 金丝雀。

## 事实来源

- 动态配置：`scripts/codex-autopilot/codex-autopilot.config.json`
- 结构契约：`plugins/cgc-pms-autopilot/schemas/**`
- 状态迁移：`scripts/codex-autopilot/autopilot-transition.ps1`
- 恢复规则：`plugins/cgc-pms-autopilot/references/rerun-policy.md`
- 角色边界：`plugins/cgc-pms-autopilot/references/owner-boundary.md`
- 桌面执行宿主：`plugins/cgc-pms-autopilot/references/desktop-execution-policy.md`

## 上下文、证据与收口事实链

- 模型输入由不可变 Context Base 与逐阶段 Context Delta 组成；恢复时必须同时核验 Ready、base commit、候选取证提交、控制面策略和当前 diff，禁止用后续阶段覆盖基础上下文。
- Evidence v2 只允许在同一 Issue、同一命令、同一上下文身份、同一 diff 和相同环境指纹下按类别复用；静态廉价检查、集成测试和浏览器验收仍按策略重新执行，v1 证据只读兼容但不得复用。
- 正式报告的自动事实区只由提交前 `PreCloseoutFacts` 确定性投影，不得包含报告自身的 closeout commit、report hash、result hash 或 `REGISTERED` 状态；人工裁决区不得被投影覆盖。
- 合并后先冻结 final result snapshot，再幂等登记 Closeout Record v2；同 key 不同 payload 必须进入 `integrity_conflict`，ledger 写后读回成功前不得迁移到 `REGISTERED`。
- 历史 `key + registeredAt` 记录按 v1 只读，不原地升级，也不进入 v2 最近收口指标样本。

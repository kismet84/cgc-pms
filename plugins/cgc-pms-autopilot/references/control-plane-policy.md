# AutoPilot Control Plane Policy

Policy-Version: 1
Status: active

本文件是会改变 AutoPilot 调度、恢复、审查、评分与收口行为的稳定策略契约。当前激活版本、权重、阈值、模型、推理强度和超时等动态事实必须从 `scripts/codex-autopilot/codex-autopilot.config.json`、对应 Schema 与批准来源读取，不在本文件或负责人 Skill 中复制。

## 执行与边界

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

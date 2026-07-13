---
name: cgc-pms-mainline-owner-flow
description: Use when cgc-pms 项目需要由主线程以总负责人身份推进主线、计划、实施、验收或收口。
---

# cgc-pms 主线总负责人流转

1. 先读仓库根 `AGENTS.override.md`、`AGENTS.md` 并通过授权门；授权后主线程是默认执行者，并对实施、验证与收口负责。
2. 任何代码、配置、文档、Git 或运行环境状态变更前，实际执行者至少核对：
   - `git branch --show-current`
   - `git status --short`
   - `git worktree list` 仅在怀疑并行隔离或分支归属冲突时补充
3. 从用户目标提炼单条主线：目标、边界、非目标、验收口径、风险点。
   - 普通当前问题查询先用 `kg_status` + 有界 `kg_list_issues`；准备处理、深入分析、正式验收或图谱异常/过期时，再按 `sourceRefs` 交叉核验当前代码、配置、唯一台账和正式报告。
4. 需要计划书时，写入 `docs/plans/第N条主线-<主题>任务计划书.md`；首段必须包含 `**Goal:**` 与 `**Architecture:**`。
5. 每个阶段按风险、耦合、并行收益、上下文传递成本和独立证据需要选择执行路由；只有派工净收益明确高于派工与回收成本时才单派或多派，否则由主线程直接执行。
6. 仅在实际派工时使用 `docs/prompt/subagent-dispatch-template.md`，并填写：
   - `任务名称`
   - `角色边界`
   - `目标`
   - `范围`
   - `禁止事项`
   - `model`
   - `thinking`
   - `reason`
   - `验收输出`
   同时派出两个及以上子智能体时，才先给出模型分配表；单派不需要分配表。
7. 阶段切换、首次阻塞、不通过或验收口径变化时，重新评估直接执行是否仍安全，或重新分档。子智能体超时或悬挂时先只读核验，再按风险收回直接执行、补充上下文或重派，不持续硬等。
8. 收口时由主线程统一验收，只看正式交付物、验收证据、git 状态、阻塞项和剩余风险，不把临时日志、截图名、run id 写入长期规则。
   - 若 AutoPilot 已激活任务评分，先确认硬门禁全部通过，再区分 `implementationCommit` 与 `closeoutCommit`；评分绑定前者，后者及 ledger 登记成功后才增加跨批次回顾计数。
   - 未经用户明确批准的评分版本只能处于 candidate/disabled 状态。达到20个有效任务后，无界模式不得选择第21个任务；有界批次完成当前 N 后整批回顾且不结转。
   - AutoPilot 恢复先读取 durable Issue phase checkpoint；证据有效时保留 worktree 并从 D/E/F 首个未完成阶段继续，Reviewer `tool_config` 不得回退到 B/C。控制面指纹变化后，N>1/无界执行必须先通过用户明确启动的单任务金丝雀。
   - `autopilot-task-score/v2` 已按35/25/20/10/10获批，`taskExecutionEfficiency=10` 自批准配置提交后的下一项新实施型 Ready 生效；v1 历史不回算，同一任务只允许一个正式评分版本和一个20任务计数。
   - 回顾只生成并去重写入唯一问题事实源的 `NEEDS_CONFIRMATION` 提案；报告、事实写回、图谱游标和稳定 Episode 未全部读回前不得清零，也不得自动实施提案或恢复迭代。
9. 最终结论至少明确：
   - `结论=通过 / 不通过`
   - `阻塞=阻塞 / 非阻塞`
   - `是否可上线=可上线 / 不可上线 / 需要确认`

## 默认输出骨架

```text
决策建议=
执行任务=
验收标准=
风险点=
```

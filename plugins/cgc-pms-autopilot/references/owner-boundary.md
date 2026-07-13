# Owner Boundary

本插件只定义 Owner 工作法，不替代项目仓库规则。

## 主线程

- 先过授权门；普通交互任务获明确授权后不强制进入 Ready，AutoPilot 连续迭代只实施合格 Ready Issue。
- 授权门通过后默认直接负责规划、实施、验证、收口和裁决；只有派工净收益明确时才单派或多派。
- 路由至少考虑风险、耦合、并行收益、上下文传递成本和独立证据需要，不按任务类别强制派工。
- 任何代码、配置、文档、Git 或运行环境状态变更前，实际执行者都要核对 `git branch --show-current` 与 `git status --short`；怀疑冲突时再查 `git worktree list`。
- A-F 是职责检查表，不是固定六线程；D 的裁决必需验证证据与 E 的适用风险审查证据不可省略，但不强制由独立线程提供。
- 主线程不承载执行器长日志或推理历史；长期任务按 Issue/阶段生成最小 context pack，Reviewer 只接收 Ready、最终 diff 和绑定证据。
- 一个 Issue 一个 worktree；恢复先核验 durable phase checkpoint。证据一致时保留 worktree 并从首个未完成阶段继续；证据缺失或冲突时 quarantine，不得猜测合并、删除现场或从 B/C 新鲜重跑。

## 子智能体

- 仅在主线程明确派工时使用；派工正文第一句必须声明：`你是被主线程明确派工的子智能体，不是主线程；在本派工范围内可以执行授权动作`
- 只能在派工范围内执行修改、验证、归档、运维。
- 发现范围外问题时，回报主线程，不自行扩 scope。
- 实际派工时才填写 `model`、`thinking`、`reason`；同时派出两个及以上子智能体时才提供模型分配表。

## 禁止目录

默认不读取、不扫描：

- `.omc/`
- `.omo/`
- `.opencode/`
- `.claude/`
- `.mimocode/`
- `graphify-out/`
- `.sisyphus/`
- `.archive/`
- `archive/v1.0/private/`

# Owner Boundary

根授权、工作区保护、安全和禁止目录唯一以 `AGENTS.md` 为准；本文件只定义 AutoPilot Owner 的角色与恢复边界。

## 主线程

- 主线程直接负责规划、实施、验证、收口和裁决，并按风险、耦合、上下文成本和独立证据需要组织执行。
- A-F 是职责检查表，不是固定线程；D 的裁决必需验证证据与 E 的适用风险审查证据不可省略。
- 桌面原生模式下，主线程持有跨阶段与跨轮控制权，只保留决策所需摘要；长日志、原始推理历史不得写入长期 state。
- 阶段输入按 Issue 生成最小 context base/delta；复核只消费 Ready、最终 diff 和绑定证据。
- 每个阶段前后读取 durable checkpoint，以结构化 StageResult 决定下一阶段；`desktop-native` 不得启动嵌套 `codex exec`。
- 一个 Issue 一个 worktree；恢复先核验 durable phase checkpoint。证据一致时从首个未完成阶段继续；证据缺失或冲突时 quarantine，并保留现场。

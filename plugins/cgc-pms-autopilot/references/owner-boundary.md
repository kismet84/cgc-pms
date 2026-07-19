# Owner Boundary

本插件只定义 Owner 工作法，不替代项目仓库规则。

## 主线程

- 先过授权门；普通交互任务获明确授权后不强制进入 Ready，AutoPilot 连续迭代只实施合格 Ready Issue。
- 授权门通过后，主线程直接负责规划、实施、验证、收口和裁决。
- 执行方案至少考虑风险、耦合、上下文传递成本和独立证据需要。
- 任何代码、配置、文档、Git 或运行环境状态变更前，主线程都要核对 `git branch --show-current` 与 `git status --short`；怀疑冲突时再查 `git worktree list`。
- A-F 是职责检查表，不是固定线程；D 的裁决必需验证证据与 E 的适用风险审查证据不可省略。
- 桌面原生模式下，主线程持有跨阶段与跨轮控制权，只保留决策所需摘要；长日志、原始推理历史不得写入长期 state。阶段输入按 Issue 生成最小 context base/delta，复核只消费 Ready、最终 diff 和绑定证据。
- 主线程每个阶段前后读取 durable checkpoint，以结构化 StageResult 决定下一阶段；不得调用旧 runner 代替桌面编排，也不得在 `desktop-native` 下启动嵌套 `codex exec`。
- 一个 Issue 一个 worktree；恢复先核验 durable phase checkpoint。证据一致时保留 worktree 并从首个未完成阶段继续；证据缺失或冲突时 quarantine，不得猜测合并、删除现场或从 B/C 新鲜重跑。

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

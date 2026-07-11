# 第 36 条主线 AutoPilot 无人值守验收报告

日期：2026-07-11

## 最终结论

结论：**通过**。

阻塞：无。

上线口径：本次“通过”表示本地 AutoPilot 统一控制面和低/中风险串行无人值守能力可投入受控使用；不等于生产发布放行，不授权 push，不启用并发 2。

## 交付物

- 单一控制面：`scripts/codex-autopilot/autopilot-run-continuous.ps1`；插件 runner 仅作兼容预演入口。
- 严格 Ready 合同、风险路由、隔离 worktree、最小上下文包、验证证据、独立 Reviewer、有限修复、恢复与幂等收口。
- state v2 原子写入与事件流；运行态 state/log/lock 不再作为 Git 事实源。
- 45 分钟 Implementer、20 分钟 Repair、5/10 分钟停滞检查、单 Issue 默认 20 文件预算。
- 20-Issue qualification、故障注入和全套 PowerShell 自测。
- 基线、资格和本验收报告。

## 多维度验收

| 维度 | 验收结果 | 依据 |
|---|---|---|
| 任务执行时长 | 通过 | 20-Issue 最终窗口 312.6 秒；分阶段事件可计算 queue/active/verify/review/closeout |
| 任务完成度 | 通过 | 20/20 Done；Blocked 不计完成额度；迭代上限准确停止 |
| 产出物 | 通过 | Ready、正式报告、证据 JSON、result、commit、Done 台账形成闭环 |
| 代码质量 | 通过 | 先后独立审查识别并修复控制面、恢复、证据、路由、补货与资格统计缺陷；针对性回归与完整 runner 回归通过 |
| 安全边界 | 通过 | 命令白名单、仓库目录约束、allowlist/forbidden 最终复检、baseBranch、no push、无生产连接 |
| 上下文污染 | 通过 | 一 Issue 一 worktree；implement/repair/review 独立上下文；Reviewer 只读且不接收 Implementer 推理/原始日志；上下文预算 fail-close |
| 无人值守恢复 | 通过 | 停滞终止、一次缩小上下文重试、死锁恢复、不确定提交安全重跑、closeout 幂等键 |

## 上下文污染预防与解决方案

1. 主线程只保留 Issue ID、阶段、结论、证据路径、commit 和剩余风险，不承载长日志与实现推理。
2. 每个 Issue 使用独立 worktree；修复阶段生成新 context pack，不复用漂移会话。
3. context pack 只包含目标、非目标、验收标准、允许/禁止路径、验证命令、最多 12 个相关符号、最多 5 KB 前阶段摘要和最多 20 个变更文件。
4. 原始日志只进入运行目录，正式报告仅保留裁决所需摘要；Reviewer 仅读取 Ready、最终 diff 和绑定证据。
5. diff、Ready、evidence 和 Reviewer 均用 hash/issueId/baseCommit 绑定；任何过期或不匹配直接阻塞。
6. 长任务 5 分钟检查、10 分钟终止；只允许一次停滞后的新鲜缩小上下文重试，第二次失败安全 Blocked。

## 验收证据

- `test-continuous-runner.ps1`：通过。
- 其余控制面、状态、路由、上下文、证据、Reviewer、补货、恢复、收口、停滞、修复、计数测试：通过。
- `test-unattended-canary.ps1`：20/20，通过，312.6 秒。
- `git diff --check`：通过。
- 独立代码审查：采用完整审查与分段复审；所有已回传 P1/P2 均已修复并加入回归保护。审查工具两次大范围超时按工具类处理，未作为通过证据。

## 非阻塞观察项

- 并发 2 保持关闭，待真实业务 20-Issue 滚动窗口再评估。
- 不执行 push、不发布生产；如需扩大授权，必须重新过项目门禁。


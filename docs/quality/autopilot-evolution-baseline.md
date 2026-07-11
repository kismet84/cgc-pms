# AutoPilot 统一演进基线

## 结论

- 样本：主工作区 `.codex-autopilot/runs` 中按结果文件时间倒序的最近 10 个运行结果。
- 结果：`done=7`、`blocked=2`、`noop=1`；排除 dry-run 后，9 个真实运行的事件首尾跨度 P50 为 `453.7s`，P95 为 `728.9s`。
- 完整完成度：`0/9` 可由现有结果文件独立证明“正式产物、验证、backlog 收口、本地 commit”四项齐全；现有结果没有绑定 commit。
- 裁决：旧机制已证明可以选择和执行 Issue，但没有足够证据证明完整无人值守、独立复核、上下文隔离或崩溃幂等恢复。

## 样本摘要

| Issue | 状态 | 失败分类 | 事件跨度（秒） | 结果内验证项 | 结果内 commit |
|---|---|---|---:|---:|---|
| ISSUE-032-008 | done | none | 310.6 | 2 | 缺失 |
| ISSUE-032-007 | done | none | 339.1 | 2 | 缺失 |
| ISSUE-032-006 | blocked | quality_security | 301.3 | 2 | 缺失 |
| ISSUE-032-004 | done | none | 341.9 | 2 | 缺失 |
| ISSUE-008-007 | blocked | quality_security | 453.7 | 2 | 缺失 |
| ISSUE-008-009 | done | none | 728.9 | 2 | 缺失 |
| ISSUE-008-008 | done | none | 557.8 | 2 | 缺失 |
| ISSUE-008-007 | done | none | 651.8 | 2 | 缺失 |
| ISSUE-008-006 | done | none | 578.2 | 2 | 缺失 |
| ISSUE-008-006 | noop | none | 0.1 | 2 | 不适用 |

事件跨度仅表示同一运行事件文件中最早与最晚时间戳之差，不等价于纯执行时长。

## 第36主线指标基线

| 指标 | 当前证据 | 基线结论 |
|---|---|---|
| queueWaitSeconds | 运行结果没有 Ready 入队时间 | 证据缺失 |
| activeSeconds | 只有事件首尾跨度，未分离执行阶段 | 不可复算 |
| verifySeconds | 未记录验证阶段开始/结束 | 证据缺失 |
| reviewSeconds | 未记录独立 Reviewer 阶段 | 证据缺失 |
| repairSeconds | 未记录修复阶段和轮次预算 | 证据缺失 |
| closeoutSeconds | 未记录最终验证到 commit 的区间 | 证据缺失 |
| firstPassSuccess | 无首次实现与修复轮次字段 | 证据缺失 |
| completionIntegrity | 9 个真实运行均无 commit 绑定 | `0/9` 可证明完整 |
| reviewFindingCount | 无结构化 Reviewer 结果 | 证据缺失 |
| changeChurn | 无首次 diff 与最终 diff hash | 证据缺失 |
| manualInterventionCount | 无人工介入事件 | 证据缺失 |
| scopeViolationCount | 无 allowlist 与实际 diff 的结构化比对结果 | 证据缺失 |

## 机制差异

| 维度 | 旧连续运行器 | 新插件/治理能力 | 统一演进缺口 |
|---|---|---|---|
| 控制面 | 能真实选择和执行 Issue | 能 dry-run、checkpoint、分类 | 只能保留一个真实状态写入者 |
| 状态 | 记录迭代数量和最近动作 | 定义 loop schema | 需要原子 state v2 和合法迁移 |
| Ready | 已有基础 lint | 规则要求更严格 | 需要 hash、非目标、路径、migration、风险契约 |
| 执行 | Codex 长超时执行 | A–F 和自适应路由 | 需要新鲜会话、worktree 和上下文预算 |
| 验证 | 结果内有少量 validation | 失败分类更清晰 | 需要绑定 commit/diff 的证据清单 |
| 审查 | 无结构化独立 Reviewer 证据 | 规则要求高风险复核 | 需要 Reviewer 协议和硬门禁 |
| 恢复 | 有 lock、heartbeat 和迭代修复基础 | 有 stop/pause checkpoint | 需要幂等键、停滞预算和污染恢复 |

## 基线测试状态

- 现有 `test-continuous-runner.ps1` 已覆盖多种 runner、Ready、并发、lock 和迭代计数场景。
- 一次完整基线调用在外层 124 秒限制内未结束；进程仍在依次推进 fixture，没有证据表明断言失败。
- 该入口缺少逐场景进度和总耗时输出，后续必须拆出职责测试并保留完整 runner 回归，避免“无输出长等待”掩盖真实停滞。

## 验收口径

统一实现完成后，以同一统计口径重新采集；未知字段不得填默认成功值。连续 20 个低/中风险实施型 Issue 达到第36主线资格线前，不得宣称完整无人值守通过。
